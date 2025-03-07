package com.mongodb.jbplugin.sidePanel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mongodb.jbplugin.sidePanel.ui.hooks.useViewModelMutator
import com.mongodb.jbplugin.sidePanel.ui.hooks.useViewModelState
import com.mongodb.jbplugin.sidePanel.viewModel.ConnectionState.Connected
import com.mongodb.jbplugin.sidePanel.viewModel.ConnectionState.Disconnected
import com.mongodb.jbplugin.sidePanel.viewModel.ConnectionStateViewModel
import org.jetbrains.jewel.ui.component.Text

@Composable
fun TestConnectionList() {
    val currentConnection by useViewModelState(ConnectionStateViewModel::connection)
    val connectionList by useViewModelState(ConnectionStateViewModel::connectionList)
    val connect = useViewModelMutator(ConnectionStateViewModel::connect)

    Column {
        Row {
            when (currentConnection) {
                is Disconnected -> Text("Disconnected")
                is Connected -> Text("Connected to ${(currentConnection as Connected).dataSource.name}")
            }
        }

        Row(Modifier.padding(1.dp).background(Color.Green)) {}

        for (connection in connectionList) {
            Row(Modifier.clickable { connect(connection) }) {
                Text(connection.name)
            }
        }
    }
}
