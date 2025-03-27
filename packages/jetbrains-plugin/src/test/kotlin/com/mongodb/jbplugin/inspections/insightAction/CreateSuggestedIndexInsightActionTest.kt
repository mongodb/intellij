package com.mongodb.jbplugin.inspections.insightAction

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.localDataSource
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbPsiFacade
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.editor.CachedQueryService
import com.mongodb.jbplugin.editor.DatagripConsoleEditor
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.mockDataSource
import com.mongodb.jbplugin.fixtures.withMockedService
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.observability.probe.CreateIndexIntentionProbe
import com.mongodb.jbplugin.ui.viewModel.ConnectionState
import com.mongodb.jbplugin.ui.viewModel.ConnectionStateViewModel
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
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
        val query = project.aQuery()

        insightAction.apply(QueryInsight.notUsingIndex(query))

        verify(probe).intentionClicked(query)
    }

    @Test
    fun `should open a console with the index script`(
        project: Project,
        coroutineScope: TestScope,
    ) = coroutineScope.runTest {
        val dataSource = mockDataSource()
        project.connectedTo(dataSource)

        val probe = mock<CreateIndexIntentionProbe>()
        val consoleEditor = mock<DatagripConsoleEditor>()
        val cachedQueryService = mock<CachedQueryService>()
        whenever(cachedQueryService.allSiblingsOf(any())).thenReturn(emptyArray())

        project.withMockedService(cachedQueryService)

        val insightAction = CreateSuggestedIndexInsightAction(probe, consoleEditor)
        val query = project.aQuery()

        insightAction.apply(QueryInsight.notUsingIndex(query))

        verify(consoleEditor).openConsoleForDataSource(project, dataSource)
    }

    private fun Project.aQuery(): Node<PsiElement> {
        val query = mock<Node<PsiElement>>()
        val source = mock<PsiElement>()
        val containingFile = mock<PsiFile>()
        val backingVirtualFile = mock<VirtualFile>()

        whenever(source.project).thenReturn(this)
        whenever(query.source).thenReturn(source)
        whenever(source.containingFile).thenReturn(containingFile)
        whenever(containingFile.virtualFile).thenReturn(backingVirtualFile)
        whenever(containingFile.project).thenReturn(this)

        return query
    }

    private fun Project.connectedTo(
        dataSource: LocalDataSource,
    ) {
        val uniqueId = dataSource.uniqueId

        val dbDataSource = mock<DbDataSource>()
        whenever(dbDataSource.uniqueId).thenReturn(uniqueId)
        whenever(dbDataSource.localDataSource).thenReturn(dataSource)

        val dbPsiFacade = mock<DbPsiFacade>()
        whenever(dbPsiFacade.findDataSource(any())).thenReturn(dbDataSource)

        withMockedService(dbPsiFacade)

        val viewModel = mock<ConnectionStateViewModel>()
        val connectionState = MutableStateFlow(
            ConnectionState.default().withConnectionState(
                SelectedConnectionState.Connected(dataSource)
            )
        )

        whenever(viewModel.mutableConnectionState).thenReturn(connectionState)
        whenever(viewModel.connectionState).thenReturn(connectionState)

        withMockedService(viewModel)
    }
}
