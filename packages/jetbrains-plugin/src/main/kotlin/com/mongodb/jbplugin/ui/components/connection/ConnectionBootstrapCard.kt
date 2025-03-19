package com.mongodb.jbplugin.ui.components.connection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.intellij.database.dataSource.LocalDataSource
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.ui.components.utilities.ActionLink
import com.mongodb.jbplugin.ui.components.utilities.Card
import com.mongodb.jbplugin.ui.components.utilities.CardCategory
import com.mongodb.jbplugin.ui.components.utilities.hooks.useTranslation
import com.mongodb.jbplugin.ui.components.utilities.hooks.useViewModelMutator
import com.mongodb.jbplugin.ui.components.utilities.hooks.useViewModelState
import com.mongodb.jbplugin.ui.viewModel.ConnectionState
import com.mongodb.jbplugin.ui.viewModel.ConnectionStateViewModel
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState.Connected
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState.Connecting
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState.Empty
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState.Failed
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.ComboBox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun ConnectionBootstrapCard() {
    val connectionState by useViewModelState(ConnectionStateViewModel::connectionState, ConnectionState.default())
    val connect by useViewModelMutator(ConnectionStateViewModel::selectDataSource)
    val requestDataSourceCreation by useViewModelMutator(ConnectionStateViewModel::requestDataSourceCreation)
    val requestEditDataSource by useViewModelMutator(ConnectionStateViewModel::requestEditDataSource)

    val connectionCallbacks = ConnectionCallbacks(
        onConnect = connect,
        onRequestCreateDataSource = requestDataSourceCreation,
        onRequestEditDataSource = requestEditDataSource
    )

    CompositionLocalProvider(LocalConnectionCallbacks provides connectionCallbacks) {
        _ConnectionBootstrapCard(connectionState)
    }
}

@Composable
internal fun _ConnectionBootstrapCard(
    connectionState: ConnectionState
) {
    when (connectionState.selectedConnectionState) {
        is Empty -> ConnectionCardWhenNotConnected(connectionState)
        is Failed -> ConnectionCardWhenConnectionFailed(connectionState)
        is Connected, is Connecting -> LightweightConnectionSection(connectionState)
    }
}

@Composable
internal fun ConnectionCardWhenNotConnected(connectionState: ConnectionState) {
    val callbacks = useConnectionCallbacks()

    Card(CardCategory.LOGO, useTranslation("side-panel.connection.ConnectionBootstrapCard.title")) {
        Column {
            Box { Text(useTranslation("side-panel.connection.ConnectionBootstrapCard.subtitle")) }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                BulletItem(useTranslation("side-panel.connection.ConnectionBootstrapCard.1st-bullet-point"))
                BulletItem(useTranslation("side-panel.connection.ConnectionBootstrapCard.2st-bullet-point"))
                BulletItem(useTranslation("side-panel.connection.ConnectionBootstrapCard.3st-bullet-point"))
            }
            Column(modifier = Modifier.padding(top = 8.dp)) {
                if (connectionState.connections.isEmpty()) {
                    DefaultButton(onClick = { callbacks.onRequestCreateDataSource() }) {
                        Text(useTranslation("side-panel.connection.ConnectionBootstrapCard.add-datasource"))
                    }
                } else {
                    ConnectionComboBox(connectionState.selectedConnectionState, connectionState.connections)
                }
            }
        }
    }
}

@Composable
internal fun ConnectionCardWhenConnectionFailed(connectionState: ConnectionState) {
    val errorInfo = connectionState.selectedConnectionState as Failed
    val scrollState = rememberScrollState()

    Card(CardCategory.ERROR, useTranslation("side-panel.connection.ConnectionBootstrapCard.title-when-failure")) {
        Column {
            Box { Text(useTranslation("side-panel.connection.ConnectionBootstrapCard.subtitle-when-failure")) }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row {
                    Text(modifier = Modifier.fillMaxWidth(0.95f).heightIn(max = 120.dp).verticalScroll(scrollState), text = errorInfo.error)
                    Icon(
                        AllIconsKeys.General.Copy,
                        "Copy",
                        modifier = Modifier.testTag("CopyToClipboard").clickable {
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                StringSelection(errorInfo.error),
                                null
                            )
                        }
                    )
                }
            }
            Column(modifier = Modifier.padding(top = 8.dp)) {
                ConnectionComboBox(connectionState.selectedConnectionState, connectionState.connections)
            }
        }
    }
}

@Composable
internal fun LightweightConnectionSection(connectionState: ConnectionState) {
    val callbacks = useConnectionCallbacks()

    Column {
        if (connectionState.selectedConnectionState is Connecting) {
            Text(useTranslation("side-panel.connection.ConnectionBootstrapCard.connecting-to"))
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(useTranslation("side-panel.connection.ConnectionBootstrapCard.connected-to"))
                ActionLink(text = useTranslation("side-panel.connection.ConnectionBootstrapCard.edit-connection")) {
                    callbacks.onRequestEditDataSource()
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
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
        modifier = Modifier.testTag("ConnectionComboBox"),
        labelText = when (selectedConnectionState) {
            is Empty -> useTranslation("side-panel.connection.ConnectionBootstrapCard.combobox.choose-a-connection")
            is Connected -> selectedConnectionState.dataSource.name
            is Connecting -> selectedConnectionState.dataSource.name
            is Failed -> selectedConnectionState.dataSource.name
        },
        outline = if (selectedConnectionState is Failed) {
            Outline.Error
        } else {
            Outline.None
        }
    ) {
        Column {
            if (selectedConnectionState !is Empty) {
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
            Text(useTranslation("side-panel.connection.ConnectionBootstrapCard.combobox.disconnect"))
        }
    }
}

@Composable
internal fun ConnectionItem(dataSource: LocalDataSource) {
    val connectionCallback = useConnectionCallbacks()
    val status by useLocalDataSourceStatus(dataSource)

    Box(
        modifier = Modifier
            .testTag("ConnectionItem::${dataSource.uniqueId}")
            .clickable { connectionCallback.onConnect(dataSource) }
            .padding(4.dp)
            .fillMaxWidth(1f)
    ) {
        Row {
            Icon(
                when (status) {
                    LocalDataSourceStatus.Connected -> AllIconsKeys.General.GreenCheckmark
                    LocalDataSourceStatus.Disconnected -> AllIconsKeys.Providers.MongoDB
                },
                "",
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(dataSource.name)
        }
    }
}

internal data class ConnectionCallbacks(
    val onConnect: (LocalDataSource?) -> Unit = {},
    val onRequestCreateDataSource: () -> Unit = {},
    val onRequestEditDataSource: () -> Unit = {}
)

internal val LocalConnectionCallbacks = compositionLocalOf { ConnectionCallbacks() }

@Composable
internal fun useConnectionCallbacks(): ConnectionCallbacks {
    return LocalConnectionCallbacks.current
}

internal enum class LocalDataSourceStatus {
    Disconnected,
    Connected
}

@Composable
internal fun useLocalDataSourceStatus(localDataSource: LocalDataSource): State<LocalDataSourceStatus> {
    val isConnected = localDataSource.isConnected()
    return derivedStateOf {
        if (isConnected) {
            LocalDataSourceStatus.Connected
        } else {
            LocalDataSourceStatus.Disconnected
        }
    }
}
