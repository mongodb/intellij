package com.mongodb.jbplugin.ui.components.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.mongodb.jbplugin.ui.components.utilities.hooks.useViewModelState
import com.mongodb.jbplugin.ui.viewModel.ConnectionState
import com.mongodb.jbplugin.ui.viewModel.ConnectionStateViewModel
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState

@Composable
fun OnlyWhenConnected(body: @Composable () -> Unit) {
    val connectionState by useViewModelState(ConnectionStateViewModel::connectionState, ConnectionState.default())
    _OnlyWhenConnected(connectionState.selectedConnectionState, body)
}

@Composable
internal fun _OnlyWhenConnected(state: SelectedConnectionState, body: @Composable () -> Unit) {
    if (state is SelectedConnectionState.Connected) {
        body()
    }
}
