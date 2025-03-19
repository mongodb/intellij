package com.mongodb.jbplugin.ui.components.utilities

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.ui.component.Text

private val LinkColor = Color(0x6B, 0x9B, 0xFA)

@Composable
fun ActionLink(modifier: Modifier = Modifier, text: String, onClick: () -> Unit) {
    Text(
        text,
        color = LinkColor,
        modifier = modifier.clickable { onClick() }
    )
}
