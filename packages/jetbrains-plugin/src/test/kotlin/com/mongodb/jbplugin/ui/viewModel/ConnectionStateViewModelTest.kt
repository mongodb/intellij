package com.mongodb.jbplugin.ui.viewModel

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.psi.DataSourceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.testFramework.assertInstanceOf
import com.mongodb.jbplugin.editor.services.implementations.ConnectionPreferencesStateComponent
import com.mongodb.jbplugin.editor.services.implementations.MdbEditorService
import com.mongodb.jbplugin.editor.services.implementations.PersistentConnectionPreferences
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.eventually
import com.mongodb.jbplugin.fixtures.mockDataSource
import com.mongodb.jbplugin.fixtures.withMockedService
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
    private lateinit var editorService: MdbEditorService
    private lateinit var connectionPreferences: PersistentConnectionPreferences
    private lateinit var mongoDBToolWindow: ToolWindow

    @BeforeEach
    fun setUp(project: Project) {
        editorService = mock()
        dataSource = mockDataSource()
        connectionSaga = mock()
        whenever(connectionSaga.listMongoDbConnections()).thenReturn(listOf(dataSource))

        connectionPreferences = mock()
        val connectionPreferencesStateComponent = mock<ConnectionPreferencesStateComponent>()
        whenever(connectionPreferencesStateComponent.state).thenReturn(connectionPreferences)

        mongoDBToolWindow = mock()
        whenever(mongoDBToolWindow.id).thenReturn("MongoDB")

        project
            .withMockedService(editorService)
            .withMockedService(connectionPreferencesStateComponent)
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
        }
        verify(editorService, timeout(1000).times(2)).reAnalyzeSelectedEditor(true)

        runBlocking {
            viewModel.unselectSelectedConnection()
        }
        verify(editorService, timeout(1000).times(3)).reAnalyzeSelectedEditor(true)
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

    @Test
    fun `when selected connection is connected, attempts to load databases for the connection`(
        project: Project,
        coroutineScope: TestScope,
    ) {
        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        val realConnectionSaga = viewModel.connectionSaga
        whenever(connectionSaga.connect(dataSource)).then {
            coroutineScope.launch {
                realConnectionSaga.emitSelectedConnectionStateChange(
                    SelectedConnectionState.Connected(
                        connection = dataSource,
                    )
                )
            }
        }

        var databasesRequested: Boolean = false
        whenever(connectionSaga.listDatabases(dataSource)).then {
            databasesRequested = true
            coroutineScope.launch {
                realConnectionSaga.emitDatabasesLoadingStateChange(
                    DatabasesLoadingState.Loaded(
                        connection = dataSource,
                        databases = listOf()
                    )
                )
            }
        }
        viewModel.connectionSaga = connectionSaga

        runBlocking {
            viewModel.selectConnection(dataSource)
        }

        eventually {
            coroutineScope.advanceUntilIdle()
            assertTrue(databasesRequested)
        }
    }

    @Test
    fun `when databases are loaded, it should update the DatabaseState to reflect the available databases`(
        project: Project,
        coroutineScope: TestScope,
    ) {
        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        val realConnectionSaga = viewModel.connectionSaga
        whenever(connectionSaga.connect(dataSource)).then {
            coroutineScope.launch {
                realConnectionSaga.emitSelectedConnectionStateChange(
                    SelectedConnectionState.Connected(
                        connection = dataSource,
                    )
                )
            }
        }

        whenever(connectionSaga.listDatabases(dataSource)).then {
            coroutineScope.launch {
                realConnectionSaga.emitDatabasesLoadingStateChange(
                    DatabasesLoadingState.Loaded(
                        connection = dataSource,
                        databases = listOf("DB1", "DB2")
                    )
                )
            }
        }
        viewModel.connectionSaga = connectionSaga

        runBlocking {
            viewModel.selectConnection(dataSource)
        }

        eventually {
            coroutineScope.advanceUntilIdle()
            assertEquals(2, viewModel.databaseState.value.databasesLoadingState.databases.size)
        }
    }

    @Test
    fun `when databases are loaded and there was a preselected database, it should update the DatabaseState to reflect the selection`(
        project: Project,
        coroutineScope: TestScope,
    ) {
        whenever(connectionPreferences.database).thenReturn("DB1")
        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        val realConnectionSaga = viewModel.connectionSaga
        whenever(connectionSaga.connect(dataSource)).then {
            coroutineScope.launch {
                realConnectionSaga.emitSelectedConnectionStateChange(
                    SelectedConnectionState.Connected(
                        connection = dataSource,
                    )
                )
            }
        }

        whenever(connectionSaga.listDatabases(dataSource)).then {
            coroutineScope.launch {
                realConnectionSaga.emitDatabasesLoadingStateChange(
                    DatabasesLoadingState.Loaded(
                        connection = dataSource,
                        databases = listOf("DB1", "DB2")
                    )
                )
            }
        }
        viewModel.connectionSaga = connectionSaga

        runBlocking {
            viewModel.selectConnection(dataSource)
        }

        eventually {
            coroutineScope.advanceUntilIdle()
            assertEquals(2, viewModel.databaseState.value.databasesLoadingState.databases.size)
            assertEquals("DB1", viewModel.databaseState.value.selectedDatabase)
        }
    }

    @Test
    fun `when databases loading fails, it should update the DatabaseState to reflect the state`(
        project: Project,
        coroutineScope: TestScope,
    ) {
        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        val realConnectionSaga = viewModel.connectionSaga
        whenever(connectionSaga.connect(dataSource)).then {
            coroutineScope.launch {
                realConnectionSaga.emitSelectedConnectionStateChange(
                    SelectedConnectionState.Connected(
                        connection = dataSource,
                    )
                )
            }
        }

        whenever(connectionSaga.listDatabases(dataSource)).then {
            coroutineScope.launch {
                realConnectionSaga.emitDatabasesLoadingStateChange(
                    DatabasesLoadingState.Failed(
                        connection = dataSource,
                        errorMessage = "An error occurred"
                    )
                )
            }
        }
        viewModel.connectionSaga = connectionSaga

        runBlocking {
            viewModel.selectConnection(dataSource)
        }

        eventually {
            coroutineScope.advanceUntilIdle()
            assertInstanceOf<DatabasesLoadingState.Failed>(viewModel.databaseState.value.databasesLoadingState)
        }
    }

    @Test
    fun `when a data source is changed, it updates the connection list and reconnects if selected`(
        project: Project,
        coroutineScope: TestScope,
    ) {
        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        viewModel.connectionSaga = mock()
        val dataSourceManager = mock<DataSourceManager<LocalDataSource>>()
        val updatedDataSource = mockDataSource(name = dataSource.name, uniqueId = dataSource.uniqueId)

        runBlocking {
            viewModel.selectConnection(dataSource)
            assertEquals(dataSource, viewModel.connectionState.value.selectedConnection)
        }

        viewModel.dataSourceChanged(dataSourceManager, updatedDataSource)

        eventually {
            coroutineScope.advanceUntilIdle()
            assertTrue(viewModel.connectionState.value.connections.any { it.uniqueId == updatedDataSource.uniqueId })
            assertEquals(updatedDataSource, viewModel.connectionState.value.selectedConnection)
            verify(viewModel.connectionSaga, timeout(1000)).connect(updatedDataSource)
        }
    }

    @Test
    fun `when selecting a database, it updates preferences and triggers editor reanalysis`(
        project: Project,
        coroutineScope: TestScope,
    ) {
        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        viewModel.connectionSaga = mock()

        runBlocking {
            viewModel.selectDatabase("testDB")
        }

        eventually {
            coroutineScope.advanceUntilIdle()
            assertEquals("testDB", viewModel.databaseState.value.selectedDatabase)
            assertEquals("testDB", connectionPreferences.database)
            verify(editorService, timeout(1000)).reAnalyzeSelectedEditor(true)
        }
    }

    @Test
    fun `when unselecting a database, it clears preferences and triggers editor reanalysis`(
        project: Project,
        coroutineScope: TestScope,
    ) {
        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        viewModel.connectionSaga = mock()

        runBlocking {
            viewModel.selectDatabase("testDB")
            viewModel.unselectSelectedDatabase()
        }

        eventually {
            coroutineScope.advanceUntilIdle()
            assertEquals(null, viewModel.databaseState.value.selectedDatabase)
            assertEquals(null, connectionPreferences.database)
            verify(editorService, timeout(1000).times(2)).reAnalyzeSelectedEditor(true)
        }
    }

    @Test
    fun `when adding a new connection, it selects the connection if successfully created`(
        project: Project,
        coroutineScope: TestScope,
    ) = runTest {
        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        val newDataSource = mockDataSource()
        whenever(connectionSaga.addNewConnection()).thenReturn(newDataSource)
        viewModel.connectionSaga = connectionSaga

        runBlocking {
            viewModel.addNewConnection()
        }

        eventually {
            coroutineScope.advanceUntilIdle()
            assertEquals(newDataSource, viewModel.connectionState.value.selectedConnection)
            verify(connectionSaga, timeout(1000)).connect(newDataSource)
        }
    }

    @Test
    fun `when connection fails, it updates state with error message`(
        project: Project,
        coroutineScope: TestScope,
    ) {
        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        val realConnectionSaga = viewModel.connectionSaga
        val errorMessage = "Connection failed"
        whenever(connectionSaga.connect(dataSource)).then {
            coroutineScope.launch {
                realConnectionSaga.emitSelectedConnectionStateChange(
                    SelectedConnectionState.Failed(dataSource, errorMessage)
                )
            }
        }
        viewModel.connectionSaga = connectionSaga

        runBlocking {
            viewModel.selectConnection(dataSource)
        }

        eventually {
            coroutineScope.advanceUntilIdle()
            val state = viewModel.connectionState.value.selectedConnectionState
            assertInstanceOf<SelectedConnectionState.Failed>(state)
            assertEquals(errorMessage, (state as SelectedConnectionState.Failed).errorMessage)
        }
    }

    @Test
    fun `when tool window is shown multiple times, it only loads initial state once`(
        project: Project,
        coroutineScope: TestScope,
    ) {
        val viewModel = ConnectionStateViewModel(project, coroutineScope)
        viewModel.connectionSaga = connectionSaga

        viewModel.toolWindowShown(mongoDBToolWindow)
        viewModel.toolWindowShown(mongoDBToolWindow)

        eventually {
            coroutineScope.advanceUntilIdle()
            verify(connectionSaga, timeout(1000).times(1)).listMongoDbConnections()
        }
    }
}
