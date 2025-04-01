package com.mongodb.jbplugin.ui.viewModel

import com.intellij.database.console.JdbcDriverManager
import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.connection.ConnectionRequestor
import com.intellij.database.dataSource.findProjectWhereActive
import com.intellij.database.dataSource.localDataSource
import com.intellij.database.datagrid.DataAuditor
import com.intellij.database.datagrid.DataRequest.Context
import com.intellij.database.dialects.base.ProcessDbmsOutputAction.Companion.connection
import com.intellij.database.model.RawDataSource
import com.intellij.database.psi.DataSourceManager
import com.intellij.database.run.ConsoleRunConfiguration
import com.intellij.database.view.ui.DataSourceManagerDialog
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isMongoDbDataSource
import com.mongodb.jbplugin.editor.services.MdbPluginDisposable
import com.mongodb.jbplugin.editor.services.implementations.getConnectionPreferences
import com.mongodb.jbplugin.editor.services.implementations.getDataSourceService
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.observability.useLogMessage
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState.Connected
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState.Connecting
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState.Empty
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState.Failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private val log = logger<ConnectionStateViewModel>()

sealed interface SelectedConnectionState {
    data object Empty : SelectedConnectionState
    data class Connecting(val dataSource: LocalDataSource) : SelectedConnectionState
    data class Connected(val dataSource: LocalDataSource) : SelectedConnectionState
    data class Failed(val dataSource: LocalDataSource, val error: String) : SelectedConnectionState
}

data class ConnectionState(
    val connections: List<LocalDataSource>,
    val selectedConnectionState: SelectedConnectionState
) {
    fun withDataSource(dataSource: LocalDataSource): ConnectionState {
        if (!dataSource.isMongoDbDataSource()) {
            return this
        }

        val dataSources = connections.filter { it.uniqueId != dataSource.uniqueId } + dataSource
        return copy(connections = dataSources)
    }

    fun withoutDataSource(dataSource: LocalDataSource): ConnectionState {
        val dataSources = connections.filter { it.uniqueId != dataSource.uniqueId }
        return copy(connections = dataSources).whenDisconnected(dataSource)
    }

    fun withConnectionState(newState: SelectedConnectionState): ConnectionState {
        return copy(selectedConnectionState = newState)
    }

    fun whenDisconnected(dataSource: LocalDataSource): ConnectionState {
        return when (selectedConnectionState) {
            Empty, is Failed, is Connecting -> this
            is Connected -> if (selectedConnectionState.dataSource.uniqueId == dataSource.uniqueId) {
                copy(selectedConnectionState = Empty)
            } else {
                this
            }
        }
    }

    companion object {
        fun default() = ConnectionState(emptyList(), Empty)
    }
}

@Service(Service.Level.PROJECT)
class ConnectionStateViewModel(
    private val project: Project,
    private val coroutineScope: CoroutineScope
) : DataSourceManager.Listener, JdbcDriverManager.Listener {
    internal var connectionSaga = ConnectionSaga(project, ::emitSelectedConnectionChanged)
    internal val mutableConnectionState = MutableStateFlow(ConnectionState.default())
    val connectionState = mutableConnectionState.asStateFlow()

    init {
        coroutineScope.launch {
            val dataSources = project.getDataSourceService().listMongoDbDataSources()
            mutableConnectionState.emit(ConnectionState(dataSources, Empty))

            val connectionPreferences = project.getConnectionPreferences()
            val selectedDataSource = dataSources.find { it.uniqueId == connectionPreferences.dataSourceId }
            if (selectedDataSource != null) {
                selectDataSource(selectedDataSource, background = false)
            }
        }

        coroutineScope.launch {
            mutableConnectionState.distinctUntilChangedBy { it.selectedConnectionState }
                .map { it.selectedConnectionState }
                .collectLatest(::onSelectedConnectionChanges)
        }

        val messageBusConnection = project.messageBus.connect(
            MdbPluginDisposable.getInstance(project)
        )

        messageBusConnection.subscribe(DataSourceManager.TOPIC, this)
        messageBusConnection.subscribe(JdbcDriverManager.TOPIC, this)
    }

    suspend fun selectDataSource(dataSource: LocalDataSource?, background: Boolean = true) {
        if (dataSource != null) {
            if (background) {
                coroutineScope.launch {
                    connectionSaga.doConnect(dataSource)
                }.join()
            } else {
                connectionSaga.doConnect(dataSource)
            }
        } else {
            connectionSaga.doDisconnect()
        }
    }

    suspend fun requestDataSourceCreation() {
        val dataSource = connectionSaga.requestAddNewDataSource()
        if (dataSource != null) {
            connectionSaga.doConnect(dataSource)
        }
    }

    suspend fun requestEditDataSource() {
        val selectedConnectionState = mutableConnectionState.value.selectedConnectionState
        if (selectedConnectionState is Connected) {
            connectionSaga.requestEditDataSource(selectedConnectionState.dataSource)
        }
    }

    internal suspend fun onSelectedConnectionChanges(newState: SelectedConnectionState) {
        when (newState) {
            is Connected -> withContext(Dispatchers.IO) {
                val codeEditorViewModel by project.service<CodeEditorViewModel>()
                codeEditorViewModel.reanalyzeRelevantEditors()
            }
            else -> {
            }
        }
    }

    private suspend fun emitSelectedConnectionChanged(newState: SelectedConnectionState) {
        mutableConnectionState.emit(mutableConnectionState.value.withConnectionState(newState))
    }

    override fun <T : RawDataSource?> dataSourceAdded(
        manager: DataSourceManager<T>,
        dataSource: T & Any
    ) {
        if (dataSource is LocalDataSource) {
            coroutineScope.launch {
                mutableConnectionState.emit(mutableConnectionState.value.withDataSource(dataSource))
            }
        }
    }

    override fun <T : RawDataSource?> dataSourceRemoved(
        manager: DataSourceManager<T>,
        dataSource: T & Any
    ) {
        if (dataSource is LocalDataSource) {
            coroutineScope.launch {
                mutableConnectionState.emit(mutableConnectionState.value.withoutDataSource(dataSource))
            }
        }
    }

    override fun onTerminated(
        dataSource: LocalDataSource,
        configuration: ConsoleRunConfiguration?
    ) {
        coroutineScope.launch {
            mutableConnectionState.emit(mutableConnectionState.value.whenDisconnected(dataSource))
        }
    }

    override fun <T : RawDataSource?> dataSourceChanged(
        manager: DataSourceManager<T>?,
        dataSource: T?
    ) {
        if (dataSource is LocalDataSource) {
            coroutineScope.launch {
                mutableConnectionState.emit(mutableConnectionState.value.withDataSource(dataSource))
            }
        }
    }
}

