/**
 * This inspection is used for type checking. It also warns if a field is referenced in a
 * query but doesn't exist in the MongoDB schema.
 */

package com.mongodb.jbplugin.inspections.impl

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.i18n.InspectionsAndInlaysMessages
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridge
import com.mongodb.jbplugin.inspections.MongoDbInspection
import com.mongodb.jbplugin.inspections.quickfixes.OpenConnectionChooserQuickFix
import com.mongodb.jbplugin.linting.NamespaceCheckWarning
import com.mongodb.jbplugin.linting.NamespaceCheckingLinter
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.probe.InspectionStatusChangedProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

/**
 * @param coroutineScope
 */
class NamespaceCheckInspectionBridge(coroutineScope: CoroutineScope) :
    AbstractMongoDbInspectionBridge(
        coroutineScope,
        NamespaceCheckingLinterInspection,
    ) {
    override fun emitFinishedInspectionTelemetryEvent(problemsHolder: ProblemsHolder) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.finishedProcessingInspections(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.NO_NAMESPACE_INFERRED,
            problemsHolder
        )

        probe.finishedProcessingInspections(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.DATABASE_DOES_NOT_EXIST,
            problemsHolder
        )

        probe.finishedProcessingInspections(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.COLLECTION_DOES_NOT_EXIST,
            problemsHolder
        )
    }
}

/**
 * This inspection object calls the linting engine and transforms the result so they can be rendered in the IntelliJ
 * editor.
 */
internal object NamespaceCheckingLinterInspection : MongoDbInspection {
    override fun visitMongoDbQuery(
        coroutineScope: CoroutineScope,
        dataSource: LocalDataSource?,
        problems: ProblemsHolder,
        query: Node<PsiElement>,
        formatter: DialectFormatter,
    ) {
        if (dataSource == null || !dataSource.isConnected()) {
            return
        }

        val readModelProvider by query.source.project.service<DataGripBasedReadModelProvider>()
        val result = runBlocking {
            NamespaceCheckingLinter.lintQuery(
                dataSource,
                readModelProvider,
                query,
            )
        }

        result.warnings.forEach {
            when (it) {
                is NamespaceCheckWarning.NoNamespaceInferred ->
                    registerNoNamespaceInferred(coroutineScope, problems, it.source, query)
                is NamespaceCheckWarning.CollectionDoesNotExist ->
                    registerCollectionDoesNotExist(
                        coroutineScope,
                        problems,
                        it.source,
                        it.database,
                        it.collection,
                        query
                    )
                is NamespaceCheckWarning.DatabaseDoesNotExist ->
                    registerDatabaseDoesNotExist(
                        coroutineScope,
                        problems,
                        it.source,
                        it.database,
                        query
                    )
            }
        }
    }

    private fun registerNoNamespaceInferred(
        coroutineScope: CoroutineScope,
        problems: ProblemsHolder,
        source: PsiElement,
        query: Node<PsiElement>
    ) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.inspectionChanged(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.NO_NAMESPACE_INFERRED,
            query
        )

        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.namespace.checking.error.message",
        )
        problems.registerProblem(
            source,
            problemDescription,
            ProblemHighlightType.WARNING,
            OpenConnectionChooserQuickFix(
                coroutineScope,
                InspectionsAndInlaysMessages.message(
                    "inspection.field.checking.quickfix.choose.new.connection"
                ),
            ),
        )
    }

    private fun registerDatabaseDoesNotExist(
        coroutineScope: CoroutineScope,
        problems: ProblemsHolder,
        source: PsiElement,
        dbName: String,
        query: Node<PsiElement>
    ) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.inspectionChanged(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.DATABASE_DOES_NOT_EXIST,
            query
        )

        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.namespace.checking.error.message.database.missing",
            dbName
        )
        problems.registerProblem(
            source,
            problemDescription,
            ProblemHighlightType.WARNING,
            OpenConnectionChooserQuickFix(
                coroutineScope,
                InspectionsAndInlaysMessages.message(
                    "inspection.field.checking.quickfix.choose.new.connection"
                ),
            ),
        )
    }

    private fun registerCollectionDoesNotExist(
        coroutineScope: CoroutineScope,
        problems: ProblemsHolder,
        source: PsiElement,
        dbName: String,
        collName: String,
        query: Node<PsiElement>
    ) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.inspectionChanged(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.COLLECTION_DOES_NOT_EXIST,
            query
        )

        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.namespace.checking.error.message.collection.missing",
            collName,
            dbName
        )

        problems.registerProblem(
            source,
            problemDescription,
            ProblemHighlightType.WARNING,
            OpenConnectionChooserQuickFix(
                coroutineScope,
                InspectionsAndInlaysMessages.message(
                    "inspection.field.checking.quickfix.choose.new.connection"
                ),
            ),
        )
    }
}
