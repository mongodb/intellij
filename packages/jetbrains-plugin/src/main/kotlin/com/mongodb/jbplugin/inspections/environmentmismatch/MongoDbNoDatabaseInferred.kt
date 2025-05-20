package com.mongodb.jbplugin.inspections.environmentmismatch

import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridgeV2
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionGlobalTool
import com.mongodb.jbplugin.linting.Inspection.NoDatabaseInferred
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.environmentmismatch.NoDatabaseInferredInspection
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.probe.InspectionStatusChangedProbe
import kotlinx.coroutines.CoroutineScope

class MongoDbNoDatabaseInferredGlobalTool : AbstractMongoDbInspectionGlobalTool(
    NoDatabaseInferred
)
class MongoDbNoDatabaseInferred(
    coroutineScope: CoroutineScope,
) : AbstractMongoDbInspectionBridgeV2<
    Unit,
    NoDatabaseInferred
    >(coroutineScope, NoDatabaseInferredInspection(), NoDatabaseInferred) {
    override fun buildSettings(query: Node<PsiElement>) {}

    override fun afterInsight(queryInsight: QueryInsight<PsiElement, NoDatabaseInferred>) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.inspectionChanged(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.NO_DATABASE_INFERRED,
            queryInsight.query
        )
    }

    override fun emitFinishedInspectionTelemetryEvent(queryInsights: List<QueryInsight<PsiElement, NoDatabaseInferred>>) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.finishedProcessingInspections(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.NO_DATABASE_INFERRED,
            queryInsights
        )
    }
}
