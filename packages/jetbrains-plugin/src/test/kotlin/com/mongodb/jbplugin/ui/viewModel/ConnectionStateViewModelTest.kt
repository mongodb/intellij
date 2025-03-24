package com.mongodb.jbplugin.ui.viewModel

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.psi.DataSourceManager
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.editor.services.implementations.MdbDataSourceService
import com.mongodb.jbplugin.editor.services.implementations.MdbEditorService
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.eventually
import com.mongodb.jbplugin.fixtures.mockDataSource
import com.mongodb.jbplugin.fixtures.withMockedService
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState.Empty
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

        viewModel.selectDataSource(dataSource, background = false)
        verify(viewModel.connectionSaga, timeout(1000)).doConnect(dataSource)
    }

    @Test
    fun `when a new data source is created tracks it`(
        project: Project,
        coroutineScope: TestScope
    ) = runTest {
        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        viewModel.connectionSaga = mock()
        val dataSource = mockDataSource()
        val dataSourceManager = mock<DataSourceManager<LocalDataSource>>()

        viewModel.dataSourceAdded(dataSourceManager, dataSource)
        eventually(coroutineScope = coroutineScope) {
            assertTrue(viewModel.connectionState.value.connections.any { it.uniqueId == dataSource.uniqueId })
        }
    }

    @Test
    fun `when a new data source is removed it is dropped`(
        project: Project,
        coroutineScope: TestScope
    ) = runTest {
        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        viewModel.connectionSaga = mock()
        val dataSource = mockDataSource()
        val dataSourceManager = mock<DataSourceManager<LocalDataSource>>()

        viewModel.dataSourceAdded(dataSourceManager, dataSource)
        viewModel.dataSourceRemoved(dataSourceManager, dataSource)
        eventually(coroutineScope = coroutineScope) {
            assertFalse(viewModel.connectionState.value.connections.any { it.uniqueId == dataSource.uniqueId })
        }
    }

    @Test
    fun `when a new data source is removed it is dropped and disconnected if it was connected`(
        project: Project,
        coroutineScope: TestScope
    ) = runTest {
        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        viewModel.connectionSaga = mock()
        val dataSource = mockDataSource()
        val dataSourceManager = mock<DataSourceManager<LocalDataSource>>()

        coroutineScope.launch {
            viewModel.connectionState.emit(
                ConnectionState.default()
                    .withDataSource(dataSource)
                    .withConnectionState(SelectedConnectionState.Connected(dataSource))
            )
        }

        eventually(coroutineScope = coroutineScope) {
            assertFalse(viewModel.connectionState.value.selectedConnectionState is Empty)
        }

        viewModel.dataSourceRemoved(dataSourceManager, dataSource)

        eventually(coroutineScope = coroutineScope) {
            assertFalse(viewModel.connectionState.value.connections.any { it.uniqueId == dataSource.uniqueId })
            assertTrue(viewModel.connectionState.value.selectedConnectionState is Empty)
        }
    }

    @Test
    fun `when the data source is disconnected it unlinks the connection`(
        project: Project,
        coroutineScope: TestScope
    ) = runTest {
        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        viewModel.connectionSaga = mock()
        val dataSource = mockDataSource()

        coroutineScope.launch {
            viewModel.connectionState.emit(
                ConnectionState.default()
                    .withDataSource(dataSource)
                    .withConnectionState(SelectedConnectionState.Connected(dataSource))
            )
        }

        eventually(coroutineScope = coroutineScope) {
            assertFalse(viewModel.connectionState.value.selectedConnectionState is Empty)
        }

        viewModel.onTerminated(dataSource, null)

        eventually(coroutineScope = coroutineScope) {
            assertTrue(viewModel.connectionState.value.connections.any { it.uniqueId == dataSource.uniqueId })
            assertTrue(viewModel.connectionState.value.selectedConnectionState is Empty)
        }
    }
}
