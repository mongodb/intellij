package com.mongodb.jbplugin.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey

@Composable
fun Card(icon: IconKey, title: String, body: @Composable () -> Unit) {
    Box(
        modifier = Modifier.padding(12.dp).background(Color.DarkGray).fillMaxWidth()
            .border(BorderStroke(1.dp, Color.DarkGray), RoundedCornerShape(12.dp))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Icon(icon, title)
                Text(style = JewelTheme.defaultTextStyle.merge(fontWeight = FontWeight.Bold), text = title)
            }

            Box(Modifier.padding(top = 38.dp)) {
                body()
            }
        }
    }
}
