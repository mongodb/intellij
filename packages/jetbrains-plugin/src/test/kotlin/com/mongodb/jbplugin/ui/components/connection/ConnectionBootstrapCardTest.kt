package com.mongodb.jbplugin.ui.components.connection

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.intellij.database.dataSource.LocalDataSource
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.MongoDbServerUrl
import com.mongodb.jbplugin.fixtures.mockDataSource
import com.mongodb.jbplugin.fixtures.readClipboard
import com.mongodb.jbplugin.fixtures.setContentWithTheme
import com.mongodb.jbplugin.ui.viewModel.ConnectionState
import com.mongodb.jbplugin.ui.viewModel.DatabaseState
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@IntegrationTest
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
                    DatabaseState.initial(),
                    false
                )
            }
        }

        onNodeWithText("Add a MongoDB Data Source")
            .assertExists()
            .performClick()

        assertTrue(requestedDataSourceCreation)
    }

    @Test
    fun `shows an option to create a new data source in the connection list`() = runComposeUiTest {
        var requestedDataSourceCreation = false
        val dataSource = mockDataSource()

        setContentWithTheme {
            CompositionLocalProvider(
                LocalConnectionCallbacks provides ConnectionCallbacks(
                    addNewConnection = { requestedDataSourceCreation = true },
                )
            ) {
                _ConnectionBootstrapCard(
                    ConnectionState(
                        listOf(dataSource),
                        dataSource,
                        SelectedConnectionState.Failed(dataSource, "Error message.")
                    ),
                    DatabaseState.initial(),
                    false
                )
            }
        }

        onNodeWithTag("ConnectionComboBox").performClick()
        onNodeWithTag("NewConnectionItem").performClick()

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
                DatabaseState.initial(),
                false
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
                DatabaseState.initial(),
                false
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
                DatabaseState.initial(),
                false
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
                DatabaseState.initial(),
                false
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
                DatabaseState.initial(),
                true
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
                    DatabaseState.initial(),
                    false
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
    fun `connection combobox should respond to external triggers in state`() = runComposeUiTest {
        val dataSource = mockDataSource()
        lateinit var expandComboBox: () -> Unit
        setContentWithTheme {
            val connectionComboBoxState = ConnectionComboBoxState.default()
            expandComboBox = {
                connectionComboBoxState.setComboBoxExpanded(true)
            }
            CompositionLocalProvider(
                LocalConnectionComboBoxState provides connectionComboBoxState
            ) {
                _ConnectionBootstrapCard(
                    ConnectionState(
                        listOf(dataSource),
                        dataSource,
                        SelectedConnectionState.Connected(dataSource)
                    ),
                    DatabaseState.initial(),
                    false
                )
            }
        }

        onNodeWithTag("ConnectionItem::${dataSource.uniqueId}").assertDoesNotExist()
        expandComboBox()
        onNodeWithTag("ConnectionItem::${dataSource.uniqueId}").assertExists()
    }
}
