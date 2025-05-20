package com.mongodb.jbplugin.inspections.correctness

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridge
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionGlobalTool
import com.mongodb.jbplugin.linting.Inspection.FieldDoesNotExist
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.correctness.FieldDoesNotExistInspection
import com.mongodb.jbplugin.linting.correctness.FieldDoesNotExistInspectionSettings
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.probe.InspectionStatusChangedProbe
import com.mongodb.jbplugin.settings.pluginSetting
import kotlinx.coroutines.CoroutineScope

class MongoDbFieldDoesNotExistGlobalTool : AbstractMongoDbInspectionGlobalTool(FieldDoesNotExist)
class MongoDbFieldDoesNotExist(coroutineScope: CoroutineScope) : AbstractMongoDbInspectionBridge<
    FieldDoesNotExistInspectionSettings<LocalDataSource>,
    FieldDoesNotExist
    >(
    coroutineScope,
    FieldDoesNotExistInspection(),
    FieldDoesNotExist
) {
    override fun buildSettings(query: Node<PsiElement>): FieldDoesNotExistInspectionSettings<LocalDataSource> {
        val documentsSampleSize by pluginSetting { ::sampleSize }
        val readModelProvider by query.source.project.service<DataGripBasedReadModelProvider>()
        val dataSource = query.source.containingFile.dataSource!!

        return FieldDoesNotExistInspectionSettings(
            dataSource,
            readModelProvider,
            documentsSampleSize
        )
    }

    override fun afterInsight(queryInsight: QueryInsight<PsiElement, FieldDoesNotExist>) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.inspectionChanged(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.FIELD_DOES_NOT_EXIST,
            queryInsight.query
        )
    }

    override fun emitFinishedInspectionTelemetryEvent(queryInsights: List<QueryInsight<PsiElement, FieldDoesNotExist>>) {
        val probe by service<InspectionStatusChangedProbe>()

        probe.finishedProcessingInspections(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.FIELD_DOES_NOT_EXIST,
            queryInsights
        )
    }
}
