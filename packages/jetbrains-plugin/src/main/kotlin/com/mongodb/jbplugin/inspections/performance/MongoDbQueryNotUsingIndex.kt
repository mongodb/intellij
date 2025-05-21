package com.mongodb.jbplugin.inspections.performance

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridge
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionGlobalTool
import com.mongodb.jbplugin.linting.Inspection.NotUsingIndex
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.performance.QueryNotUsingIndexInspection
import com.mongodb.jbplugin.linting.performance.QueryNotUsingIndexInspectionSettings
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType.FULL
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType.SAFE
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.probe.InspectionStatusChangedProbe
import com.mongodb.jbplugin.settings.pluginSetting
import kotlinx.coroutines.CoroutineScope

class MongoDbQueryNotUsingIndexGlobalTool : AbstractMongoDbInspectionGlobalTool(NotUsingIndex)
class MongoDbQueryNotUsingIndex(coroutineScope: CoroutineScope) : AbstractMongoDbInspectionBridge<
    QueryNotUsingIndexInspectionSettings<LocalDataSource>,
    NotUsingIndex
    >(
    coroutineScope,
    QueryNotUsingIndexInspection(),
    NotUsingIndex
) {
    override fun buildSettings(query: Node<PsiElement>): QueryNotUsingIndexInspectionSettings<LocalDataSource> {
        val isFullExplainPlanEnabled by pluginSetting { ::isFullExplainPlanEnabled }
        val explainPlanType = if (isFullExplainPlanEnabled) {
            FULL
        } else {
            SAFE
        }
        val readModelProvider by query.source.project.service<DataGripBasedReadModelProvider>()

        return QueryNotUsingIndexInspectionSettings(
            query.source.containingFile.dataSource!!,
            readModelProvider,
            explainPlanType
        )
    }

    override fun afterInsight(queryInsight: QueryInsight<PsiElement, NotUsingIndex>) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.inspectionChanged(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.QUERY_NOT_COVERED_BY_INDEX,
            queryInsight.query
        )
    }

    override fun emitFinishedInspectionTelemetryEvent(queryInsights: List<QueryInsight<PsiElement, NotUsingIndex>>) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.finishedProcessingInspections(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.QUERY_NOT_COVERED_BY_INDEX,
            queryInsights,
        )
    }
}
