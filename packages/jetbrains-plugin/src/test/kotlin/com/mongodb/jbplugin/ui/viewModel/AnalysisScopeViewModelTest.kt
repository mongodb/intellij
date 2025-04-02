package com.mongodb.jbplugin.ui.viewModel

import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.eventually
import com.mongodb.jbplugin.fixtures.withMockedService
import com.mongodb.jbplugin.inspections.analysisScope.AnalysisScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@IntegrationTest
class AnalysisScopeViewModelTest {
    @Test
    fun `when the scope is current file it reemits the new scope when the editor state changes`(
        project: Project,
        coroutineScope: TestScope
    ) {
        val editorState = MutableStateFlow(EditorState.default())
        val editorViewModel = mock<CodeEditorViewModel>()
        whenever(editorViewModel.editorState).thenReturn(editorState)

        project.withMockedService(editorViewModel)
        val analysisScopeViewModel = AnalysisScopeViewModel(project, coroutineScope)

        val focusedFiles = listOf(fileAt("File1.java"), fileAt("File2.java"))
        editorState.tryEmit(
            EditorState(
                focusedFiles = focusedFiles,
                openFiles = emptyList()
            )
        )

        eventually(coroutineScope = coroutineScope) {
            val currentScope = analysisScopeViewModel.analysisScope.value
            assertInstanceOf(AnalysisScope.CurrentFile::class.java, currentScope)
            currentScope as AnalysisScope.CurrentFile

            assertEquals(focusedFiles, currentScope.files)
        }
    }

    @Test
    fun `reanalyze the current scope when requested`(
        project: Project,
        coroutineScope: TestScope
    ) {
        val editorState = MutableStateFlow(EditorState.default())
        val editorViewModel = mock<CodeEditorViewModel>()
        whenever(editorViewModel.editorState).thenReturn(editorState)

        project.withMockedService(editorViewModel)
        val analysisScopeViewModel = AnalysisScopeViewModel(project, coroutineScope)

        runBlocking {
            analysisScopeViewModel.reanalyzeCurrentScope()
        }

        eventually(coroutineScope = coroutineScope) {
            val currentScope = analysisScopeViewModel.analysisScope.value
            assertInstanceOf(AnalysisScope.CurrentFile::class.java, currentScope)
            currentScope as AnalysisScope.CurrentFile

            runBlocking {
                verify(editorViewModel).reanalyzeRelevantEditors()
            }
        }
    }
}
