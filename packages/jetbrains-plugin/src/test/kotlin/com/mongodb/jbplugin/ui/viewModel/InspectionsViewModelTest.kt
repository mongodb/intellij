package com.mongodb.jbplugin.ui.viewModel

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.linting.Inspection.NotUsingIndex
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.mql.Node
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@IntegrationTest
class InspectionsViewModelTest {
    @Test
    fun `deduplicates insights`(project: Project, coroutineScope: CoroutineScope) {
        val viewModel = InspectionsViewModel(project, coroutineScope)
        val query = mock<Node<PsiElement>>()

        runBlocking {
            viewModel.addInsight(QueryInsight.notUsingIndex(query))
            viewModel.addInsight(QueryInsight.notUsingIndex(query))
        }

        assertEquals(1, viewModel.insights.value.size)
    }

    @Test
    fun `can hold multiple insights for the same query`(project: Project, coroutineScope: CoroutineScope) {
        val viewModel = InspectionsViewModel(project, coroutineScope)
        val query = mock<Node<PsiElement>>()

        runBlocking {
            viewModel.addInsight(QueryInsight.notUsingIndex(query))
            viewModel.addInsight(QueryInsight.notUsingIndexEffectively(query))
        }

        assertEquals(2, viewModel.insights.value.size)
    }

    @Test
    fun `a new session clears all insights for the same inspection and file`(project: Project, coroutineScope: CoroutineScope) {
        val viewModel = InspectionsViewModel(project, coroutineScope)
        val queryAttachment = mock<PsiElement>()
        val file = mock<PsiFile>()
        whenever(queryAttachment.containingFile).thenReturn(file)
        whenever(file.isEquivalentTo(file)).thenReturn(true)

        val query = Node(queryAttachment, emptyList())

        runBlocking {
            viewModel.addInsight(QueryInsight.notUsingIndex(query))
            assertEquals(1, viewModel.insights.value.size)

            viewModel.startInspectionSessionOf(file, NotUsingIndex)
            assertEquals(0, viewModel.insights.value.size)
        }
    }
}
