package com.mongodb.jbplugin.inspections.performance

import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridge
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionGlobalTool
import com.mongodb.jbplugin.linting.Inspection.NotUsingFilters
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.performance.QueryNotUsingFiltersInspection
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.probe.InspectionStatusChangedProbe
import kotlinx.coroutines.CoroutineScope

class MongoDbQueryNotUsingFiltersGlobalTool : AbstractMongoDbInspectionGlobalTool(
    NotUsingFilters
)

class MongoDbQueryNotUsingFilters(coroutineScope: CoroutineScope) : AbstractMongoDbInspectionBridge<
    Unit,
    NotUsingFilters
    >(
    coroutineScope = coroutineScope,
    queryInspection = QueryNotUsingFiltersInspection(),
    inspection = NotUsingFilters
) {
    override fun buildSettings(query: Node<PsiElement>) {}

    override fun afterInsight(queryInsight: QueryInsight<PsiElement, NotUsingFilters>) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.inspectionChanged(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.QUERY_NOT_COVERED_BY_INDEX,
            queryInsight.query
        )
    }

    override fun emitFinishedInspectionTelemetryEvent(
        queryInsights: List<QueryInsight<PsiElement, NotUsingFilters>>
    ) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.finishedProcessingInspections(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.QUERY_NOT_COVERED_BY_INDEX,
            queryInsights,
        )
    }
}
