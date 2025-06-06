package com.mongodb.jbplugin.ui.components.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mongodb.jbplugin.ui.components.utilities.hooks.useTranslation
import com.mongodb.jbplugin.ui.components.utilities.hooks.useViewModelState
import com.mongodb.jbplugin.ui.viewModel.ConnectionState
import com.mongodb.jbplugin.ui.viewModel.ConnectionStateViewModel
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.CircularProgressStyle
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun OnlyWhenConnected(body: @Composable () -> Unit) {
    val connectionState by useViewModelState(
        ConnectionStateViewModel::connectionState,
        ConnectionState.initial()
    )
    _OnlyWhenConnected(connectionState.selectedConnectionState, body)
}

@Composable
internal fun _OnlyWhenConnected(state: SelectedConnectionState, body: @Composable () -> Unit) {
    if (state is SelectedConnectionState.Connected) {
        body()
    } else if (state is SelectedConnectionState.Connecting) {
        Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceAround) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Column {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(start = 8.dp).height(32.dp).width(32.dp).align(
                            Alignment.CenterHorizontally
                        ),
                        style = CircularProgressStyle(250.milliseconds, Color.Gray)
                    )
                    Text(
                        useTranslation(
                            "side-panel.connection.ConnectionBootstrapCard.connecting-to-detailed",
                            state.connection.name
                        )
                    )
                }
            }
        }
    }
}
