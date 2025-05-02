package com.mongodb.jbplugin.observability.probe

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.meta.withinReadActionBlocking
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryEvent.InspectionStatusChangeEvent.InspectionType
import com.mongodb.jbplugin.observability.TelemetryService
import com.mongodb.jbplugin.observability.probe.InspectionStatusChangedProbe.UniqueInspection
import com.mongodb.jbplugin.observability.useLogMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger: Logger = logger<InspectionStatusChangedProbe>()

internal typealias ProblemsByInspectionType = MutableMap<InspectionType, MutableList<UniqueInspection>>

@Service
class InspectionStatusChangedProbe(
    private val cs: CoroutineScope
) {
    internal data class UniqueInspection(val id: UUID, val on: WeakReference<Node<PsiElement>>) {
        companion object {
            fun new(query: Node<PsiElement>): UniqueInspection {
                return UniqueInspection(UUID.randomUUID(), WeakReference(query))
            }
        }
    }

    private val mutex: ReentrantLock = ReentrantLock()
    private val problemsByInspectionType: ProblemsByInspectionType = ConcurrentHashMap()

    /**
     * We are using this function because we don't really care much about exceptions here. It's just
     * telemetry, and we will just fail safely and wait for the next event to happen. This is better
     * than raising an exception to the user.
     */
    private fun runSafe(fn: () -> Unit) {
        runCatching {
            fn()
        }.getOrNull()
    }

    fun inspectionChanged(inspectionType: InspectionType, query: Node<PsiElement>) = runSafe {
        val dialect = query.component<HasSourceDialect>() ?: return@runSafe
        val psiElement = query.source

        cs.launch(Dispatchers.IO) {
            mutex.withLock {
                val elementsWithProblems = problemsByInspectionType(inspectionType)

                // check if the element is already in the list
                if (isElementRegistered(elementsWithProblems) { psiElement }) {
                    // do nothing, it's already registered
                    return@launch
                }

                // it's a new error, send a telemetry event and store it
                val inspection = UniqueInspection.new(query)
                elementsWithProblems.add(inspection)

                val telemetry by service<TelemetryService>()
                val event = TelemetryEvent.InspectionStatusChangeEvent(
                    dialect = dialect.name,
                    inspectionType = inspectionType,
                    inspectionStatus = TelemetryEvent.InspectionStatusChangeEvent.InspectionStatus.ACTIVE,
                    null,
                    null
                )

                telemetry.sendEvent(event)
                logger.info(
                    useLogMessage("New inspection triggered")
                        .put("inspection_id", inspection.id.toString())
                        .mergeTelemetryEventProperties(event)
                        .build()
                )
            }
        }
    }

    fun typeMismatchInspectionActive(query: Node<PsiElement>, actualType: String, expectedType: String) = runSafe {
        val inspectionType = InspectionType.TYPE_MISMATCH
        val dialect = query.component<HasSourceDialect>() ?: return@runSafe
        val psiElement = query.source

        cs.launch(Dispatchers.IO) {
            mutex.withLock {
                val elementsWithProblems = problemsByInspectionType(inspectionType)

                if (isElementRegistered(elementsWithProblems) { psiElement }) {
                    // do nothing, it's already registered
                    return@launch
                }

                // it's a new error, send a telemetry event and store it
                val inspection = UniqueInspection.new(query)
                elementsWithProblems.add(inspection)

                val telemetry by service<TelemetryService>()
                val event = TelemetryEvent.InspectionStatusChangeEvent(
                    dialect = dialect.name,
                    inspectionType = inspectionType,
                    inspectionStatus = TelemetryEvent.InspectionStatusChangeEvent.InspectionStatus.ACTIVE,
                    actualFieldType = actualType,
                    expectedFieldType = expectedType,
                )

                telemetry.sendEvent(event)
                logger.info(
                    useLogMessage("New inspection triggered")
                        .put("inspection_id", inspection.id.toString())
                        .mergeTelemetryEventProperties(event)
                        .build()
                )
            }
        }
    }

    fun finishedProcessingInspections(inspectionType: InspectionType, problemsHolder: ProblemsHolder) = runSafe {
        cs.launch(Dispatchers.IO) {
            val results = problemsHolder.results
            // check all our registered problems
            // if at the end of the processing cycle it's empty
            // we will assume they are
            mutex.withLock {
                val elementsWithProblems = problemsByInspectionType(inspectionType)

                for (elementWithProblem in elementsWithProblems) {
                    val findEquivalentProblem = results.find {
                        isElementRegistered(elementsWithProblems, it::getPsiElement)
                    }
                    if (findEquivalentProblem != null) {
                        // the problem is still there, so don't do anything
                        // do nothing, it's already registered
                        continue
                    }

                    elementsWithProblems.remove(elementWithProblem)

                    val dialect =
                        elementWithProblem.on.get()?.component<HasSourceDialect>() ?: continue
                    val telemetry by service<TelemetryService>()
                    val event = TelemetryEvent.InspectionStatusChangeEvent(
                        dialect = dialect.name,
                        inspectionType = inspectionType,
                        inspectionStatus = TelemetryEvent.InspectionStatusChangeEvent.InspectionStatus.RESOLVED,
                        null,
                        null
                    )

                    telemetry.sendEvent(event)
                    logger.info(
                        useLogMessage("Inspection resolved")
                            .put("inspection_id", elementWithProblem.id.toString())
                            .mergeTelemetryEventProperties(event)
                            .build()
                    )
                }
            }
        }
    }

    private fun isElementRegistered(
        elementsWithProblems: MutableList<UniqueInspection>,
        psiElement: () -> PsiElement
    ): Boolean = runCatching {
        withinReadActionBlocking {
            elementsWithProblems.find {
                val isStrictlyEqual = it.on.get()?.source == psiElement()
                val isEquivalent = it.on.get()?.source?.isEquivalentTo(psiElement()) == true

                isStrictlyEqual || isEquivalent
            } != null
        }
    }.getOrDefault(false)

    private fun problemsByInspectionType(inspectionType: InspectionType): MutableList<UniqueInspection> {
        val result = problemsByInspectionType.computeIfAbsent(inspectionType) {
            CopyOnWriteArrayList()
        }

        result.removeAll { it.on.get() == null }
        return result
    }
}
