package com.mongodb.jbplugin.ui.components.inspections

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.intellij.testFramework.assertInstanceOf
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.setContentWithTheme
import com.mongodb.jbplugin.inspections.analysisScope.AnalysisScope
import com.mongodb.jbplugin.ui.viewModel.AnalysisStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@IntegrationTest
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
            _InspectionScopeSettings(AnalysisScope.default(), AnalysisStatus.Done(10, 250.milliseconds))
        }

        onNodeWithText("Processed 10 files in 250 ms.").assertExists()
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
