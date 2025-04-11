package com.mongodb.jbplugin.ui.components.inspections

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.mongodb.jbplugin.fixtures.setContentWithTheme
import com.mongodb.jbplugin.inspections.analysisScope.AnalysisScope
import com.mongodb.jbplugin.linting.Inspection.NotUsingIndex
import com.mongodb.jbplugin.linting.InspectionCategory.CORRECTNESS
import com.mongodb.jbplugin.linting.InspectionCategory.PERFORMANCE
import com.mongodb.jbplugin.ui.components.utilities.MoreActionItem
import com.mongodb.jbplugin.ui.viewModel.AnalysisStatus
import com.mongodb.jbplugin.ui.viewModel.getToolShortName
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class InspectionAccordionTest {
    @Test
    fun `should start with all accordions closed`() = runComposeUiTest {
        setContentWithTheme {
            _InspectionAccordion(
                AnalysisScope.default(),
                AnalysisStatus.CollectingFiles,
                listOf(),
                emptySet(),
                null
            )
        }

        onNodeWithTag("InspectionAccordionSection::Body::PERFORMANCE").assertDoesNotExist()
    }

    @Test
    fun `should open an accordion section when clicked`() = runComposeUiTest {
        var opened = false

        val accordionCallbacks = InspectionAccordionCallbacks(
            onToggleInspectionCategory = { opened = true },
        )

        setContentWithTheme {
            CompositionLocalProvider(LocalInspectionAccordionCallbacks provides accordionCallbacks) {
                _InspectionAccordion(
                    AnalysisScope.default(),
                    AnalysisStatus.CollectingFiles,
                    emptyList(),
                    emptySet(),
                    null
                )
            }
        }

        onNodeWithTag("InspectionAccordionSection::Opener::PERFORMANCE").performClick()
        assertTrue(opened)
    }

    @Test
    fun `should show the section body when opened`() = runComposeUiTest {
        setContentWithTheme {
            _InspectionAccordion(
                AnalysisScope.default(),
                AnalysisStatus.CollectingFiles,
                emptyList(),
                emptySet(),
                PERFORMANCE
            )
        }

        onNodeWithTag("InspectionAccordionSection::Opener::PERFORMANCE").assertExists()
        onNodeWithTag("InspectionAccordionSection::Body::PERFORMANCE").assertExists()
    }

    @Test
    fun `should allow changing to recommended scopes when no insights and no analysis in flight for file scoped scopes`() = runComposeUiTest {
        setContentWithTheme {
            _InspectionAccordion(
                AnalysisScope.CurrentFile(),
                AnalysisStatus.NoAnalysis,
                emptyList(),
                emptySet(),
                null
            )
        }

        onNodeWithTag("NoInsightsNotification").assertExists()
    }

    @Test
    fun `should allow changing to recommended scopes when no insights and no analysis in flight for query scoped scopes`() = runComposeUiTest {
        setContentWithTheme {
            _InspectionAccordion(
                AnalysisScope.CurrentQuery(),
                AnalysisStatus.NoAnalysis,
                emptyList(),
                emptySet(),
                null
            )
        }

        onNodeWithTag("NoInsightsNotification").assertExists()
    }

    @Test
    fun `should allow changing to recommended scopes `() = runComposeUiTest {
        var newScope: AnalysisScope? = null

        val accordionCallbacks = InspectionAccordionCallbacks(
            onChangeScope = { newScope = it },
        )

        setContentWithTheme {
            CompositionLocalProvider(LocalInspectionAccordionCallbacks provides accordionCallbacks) {
                _InspectionAccordion(
                    AnalysisScope.default(),
                    AnalysisStatus.NoAnalysis,
                    emptyList(),
                    emptySet(),
                    null
                )
            }
        }

        onNodeWithText("Change to Recommended Insights").performClick()
        assertTrue(newScope is AnalysisScope.RecommendedInsights)
    }

    @Test
    fun `should allow changing to all files scopes `() = runComposeUiTest {
        var newScope: AnalysisScope? = null

        val accordionCallbacks = InspectionAccordionCallbacks(
            onChangeScope = { newScope = it },
        )

        setContentWithTheme {
            CompositionLocalProvider(LocalInspectionAccordionCallbacks provides accordionCallbacks) {
                _InspectionAccordion(
                    AnalysisScope.RecommendedInsights(),
                    AnalysisStatus.NoAnalysis,
                    emptyList(),
                    emptySet(),
                    null
                )
            }
        }

        onNodeWithText("Change to All Insights").performClick()
        assertTrue(newScope is AnalysisScope.AllInsights)
    }

    @Test
    fun `shows an action link for showing disabled inspection in each category if there are any`() = runComposeUiTest {
        setContentWithTheme {
            _InspectionAccordion(
                AnalysisScope.default(),
                AnalysisStatus.CollectingFiles,
                emptyList(),
                setOf(NotUsingIndex),
                PERFORMANCE
            )
        }

        onNodeWithTag("InspectionAccordionSection::Body::PERFORMANCE").assertExists()
        onNodeWithText("Show disabled inspections").assertExists()

        setContentWithTheme {
            _InspectionAccordion(
                AnalysisScope.default(),
                AnalysisStatus.CollectingFiles,
                emptyList(),
                setOf(NotUsingIndex),
                CORRECTNESS
            )
        }

        onNodeWithTag("InspectionAccordionSection::Body::CORRECTNESS").assertExists()
        onNodeWithText("Show disabled inspections").assertDoesNotExist()
    }

    @Test
    fun `shows the disabled inspections, after clicking Show disabled inspection and hides it after clicking Hide disabled inspection`() = runComposeUiTest {
        setContentWithTheme {
            _InspectionAccordion(
                AnalysisScope.default(),
                AnalysisStatus.CollectingFiles,
                emptyList(),
                setOf(NotUsingIndex),
                PERFORMANCE
            )
        }

        onNodeWithTag("InspectionAccordionSection::Body::PERFORMANCE").assertExists()

        onNodeWithText("Show disabled inspections").performClick()
        onNodeWithText("Hide disabled inspections").assertExists()
        onNodeWithTag("DisabledInspectionCard::${NotUsingIndex.getToolShortName()}").assertExists()

        onNodeWithText("Hide disabled inspections").performClick()
        onNodeWithText("Show disabled inspections").assertExists()
        onNodeWithTag("DisabledInspectionCard::${NotUsingIndex.getToolShortName()}").assertDoesNotExist()
    }

    @Test
    fun `should render InsightCard structure with provided MoreActionItems`() = runComposeUiTest {
        var enableInspectionCalled = false
        var disableInspectionCalled = false
        setContentWithTheme {
            InsightCardStructure(
                title = "Test Title",
                testTag = "TestCard",
                moreActionItems = listOf(
                    MoreActionItem("Enable inspection") {
                        enableInspectionCalled = true
                    },
                    MoreActionItem("Disable inspection") {
                        disableInspectionCalled = true
                    },
                )
            )
        }

        onNodeWithTag("TestCard::MoreActionsButton").performClick()
        onNodeWithText("Enable inspection").performClick()
        assertTrue(enableInspectionCalled)

        onNodeWithTag("TestCard::MoreActionsButton").performClick()
        onNodeWithText("Disable inspection").performClick()
        assertTrue(disableInspectionCalled)
    }
}
