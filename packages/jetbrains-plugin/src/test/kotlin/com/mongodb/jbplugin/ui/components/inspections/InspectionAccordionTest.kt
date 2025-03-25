package com.mongodb.jbplugin.ui.components.inspections

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.mongodb.jbplugin.fixtures.setContentWithTheme
import com.mongodb.jbplugin.inspections.analysisScope.AnalysisScope
import com.mongodb.jbplugin.linting.InspectionCategory.PERFORMANCE
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class InspectionAccordionTest {
    @Test
    fun `should start with all accordions closed`() = runComposeUiTest {
        setContentWithTheme {
            _InspectionAccordion(
                AnalysisScope.default(),
                emptyList(),
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
                    emptyList(),
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
                emptyList(),
                PERFORMANCE
            )
        }

        onNodeWithTag("InspectionAccordionSection::Opener::PERFORMANCE").assertExists()
        onNodeWithTag("InspectionAccordionSection::Body::PERFORMANCE").assertExists()
    }
}
