package com.mongodb.jbplugin.ui.components.inspections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mongodb.jbplugin.ui.components.utilities.ActionLink
import org.jetbrains.jewel.ui.component.ComboBox
import org.jetbrains.jewel.ui.component.Text

@Composable
fun InspectionScopeSettings() {
    Column(modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Insights")
            ActionLink(modifier = Modifier.testTag("InspectionScopeSettings::Refresh").alpha(0f), "Refresh") {
            }
        }

        InspectionScopeComboBox()
    }
}

@Composable
private fun InspectionScopeComboBox(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Scope to: ")
        ComboBox(modifier = Modifier.fillMaxWidth(), labelText = "Current File") {}
    }
}
