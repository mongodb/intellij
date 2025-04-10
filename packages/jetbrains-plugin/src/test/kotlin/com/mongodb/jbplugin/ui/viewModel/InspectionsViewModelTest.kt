package com.mongodb.jbplugin.ui.viewModel

import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.eventually
import com.mongodb.jbplugin.linting.ALL_MDB_INSPECTIONS
import com.mongodb.jbplugin.linting.Inspection.NotUsingIndex
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.mql.Node
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
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

            viewModel.flushOldInsightsFor(file, NotUsingIndex)
            assertEquals(0, viewModel.insights.value.size)
        }
    }

    @Test
    fun `initialize the inspectionsWithStatus Flow with mongodb inspections using their enabled values from current profile`(project: Project, coroutineScope: CoroutineScope) {
        val viewModel = InspectionsViewModel(project, coroutineScope)
        eventually {
            assertEquals(
                ALL_MDB_INSPECTIONS.associateWith { false },
                viewModel.inspectionsWithStatus.value
            )
        }
    }

    @Test
    fun `updates inspectionsWithStatus Flow when the inspections enabled values changes in profile`(project: Project, coroutineScope: CoroutineScope) {
        val viewModel = spy(InspectionsViewModel(project, coroutineScope))
        assertEquals(
            ALL_MDB_INSPECTIONS.associateWith { false },
            viewModel.inspectionsWithStatus.value
        )

        whenever(viewModel.getMdbInspectionsWithStatus()).thenReturn(
            ALL_MDB_INSPECTIONS.associateWith { true }
        )
        viewModel.profileChanged(mock())

        eventually {
            assertEquals(true, viewModel.inspectionsWithStatus.value[NotUsingIndex])
        }

        val profile = ProjectInspectionProfileManager.getInstance(project).currentProfile
    }
}
