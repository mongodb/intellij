package com.mongodb.jbplugin.ui.components.connection

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.asDisposable
import com.mongodb.jbplugin.ui.components.utilities.hooks.useTranslation
import com.mongodb.jbplugin.ui.components.utilities.hooks.useViewModelState
import com.mongodb.jbplugin.ui.viewModel.ConnectionState
import com.mongodb.jbplugin.ui.viewModel.ConnectionStateViewModel
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState
import kotlinx.coroutines.CoroutineScope
import java.awt.BorderLayout

@Composable
fun OnlyWhenConnected(body: @Composable () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    coroutineScope.coroutineContext
    val connectionState by useViewModelState(
        ConnectionStateViewModel::connectionState,
        ConnectionState.initial()
    )
    _OnlyWhenConnected(connectionState.selectedConnectionState, coroutineScope, body)
}

@Composable
internal fun _OnlyWhenConnected(state: SelectedConnectionState, coroutineScope: CoroutineScope, body: @Composable () -> Unit) {
    var loadingPanel: JBLoadingPanel? by remember { mutableStateOf(null) }
    if (state is SelectedConnectionState.Connected) {
        loadingPanel?.stopLoading()
        body()
    } else if (state is SelectedConnectionState.Connecting) {
        val connectingText = "${useTranslation(
            "side-panel.connection.ConnectionBootstrapCard.connecting-to-detailed",
            state.connection.name
        )}..."
        SwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = {
                JBLoadingPanel(
                    BorderLayout(),
                    coroutineScope.asDisposable()
                ).apply { isOpaque = false }.also { loadingPanel = it }
            },
            update = { panel ->
                panel.setLoadingText(connectingText)
                panel.startLoading()
            }
        )
    }
}
