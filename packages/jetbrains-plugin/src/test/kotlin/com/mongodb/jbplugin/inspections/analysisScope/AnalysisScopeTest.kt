package com.mongodb.jbplugin.inspections.analysisScope

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.Application
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.withMockedService
import com.mongodb.jbplugin.fixtures.withMockedServiceInScope
import com.mongodb.jbplugin.linting.Inspection
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.meta.withinReadActionBlocking
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.ui.viewModel.CodeEditorViewModel
import com.mongodb.jbplugin.ui.viewModel.EditorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@IntegrationTest
class AnalysisScopeTest {
    @Test
    fun `CurrentFile only accepts insights from the provided virtual files`(
        project: Project
    ) {
        val codeEditorState = MutableStateFlow(EditorState.default())
        val codeEditorViewModel = mock<CodeEditorViewModel>()
        whenever(codeEditorViewModel.editorState).thenReturn(codeEditorState)

        project.withMockedService(codeEditorViewModel)

        val vf1 = LightVirtualFile("F1.java")
        val vf2 = LightVirtualFile("F2.java")
        val vf3 = LightVirtualFile("F3.java")

        codeEditorState.tryEmit(
            codeEditorState.value.copy(
                focusedFiles = listOf(vf1, vf2),
                openFiles = listOf(vf1, vf2, vf3)
            )
        )

        val scope = AnalysisScope.CurrentFile()

        val insights = listOf(
            insightOnFile(vf1),
            insightOnFile(vf2),
            insightOnFile(vf3)
        )

        val filteredInsights = scope.getFilteredInsights(project, insights)
        assertTrue(filteredInsights.all { it.query.source.containingFile.virtualFile == vf1 || it.query.source.containingFile.virtualFile == vf2 })
        assertTrue(filteredInsights.isNotEmpty())
        assertEquals(2, filteredInsights.size)
    }

    @Test
    fun `CurrentQuery only accepts insights from the query at caret`(
        project: Project
    ) {
        val queryAtCaret = queryWithHash(0xC0FFEE)

        val codeEditorViewModel = mock<CodeEditorViewModel>()
        whenever(codeEditorViewModel.queriesAtCaret()).thenReturn(listOf(queryAtCaret))

        project.withMockedService(codeEditorViewModel)

        val insights = listOf(
            insightOnQuery(queryAtCaret),
            insightOnQuery(queryWithHash(0xFABADA)),
        )

        val filteredInsights = AnalysisScope.CurrentQuery().getFilteredInsights(project, insights)
        assertTrue(filteredInsights.all { it.query.queryHash() == queryAtCaret.queryHash() })
        assertEquals(1, filteredInsights.size)
    }

    @Test
    fun `AllInsights loads all java files in the project`(
        project: Project,
        application: Application
    ) {
        val vf1 = LightVirtualFile("F1.java", JavaFileType.INSTANCE, "import com.mongodb.client.MongoClient;")
        val vf2 = LightVirtualFile("F2.java", JavaFileType.INSTANCE, "import com.mongodb.client.MongoClient;")
        val vf3 = LightVirtualFile("F3.java", JavaFileType.INSTANCE, "import not.com.mongodb.client.MongoClient;")

        val fileBasedIndex = mock<FileBasedIndex>()
        whenever(fileBasedIndex.getContainingFiles(any<ID<FileType, *>>(), any<FileType>(), any())).thenReturn(listOf(vf1, vf2, vf3))

        application.withMockedServiceInScope(fileBasedIndex) {
            val result = withinReadActionBlocking {
                AnalysisScope.AllInsights().getAdditionalFilesInScope(project)
            }

            assertEquals(result.size, 2)
        }
    }

