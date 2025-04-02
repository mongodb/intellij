package com.mongodb.jbplugin.ui.components.connection

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.intellij.database.dataSource.LocalDataSource
import com.mongodb.jbplugin.fixtures.MongoDbServerUrl
import com.mongodb.jbplugin.fixtures.mockDataSource
import com.mongodb.jbplugin.fixtures.readClipboard
import com.mongodb.jbplugin.fixtures.setContentWithTheme
import com.mongodb.jbplugin.ui.viewModel.ConnectionState
import com.mongodb.jbplugin.ui.viewModel.DatabaseState
import com.mongodb.jbplugin.ui.viewModel.DatabasesLoadingState
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ConnectionBootstrapCardTest {
    @Test
    fun `shows a button to create a new data source when disconnected`() = runComposeUiTest {
        var requestedDataSourceCreation = false

        setContentWithTheme {
            CompositionLocalProvider(
                LocalConnectionCallbacks provides ConnectionCallbacks(
                    addNewConnection = { requestedDataSourceCreation = true },
                )
            ) {
                _ConnectionBootstrapCard(
                    ConnectionState(
                        emptyList(),
                        null,
                        SelectedConnectionState.Initial
                    ),
                    DatabaseState.initial()
                )
            }
        }

        onNodeWithText("Add a MongoDB Data Source")
            .assertExists()
            .performClick()

        assertTrue(requestedDataSourceCreation)
    }

    @Test
    fun `shows the list of connections when disconnected if there is any`() = runComposeUiTest {
        val dataSource = mockDataSource()

        setContentWithTheme {
            _ConnectionBootstrapCard(
                ConnectionState(
                    listOf(dataSource),
                    null,
                    SelectedConnectionState.Initial
                ),
                DatabaseState.initial()
            )
        }

        onNodeWithText("Add a MongoDB Data Source").assertDoesNotExist()
        onNodeWithTag("Card::Connect to MongoDB").assertExists()
        onNodeWithTag("ConnectionComboBox").performClick()
        onNodeWithTag("ConnectionItem::${dataSource.uniqueId}").assertExists()
    }

    @Test
    fun `shows the list of connections and the error message when failed`() = runComposeUiTest {
        val dataSource = mockDataSource()

        setContentWithTheme {
            _ConnectionBootstrapCard(
                ConnectionState(
                    listOf(dataSource),
                    dataSource,
                    SelectedConnectionState.Failed(dataSource, "Error message.")
                ),
                DatabaseState.initial()
            )
        }

        onNodeWithTag("Card::MongoDB connection unavailable").assertExists()
        onNodeWithText("Error message.").assertExists()
        onNodeWithTag("ConnectionComboBox").performClick()
        onNodeWithTag("ConnectionItem::${dataSource.uniqueId}").assertExists()
        onNodeWithTag("CopyToClipboard").performClick()
        assertEquals("Error message.", readClipboard())
    }

    @Test
    fun `shows the connecting spinner when connecting`() = runComposeUiTest {
        val dataSource = mockDataSource()

        setContentWithTheme {
            _ConnectionBootstrapCard(
                ConnectionState(
                    listOf(dataSource),
                    dataSource,
                    SelectedConnectionState.Connecting(dataSource)
                ),
                DatabaseState.initial()
            )
        }

        onNodeWithText("Connecting to").assertExists()
        onNodeWithTag("ConnectionComboBox").performClick()
        onNodeWithTag("ConnectionItem::${dataSource.uniqueId}").assertExists()
    }

    @Test
    fun `shows the connected to text when connected`() = runComposeUiTest {
        val dataSource = mockDataSource()

        setContentWithTheme {
            _ConnectionBootstrapCard(
                ConnectionState(
                    listOf(dataSource),
                    dataSource,
                    SelectedConnectionState.Connected(dataSource)
                ),
                DatabaseState.initial()
            )
        }

        onNodeWithText("Connected to").assertExists()
        onNodeWithTag("ConnectionComboBox").performClick()
        onNodeWithTag("ConnectionItem::${dataSource.uniqueId}").assertExists()
    }

    @Test
    fun `shows the database combobox when connected`() = runComposeUiTest {
        val dataSource = mockDataSource()

        setContentWithTheme {
            _ConnectionBootstrapCard(
                ConnectionState(
                    listOf(dataSource),
                    dataSource,
                    SelectedConnectionState.Connected(dataSource)
                ),
                DatabaseState.initial()
            )
        }

        onNodeWithText("Connected to").assertExists()
        onNodeWithTag("DatabaseComboBox").assertExists()
    }

    @Test
    fun `can edit a data source when connected`() = runComposeUiTest {
        val dataSource = mockDataSource()
        var askedForEdit = false

        setContentWithTheme {
            CompositionLocalProvider(
                LocalConnectionCallbacks provides ConnectionCallbacks(
                    editSelectedConnection = { askedForEdit = true }
                )
            ) {
                _ConnectionBootstrapCard(
                    ConnectionState(
                        listOf(dataSource),
                        dataSource,
                        SelectedConnectionState.Connected(dataSource)
                    ),
                    DatabaseState.initial()
                )
            }
        }

        onNodeWithText("Edit connection")
            .assertExists()
            .performClick()

        assertTrue(askedForEdit)
    }

    @Test
    fun `ConnectionItem should show the data source name`() = runComposeUiTest {
        val dataSource = mockDataSource(MongoDbServerUrl("mongodb://localhost:27017"))

        setContentWithTheme {
            ConnectionItem(dataSource)
        }

        onNodeWithTag("ConnectionItem::${dataSource.uniqueId}").assertTextEquals(dataSource.name)
    }

    @Test
    fun `ConnectionItem should trigger selectConnection when clicked`() = runComposeUiTest {
        val dataSource = mockDataSource(MongoDbServerUrl("mongodb://localhost:27017"))
        var clickedDataSource: LocalDataSource? = null

        setContentWithTheme {
            CompositionLocalProvider(
                LocalConnectionCallbacks provides ConnectionCallbacks(
                    selectConnection = { clickedDataSource = it }
                )
            ) {
                ConnectionItem(dataSource)
            }
        }

        onNodeWithTag("ConnectionItem::${dataSource.uniqueId}").performClick()
        assertEquals(dataSource, clickedDataSource)
    }

    @Test
    fun `DisconnectItem should show the Disconnect text`() = runComposeUiTest {
        setContentWithTheme {
            DisconnectItem()
        }

        onNodeWithTag("DisconnectItem").assertTextEquals("Disconnect")
    }

    @Test
    fun `DisconnectItem should trigger unselectSelectedConnection when clicked`() = runComposeUiTest {
        var clicked = false

        setContentWithTheme {
            CompositionLocalProvider(
                LocalConnectionCallbacks provides ConnectionCallbacks(
                    unselectSelectedConnection = { clicked = true }
                )
            ) {
                DisconnectItem()
            }
        }

        onNodeWithTag("DisconnectItem").performClick()
        assertTrue(clicked)
    }

    @Test
    fun `shows selected database when one is selected`() = runComposeUiTest {
        val dataSource = mockDataSource()

        setContentWithTheme {
            _ConnectionBootstrapCard(
                ConnectionState(
                    listOf(dataSource),
                    dataSource,
                    SelectedConnectionState.Connected(dataSource)
                ),
                DatabaseState(
                    databasesLoadingState = DatabasesLoadingState.Loaded(dataSource, listOf("testDB")),
                    selectedDatabase = "testDB"
                )
            )
        }

        onNodeWithTag("DatabaseComboBox").assertTextEquals("testDB")
    }

    @Test
    fun `shows loading databases state when fetching databases`() = runComposeUiTest {
        val dataSource = mockDataSource()

        setContentWithTheme {
            _ConnectionBootstrapCard(
                ConnectionState(
                    listOf(dataSource),
                    dataSource,
                    SelectedConnectionState.Connected(dataSource)
                ),
                DatabaseState(
                    databasesLoadingState = DatabasesLoadingState.Loading(dataSource),
                    selectedDatabase = null
                )
            )
        }

        onNodeWithTag("DatabaseComboBox").assertTextEquals("Loading databases...")
    }

    @Test
    fun `handles database selection through UI`() = runComposeUiTest {
        val dataSource = mockDataSource()
        var selectedDatabase: String? = null

        setContentWithTheme {
            CompositionLocalProvider(
                LocalDatabaseCallbacks provides DatabaseCallbacks(
                    selectDatabase = { selectedDatabase = it }
                )
            ) {
                _ConnectionBootstrapCard(
                    ConnectionState(
                        listOf(dataSource),
                        dataSource,
                        SelectedConnectionState.Connected(dataSource)
                    ),
                    DatabaseState(
                        databasesLoadingState = DatabasesLoadingState.Loaded(dataSource, listOf("testDB")),
                        selectedDatabase = null
                    )
                )
            }
        }

        onNodeWithTag("DatabaseComboBox").performClick()
        onNodeWithTag("DatabaseItem::testDB").performClick()
        assertEquals("testDB", selectedDatabase)
    }

    @Test
    fun `handles database unselection through UI`() = runComposeUiTest {
        val dataSource = mockDataSource()
        var unselectClicked = false

        setContentWithTheme {
            CompositionLocalProvider(
                LocalDatabaseCallbacks provides DatabaseCallbacks(
                    unselectSelectedDatabase = { unselectClicked = true }
                )
            ) {
                _ConnectionBootstrapCard(
                    ConnectionState(
                        listOf(dataSource),
                        dataSource,
                        SelectedConnectionState.Connected(dataSource)
                    ),
                    DatabaseState(
                        databasesLoadingState = DatabasesLoadingState.Loaded(dataSource, listOf("testDB")),
                        selectedDatabase = "testDB"
                    )
                )
            }
        }

        onNodeWithTag("DatabaseComboBox").performClick()
        onNodeWithTag("UnselectItem").performClick()
        assertTrue(unselectClicked)
    }
}
