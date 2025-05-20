package com.mongodb.jbplugin.inspections.environmentmismatch

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridgeV2
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionGlobalTool
import com.mongodb.jbplugin.linting.Inspection.CollectionDoesNotExist
import com.mongodb.jbplugin.linting.Inspection.DatabaseDoesNotExist
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.environmentmismatch.DatabaseDoesNotExistInspection
import com.mongodb.jbplugin.linting.environmentmismatch.DatabaseDoesNotExistInspectionSettings
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.probe.InspectionStatusChangedProbe
import kotlinx.coroutines.CoroutineScope

class MongoDbDatabaseDoesNotExistGlobalTool : AbstractMongoDbInspectionGlobalTool(DatabaseDoesNotExist)
class MongoDbDatabaseDoesNotExist(
    coroutineScope: CoroutineScope
) : AbstractMongoDbInspectionBridgeV2<
    DatabaseDoesNotExistInspectionSettings<LocalDataSource>,
    DatabaseDoesNotExist
    >(
    coroutineScope,
    DatabaseDoesNotExistInspection(),
    DatabaseDoesNotExist
) {
    override fun buildSettings(
        query: Node<PsiElement>
    ): DatabaseDoesNotExistInspectionSettings<LocalDataSource> {
        val readModelProvider by query.source.project.service<DataGripBasedReadModelProvider>()
        val dataSource = query.source.containingFile.dataSource!!

        return DatabaseDoesNotExistInspectionSettings(
            dataSource,
            readModelProvider,
        )
    }

    override fun afterInsight(queryInsight: QueryInsight<PsiElement, DatabaseDoesNotExist>) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.inspectionChanged(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.DATABASE_DOES_NOT_EXIST,
            queryInsight.query
        )
    }

    override fun emitFinishedInspectionTelemetryEvent(queryInsights: List<QueryInsight<PsiElement, DatabaseDoesNotExist>>) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.finishedProcessingInspections(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.DATABASE_DOES_NOT_EXIST,
          queryInsights
        )
    }
}
