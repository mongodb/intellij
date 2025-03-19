package com.mongodb.jbplugin.ui.components.inspections

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.mongodb.jbplugin.fixtures.setContentWithTheme
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class InspectionAccordionTest {
    @Test
    fun `should start with all accordions closed`() = runComposeUiTest {
        setContentWithTheme {
            InspectionAccordion()
        }

        onNodeWithTag("InspectionAccordionSection::Body::Performance Warnings").assertDoesNotExist()
        onNodeWithTag("InspectionAccordionSection::Body::Correctness Warnings").assertDoesNotExist()
        onNodeWithTag("InspectionAccordionSection::Body::Environment Mismatches").assertDoesNotExist()
        onNodeWithTag("InspectionAccordionSection::Body::Other").assertDoesNotExist()
    }

    @Test
    fun `should open an accordion section when clicked`() = runComposeUiTest {
        setContentWithTheme {
            InspectionAccordion()
        }

        onNodeWithTag("InspectionAccordionSection::Opener::Performance Warnings").performClick()
        onNodeWithTag("InspectionAccordionSection::Body::Performance Warnings").assertExists()
        onNodeWithTag("InspectionAccordionSection::Body::Correctness Warnings").assertDoesNotExist()
        onNodeWithTag("InspectionAccordionSection::Body::Environment Mismatches").assertDoesNotExist()
        onNodeWithTag("InspectionAccordionSection::Body::Other").assertDoesNotExist()
    }

    @Test
    fun `should close other open sections when an accordion section is clicked`() = runComposeUiTest {
        setContentWithTheme {
            InspectionAccordion()
        }

        onNodeWithTag("InspectionAccordionSection::Opener::Performance Warnings").performClick()
        onNodeWithTag("InspectionAccordionSection::Opener::Correctness Warnings").performClick()
        onNodeWithTag("InspectionAccordionSection::Body::Performance Warnings").assertDoesNotExist()
        onNodeWithTag("InspectionAccordionSection::Body::Correctness Warnings").assertExists()
        onNodeWithTag("InspectionAccordionSection::Body::Environment Mismatches").assertDoesNotExist()
        onNodeWithTag("InspectionAccordionSection::Body::Other").assertDoesNotExist()
    }
}
