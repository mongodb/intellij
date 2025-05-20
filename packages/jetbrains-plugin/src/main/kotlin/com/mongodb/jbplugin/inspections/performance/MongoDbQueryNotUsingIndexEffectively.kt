package com.mongodb.jbplugin.inspections.performance

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridgeV2
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionGlobalTool
import com.mongodb.jbplugin.linting.Inspection.NotUsingIndexEffectively
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.performance.QueryNotUsingIndexEffectivelyInspection
import com.mongodb.jbplugin.linting.performance.QueryNotUsingIndexEffectivelyInspectionSettings
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType.FULL
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType.SAFE
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.probe.InspectionStatusChangedProbe
import com.mongodb.jbplugin.settings.pluginSetting
import kotlinx.coroutines.CoroutineScope

class MongoDbQueryNotUsingIndexEffectivelyGlobalTool : AbstractMongoDbInspectionGlobalTool(NotUsingIndexEffectively)
class MongoDbQueryNotUsingIndexEffectively(coroutineScope: CoroutineScope) : AbstractMongoDbInspectionBridgeV2<
    QueryNotUsingIndexEffectivelyInspectionSettings<LocalDataSource>,
    NotUsingIndexEffectively
    >(
    coroutineScope,
    QueryNotUsingIndexEffectivelyInspection(),
    NotUsingIndexEffectively
) {
    override fun buildSettings(query: Node<PsiElement>): QueryNotUsingIndexEffectivelyInspectionSettings<LocalDataSource> {
        val isFullExplainPlanEnabled by pluginSetting { ::isFullExplainPlanEnabled }
        val explainPlanType = if (isFullExplainPlanEnabled) {
            FULL
        } else {
            SAFE
        }
        val readModelProvider by query.source.project.service<DataGripBasedReadModelProvider>()

        return QueryNotUsingIndexEffectivelyInspectionSettings(
            query.source.containingFile.dataSource!!,
            readModelProvider,
            explainPlanType
        )
    }

    override fun afterInsight(queryInsight: QueryInsight<PsiElement, NotUsingIndexEffectively>) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.inspectionChanged(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.QUERY_NOT_COVERED_BY_INDEX,
            queryInsight.query
        )
    }

    override fun emitFinishedInspectionTelemetryEvent(queryInsights: List<QueryInsight<PsiElement, NotUsingIndexEffectively>>) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.finishedProcessingInspections(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.QUERY_NOT_COVERED_BY_INDEX,
            queryInsights,
        )
    }
}
