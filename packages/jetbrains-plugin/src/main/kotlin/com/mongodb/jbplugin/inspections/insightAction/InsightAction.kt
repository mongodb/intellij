package com.mongodb.jbplugin.inspections.insightAction

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.editor.DatagripConsoleEditor
import com.mongodb.jbplugin.linting.InspectionAction
import com.mongodb.jbplugin.linting.InspectionAction.ChooseConnection
import com.mongodb.jbplugin.linting.InspectionAction.CreateIndexSuggestionScript
import com.mongodb.jbplugin.linting.InspectionAction.NoAction
import com.mongodb.jbplugin.linting.InspectionAction.RunQuery
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.meta.projectOrNull
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.observability.probe.CreateIndexIntentionProbe
import com.mongodb.jbplugin.observability.probe.QueryRunProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

interface InsightAction {
    val displayName: String

    suspend fun apply(insight: QueryInsight<PsiElement, *>)

    companion object {
        fun resolveAllActions(insight: QueryInsight<PsiElement, *>): List<InsightAction> {
            val allActions = arrayOf(insight.inspection.primaryAction) + insight.inspection.secondaryActions
            return allActions.mapNotNull { resolveSingle(insight.query.projectOrNull, it) }
        }

        private fun resolveSingle(project: Project?, action: InspectionAction): InsightAction? {
            project ?: return null

            return when (action) {
                ChooseConnection -> {
                    ChooseConnectionInsightAction()
                }
                CreateIndexSuggestionScript -> {
                    val createIndex by project.service<CreateIndexIntentionProbe>()
                    CreateSuggestedIndexInsightAction(createIndex, DatagripConsoleEditor)
                }
                NoAction -> null
                RunQuery -> {
                    val queryRun by project.service<QueryRunProbe>()
                    RunQueryInsightAction(
                        CoroutineScope(Dispatchers.IO),
                        queryRun,
                        DatagripConsoleEditor
                    )
                }
            }
        }
    }
}
