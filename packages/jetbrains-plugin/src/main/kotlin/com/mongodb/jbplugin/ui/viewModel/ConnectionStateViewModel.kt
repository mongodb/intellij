package com.mongodb.jbplugin.ui.viewModel

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.editor.services.implementations.getDataSourceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

sealed interface SelectedConnectionState {
    data object Empty : SelectedConnectionState
    data class Connected(val dataSource: LocalDataSource) : SelectedConnectionState
}

data class ConnectionState(
    val connections: List<LocalDataSource>,
    val selectedConnectionState: SelectedConnectionState
) {
    companion object {
        fun default() = ConnectionState(emptyList(), SelectedConnectionState.Empty)
    }
}

@Service(Service.Level.PROJECT)
class ConnectionStateViewModel(
    project: Project,
    coroutineScope: CoroutineScope
) {
    val connectionState = MutableStateFlow(ConnectionState.default())

    init {
        // TODO
        coroutineScope.launch {
            val dataSources = project.getDataSourceService().listMongoDbDataSources()
            connectionState.emit(ConnectionState(dataSources, SelectedConnectionState.Empty))
        }
    }

    suspend fun selectDataSource(dataSource: LocalDataSource?) {
        val connected = connectionState.value.copy(
            selectedConnectionState = dataSource?.let { SelectedConnectionState.Connected(dataSource) } ?: SelectedConnectionState.Empty
        )

        connectionState.emit(connected)
    }
}
