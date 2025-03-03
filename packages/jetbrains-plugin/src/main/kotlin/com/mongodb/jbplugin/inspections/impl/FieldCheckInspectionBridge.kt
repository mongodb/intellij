/**
 * This inspection is used for type checking. It also warns if a field is referenced in a
 * query but doesn't exist in the MongoDB schema.
 */

package com.mongodb.jbplugin.inspections.impl

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.Inspection
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.i18n.InspectionsAndInlaysMessages
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridge
import com.mongodb.jbplugin.inspections.IntelliJBasedInspectionHolder
import com.mongodb.jbplugin.inspections.MongoDbInspection
import com.mongodb.jbplugin.linting.FieldCheckWarning
import com.mongodb.jbplugin.linting.FieldCheckingLinter
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.probe.InspectionStatusChangedProbe
import com.mongodb.jbplugin.settings.pluginSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class FieldCheckInspectionBridge(coroutineScope: CoroutineScope) :
    AbstractMongoDbInspectionBridge(
        coroutineScope,
        FieldCheckLinterInspection,
    ) {
    override fun emitFinishedInspectionTelemetryEvent(problemsHolder: ProblemsHolder) {
        val probe by service<InspectionStatusChangedProbe>()

        probe.finishedProcessingInspections(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.FIELD_DOES_NOT_EXIST,
            problemsHolder
        )

        probe.finishedProcessingInspections(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.TYPE_MISMATCH,
            problemsHolder
        )
    }
}

/**
 * This inspection object calls the linting engine and transforms the result so they can be rendered in the IntelliJ
 * editor.
 */
internal object FieldCheckLinterInspection : MongoDbInspection {
    override fun visitMongoDbQuery(
        coroutineScope: CoroutineScope,
        dataSource: LocalDataSource?,
        problems: IntelliJBasedInspectionHolder,
        query: Node<PsiElement>,
        formatter: DialectFormatter,
    ) {
        if (dataSource == null || !dataSource.isConnected()) {
            return registerNoConnectionProblem(coroutineScope, problems, query)
        }

        if (query.component<HasCollectionReference<PsiElement>>()?.reference is HasCollectionReference.OnlyCollection) {
            return registerNoDatabaseSelectedProblem(coroutineScope, problems, query)
        }

        val readModelProvider by query.source.project.service<DataGripBasedReadModelProvider>()
        val sampleSize by pluginSetting { ::sampleSize }

        val result = runBlocking {
            FieldCheckingLinter.lintQuery(
                dataSource,
                readModelProvider,
                query,
                sampleSize
            )
        }

        result.warnings.forEach {
            when (it) {
                is FieldCheckWarning.FieldDoesNotExist -> registerFieldDoesNotExistProblem(
                    coroutineScope,
                    problems,
                    it,
                    query
                )
                is FieldCheckWarning.FieldValueTypeMismatch ->
                    registerFieldValueTypeMismatch(coroutineScope, problems, it, formatter, query)
            }
        }
    }

    private fun registerNoConnectionProblem(
        coroutineScope: CoroutineScope,
        problems: IntelliJBasedInspectionHolder,
        query: Node<PsiElement>
    ) {
        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.field.checking.error.message.no.connection",
        )
        coroutineScope.launch {
            problems.register(
                Inspection.PerformanceWarning(query, problemDescription, Inspection.NoAction)
            )
        }
        //
        // problems.registerProblem(
        //     source,
        //     problemDescription,
        //     ProblemHighlightType.WARNING,
        //     OpenConnectionChooserQuickFix(
        //         coroutineScope,
        //         InspectionsAndInlaysMessages.message(
        //             "inspection.field.checking.quickfix.choose.new.connection"
        //         ),
        //     ),
        // )
    }

    private fun registerNoDatabaseSelectedProblem(
        coroutineScope: CoroutineScope,
        problems: IntelliJBasedInspectionHolder,
        source: Node<PsiElement>
    ) {
        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.field.checking.error.message.no.database",
        )
        coroutineScope.launch {
            problems.register(
                Inspection.PerformanceWarning(
                    source,
                    problemDescription,
                    Inspection.NoAction
                )
            )
        }
        // problems.registerProblem(
        //     source,
        //     problemDescription,
        //     ProblemHighlightType.WARNING,
        //     OpenConnectionChooserQuickFix(
        //         coroutineScope,
        //         InspectionsAndInlaysMessages.message(
        //             "inspection.field.checking.quickfix.choose.new.connection"
        //         ),
        //     ),
        // )
    }

    private fun registerFieldDoesNotExistProblem(
        coroutineScope: CoroutineScope,
        problems: IntelliJBasedInspectionHolder,
        warningInfo: FieldCheckWarning.FieldDoesNotExist<PsiElement>,
        source: Node<PsiElement>,
    ) {
        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.field.checking.error.message",
            warningInfo.field,
            warningInfo.namespace,
        )
        coroutineScope.launch {
            problems.register(
                Inspection.PerformanceWarning(
                    source,
                    problemDescription,
                    Inspection.NoAction
                )
            )
        }

        // problems.registerProblem(
        //     warningInfo.source,
        //     problemDescription,
        //     ProblemHighlightType.WARNING,
        //     OpenConnectionChooserQuickFix(
        //         coroutineScope,
        //         InspectionsAndInlaysMessages.message(
        //             "inspection.field.checking.quickfix.choose.new.connection"
        //         ),
        //     ),
        // )

        val probe by service<InspectionStatusChangedProbe>()
        probe.inspectionChanged(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.FIELD_DOES_NOT_EXIST,
            source
        )
    }

    private fun registerFieldValueTypeMismatch(
        coroutineScope: CoroutineScope,
        problems: IntelliJBasedInspectionHolder,
        warningInfo: FieldCheckWarning.FieldValueTypeMismatch<PsiElement>,
        formatter: DialectFormatter,
        source: Node<PsiElement>
    ) {
        val expectedType = formatter.formatType(warningInfo.fieldType)
        val actualType = formatter.formatType(warningInfo.valueType)

        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.field.checking.error.message.value.type.mismatch",
            actualType,
            expectedType,
            warningInfo.field,
        )
        coroutineScope.launch {
            problems.register(
                Inspection.PerformanceWarning(
                    source,
                    problemDescription,
                    Inspection.NoAction
                )
            )
        }

        // problems.registerProblem(
        //     warningInfo.valueSource,
        //     problemDescription,
        //     ProblemHighlightType.WARNING,
        //     OpenConnectionChooserQuickFix(
        //         coroutineScope,
        //         InspectionsAndInlaysMessages.message(
        //             "inspection.field.checking.quickfix.choose.new.connection"
        //         ),
        //     ),
        // )

        val probe by service<InspectionStatusChangedProbe>()
        probe.typeMismatchInspectionActive(
            source,
            actualType,
            expectedType,
        )
    }
}
