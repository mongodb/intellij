package com.mongodb.jbplugin.ui.components.utilities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.setContentWithTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.jewel.ui.component.Text
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
@IntegrationTest
class ControlledComboBoxTest {

    @Test
    fun `should be able to control the popup via normal clicks`() = runComposeUiTest {
        setContentWithTheme {
            ControlledComboBox(
                labelText = "Select a description",
            ) {
                Column {
                    Row { Text("Description A") }
                }
            }
        }

        onNodeWithText("Select a description").assertExists()
        onNodeWithText("Select a description").performClick()

        onNodeWithText("Description A").assertExists()
    }

    @Test
    fun `should be able to select items from popup`() = runComposeUiTest {
        setContentWithTheme {
            var selectedItem by remember { mutableStateOf<String?>(null) }
            ControlledComboBox(
                modifier = Modifier.testTag("ComboBox"),
                labelText = if (selectedItem == null) {
                    "Select a description"
                } else {
                    selectedItem!!
                },
            ) {
                Column {
                    Row(
                        modifier = Modifier.clickable {
                            selectedItem = "Description A"
                        }
                    ) {
                        Text("Description A")
                    }
                }
            }
        }

        onNodeWithTag("ComboBox").assertTextEquals("Select a description")
        onNodeWithTag("ComboBox").performClick()

        onNodeWithText("Description A").performClick()
        onNodeWithTag("ComboBox").assertTextEquals("Description A")
    }

    @Test
    fun `popup menu can be controlled via lifted state`() = runComposeUiTest {
        var setComboBoxExpanded: (Boolean) -> Unit = {}
        setContentWithTheme {
            var comboBoxExpanded by remember { mutableStateOf(false) }
            setComboBoxExpanded = { comboBoxExpanded = it }
            ControlledComboBox(
                labelText = "Select a description",
                comboBoxExpanded = comboBoxExpanded,
                setComboBoxExpanded = setComboBoxExpanded,
            ) {
                Column {
                    Row { Text("Description A") }
                }
            }
        }

        onNodeWithText("Description A").assertDoesNotExist()
        setComboBoxExpanded(true)
        onNodeWithText("Description A").assertExists()
    }
}
