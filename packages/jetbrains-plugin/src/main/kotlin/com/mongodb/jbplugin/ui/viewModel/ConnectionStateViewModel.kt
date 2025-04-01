package com.mongodb.jbplugin.ui.viewModel

import com.intellij.database.console.JdbcDriverManager
import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.connection.ConnectionRequestor
import com.intellij.database.dataSource.localDataSource
import com.intellij.database.model.RawDataSource
import com.intellij.database.psi.DataSourceManager
import com.intellij.database.run.ConsoleRunConfiguration
import com.intellij.database.view.ui.DataSourceManagerDialog
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isMongoDbDataSource
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.editor.services.ConnectionPreferences.Companion.UNINITIALIZED_DATABASE
import com.mongodb.jbplugin.editor.services.MdbPluginDisposable
import com.mongodb.jbplugin.editor.services.implementations.MdbEditorService
import com.mongodb.jbplugin.editor.services.implementations.getConnectionPreferences
import com.mongodb.jbplugin.meta.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

sealed interface SelectedConnectionState {
    val connection: LocalDataSource?

    fun isForConnection(selectedConnection: LocalDataSource?): Boolean {
        return selectedConnection?.uniqueId == connection?.uniqueId
    }

    data object Initial : SelectedConnectionState {
        override var connection: LocalDataSource? = null
        override fun isForConnection(selectedConnection: LocalDataSource?): Boolean {
            return selectedConnection == null
        }
    }

    data class Connecting(override val connection: LocalDataSource) : SelectedConnectionState

    data class Connected(override val connection: LocalDataSource) : SelectedConnectionState

    data class Failed(
        override val connection: LocalDataSource,
        val errorMessage: String
    ) : SelectedConnectionState
}

data class ConnectionState(
    val connections: List<LocalDataSource>,
    val selectedConnection: LocalDataSource?,
    val selectedConnectionState: SelectedConnectionState
) {
    companion object {
        fun initial() = ConnectionState(
            connections = emptyList(),
            selectedConnection = null,
            selectedConnectionState = SelectedConnectionState.Initial,
        )
    }
}

sealed interface DatabasesLoadingState {
    val connection: LocalDataSource?
    val databases: List<String>

    fun isForConnection(selectedConnection: LocalDataSource?): Boolean {
        return selectedConnection?.uniqueId == connection?.uniqueId
    }

    data object Initial : DatabasesLoadingState {
        override val connection: LocalDataSource? = null
        override var databases = emptyList<String>()
        override fun isForConnection(selectedConnection: LocalDataSource?): Boolean {
            return selectedConnection == null
        }
    }

    data class Loading(override val connection: LocalDataSource) : DatabasesLoadingState {
        override var databases = emptyList<String>()
    }

    data class Loaded(
        override val connection: LocalDataSource,
        override val databases: List<String>
    ) : DatabasesLoadingState

    data class Failed(
        override val connection: LocalDataSource,
        val errorMessage: String,
    ) : DatabasesLoadingState {
        override var databases = emptyList<String>()
    }
}

data class DatabaseState(
    val databasesLoadingState: DatabasesLoadingState,
    val selectedDatabase: String?,
) {
    companion object {
        fun initial() = DatabaseState(
            databasesLoadingState = DatabasesLoadingState.Initial,
            selectedDatabase = null,
        )
    }
}

@Service(Service.Level.PROJECT)
class ConnectionStateViewModel(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) : DataSourceManager.Listener, JdbcDriverManager.Listener, ToolWindowManagerListener {
    @VisibleForTesting
    internal val initialStateLoaded = AtomicBoolean(false)

    @VisibleForTesting
    internal var connectionSaga = ConnectionSaga(
        project = project,
        coroutineScope = coroutineScope,
        emitSelectedConnectionStateChange = ::emitSelectedConnectionStateChange,
        emitDatabasesLoadingStateChange = ::emitDatabasesLoadingStateChange,
    )

    private val mutableConnectionState = MutableStateFlow(ConnectionState.initial())
    val connectionState = mutableConnectionState.asStateFlow()

    private val mutableDatabaseState = MutableStateFlow(DatabaseState.initial())
    val databaseState = mutableDatabaseState.asStateFlow()

