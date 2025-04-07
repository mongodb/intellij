package com.mongodb.jbplugin.inspections.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.inspections.insightAction.InsightAction
import com.mongodb.jbplugin.linting.QueryInsight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LocalQuickFixBridge(
    private val action: InsightAction,
    private val insight: QueryInsight<PsiElement, *>,
    private val coroutineScope: CoroutineScope
) : LocalQuickFix {
    override fun getFamilyName() = action.displayName

    override fun applyFix(
        p0: Project,
        p1: ProblemDescriptor
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            action.apply(insight)
        }
    }

    companion object {
        fun allQuickFixes(coroutineScope: CoroutineScope, insight: QueryInsight<PsiElement, *>): Array<LocalQuickFix> {
            return InsightAction.resolveAllActions(insight)
                .map { LocalQuickFixBridge(it, insight, coroutineScope) }
                .toTypedArray()
        }
    }
}
