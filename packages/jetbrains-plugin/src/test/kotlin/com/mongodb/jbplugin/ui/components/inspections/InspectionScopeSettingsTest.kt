package com.mongodb.jbplugin.ui.components.inspections

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.mongodb.jbplugin.fixtures.setContentWithTheme
import com.mongodb.jbplugin.inspections.analysisScope.AnalysisScope
import com.mongodb.jbplugin.ui.viewModel.AnalysisStatus
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class InspectionScopeSettingsTest {
    @Test
    fun `should add a combobox with the list of available scopes`() = runComposeUiTest {
        setContentWithTheme {
            _InspectionScopeSettings(AnalysisScope.default(), AnalysisStatus.default())
        }

        onNodeWithTag("InspectionScopeComboBox")
            .assertExists()
            .assertTextEquals("Current File")
    }
}