    init {
        setupListeners()
        coroutineScope.launch(Dispatchers.IO) {
            connectionState.distinctUntilChanged { old, new ->
                old.selectedConnection?.uniqueId == new.selectedConnection?.uniqueId &&
                    old.selectedConnectionState == new.selectedConnectionState
            }.collectLatest { (_, selectedConnection, selectedConnectionState) ->
                val isDisconnected = selectedConnection == null
                val isConnected = selectedConnection != null && selectedConnectionState is SelectedConnectionState.Connected

                // Doing this to account for only the relevant states that will be necessary for
                // re-analysing the files.
                if (isDisconnected || isConnected) {
                    val mdbEditorService by project.service<MdbEditorService>()
                    mdbEditorService.reAnalyzeSelectedEditor(true)
                }

                if (isDisconnected) {
                    connectionSaga.cancelOnGoingDatabasesFetch()
                    mutableDatabaseState.update { databaseState ->
                        databaseState.copy(
                            databasesLoadingState = DatabasesLoadingState.Initial,
                            selectedDatabase = null,
                        )
                    }
                } else if (selectedConnectionState is SelectedConnectionState.Connecting) {
                    mutableDatabaseState.update { databaseState ->
                        databaseState.copy(
                            databasesLoadingState = DatabasesLoadingState.Loading(selectedConnection!!),
                            selectedDatabase = null,
                        )
                    }
                } else if (isConnected) {
                    connectionSaga.listDatabases(selectedConnection!!)
                }
            }
        }

        coroutineScope.launch(Dispatchers.IO) {
            databaseState.distinctUntilChanged { old, new ->
                old.selectedDatabase == new.selectedDatabase
            }.collectLatest {
                val mdbEditorService by project.service<MdbEditorService>()
                mdbEditorService.reAnalyzeSelectedEditor(true)
            }
        }
    }

    override fun toolWindowShown(toolWindow: ToolWindow) {
        // We defer loading the initial state until we actually need to. MongoDB Tool window getting
        // visible is a good sign of when we ideally would want data to be loaded. This is done to
        // postpone immediate effects of loading data during startup, such as a connection trying
        // to connect but failing in 10 seconds due to timeout and by the time user checks the tool
        // window they only see the error content without any clue about what happened.
        if (toolWindow.id == "MongoDB" &&
            initialStateLoaded.compareAndSet(
                false,
                true
            )
        ) {
            loadInitialState()
        }
    }

    override fun <T : RawDataSource?> dataSourceAdded(
        manager: DataSourceManager<T>,
        addedDataSource: T & Any
    ) {
        if (addedDataSource is LocalDataSource) {
            coroutineScope.launch(Dispatchers.IO) {
                mutableConnectionState.update { state ->
                    state.copy(connections = state.connections + addedDataSource)
                }
            }
        }
    }

    override fun <T : RawDataSource?> dataSourceRemoved(
        manager: DataSourceManager<T>,
        removedDataSource: T & Any
    ) {
        if (removedDataSource is LocalDataSource) {
            coroutineScope.launch(Dispatchers.IO) {
                val (_, selectedConnection) = mutableConnectionState.getAndUpdate { state ->
                    state.copy(connections = state.connections - removedDataSource)
                }

                if (selectedConnection?.uniqueId == removedDataSource.uniqueId) {
                    unselectSelectedConnection()
                }
            }
        }
    }

    override fun <T : RawDataSource?> dataSourceChanged(
        manager: DataSourceManager<T>?,
        changedDataSource: T?
    ) {
        if (changedDataSource is LocalDataSource) {
            coroutineScope.launch(Dispatchers.IO) {
                val (_, selectedConnection) = mutableConnectionState.getAndUpdate { state ->
                    state.copy(
                        connections = state.connections.filter {
                            it.uniqueId != changedDataSource.uniqueId
                        } + changedDataSource
                    )
                }

                if (selectedConnection?.uniqueId == changedDataSource.uniqueId) {
                    selectConnection(changedDataSource)
                }
            }
        }
    }

    override fun onTerminated(
        terminatedDataSource: LocalDataSource,
        configuration: ConsoleRunConfiguration?
    ) {
        if (connectionState.value.selectedConnection?.uniqueId == terminatedDataSource.uniqueId) {
            coroutineScope.launch(Dispatchers.IO) {
                unselectSelectedConnection()
            }
        }
    }

