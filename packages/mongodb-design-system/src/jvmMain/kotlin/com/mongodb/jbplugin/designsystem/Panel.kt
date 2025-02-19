package com.mongodb.jbplugin.designsystem

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
@Preview
fun Panel() {
    SwingBridgeTheme {
        Column {
            Text("Hello World!")
        }
    }
}