    @Test
    fun `RecommendedInsights loads all java files in the project that share a common prefix with the open files`(
        project: Project,
        application: Application
    ) {
        val vf1 = LightVirtualFile("A/F1.java", JavaFileType.INSTANCE, "import com.mongodb.client.MongoClient;")
        val vf2 = LightVirtualFile("A/B/F2.java", JavaFileType.INSTANCE, "import com.mongodb.client.MongoClient;")
        val vf3 = LightVirtualFile("A/F3.java", JavaFileType.INSTANCE, "import not.com.mongodb.client.MongoClient;")
        val vf4 = LightVirtualFile("C/F4.java", JavaFileType.INSTANCE, "import not.com.mongodb.client.MongoClient;")

        val codeEditorState = MutableStateFlow(EditorState.default())
        val codeEditorViewModel = mock<CodeEditorViewModel>()
        whenever(codeEditorViewModel.editorState).thenReturn(codeEditorState)

        runBlocking {
            whenever(codeEditorViewModel.allProjectFiles(project)).thenReturn(listOf(vf1, vf2, vf3, vf4))
        }

        project.withMockedService(codeEditorViewModel)

        codeEditorState.tryEmit(
            codeEditorState.value.copy(
                focusedFiles = emptyList(),
                openFiles = listOf(vf1, vf2)
            )
        )

        val result = withinReadActionBlocking {
            AnalysisScope.RecommendedInsights().getAdditionalFilesInScope(project)
        }

        assertEquals(result.size, 3)
        assertTrue(result.all { it.canonicalPath!!.startsWith("/A/") })
    }

    @Test
    fun `RecommendedInsights filters out insights out of the actual shared parent on open files`(
        project: Project,
        application: Application
    ) {
        val vf1 = LightVirtualFile("A/F1.java", JavaFileType.INSTANCE, "import com.mongodb.client.MongoClient;")
        val vf2 = LightVirtualFile("A/B/F2.java", JavaFileType.INSTANCE, "import com.mongodb.client.MongoClient;")
        val vf3 = LightVirtualFile("A/F3.java", JavaFileType.INSTANCE, "import not.com.mongodb.client.MongoClient;")
        val vf4 = LightVirtualFile("C/F4.java", JavaFileType.INSTANCE, "import not.com.mongodb.client.MongoClient;")

        val codeEditorState = MutableStateFlow(EditorState.default())
        val codeEditorViewModel = mock<CodeEditorViewModel>()
        whenever(codeEditorViewModel.editorState).thenReturn(codeEditorState)

        runBlocking {
            whenever(codeEditorViewModel.allProjectFiles(project)).thenReturn(listOf(vf1, vf2, vf3, vf4))
        }

        project.withMockedService(codeEditorViewModel)

        codeEditorState.tryEmit(
            codeEditorState.value.copy(
                focusedFiles = emptyList(),
                openFiles = listOf(vf1, vf2)
            )
        )

        val allInsights = listOf(
            insightOnFile(vf1),
            insightOnFile(vf2),
            insightOnFile(vf3),
            insightOnFile(vf4),
        )

        val result = withinReadActionBlocking {
            AnalysisScope.RecommendedInsights().getFilteredInsights(project, allInsights)
        }

        assertEquals(result.size, 3)
        assertTrue(result.all { it.query.source.containingFile.virtualFile.canonicalPath!!.startsWith("/A/") })
    }

    private fun insightOnFile(file: VirtualFile): QueryInsight<PsiElement, *> {
        val source = mock<PsiElement>()
        val psiFile = mock<PsiFile>()

        whenever(source.containingFile).thenReturn(psiFile)
        whenever(psiFile.virtualFile).thenReturn(file)

        val qi = QueryInsight(
            Node(
                source,
                emptyList()
            ),
            "Some",
            emptyList(),
            Inspection.NotUsingIndex
        )

        return qi
    }

    private fun insightOnQuery(query: Node<PsiElement>): QueryInsight<PsiElement, *> {
        return QueryInsight(query, "Some", emptyList(), Inspection.NotUsingIndex)
    }

    private fun queryWithHash(hash: Int): Node<PsiElement> {
        val query = mock<Node<PsiElement>>()
        whenever(query.queryHash()).thenReturn(hash)

        return query
    }
}
