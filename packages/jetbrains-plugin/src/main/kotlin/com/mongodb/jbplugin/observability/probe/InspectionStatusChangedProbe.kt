package com.mongodb.jbplugin.observability.probe

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryEvent.InspectionStatusChangeEvent.InspectionType
import com.mongodb.jbplugin.observability.TelemetryService
import com.mongodb.jbplugin.observability.useLogMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.WeakReference
import java.util.UUID

private val inspectionTelemetry = Dispatchers.IO
private val logger: Logger = logger<InspectionStatusChangedProbe>()

@Service
class InspectionStatusChangedProbe(
    private val cs: CoroutineScope
) {
    private data class UniqueInspection(val id: UUID, val on: WeakReference<Node<PsiElement>>) {
        companion object {
            fun new(query: Node<PsiElement>): UniqueInspection {
                return UniqueInspection(UUID.randomUUID(), WeakReference(query))
            }
        }
    }

    private val mutex: Mutex = Mutex()

    private val problemsByInspectionType: MutableMap<InspectionType, MutableList<UniqueInspection>> =
        mutableMapOf()

    fun inspectionChanged(inspectionType: InspectionType, query: Node<PsiElement>) {
        val dialect = query.component<HasSourceDialect>() ?: return
        val psiElement = query.source

        cs.launch(inspectionTelemetry) {
            val elementsWithProblems = problemsByInspectionType(inspectionType)

            mutex.withLock {
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

    fun typeMismatchInspectionActive(query: Node<PsiElement>, actualType: String, expectedType: String) {
        val inspectionType = InspectionType.TYPE_MISMATCH
        val dialect = query.component<HasSourceDialect>() ?: return
        val psiElement = query.source

        cs.launch(inspectionTelemetry) {
            val elementsWithProblems = problemsByInspectionType(inspectionType)

            mutex.withLock {
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

    fun finishedProcessingInspections(inspectionType: InspectionType, problemsHolder: ProblemsHolder) {
        cs.launch(inspectionTelemetry) {
            val elementsWithProblems = problemsByInspectionType(inspectionType)

            val results = problemsHolder.results
            // check all our registered problems
            // if at the end of the processing cycle it's empty
            // we will assume they are
            mutex.withLock {
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
        ApplicationManager.getApplication().runReadAction<Boolean> {
            elementsWithProblems.find {
                val isStrictlyEqual = it.on.get()?.source == psiElement()
                val isEquivalent = it.on.get()?.source?.isEquivalentTo(psiElement()) == true

                isStrictlyEqual || isEquivalent
            } != null
        }
    }.getOrDefault(false)

    private suspend fun problemsByInspectionType(inspectionType: InspectionType): MutableList<UniqueInspection> {
        return mutex.withLock {
            val result = problemsByInspectionType.computeIfAbsent(inspectionType) {
                mutableListOf()
            }

            result.removeAll { it.on.get() == null }
            result
        }
    }
}
