package com.mongodb.jbplugin.ui.components.inspections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mongodb.jbplugin.inspections.analysisScope.AnalysisScope
import com.mongodb.jbplugin.ui.components.utilities.ActionLink
import com.mongodb.jbplugin.ui.components.utilities.hooks.useTranslation
import com.mongodb.jbplugin.ui.components.utilities.hooks.useViewModelState
import com.mongodb.jbplugin.ui.viewModel.AnalysisScopeViewModel
import org.jetbrains.jewel.ui.component.ComboBox
import org.jetbrains.jewel.ui.component.Text

@Composable
fun InspectionScopeSettings() {
    val currentScope by useViewModelState(AnalysisScopeViewModel::analysisScope, AnalysisScope.default())
    _InspectionScopeSettings(currentScope)
}

@Composable
fun _InspectionScopeSettings(currentScope: AnalysisScope) {
    Column(modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = useTranslation("side-panel.scope.insights") + " ")
            ActionLink(
                modifier = Modifier.testTag("InspectionScopeSettings::Refresh").alpha(0f),
                useTranslation("side-panel.scope.refresh")
            ) {
            }
        }

        InspectionScopeComboBox(currentScope)
    }
}

@Composable
private fun InspectionScopeComboBox(currentScope: AnalysisScope, modifier: Modifier = Modifier,) {
    Row(modifier = modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = useTranslation("side-panel.scope.scope-to"))
        ComboBox(
            modifier = Modifier.fillMaxWidth().testTag("InspectionScopeComboBox"),
            labelText = useTranslation(currentScope.displayName)
        ) {}
    }
}
