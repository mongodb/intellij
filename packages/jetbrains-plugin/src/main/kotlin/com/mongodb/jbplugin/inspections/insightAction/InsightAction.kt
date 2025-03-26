package com.mongodb.jbplugin.inspections.insightAction

import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.linting.InspectionAction
import com.mongodb.jbplugin.linting.InspectionAction.ChooseConnection
import com.mongodb.jbplugin.linting.InspectionAction.CreateIndexSuggestionScript
import com.mongodb.jbplugin.linting.InspectionAction.NoAction
import com.mongodb.jbplugin.linting.InspectionAction.RunQuery
import com.mongodb.jbplugin.linting.QueryInsight

interface InsightAction {
    val displayName: String

    suspend fun apply(insight: QueryInsight<PsiElement, *>)

    companion object {
        fun resolveAllActions(insight: QueryInsight<PsiElement, *>): List<InsightAction> {
            val allActions = arrayOf(insight.inspection.primaryAction) + insight.inspection.secondaryActions
            return allActions.mapNotNull(::resolveSingle)
        }

        private fun resolveSingle(action: InspectionAction): InsightAction? {
            return when (action) {
                ChooseConnection -> null
                CreateIndexSuggestionScript -> CreateSuggestedIndexInsightAction
                NoAction -> null
                RunQuery -> null
            }
        }
    }
}
