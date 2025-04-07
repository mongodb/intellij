package com.mongodb.jbplugin.ui.components.utilities

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun Separator() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).border(BorderStroke(1.dp, Color.DarkGray)))
}
