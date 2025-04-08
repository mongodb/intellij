package com.mongodb.jbplugin.inspections.insightAction

import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.ui.viewModel.SidePanelViewModel

class ChooseConnectionInsightAction : InsightAction {
    override val displayName = "Choose a connection"

    override suspend fun apply(insight: QueryInsight<PsiElement, *>) {
        val viewModel by insight.query.source.project.service<SidePanelViewModel>()
        viewModel.openConnectionComboBox()
    }
}
