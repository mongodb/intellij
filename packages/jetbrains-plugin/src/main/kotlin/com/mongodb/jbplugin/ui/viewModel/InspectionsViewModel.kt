package com.mongodb.jbplugin.ui.viewModel

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.linting.Inspection
import com.mongodb.jbplugin.linting.InspectionCategory
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.meta.withinReadAction
import com.mongodb.jbplugin.ui.components.mongoDbSidePanel
import com.mongodb.jbplugin.ui.components.setAttentionIcon
import com.mongodb.jbplugin.ui.components.setOkIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
class InspectionsViewModel(
    val project: Project,
    val coroutineScope: CoroutineScope
) {
    private val mutableInsights = MutableStateFlow<List<QueryInsight<PsiElement, *>>>(emptyList())
    val insights = mutableInsights.asStateFlow()

    private val mutableOpenCategories = MutableStateFlow<InspectionCategory?>(null)
    val openCategories = mutableOpenCategories.asStateFlow()

    init {
        val connectionStateViewModel by project.service<ConnectionStateViewModel>()

        connectionStateViewModel.connectionState
            .zip(insights) { state, insight -> state to insight }
            .onEach { refreshSidePanelStatusOnInsights(it.first, it.second) }
            .launchIn(coroutineScope)
    }

    suspend fun startInspectionSessionOf(psiFile: PsiFile, inspection: Inspection) {
        withContext(Dispatchers.IO) {
            val allOtherInspections = withinReadAction {
                insights.value.filter {
                    !(
                        it.inspection == inspection &&
                            it.query.source.containingFile.isEquivalentTo(
                                psiFile
                            )
                        )
                }
            }

            mutableInsights.emit(allOtherInspections)
        }
    }

    suspend fun addInsight(insight: QueryInsight<PsiElement, *>) {
        withContext(Dispatchers.IO) {
            val currentState = mutableInsights.value
            val withoutExistingInsight = withinReadAction {
                currentState.filter { !areEquivalent(insight, it) }
            }
            mutableInsights.emit(withoutExistingInsight + insight)
        }
    }

    suspend fun openCategory(category: InspectionCategory) {
        withContext(Dispatchers.IO) {
            if (mutableOpenCategories.value == category) {
                mutableOpenCategories.emit(null)
            } else {
                mutableOpenCategories.emit(category)
            }
        }
    }

    suspend fun visitQueryOfInsightInEditor(insight: QueryInsight<PsiElement, *>) {
        val codeEditorViewModel by insight.query.source.project.service<CodeEditorViewModel>()
        codeEditorViewModel.focusQueryInEditor(insight.query)
    }

    private fun areEquivalent(a: QueryInsight<PsiElement, *>, b: QueryInsight<PsiElement, *>): Boolean {
        return a.description == b.description &&
            a.descriptionArguments == b.descriptionArguments &&
            a.inspection == b.inspection &&
            (a.query.source == b.query.source || a.query.source.isEquivalentTo(b.query.source))
    }

    private fun refreshSidePanelStatusOnInsights(connectionState: ConnectionState, insights: List<QueryInsight<*, *>>) {
        if (connectionState.selectedConnectionState is SelectedConnectionState.Initial ||
            connectionState.selectedConnectionState is SelectedConnectionState.Failed
        ) {
            project.mongoDbSidePanel.setAttentionIcon()
        } else if (insights.isNotEmpty()) {
            project.mongoDbSidePanel.setAttentionIcon()
        } else {
            project.mongoDbSidePanel.setOkIcon()
        }
    }
}
