package com.mongodb.jbplugin.ui.viewModel

import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.connection.ConnectionRequestor
import com.intellij.database.run.ConsoleRunConfiguration
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.editor.services.implementations.MdbEditorService
import com.mongodb.jbplugin.editor.services.implementations.getDataSourceService
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.observability.useLogMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    fun withConnectionState(newState: SelectedConnectionState): ConnectionState {
        return copy(selectedConnectionState = newState)
    }

    companion object {
        fun default() = ConnectionState(emptyList(), SelectedConnectionState.Empty)
    }
}

@Service(Service.Level.PROJECT)
class ConnectionStateViewModel(
    private val project: Project,
    coroutineScope: CoroutineScope
) {
    internal var connectionSaga = ConnectionSaga(project, ::emitSelectedConnectionChanged)
    val connectionState = MutableStateFlow(ConnectionState.default())

    init {
        coroutineScope.launch {
            val dataSources = project.getDataSourceService().listMongoDbDataSources()
            connectionState.emit(ConnectionState(dataSources, SelectedConnectionState.Empty))
        }

        coroutineScope.launch {
            connectionState.distinctUntilChangedBy { it.selectedConnectionState }
                .map { it.selectedConnectionState }
                .collectLatest(::onSelectedConnectionChanges)
        }
    }

    suspend fun selectDataSource(dataSource: LocalDataSource?) {
        if (dataSource != null) {
            connectionSaga.doConnect(dataSource)
        } else {
            connectionSaga.doDisconnect()
        }
    }

    internal suspend fun onSelectedConnectionChanges(newState: SelectedConnectionState) {
        when (newState) {
            is SelectedConnectionState.Connected -> withContext(Dispatchers.IO) {
                val mdbEditorService by project.service<MdbEditorService>()
                mdbEditorService.reAnalyzeSelectedEditor(true)
            }
            else -> {
            }
        }
    }

    private suspend fun emitSelectedConnectionChanged(newState: SelectedConnectionState) {
        connectionState.emit(connectionState.value.withConnectionState(newState))
    }
}

/**
 * This is going to handle the actual connection logic. We are keeping this internal to this module
 * because we don't want to be able to connect through any other API that is not the ConnectionStateViewModel.
 */
internal class ConnectionSaga(val project: Project, val emitConnectionChange: suspend (SelectedConnectionState) -> Unit) {
    suspend fun doConnect(dataSource: LocalDataSource) {
        emitConnectionChange(SelectedConnectionState.Connecting(dataSource))
        if (dataSource.isConnected()) {
            emitConnectionChange(SelectedConnectionState.Connected(dataSource))
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
                emitConnectionChange(SelectedConnectionState.Failed(dataSource, "Could not establish connection."))
                log.warn(
                    useLogMessage(
                        "Could not connect to DataSource(${dataSource.uniqueId})"
                    ).build(),
                )
                return
            }
            emitConnectionChange(SelectedConnectionState.Connected(dataSource))
        } catch (exception: Exception) {
            emitConnectionChange(SelectedConnectionState.Failed(dataSource, exception.message ?: exception.cause?.message ?: "Could not establish connection."))
            log.warn(
                useLogMessage(
                    "Could not connect to DataSource(${dataSource.uniqueId})"
                ).build(),
            )
        }
    }

    suspend fun doDisconnect() {
        emitConnectionChange(SelectedConnectionState.Empty)
    }
}
