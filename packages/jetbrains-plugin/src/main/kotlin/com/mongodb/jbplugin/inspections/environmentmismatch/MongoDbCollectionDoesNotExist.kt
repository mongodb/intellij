package com.mongodb.jbplugin.inspections.environmentmismatch

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridgeV2
import com.mongodb.jbplugin.linting.Inspection.CollectionDoesNotExist
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.environmentmismatch.CollectionDoesNotExistInspection
import com.mongodb.jbplugin.linting.environmentmismatch.CollectionDoesNotExistInspectionSettings
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.probe.InspectionStatusChangedProbe
import kotlinx.coroutines.CoroutineScope

class MongoDbCollectionDoesNotExist(
    coroutineScope: CoroutineScope,
) : AbstractMongoDbInspectionBridgeV2<
    CollectionDoesNotExistInspectionSettings<LocalDataSource>,
    CollectionDoesNotExist
    >(
    coroutineScope,
    CollectionDoesNotExistInspection(),
    CollectionDoesNotExist,
) {
    override fun buildSettings(
        query: Node<PsiElement>
    ): CollectionDoesNotExistInspectionSettings<LocalDataSource> {
        val readModelProvider by query.source.project.service<DataGripBasedReadModelProvider>()
        val dataSource = query.source.containingFile.dataSource!!

        return CollectionDoesNotExistInspectionSettings(
            dataSource,
            readModelProvider,
        )
    }

    override fun afterInsight(queryInsight: QueryInsight<PsiElement, CollectionDoesNotExist>) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.inspectionChanged(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.COLLECTION_DOES_NOT_EXIST,
            queryInsight.query
        )
    }

    override fun emitFinishedInspectionTelemetryEvent(problemsHolder: ProblemsHolder) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.finishedProcessingInspections(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.COLLECTION_DOES_NOT_EXIST,
            problemsHolder
        )
    }
}
