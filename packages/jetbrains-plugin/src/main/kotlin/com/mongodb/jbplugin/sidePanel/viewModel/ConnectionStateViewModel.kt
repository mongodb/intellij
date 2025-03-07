package com.mongodb.jbplugin.sidePanel.viewModel

import com.intellij.database.console.JdbcDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.model.RawDataSource
import com.intellij.database.psi.DataSourceManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.editor.services.implementations.MdbDataSourceService
import com.mongodb.jbplugin.meta.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connected(val dataSource: LocalDataSource) : ConnectionState
}

@Service(Service.Level.PROJECT)
class ConnectionStateViewModel(
    project: Project,
    private val coroutineScope: CoroutineScope
) :
    DataSourceManager.Listener,
    JdbcDriverManager.Listener {
    val connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionList: MutableStateFlow<List<LocalDataSource>> = MutableStateFlow(emptyList())

    init {
        val mdbDataSourceService by project.service<MdbDataSourceService>()
        coroutineScope.launch {
            connectionList.emit(mdbDataSourceService.listMongoDbDataSources())
        }
    }

    suspend fun connect(dataSource: LocalDataSource) {
        connection.emit(ConnectionState.Connected(dataSource))
    }

    override fun <T : RawDataSource?> dataSourceAdded(
        manager: DataSourceManager<T>,
        dataSource: T & Any
    ) {
        if (dataSource is LocalDataSource) {
            val newList = connectionList.value + dataSource
            coroutineScope.launch {
                connectionList.emit(newList)
            }
        }
    }

    override fun <T : RawDataSource?> dataSourceRemoved(
        manager: DataSourceManager<T>,
        dataSource: T & Any
    ) {
        coroutineScope.launch {
            val newList = connectionList.value.filter { it.uniqueId != dataSource.uniqueId }
            connectionList.emit(newList)
        }
    }
}