    suspend fun selectConnection(connection: LocalDataSource) {
        withContext(Dispatchers.IO) {
            mutableConnectionState.update { state ->
                state.copy(
                    selectedConnection = connection,
                    selectedConnectionState = SelectedConnectionState.Initial,
                )
            }
            val connectionPreferences = project.getConnectionPreferences()
            connectionPreferences.dataSourceId = connection.uniqueId

            connectionSaga.connect(dataSource = connection)
        }
    }

    suspend fun unselectSelectedConnection() {
        withContext(Dispatchers.IO) {
            mutableConnectionState.update { state ->
                state.copy(
                    selectedConnection = null,
                    selectedConnectionState = SelectedConnectionState.Initial,
                )
            }
            val connectionPreferences = project.getConnectionPreferences()
            connectionPreferences.dataSourceId = null

            connectionSaga.cancelOnGoingConnection()
        }
    }

    suspend fun addNewConnection() {
        val connection = connectionSaga.addNewConnection()
        if (connection != null) {
            selectConnection(connection)
        }
    }

    suspend fun selectDatabase(database: String) {
        withContext(Dispatchers.IO) {
            mutableDatabaseState.update { state ->
                state.copy(
                    selectedDatabase = database,
                )
            }
            val connectionPreferences = project.getConnectionPreferences()
            connectionPreferences.database = database
        }
    }

    suspend fun unselectSelectedDatabase() {
        withContext(Dispatchers.IO) {
            mutableDatabaseState.update { state ->
                state.copy(
                    selectedDatabase = null,
                )
            }
            val connectionPreferences = project.getConnectionPreferences()
            connectionPreferences.database = null
        }
    }

    suspend fun editSelectedConnection() {
        val (_, selectedConnection, selectedConnectionState) = mutableConnectionState.value
        if (
            selectedConnection != null &&
            selectedConnectionState is SelectedConnectionState.Connected
        ) {
            connectionSaga.editConnection(selectedConnection)
        }
    }

    private fun setupListeners() {
        val messageBusConnection = project.messageBus.connect(
            MdbPluginDisposable.getInstance(project)
        )
        messageBusConnection.subscribe(DataSourceManager.TOPIC, this)
        messageBusConnection.subscribe(JdbcDriverManager.TOPIC, this)
        messageBusConnection.subscribe(ToolWindowManagerListener.TOPIC, this)
    }

    private fun loadInitialState() {
        coroutineScope.launch(Dispatchers.IO) {
            val connectionPreferences = project.getConnectionPreferences()
            val connections = connectionSaga.listMongoDbConnections()
            val selectedConnection =
                connections.find { it.uniqueId == connectionPreferences.dataSourceId }
            mutableConnectionState.emit(
                ConnectionState(
                    connections = connections,
                    selectedConnection = null,
                    selectedConnectionState = SelectedConnectionState.Initial
                )
            )

            if (selectedConnection != null) {
                selectConnection(selectedConnection)
            }
        }
    }

    private fun emitSelectedConnectionStateChange(
        newSelectedConnectionState: SelectedConnectionState
    ) {
        mutableConnectionState.update { currentConnectionState ->
            if (newSelectedConnectionState.isForConnection(
                    currentConnectionState.selectedConnection
                )
            ) {
                currentConnectionState.copy(selectedConnectionState = newSelectedConnectionState)
            } else {
                currentConnectionState
            }
        }
    }

    private fun emitDatabasesLoadingStateChange(
        newDatabasesLoadingState: DatabasesLoadingState
    ) {
        val editorService by project.service<MdbEditorService>()
        val connectionPreferences = project.getConnectionPreferences()
        mutableDatabaseState.update { currentDatabaseState ->
            if (newDatabasesLoadingState.isForConnection(connectionState.value.selectedConnection)) {
                val databaseToBeSelected = if (connectionPreferences.database == UNINITIALIZED_DATABASE) {
                    editorService.inferredDatabase
                } else {
                    connectionPreferences.database
                }.takeIf { newDatabasesLoadingState.databases.contains(it) }

                currentDatabaseState.copy(
                    databasesLoadingState = newDatabasesLoadingState,
                    selectedDatabase = databaseToBeSelected
                )
            } else {
                currentDatabaseState
            }
        }
    }
}

