package com.mongodb.jbplugin.ui.viewModel

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.inspections.analysisScope.AnalysisScope
import com.mongodb.jbplugin.meta.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class AnalysisScopeViewModel(
    val project: Project,
    val coroutineScope: CoroutineScope
) {
    private val mutableAnalysisScope = MutableStateFlow(AnalysisScope.default())
    val analysisScope = mutableAnalysisScope.asStateFlow()

    init {
        coroutineScope.launch {
            val codeEditorViewModel by project.service<CodeEditorViewModel>()
            codeEditorViewModel.editorState.value.run(::refreshAnalysisScopeIfNecessary)
            codeEditorViewModel.editorState.collectLatest(::refreshAnalysisScopeIfNecessary)
        }
    }

    suspend fun reanalyzeCurrentScope() {
        mutableAnalysisScope.tryEmit(mutableAnalysisScope.value)
        val codeEditorViewModel by project.service<CodeEditorViewModel>()
        codeEditorViewModel.reanalyzeRelevantEditors()
    }

    private fun refreshAnalysisScopeIfNecessary(editorState: EditorState) {
        val currentScope = mutableAnalysisScope.value
        if (currentScope is AnalysisScope.CurrentFile) {
            mutableAnalysisScope.tryEmit(AnalysisScope.CurrentFile(editorState.focusedFiles))
        }
    }
}
