package com.mongodb.jbplugin.ui.viewModel

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.psi.DataSourceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.mongodb.jbplugin.editor.services.implementations.ConnectionPreferencesStateComponent
import com.mongodb.jbplugin.editor.services.implementations.PersistentConnectionPreferences
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.eventually
import com.mongodb.jbplugin.fixtures.mockDataSource
import com.mongodb.jbplugin.fixtures.withMockedService
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@IntegrationTest
class ConnectionStateViewModelTest {
    lateinit var dataSource: LocalDataSource
    private lateinit var connectionSaga: ConnectionSaga
    private lateinit var codeEditorViewModel: CodeEditorViewModel
    private lateinit var analysisScopeViewModel: AnalysisScopeViewModel
    private lateinit var inspectionsViewModel: InspectionsViewModel
    private lateinit var connectionPreferences: PersistentConnectionPreferences
    private lateinit var mongoDBToolWindow: ToolWindow

    @BeforeEach
    fun setUp(project: Project) {
        codeEditorViewModel = mock()
        inspectionsViewModel = mock()
        analysisScopeViewModel = mock()
        dataSource = mockDataSource()
        connectionSaga = mock()
        whenever(connectionSaga.listMongoDbConnections()).thenReturn(listOf(dataSource))

        connectionPreferences = mock()
        val connectionPreferencesStateComponent = mock<ConnectionPreferencesStateComponent>()
        whenever(connectionPreferencesStateComponent.state).thenReturn(connectionPreferences)

        mongoDBToolWindow = mock()
        whenever(mongoDBToolWindow.id).thenReturn("MongoDB")

        project
            .withMockedService(codeEditorViewModel)
            .withMockedService(inspectionsViewModel)
            .withMockedService(analysisScopeViewModel)
            .withMockedService(connectionPreferencesStateComponent)
    }

    @Test
    fun `clears inspections when selecting a new connection`(
        project: Project,
        testScope: TestScope,
    ) {
        val viewModel = ConnectionStateViewModel(project, testScope)
        viewModel.connectionSaga = connectionSaga

        runBlocking {
            viewModel.selectConnection(dataSource)
            verify(inspectionsViewModel, timeout(1000)).clear()
        }
    }

    @Test
    fun `clears inspections when selecting a new database`(
        project: Project,
        testScope: TestScope,
    ) {
        val viewModel = ConnectionStateViewModel(project, testScope)
        viewModel.connectionSaga = connectionSaga

        runBlocking {
            viewModel.selectDatabase("oops")
            verify(inspectionsViewModel, timeout(1000)).clear()
        }
    }

    @Test
    fun `reanalyzes the scope when selecting a new database`(
        project: Project,
        testScope: TestScope,
    ) {
        val viewModel = ConnectionStateViewModel(project, testScope)
        viewModel.connectionSaga = connectionSaga

        runBlocking {
            viewModel.selectDatabase("oops")
            verify(analysisScopeViewModel, timeout(1000)).reanalyzeCurrentScope()
        }
    }

    @Test
    fun `attempts to load initial data only when MongoDB tool window shows up`(
        project: Project,
        testScope: TestScope,
    ) {
        val viewModel = ConnectionStateViewModel(project, testScope)
        viewModel.connectionSaga = connectionSaga

        assertFalse(viewModel.initialStateLoaded.get())

        viewModel.toolWindowShown(mongoDBToolWindow)
        eventually {
            testScope.advanceUntilIdle()
            assertTrue(viewModel.initialStateLoaded.get())
        }
    }

    @Test
    fun `does not load initial data when the tool window is not MongoDB`(
        project: Project,
        testScope: TestScope,
    ) {
        val viewModel = ConnectionStateViewModel(project, testScope)
        viewModel.connectionSaga = connectionSaga

        assertFalse(viewModel.initialStateLoaded.get())

        val toolWindow = mock<ToolWindow>()
        whenever(toolWindow.id).thenReturn("SomeOtherWindow")

        viewModel.toolWindowShown(toolWindow)
        eventually {
            testScope.advanceUntilIdle()
            assertFalse(viewModel.initialStateLoaded.get())
        }
    }

    @Test
    fun `loads the list of already existing data sources when tool window shows up`(
        project: Project,
        testScope: TestScope
    ) {
        val viewModel = ConnectionStateViewModel(project, testScope)
        viewModel.connectionSaga = connectionSaga
        viewModel.toolWindowShown(mongoDBToolWindow)
        eventually {
            testScope.advanceUntilIdle()
            val connectionState = viewModel.connectionState.value
            assertEquals(1, connectionState.connections.size)
            assertEquals(dataSource, connectionState.connections.first())
        }
    }

