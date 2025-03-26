package com.mongodb.jbplugin.inspections.insightAction

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.editor.DatagripConsoleEditor
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.mockDataSource
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.observability.probe.CreateIndexIntentionProbe
import com.mongodb.jbplugin.ui.viewModel.ConnectionStateViewModel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@IntegrationTest
class CreateSuggestedIndexInsightActionTest {
    @Test
    fun `should emit a probe when a new index request is created`(
        project: Project,
        coroutineScope: TestScope,
    ) = coroutineScope.runTest {
        val dataSource = mockDataSource()
        project.connectedTo(dataSource)

        val probe = mock<CreateIndexIntentionProbe>()
        val consoleEditor = mock<DatagripConsoleEditor>()

        val insightAction = CreateSuggestedIndexInsightAction(probe, consoleEditor)
        val query = aQuery(project)

        insightAction.apply(QueryInsight.notUsingIndex(query))

        verify(probe).intentionClicked(query)
    }

    private fun aQuery(project: Project): Node<PsiElement> {
        val query = mock<Node<PsiElement>>()
        val source = mock<PsiElement>()
        val containingFile = mock<PsiFile>()

        whenever(source.project).thenReturn(project)
        whenever(query.source).thenReturn(source)
        whenever(source.containingFile).thenReturn(containingFile)

        return query
    }

    private suspend fun Project.connectedTo(dataSource: LocalDataSource) {
        val connectionViewModel by service<ConnectionStateViewModel>()
        connectionViewModel.selectDataSource(dataSource)
    }
}
