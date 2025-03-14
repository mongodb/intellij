package com.mongodb.jbplugin.ui.components.utilities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

enum class CardCategory(internal val icon: IconKey, internal val contentDescription: String) {
    LOGO(AllIconsKeys.Providers.MongoDB, "MongoDB")
}

@Composable
fun Card(category: CardCategory, title: String, body: @Composable () -> Unit) {
    Box(modifier = Modifier.testTag("Card::$title").padding(vertical = 4.dp, horizontal = 8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
                .padding(12.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(Modifier.padding(bottom = 8.dp)) {
                Icon(category.icon, category.contentDescription)
                Text(title, modifier = Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold)
            }

            Row(Modifier.padding(horizontal = 24.dp)) {
                body()
            }
        }
    }
}
