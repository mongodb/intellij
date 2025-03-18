package com.mongodb.jbplugin.ui.viewModel

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.editor.services.implementations.MdbDataSourceService
import com.mongodb.jbplugin.editor.services.implementations.MdbEditorService
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.eventually
import com.mongodb.jbplugin.fixtures.mockDataSource
import com.mongodb.jbplugin.fixtures.withMockedService
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@IntegrationTest
class ConnectionStateViewModelTest {
    lateinit var dataSource: LocalDataSource
    lateinit var dataSourceService: MdbDataSourceService
    lateinit var editorService: MdbEditorService

    @BeforeEach
    fun setUp(project: Project) {
        dataSource = mockDataSource()
        dataSourceService = mock()
        editorService = mock()

        whenever(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
        project.withMockedService(dataSourceService)
            .withMockedService(editorService)
    }

    @Test
    fun `emits the list of already existing data sources`(
        project: Project,
        coroutineScope: TestScope
    ) = runTest {
        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        eventually(coroutineScope = coroutineScope) {
            val connectionState = viewModel.connectionState.value
            assertEquals(1, connectionState.connections.size)
            assertEquals(dataSource, connectionState.connections.first())
        }
    }

    @Test
    fun `when connected triggers code analysis on the current editor`(
        project: Project,
        coroutineScope: TestScope
    ) = runTest {
        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        viewModel.onSelectedConnectionChanges(
            SelectedConnectionState.Connected(mockDataSource())
        )

        verify(editorService, timeout(1000)).reAnalyzeSelectedEditor(true)
    }

    @Test
    fun `when selecting a null data source disconnects`(
        project: Project,
        coroutineScope: TestScope
    ) = runTest {
        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        viewModel.connectionSaga = mock()

        viewModel.selectDataSource(null)
        verify(viewModel.connectionSaga, timeout(1000)).doDisconnect()
    }

    @Test
    fun `when selecting a non-null data source connects to it`(
        project: Project,
        coroutineScope: TestScope
    ) = runTest {
        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        viewModel.connectionSaga = mock()
        val dataSource = mockDataSource()

        viewModel.selectDataSource(dataSource)
        verify(viewModel.connectionSaga, timeout(1000)).doConnect(dataSource)
    }
}
