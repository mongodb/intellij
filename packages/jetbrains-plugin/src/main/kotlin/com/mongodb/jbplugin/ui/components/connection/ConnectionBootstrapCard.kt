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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.intellij.database.dataSource.LocalDataSource
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.ui.components.utilities.ActionLink
import com.mongodb.jbplugin.ui.components.utilities.Card
import com.mongodb.jbplugin.ui.components.utilities.CardCategory
import com.mongodb.jbplugin.ui.components.utilities.ControlledComboBox
import com.mongodb.jbplugin.ui.components.utilities.hooks.useTranslation
import com.mongodb.jbplugin.ui.components.utilities.hooks.useViewModel
import com.mongodb.jbplugin.ui.components.utilities.hooks.useViewModelMutator
import com.mongodb.jbplugin.ui.components.utilities.hooks.useViewModelState
import com.mongodb.jbplugin.ui.viewModel.ConnectionState
import com.mongodb.jbplugin.ui.viewModel.ConnectionStateViewModel
import com.mongodb.jbplugin.ui.viewModel.DatabaseState
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState.Connecting
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState.Failed
import com.mongodb.jbplugin.ui.viewModel.SidePanelEvents
import com.mongodb.jbplugin.ui.viewModel.SidePanelViewModel
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun ConnectionBootstrapCard() {
    val connectionState by useViewModelState(
        ConnectionStateViewModel::connectionState,
        ConnectionState.initial()
    )
    val databaseState by useViewModelState(
        ConnectionStateViewModel::databaseState,
        DatabaseState.initial()
    )

    val selectConnection by useViewModelMutator(ConnectionStateViewModel::selectConnection)
    val unselectSelectedConnection by useViewModelMutator(
        ConnectionStateViewModel::unselectSelectedConnection
    )
    val addNewConnection by useViewModelMutator(ConnectionStateViewModel::addNewConnection)
    val editSelectedConnection by useViewModelMutator(
        ConnectionStateViewModel::editSelectedConnection
    )

    val selectDatabase by useViewModelMutator(ConnectionStateViewModel::selectDatabase)
    val unselectSelectedDatabase by useViewModelMutator(ConnectionStateViewModel::unselectSelectedDatabase)

    val connectionCallbacks = ConnectionCallbacks(
        selectConnection = selectConnection,
        unselectSelectedConnection = unselectSelectedConnection,
        addNewConnection = addNewConnection,
        editSelectedConnection = editSelectedConnection,
    )

    val databaseCallbacks = DatabaseCallbacks(
        selectDatabase = selectDatabase,
        unselectSelectedDatabase = unselectSelectedDatabase,
    )

    val connectionComboBoxState = ConnectionComboBoxState.default()
    val viewModel by useViewModel<SidePanelViewModel>()
    LaunchedEffect(Unit) {
        viewModel.subscribeToSidePanelEvents { emittedEvent ->
            if (emittedEvent == SidePanelEvents.OpenConnectionComboBox) {
                connectionComboBoxState.setComboBoxExpanded(true)
                // Need to run it catching because there is slight chance that FocusRequester did not have
                // time to initialize in which case, it will throw an exception. Exception itself does
                // not harm the UI but its worth catching so it does not bubble up to the user.
                runCatching {
                    connectionComboBoxState.focusRequester.requestFocus()
                }
            }
        }
    }

    CompositionLocalProvider(LocalConnectionCallbacks provides connectionCallbacks) {
        CompositionLocalProvider(LocalDatabaseCallbacks provides databaseCallbacks) {
            CompositionLocalProvider(
                LocalConnectionComboBoxState provides connectionComboBoxState
            ) {
                _ConnectionBootstrapCard(connectionState, databaseState)
            }
        }
    }
}

@Composable
internal fun _ConnectionBootstrapCard(
    connectionState: ConnectionState,
    databaseState: DatabaseState,
) {
    if (connectionState.selectedConnection == null) {
        ConnectionCardWhenNotConnected(connectionState)
    } else if (connectionState.selectedConnectionState is Failed) {
        ConnectionCardWhenConnectionFailed(connectionState)
    } else {
        LightweightConnectionSection(connectionState, databaseState)
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
                    DefaultButton(onClick = { callbacks.addNewConnection() }) {
                        Text(useTranslation("side-panel.connection.ConnectionBootstrapCard.add-datasource"))
                    }
                } else {
                    ConnectionComboBox(
                        connections = connectionState.connections,
                        selectedConnection = connectionState.selectedConnection,
                        selectedConnectionState = connectionState.selectedConnectionState,
                    )
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
                    Text(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .heightIn(max = 120.dp)
                            .verticalScroll(scrollState),
                        text = errorInfo.errorMessage
                    )
                    Icon(
                        AllIconsKeys.General.Copy,
                        "Copy",
                        modifier = Modifier.testTag("CopyToClipboard").clickable {
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                StringSelection(errorInfo.errorMessage),
                                null
                            )
                        }
                    )
                }
            }
            Column(modifier = Modifier.padding(top = 8.dp)) {
                ConnectionComboBox(
                    connections = connectionState.connections,
                    selectedConnection = connectionState.selectedConnection,
                    selectedConnectionState = connectionState.selectedConnectionState,
                )
            }
        }
    }
}

