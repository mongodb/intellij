package com.mongodb.jbplugin.inspections.correctness

import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridge
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionGlobalTool
import com.mongodb.jbplugin.linting.Inspection.InvalidProjection
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.correctness.InvalidProjectionInspection
import com.mongodb.jbplugin.linting.correctness.InvalidProjectionInspectionSettings
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.probe.InspectionStatusChangedProbe
import kotlinx.coroutines.CoroutineScope

class MongoDbInvalidProjectionTool : AbstractMongoDbInspectionGlobalTool(InvalidProjection)
class MongoDbInvalidProjection(coroutineScope: CoroutineScope) : AbstractMongoDbInspectionBridge<
    InvalidProjectionInspectionSettings,
    InvalidProjection
    >(
    coroutineScope,
    InvalidProjectionInspection(),
    InvalidProjection
) {
    override fun buildSettings(query: Node<PsiElement>): InvalidProjectionInspectionSettings {
        return InvalidProjectionInspectionSettings()
    }

    override fun afterInsight(queryInsight: QueryInsight<PsiElement, InvalidProjection>) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.inspectionChanged(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.INVALID_PROJECTION,
            queryInsight.query
        )
    }

    override fun emitFinishedInspectionTelemetryEvent(queryInsights: List<QueryInsight<PsiElement, InvalidProjection>>) {
        val probe by service<InspectionStatusChangedProbe>()

        probe.finishedProcessingInspections(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.INVALID_PROJECTION,
            queryInsights
        )
    }
}
