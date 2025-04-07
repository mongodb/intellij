package com.mongodb.jbplugin.ui.components.inspections

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.intellij.testFramework.assertInstanceOf
import com.mongodb.jbplugin.fixtures.setContentWithTheme
import com.mongodb.jbplugin.inspections.analysisScope.AnalysisScope
import com.mongodb.jbplugin.ui.viewModel.AnalysisStatus
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

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

    @Test
    fun `should show the progress of an analysis when collecting files`() = runComposeUiTest {
        setContentWithTheme {
            _InspectionScopeSettings(AnalysisScope.default(), AnalysisStatus.CollectingFiles)
        }

        onNodeWithText("Collecting files to analyze.").assertExists()
    }

    @Test
    fun `should show the progress of an analysis when analysing files`() = runComposeUiTest {
        setContentWithTheme {
            _InspectionScopeSettings(AnalysisScope.default(), AnalysisStatus.InProgress(setOf(""), setOf()))
        }

        onNodeWithText("Analyzed 0 of 1 files.").assertExists()
    }

    @Test
    fun `should show when the analysis is done`() = runComposeUiTest {
        setContentWithTheme {
            _InspectionScopeSettings(AnalysisScope.default(), AnalysisStatus.Done(10, 5.minutes))
        }

        onNodeWithText("Processed 10 files in 5 minutes.").assertExists()
    }

    @Test
    fun `should have a refresh button that when clicked triggers an analysis`() = runComposeUiTest {
        var didRefresh = false
        val callbacks = InspectionScopeSettingsCallbacks(
            onRefreshAnalysis = { didRefresh = true }
        )

        setContentWithTheme {
            CompositionLocalProvider(LocalInspectionScopeSettingsCallbacks provides callbacks) {
                _InspectionScopeSettings(
                    AnalysisScope.default(),
                    AnalysisStatus.default()
                )
            }
        }

        onNodeWithText("Refresh").assertExists().performClick()
        assertTrue(didRefresh)
    }

    @Test
    fun `detects when an scope has changed in the scope dropdown`() = runComposeUiTest {
        var selectedScope: AnalysisScope = AnalysisScope.CurrentFile()
        val callbacks = InspectionScopeSettingsCallbacks(
            onScopeChange = { selectedScope = it }
        )

        setContentWithTheme {
            CompositionLocalProvider(LocalInspectionScopeSettingsCallbacks provides callbacks) {
                _InspectionScopeSettings(
                    AnalysisScope.default(),
                    AnalysisStatus.default()
                )
            }
        }

        onNodeWithTag("InspectionScopeComboBox").assertExists().performClick()
        onNodeWithTag("InspectionScopeComboBox::Item::AllInsights").assertExists().performClick()

        assertInstanceOf<AnalysisScope.AllInsights>(selectedScope)
    }
}