@Composable
internal fun LightweightConnectionSection(
    connectionState: ConnectionState,
    databaseState: DatabaseState,
) {
    val callbacks = useConnectionCallbacks()

    Column {
        if (connectionState.selectedConnectionState is Connecting) {
            Text(useTranslation("side-panel.connection.ConnectionBootstrapCard.connecting-to"))
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(useTranslation("side-panel.connection.ConnectionBootstrapCard.connected-to"))
                ActionLink(text = useTranslation("side-panel.connection.ConnectionBootstrapCard.edit-connection")) {
                    callbacks.editSelectedConnection()
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ConnectionComboBox(
                    connections = connectionState.connections,
                    selectedConnection = connectionState.selectedConnection,
                    selectedConnectionState = connectionState.selectedConnectionState,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                DatabaseComboBox(
                    databasesLoadingState = databaseState.databasesLoadingState,
                    selectedDatabase = databaseState.selectedDatabase,
                )
            }
        }
    }
}

@Composable
internal fun BulletItem(text: String) {
    return Box { Text("\u2022 $text") }
}

@Composable
internal fun ConnectionComboBox(
    connections: List<LocalDataSource>,
    selectedConnection: LocalDataSource?,
    selectedConnectionState: SelectedConnectionState,
) {
    val (comboBoxExpanded, setComboBoxExpanded, focusRequester) = useConnectionComboBoxState()

    ControlledComboBox(
        comboBoxExpanded = comboBoxExpanded,
        setComboBoxExpanded = setComboBoxExpanded,
        modifier = Modifier.testTag("ConnectionComboBox").focusRequester(focusRequester),
        labelText = selectedConnection?.name
            ?: useTranslation("side-panel.connection.ConnectionBootstrapCard.combobox.choose-a-connection"),
        outline = if (selectedConnectionState is Failed) {
            Outline.Error
        } else {
            Outline.None
        }
    ) {
        Column {
            if (selectedConnection != null) {
                DisconnectItem()
            }

            for (connection in connections) {
                ConnectionItem(connection)
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
            .clickable { connectionCallback.unselectSelectedConnection() }
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
internal fun ConnectionItem(connection: LocalDataSource) {
    val connectionCallback = useConnectionCallbacks()
    val status by useConnectionStatus(connection)

    Box(
        modifier = Modifier
            .testTag("ConnectionItem::${connection.uniqueId}")
            .clickable { connectionCallback.selectConnection(connection) }
            .padding(4.dp)
            .fillMaxWidth(1f)
    ) {
        Row {
            Icon(
                when (status) {
                    ConnectionStatus.Connected -> AllIconsKeys.General.GreenCheckmark
                    ConnectionStatus.Disconnected -> AllIconsKeys.Providers.MongoDB
                },
                "",
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(connection.name)
        }
    }
}

internal data class ConnectionCallbacks(
    val selectConnection: (LocalDataSource) -> Unit = {},
    val unselectSelectedConnection: () -> Unit = {},
    val addNewConnection: () -> Unit = {},
    val editSelectedConnection: () -> Unit = {}
)

internal val LocalConnectionCallbacks = compositionLocalOf { ConnectionCallbacks() }

@Composable
internal fun useConnectionCallbacks(): ConnectionCallbacks {
    return LocalConnectionCallbacks.current
}

internal enum class ConnectionStatus {
    Disconnected,
    Connected
}

@Composable
internal fun useConnectionStatus(connection: LocalDataSource): State<ConnectionStatus> {
    val isConnected = connection.isConnected()
    return derivedStateOf {
        if (isConnected) {
            ConnectionStatus.Connected
        } else {
            ConnectionStatus.Disconnected
        }
    }
}

internal data class ConnectionComboBoxState(
    val comboBoxExpanded: Boolean = false,
    val setComboBoxExpanded: (Boolean) -> Unit = {},
    val focusRequester: FocusRequester = FocusRequester(),
) {
    companion object {
        @Composable
        internal fun default(): ConnectionComboBoxState {
            var comboBoxExpanded by remember { mutableStateOf(false) }
            val setComboBoxExpanded = { expanded: Boolean -> comboBoxExpanded = expanded }
            val comboBoxFocusRequester = remember { FocusRequester() }

            return ConnectionComboBoxState(
                comboBoxExpanded = comboBoxExpanded,
                setComboBoxExpanded = setComboBoxExpanded,
                focusRequester = comboBoxFocusRequester
            )
        }
    }
}

/**
 * We purposely initialise this to null to reduce pain when testing these components otherwise
 * for each test that directly / indirectly triggers connection combobox state changes will have
 * to initialise the CompositionLocalProvider. We evade that by doing a small check in
 * useConnectionComboBoxState below - where we either return the current value if its present which
 * in test cases won't be (unless specified) or we return a newly built ConnectionComboBoxState.
 */
internal val LocalConnectionComboBoxState = compositionLocalOf<ConnectionComboBoxState?> { null }

@Composable
internal fun useConnectionComboBoxState(): ConnectionComboBoxState {
    return LocalConnectionComboBoxState.current ?: ConnectionComboBoxState.default()
}