/**
 * When we modify the target cluster somehow through DataGrip, we might want to retrigger the
 * analysis of the scope.
 */
class DataSourcesChangesAuditor : DataAuditor {
    override fun requestFinished(context: Context) {
        val dataSource = context.connection?.connectionPoint?.dataSource ?: return
        val project = dataSource.findProjectWhereActive(null)

        if (likelyChangesDataSource(context.query)) {
            dataSource.incModificationCount()
        }

        if (project != null) {
            val codeEditorViewModel by project.service<AnalysisScopeViewModel>()
            runBlocking {
                codeEditorViewModel.reanalyzeCurrentScope()
            }
        }
    }

    private fun likelyChangesDataSource(script: String): Boolean {
        return script.let {
            it.contains("createIndex") ||
                it.contains("dropIndex") ||
                it.contains("update") ||
                it.contains("delete") ||
                it.contains("insert")
        }
    }
}

/**
 * This is going to handle the actual connection logic. We are keeping this internal to this module
 * because we don't want to be able to connect through any other API that is not the ConnectionStateViewModel.
 */
internal class ConnectionSaga(val project: Project, val emitConnectionChange: suspend (SelectedConnectionState) -> Unit) {
    suspend fun doConnect(dataSource: LocalDataSource) {
        emitConnectionChange(Connecting(dataSource))
        if (dataSource.isConnected()) {
            emitConnectionChange(Connected(dataSource))
            return
        }

        try {
            val connectionManager = DatabaseConnectionManager.getInstance()
            val connectionHandler =
                connectionManager
                    .build(project, dataSource)
                    .setRequestor(ConnectionRequestor.Anonymous())
                    .setAskPassword(true)
                    .setRunConfiguration(
                        ConsoleRunConfiguration.newConfiguration(project).apply {
                            setOptionsFromDataSource(dataSource)
                        },
                    )

            val connection = connectionHandler.create()?.get()
            if (connection == null || !dataSource.isConnected()) {
                emitConnectionChange(Failed(dataSource, "Could not establish connection."))
                log.warn(
                    useLogMessage(
                        "Could not connect to DataSource(${dataSource.uniqueId})"
                    ).build(),
                )
                return
            }
            emitConnectionChange(Connected(dataSource))
        } catch (exception: Exception) {
            emitConnectionChange(Failed(dataSource, exception.message ?: exception.cause?.message ?: "Could not establish connection."))
            log.warn(
                useLogMessage(
                    "Could not connect to DataSource(${dataSource.uniqueId})"
                ).build(),
            )
        }
    }

    suspend fun requestAddNewDataSource(): LocalDataSource? {
        val driverManager = DatabaseDriverManager.getInstance()
        val mongodbDriver = driverManager.getDriver("mongo.4")
        val selection = LocalDataSource().apply {
            url = "mongodb://localhost:27017"
            isConfiguredByUrl = true
            databaseDriver = mongodbDriver
            isSingleConnection = true
        }

        val result = withContext(Dispatchers.EDT) {
            DataSourceManagerDialog.showDialog(project, selection, null)
        }
        return result.firstNotNullOfOrNull { it.localDataSource }
    }

    suspend fun requestEditDataSource(dataSource: LocalDataSource) {
        withContext(Dispatchers.EDT) {
            DataSourceManagerDialog.showDialog(project, dataSource, null)
        }
    }

    suspend fun doDisconnect() {
        emitConnectionChange(Empty)
    }
}
