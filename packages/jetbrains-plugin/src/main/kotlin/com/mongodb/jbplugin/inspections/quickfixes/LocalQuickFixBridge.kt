package com.mongodb.jbplugin.inspections.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.inspections.insightAction.InsightAction
import com.mongodb.jbplugin.linting.QueryInsight
import kotlinx.coroutines.runBlocking

class LocalQuickFixBridge(
    private val action: InsightAction,
    private val insight: QueryInsight<PsiElement, *>
) : LocalQuickFix {
    override fun getFamilyName() = action.displayName

    override fun applyFix(
        p0: Project,
        p1: ProblemDescriptor
    ) {
        runBlocking {
            action.apply(insight)
        }
    }

    companion object {
        fun allQuickFixes(insight: QueryInsight<PsiElement, *>): Array<LocalQuickFix> {
            return InsightAction.resolveAllActions(insight)
                .map { LocalQuickFixBridge(it, insight) }
                .toTypedArray()
        }
    }
}