    @Test
    fun `restores the last selected DataSource and attempts to connect to it when tool window shows up`(
        project: Project,
        coroutineScope: TestScope,
    ) {
        val dataSourceId = dataSource.uniqueId
        whenever(connectionPreferences.dataSourceId).thenReturn(dataSourceId)

        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        viewModel.connectionSaga = connectionSaga
        viewModel.toolWindowShown(mongoDBToolWindow)

        eventually {
            coroutineScope.advanceUntilIdle()
            val connectionState = viewModel.connectionState.value
            assertEquals(dataSource, connectionState.selectedConnection)
            verify(connectionSaga, times(1)).connect(dataSource)
        }
    }

    @Test
    fun `when connected and later disconnected, triggers code analysis on the current editor`(
        project: Project,
        coroutineScope: TestScope,
    ) {
        val viewModel = ConnectionStateViewModel(project, TestScope())
        val realConnectionSaga = viewModel.connectionSaga
        whenever(connectionSaga.connect(dataSource)).thenAnswer {
            coroutineScope.launch {
                realConnectionSaga.emitSelectedConnectionStateChange(
                    SelectedConnectionState.Connected(dataSource)
                )
            }
        }
        viewModel.connectionSaga = connectionSaga
        runBlocking {
            viewModel.selectConnection(dataSource)
            verify(codeEditorViewModel, timeout(1000).atLeastOnce()).reanalyzeRelevantEditors()
        }

        runBlocking {
            viewModel.unselectSelectedConnection()
            verify(codeEditorViewModel, timeout(1000).atLeastOnce()).reanalyzeRelevantEditors()
        }
    }

    @Test
    fun `should connect to the connection after selecting it`(
        project: Project,
    ) {
        val viewModel = ConnectionStateViewModel(project, TestScope())
        viewModel.connectionSaga = mock()

        runBlocking {
            viewModel.selectConnection(dataSource)
            verify(viewModel.connectionSaga, timeout(100)).connect(dataSource)
        }
    }

    @Test
    fun `should attempt clearing out any ongoing connection attempt after unselecting a connection`(
        project: Project,
    ) {
        val viewModel = ConnectionStateViewModel(project, TestScope())
        viewModel.connectionSaga = mock()
        val dataSource = mockDataSource()

        runBlocking {
            viewModel.selectConnection(dataSource)
            viewModel.unselectSelectedConnection()
            verify(viewModel.connectionSaga, timeout(1000)).cancelOnGoingConnection()
        }
    }

    @Test
    fun `when a new data source is created, it should update the list of available connections`(
        project: Project,
        coroutineScope: TestScope
    ) {
        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        viewModel.connectionSaga = mock()
        val dataSource = mockDataSource()
        val dataSourceManager = mock<DataSourceManager<LocalDataSource>>()

        viewModel.dataSourceAdded(dataSourceManager, dataSource)
        eventually {
            coroutineScope.advanceUntilIdle()
            assertTrue(viewModel.connectionState.value.connections.any { it.uniqueId == dataSource.uniqueId })
        }
    }

    @Test
    fun `when a new data source is removed, it should drop it from the list of available connections`(
        project: Project,
        coroutineScope: TestScope,
    ) {
        val viewModel = ConnectionStateViewModel(project, TestScope())
        viewModel.connectionSaga = mock()
        val dataSource = mockDataSource()
        val dataSourceManager = mock<DataSourceManager<LocalDataSource>>()

        viewModel.dataSourceAdded(dataSourceManager, dataSource)
        eventually {
            coroutineScope.advanceUntilIdle()
            assertTrue(viewModel.connectionState.value.connections.any { it.uniqueId == dataSource.uniqueId })
        }

        viewModel.dataSourceRemoved(dataSourceManager, dataSource)
        eventually {
            assertFalse(viewModel.connectionState.value.connections.any { it.uniqueId == dataSource.uniqueId })
        }
    }

    @Test
    fun `when a selected data source is removed, it is dropped from the list of available connections and unselected`(
        project: Project,
        coroutineScope: TestScope
    ) {
        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        viewModel.connectionSaga = mock()
        val dataSourceManager = mock<DataSourceManager<LocalDataSource>>()

        runBlocking {
            viewModel.selectConnection(dataSource)
            assertEquals(dataSource, viewModel.connectionState.value.selectedConnection)
        }

        viewModel.dataSourceRemoved(dataSourceManager, dataSource)

        eventually {
            coroutineScope.advanceUntilIdle()
            assertFalse(viewModel.connectionState.value.connections.any { it.uniqueId == dataSource.uniqueId })
            assertEquals(null, viewModel.connectionState.value.selectedConnection)
        }
    }

    @Test
    fun `when the data source is disconnected it is also also unselected`(
        project: Project,
        coroutineScope: TestScope
    ) {
        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        viewModel.connectionSaga = mock()

        runBlocking {
            viewModel.selectConnection(dataSource)
            assertEquals(dataSource, viewModel.connectionState.value.selectedConnection)
        }

        viewModel.onTerminated(dataSource, null)

        eventually {
            coroutineScope.advanceUntilIdle()
            assertEquals(null, viewModel.connectionState.value.selectedConnection)
        }
    }
}
