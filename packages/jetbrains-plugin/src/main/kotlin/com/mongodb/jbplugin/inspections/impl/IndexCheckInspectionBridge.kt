/**
 * This inspection is used for index checking. It warns if a query is not using a
 * proper index.
 */

package com.mongodb.jbplugin.inspections.impl

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridge
import com.mongodb.jbplugin.inspections.IntelliJBasedInspectionHolder
import com.mongodb.jbplugin.inspections.MongoDbInspection
import com.mongodb.jbplugin.linting.IndexCheckingLinter
import com.mongodb.jbplugin.linting.IndexCheckingSettings
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.components.HasExplain
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.probe.InspectionStatusChangedProbe
import com.mongodb.jbplugin.settings.pluginSetting
import kotlinx.coroutines.CoroutineScope

class IndexCheckInspectionBridge(coroutineScope: CoroutineScope) :
    AbstractMongoDbInspectionBridge(
        coroutineScope,
        IndexCheckLinterInspection,
    ) {
    override fun emitFinishedInspectionTelemetryEvent(problemsHolder: ProblemsHolder) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.finishedProcessingInspections(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.FIELD_DOES_NOT_EXIST,
            problemsHolder,
        )
    }
}

/**
 * This inspection object calls the linting engine and transforms the result so they can be rendered in the IntelliJ
 * editor.
 */
internal object IndexCheckLinterInspection : MongoDbInspection {
    private val linter = IndexCheckingLinter<LocalDataSource>()

    override suspend fun visitMongoDbQuery(
        coroutineScope: CoroutineScope,
        dataSource: LocalDataSource,
        problems: IntelliJBasedInspectionHolder,
        query: Node<PsiElement>,
        formatter: DialectFormatter,
    ) {
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

        val readModelProvider by query.source.project.service<DataGripBasedReadModelProvider>()
        linter.run(
            query.with(HasExplain(explainPlanType)),
            problems,
            IndexCheckingSettings(
                dataSource,
                readModelProvider,
                queryContext
            )
        )
    }
}
