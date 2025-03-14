package com.mongodb.jbplugin.ui.components.connection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.intellij.database.dataSource.LocalDataSource
import com.mongodb.jbplugin.ui.components.utilities.Card
import com.mongodb.jbplugin.ui.components.utilities.CardCategory
import com.mongodb.jbplugin.ui.components.utilities.hooks.useTranslation
import com.mongodb.jbplugin.ui.components.utilities.hooks.useViewModelMutator
import com.mongodb.jbplugin.ui.components.utilities.hooks.useViewModelState
import com.mongodb.jbplugin.ui.viewModel.ConnectionState
import com.mongodb.jbplugin.ui.viewModel.ConnectionStateViewModel
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState
import org.jetbrains.jewel.ui.component.ComboBox
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
fun ConnectionBootstrapCard() {
    val connectionState by useViewModelState(ConnectionStateViewModel::connectionState, ConnectionState.default())
    val connect by useViewModelMutator(ConnectionStateViewModel::selectDataSource)

    val connectionCallbacks = ConnectionCallbacks(
        onConnect = connect
    )

    CompositionLocalProvider(LocalConnectionCallbacks provides connectionCallbacks) {
        _ConnectionBootstrapCard(connectionState)
    }
}

@Composable
internal fun _ConnectionBootstrapCard(
    connectionState: ConnectionState
) {
    Card(CardCategory.LOGO, useTranslation("side-panel.connection.ConnectionBootstrapCard.title")) {
        Column {
            Box { Text(useTranslation("side-panel.connection.ConnectionBootstrapCard.subtitle")) }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                BulletItem(useTranslation("side-panel.connection.ConnectionBootstrapCard.1st-bullet-point"))
                BulletItem(useTranslation("side-panel.connection.ConnectionBootstrapCard.2st-bullet-point"))
                BulletItem(useTranslation("side-panel.connection.ConnectionBootstrapCard.3st-bullet-point"))
            }
            Column(modifier = Modifier.padding(top = 8.dp)) {
                ConnectionComboBox(connectionState.selectedConnectionState, connectionState.connections)
            }
        }
    }
}

@Composable
internal fun BulletItem(text: String) {
    return Box { Text("\u2022 $text") }
}

@Composable
internal fun ConnectionComboBox(selectedConnectionState: SelectedConnectionState, dataSources: List<LocalDataSource>) {
    ComboBox(
        when (selectedConnectionState) {
            is SelectedConnectionState.Empty -> "Choose a connection"
            is SelectedConnectionState.Connected -> selectedConnectionState.dataSource.name
        }
    ) {
        Column {
            if (selectedConnectionState !is SelectedConnectionState.Empty) {
                DisconnectItem()
            }

            for (dataSource in dataSources) {
                ConnectionItem(dataSource)
            }
        }
    }
}

@Composable
internal fun DisconnectItem() {
    val connectionCallback = useConnectionCallbacks()

    Box(
        modifier = Modifier
            .testTag("DisconnectItem")
            .clickable { connectionCallback.onConnect(null) }
            .padding(4.dp)
            .fillMaxWidth(1f)
    ) {
        Row {
            Icon(AllIconsKeys.General.Close, "", modifier = Modifier.padding(end = 8.dp))
            Text("Disconnect")
        }
    }
}

@Composable
internal fun ConnectionItem(dataSource: LocalDataSource) {
    val connectionCallback = useConnectionCallbacks()

    Box(
        modifier = Modifier
            .testTag("ConnectionItem::${dataSource.uniqueId}")
            .clickable { connectionCallback.onConnect(dataSource) }
            .padding(4.dp)
            .fillMaxWidth(1f)
    ) {
        Row {
            Icon(AllIconsKeys.Providers.MongoDB, "", modifier = Modifier.padding(end = 8.dp))
            Text(dataSource.name)
        }
    }
}

internal data class ConnectionCallbacks(
    val onConnect: (LocalDataSource?) -> Unit = {}
)

internal val LocalConnectionCallbacks = compositionLocalOf { ConnectionCallbacks() }

@Composable
internal fun useConnectionCallbacks(): ConnectionCallbacks {
    return LocalConnectionCallbacks.current
}
