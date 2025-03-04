/**
 * This inspection is used for type checking. It also warns if a field is referenced in a
 * query but doesn't exist in the MongoDB schema.
 */

package com.mongodb.jbplugin.inspections.impl

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridge
import com.mongodb.jbplugin.linting.NamespaceCheckingLinter
import com.mongodb.jbplugin.linting.NamespaceCheckingSettings
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.probe.InspectionStatusChangedProbe
import kotlinx.coroutines.CoroutineScope

/**
 * @param coroutineScope
 */
class NamespaceCheckInspectionBridge(coroutineScope: CoroutineScope) :
    AbstractMongoDbInspectionBridge<NamespaceCheckingSettings<LocalDataSource>>(
        coroutineScope,
        NamespaceCheckingLinter(),
    ) {
    override fun buildSettings(
        query: Node<PsiElement>
    ): NamespaceCheckingSettings<LocalDataSource> {
        val dataSource = query.source.containingFile.dataSource!!
        val readModelProvider by query.source.project.service<DataGripBasedReadModelProvider>()

        return NamespaceCheckingSettings(dataSource, readModelProvider)
    }

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
