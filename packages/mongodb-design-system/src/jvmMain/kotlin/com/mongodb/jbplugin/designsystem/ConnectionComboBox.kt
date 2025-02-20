package com.mongodb.jbplugin.designsystem

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mongodb.jbplugin.designsystem.ConnectionState.CONNECTED
import kotlinx.coroutines.flow.Flow
import org.jetbrains.jewel.ui.component.ComboBox
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

enum class ConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class Connection(val id: String, val name: String, val state: ConnectionState)

@Composable
@Preview
fun ConnectionComboBox(
    activeConnection: Flow<Connection?>,
    allConnections: Flow<List<Connection>>,
    onConnectionSelected: (Connection) -> Unit
) {
    val connections by allConnections.collectAsState(emptyList())
    val selected by activeConnection.collectAsState(null as Connection?)

    Row {
        Column {
            ComboBox(selected?.name ?: "Choose a connection") {
                Column {
                    for (connection in connections) {
                        ComboBoxConnectionElement(connection, onConnectionSelected)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComboBoxConnectionElement(connection: Connection, onClick: (Connection) -> Unit) {
    Box(modifier = Modifier.clickable { onClick(connection) }) {
        Row {
            Icon(
                when (connection.state) {
                    CONNECTED -> AllIconsKeys.General.GreenCheckmark
                    else -> AllIconsKeys.General.Error
                },
                "MongoDB Connection ${connection.name}"
            )
            Text(connection.name)
        }
    }
}