internal class ConnectionSaga(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
    internal val emitSelectedConnectionStateChange: suspend (SelectedConnectionState) -> Unit,
    internal val emitDatabasesLoadingStateChange: suspend (DatabasesLoadingState) -> Unit,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val connectionDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val onGoingConnection = AtomicReference<Job?>(null)

    private val onGoingDatabasesFetch = AtomicReference<Job?>(null)

    fun listMongoDbConnections(): List<LocalDataSource> {
        return DataSourceManager.byDataSource(project, LocalDataSource::class.java)
            ?.dataSources?.filter {
                it.isMongoDbDataSource()
            }
            ?: emptyList()
    }

    fun connect(dataSource: LocalDataSource) {
        onGoingConnection.getAndSet(
            coroutineScope.launch(connectionDispatcher) {
                try {
                    emitSelectedConnectionStateChange(
                        SelectedConnectionState.Connecting(dataSource)
                    )
                    if (dataSource.isConnected()) {
                        emitSelectedConnectionStateChange(
                            SelectedConnectionState.Connected(dataSource)
                        )
                        return@launch
                    }

                    val connection = DatabaseConnectionManager.getInstance()
                        .build(project, dataSource)
                        .setRequestor(ConnectionRequestor.Anonymous())
                        .setAskPassword(true)
                        .setRunConfiguration(
                            ConsoleRunConfiguration.newConfiguration(project).apply {
                                setOptionsFromDataSource(dataSource)
                            },
                        ).create()?.get()

                    if (connection == null || !dataSource.isConnected()) {
                        emitSelectedConnectionStateChange(
                            SelectedConnectionState.Failed(
                                connection = dataSource,
                                errorMessage = "Could not connect to Connection"
                            )
                        )
                    } else {
                        emitSelectedConnectionStateChange(
                            SelectedConnectionState.Connected(dataSource)
                        )
                    }
                } catch (_: CancellationException) {
                    // Do nothing
                } catch (exception: Exception) {
                    emitSelectedConnectionStateChange(
                        SelectedConnectionState.Failed(
                            connection = dataSource,
                            errorMessage = exception.cause?.message ?: "Could not establish connection."
                        )
                    )
                }
            }
        )?.cancel()
    }

    fun cancelOnGoingConnection() {
        onGoingConnection.getAndSet(null)?.cancel()
    }

    suspend fun addNewConnection(): LocalDataSource? {
        val selection = LocalDataSource().apply {
            url = "mongodb://localhost:27017"
            isConfiguredByUrl = true
            databaseDriver = DatabaseDriverManager.getInstance().getDriver("mongo.4")
            isSingleConnection = true
        }

        return withContext(Dispatchers.EDT) {
            DataSourceManagerDialog.showDialog(project, selection, null)
        }.firstNotNullOfOrNull { it.localDataSource }
    }

    suspend fun editConnection(connection: LocalDataSource) {
        withContext(Dispatchers.EDT) {
            DataSourceManagerDialog.showDialog(project, connection, null)
        }
    }

    fun listDatabases(connection: LocalDataSource) {
        onGoingDatabasesFetch.getAndSet(
            coroutineScope.launch(connectionDispatcher) {
                try {
                    emitDatabasesLoadingStateChange(
                        DatabasesLoadingState.Loading(connection)
                    )
                    val readModel = project.getService(DataGripBasedReadModelProvider::class.java)
                    val databases = readModel.slice(connection, ListDatabases.Slice)
                    emitDatabasesLoadingStateChange(
                        DatabasesLoadingState.Loaded(
                            connection = connection,
                            databases = databases.databases.map { it.name }
                        )
                    )
                } catch (exception: CancellationException) {
                    // Do nothing
                } catch (exception: Exception) {
                    emitDatabasesLoadingStateChange(
                        DatabasesLoadingState.Failed(
                            connection = connection,
                            errorMessage = exception.cause?.message ?: "Could not load databases for connection."
                        )
                    )
                }
            }
        )?.cancel()
    }

    fun cancelOnGoingDatabasesFetch() {
        onGoingDatabasesFetch.getAndSet(null)?.cancel()
    }
}
