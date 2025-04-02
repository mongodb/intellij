package com.mongodb.jbplugin.ui.components.connection

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.mongodb.jbplugin.fixtures.mockDataSource
import com.mongodb.jbplugin.fixtures.setContentWithTheme
import com.mongodb.jbplugin.ui.viewModel.DatabasesLoadingState
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class DatabaseComboBoxTest {
    @Test
    fun `should show the loading status text when the databases are loading`() = runComposeUiTest {
        setContentWithTheme {
            CompositionLocalProvider(
                LocalDatabaseCallbacks provides DatabaseCallbacks()
            ) {
                DatabaseComboBox(
                    databasesLoadingState = DatabasesLoadingState.Loading(mockDataSource()),
                    selectedDatabase = null,
                )
            }
        }

        onNodeWithTag("DatabaseComboBox").assertTextEquals("Loading databases...")
    }

    @Test
    fun `should show a list of databases when loaded`() = runComposeUiTest {
        setContentWithTheme {
            CompositionLocalProvider(
                LocalDatabaseCallbacks provides DatabaseCallbacks()
            ) {
                DatabaseComboBox(
                    databasesLoadingState = DatabasesLoadingState.Loaded(
                        mockDataSource(),
                        listOf("databaseA", "databaseB")
                    ),
                    selectedDatabase = null,
                )
            }
        }

        onNodeWithTag("DatabaseComboBox").assertTextEquals("Choose a database")
        onNodeWithTag("DatabaseComboBox").performClick()
        onNodeWithTag("DatabaseItem::databaseA").assertExists()
        onNodeWithTag("DatabaseItem::databaseB").assertExists()
    }

    @Test
    fun `should show the selected database as the database label`() = runComposeUiTest {
        setContentWithTheme {
            CompositionLocalProvider(
                LocalDatabaseCallbacks provides DatabaseCallbacks()
            ) {
                DatabaseComboBox(
                    databasesLoadingState = DatabasesLoadingState.Loaded(
                        mockDataSource(),
                        listOf("databaseA", "databaseB")
                    ),
                    selectedDatabase = "databaseA",
                )
            }
        }
        onNodeWithTag("DatabaseComboBox").assertTextEquals("databaseA")
    }

    @Test
    fun `should call selectDatabase when clicking a database item`() = runComposeUiTest {
        var databaseSelected = false
        setContentWithTheme {
            CompositionLocalProvider(
                LocalDatabaseCallbacks provides DatabaseCallbacks(
                    selectDatabase = { databaseSelected = true }
                )
            ) {
                DatabaseComboBox(
                    databasesLoadingState = DatabasesLoadingState.Loaded(
                        mockDataSource(),
                        listOf("databaseA", "databaseB")
                    ),
                    selectedDatabase = null,
                )
            }
        }
        onNodeWithTag("DatabaseComboBox").performClick()
        onNodeWithTag("DatabaseItem::databaseA").performClick()
        Assertions.assertTrue(databaseSelected, "Expected databaseA to be selected")
    }

    @Test
    fun `should call unselectSelectedDatabase when clicking the unselect item`() = runComposeUiTest {
        var unselectClicked = false
        setContentWithTheme {
            CompositionLocalProvider(
                LocalDatabaseCallbacks provides DatabaseCallbacks(
                    unselectSelectedDatabase = { unselectClicked = true }
                )
            ) {
                DatabaseComboBox(
                    databasesLoadingState = DatabasesLoadingState.Loaded(
                        mockDataSource(),
                        listOf("databaseA", "databaseB")
                    ),
                    selectedDatabase = "databaseA",
                )
            }
        }
        onNodeWithTag("DatabaseComboBox").performClick()
        onNodeWithTag("UnselectItem").performClick()
        Assertions.assertTrue(unselectClicked, "Expected unselectSelectedDatabase to be called")
    }
}
