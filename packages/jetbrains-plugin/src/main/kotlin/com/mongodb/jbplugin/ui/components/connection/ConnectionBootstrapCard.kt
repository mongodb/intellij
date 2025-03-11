package com.mongodb.jbplugin.ui.components.connection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mongodb.jbplugin.ui.components.utilities.Card
import com.mongodb.jbplugin.ui.components.utilities.CardCategory
import com.mongodb.jbplugin.ui.components.utilities.hooks.useTranslation
import org.jetbrains.jewel.ui.component.Text

@Composable
fun ConnectionBootstrapCard() {
    Card(CardCategory.LOGO, useTranslation("side-panel.connection.ConnectionBootstrapCard.title")) {
        Column {
            Box { Text(useTranslation("side-panel.connection.ConnectionBootstrapCard.subtitle")) }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                BulletItem(useTranslation("side-panel.connection.ConnectionBootstrapCard.1st-bullet-point"))
                BulletItem(useTranslation("side-panel.connection.ConnectionBootstrapCard.2st-bullet-point"))
                BulletItem(useTranslation("side-panel.connection.ConnectionBootstrapCard.3st-bullet-point"))
            }
        }
    }
}

@Composable
private fun BulletItem(text: String) {
    return Box { Text("\u2022 $text") }
}
