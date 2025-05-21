package com.mongodb.jbplugin.ui.viewModel

import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.eventually
import com.mongodb.jbplugin.fixtures.withMockedService
import com.mongodb.jbplugin.inspections.analysisScope.AnalysisScope
import com.mongodb.jbplugin.linting.ALL_MDB_INSPECTIONS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
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
                openFiles = emptyList(),
                carets = emptyList()
            )
        )

        eventually(coroutineScope = coroutineScope) {
            val currentScope = analysisScopeViewModel.analysisScope.value
            assertInstanceOf(AnalysisScope.CurrentFile::class.java, currentScope)
        }
    }

    @Test
    fun `when the scope is current query it reemits the new scope when the editor state changes`(
        project: Project,
        coroutineScope: TestScope
    ) {
        val editorState = MutableStateFlow(EditorState.default())
        val editorViewModel = mock<CodeEditorViewModel>()
        whenever(editorViewModel.editorState).thenReturn(editorState)

        project.withMockedService(editorViewModel)
        val analysisScopeViewModel = AnalysisScopeViewModel(project, coroutineScope)

        runBlocking {
            analysisScopeViewModel.changeScope(AnalysisScope.CurrentQuery())
        }

        val focusedFiles = listOf(fileAt("File1.java"), fileAt("File2.java"))
        editorState.tryEmit(
            EditorState(
                focusedFiles = focusedFiles,
                openFiles = emptyList(),
                carets = emptyList()
            )
        )

        eventually(coroutineScope = coroutineScope) {
            val currentScope = analysisScopeViewModel.analysisScope.value
            assertInstanceOf(AnalysisScope.CurrentQuery::class.java, currentScope)
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

        var reAnalyzeCalled = false
        val reanalyzeRelevantEditorsStub = {
            reAnalyzeCalled = true
        }
        runBlocking {
            whenever(editorViewModel.reanalyzeRelevantEditors()).then {
                reanalyzeRelevantEditorsStub()
            }
        }

        project.withMockedService(editorViewModel)
        val analysisScopeViewModel = AnalysisScopeViewModel(project, coroutineScope)

        runBlocking {
            analysisScopeViewModel.reanalyzeCurrentScope()
        }

        eventually(coroutineScope = coroutineScope) {
            val currentScope = analysisScopeViewModel.analysisScope.value
            assertInstanceOf(AnalysisScope.CurrentFile::class.java, currentScope)
        }

        eventually(coroutineScope = coroutineScope) {
            assertTrue(reAnalyzeCalled)
        }
    }

    @Test
    fun `it refreshes analysis on change in inspection status`(
        project: Project,
        coroutineScope: TestScope
    ) {
        val inspectionsWithStatus = MutableStateFlow(
            ALL_MDB_INSPECTIONS.associateWith {
                true
            }
        )
        val inspectionsViewModel = mock<InspectionsViewModel>()
        whenever(inspectionsViewModel.inspectionsWithStatus).thenReturn(inspectionsWithStatus)
        project.withMockedService(inspectionsViewModel)

        val analysisScopeViewModel = AnalysisScopeViewModel(project, coroutineScope)
        val currentScope = analysisScopeViewModel.analysisScope.value
        runBlocking {
            inspectionsWithStatus.emit(
                ALL_MDB_INSPECTIONS.associateWith {
                    false
                }
            )
        }

        eventually(coroutineScope = coroutineScope) {
            assertTrue(currentScope != analysisScopeViewModel.analysisScope.value)
        }
    }
}
