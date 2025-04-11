package com.mongodb.jbplugin.ui.components.utilities

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.jewel.ui.component.Text

private val LinkColor = Color(0x6B, 0x9B, 0xFA)

@Composable
fun ActionLink(
    text: String,
    modifier: Modifier = Modifier,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    onClick: () -> Unit,
) {
    Text(
        text,
        overflow = overflow,
        maxLines = maxLines,
        color = LinkColor,
        modifier = modifier.clickable { onClick() }
    )
}
