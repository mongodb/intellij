/**
 * This inspection is used for index checking. It warns if a query is not using a
 * proper index.
 */

package com.mongodb.jbplugin.inspections.impl

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.Inspection
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridge
import com.mongodb.jbplugin.linting.IndexCheckingLinter
import com.mongodb.jbplugin.linting.IndexCheckingSettings
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.probe.InspectionStatusChangedProbe
import com.mongodb.jbplugin.settings.pluginSetting
import kotlinx.coroutines.CoroutineScope

class IndexCheckInspectionBridge(coroutineScope: CoroutineScope) :
    AbstractMongoDbInspectionBridge<IndexCheckingSettings<LocalDataSource>, Inspection.IndexCheckInspection>(
        coroutineScope,
        IndexCheckingLinter(),
    ) {
    override fun buildSettings(query: Node<PsiElement>): IndexCheckingSettings<LocalDataSource> {
        val isFullExplainPlanEnabled by pluginSetting { ::isFullExplainPlanEnabled }

        val explainPlanType = if (isFullExplainPlanEnabled) {
            ExplainPlanType.FULL
        } else {
            ExplainPlanType.SAFE
        }

        val queryContext = QueryContext(
            emptyMap(),
            prettyPrint = false,
            automaticallyRun = true
        )

        val dataSource = query.source.containingFile.dataSource!!
        val readModelProvider by query.source.project.service<DataGripBasedReadModelProvider>()

        return IndexCheckingSettings(
            dataSource,
            readModelProvider,
            queryContext,
            explainPlanType
        )
    }

    override fun emitFinishedInspectionTelemetryEvent(problemsHolder: ProblemsHolder) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.finishedProcessingInspections(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.FIELD_DOES_NOT_EXIST,
            problemsHolder,
        )
    }
}
