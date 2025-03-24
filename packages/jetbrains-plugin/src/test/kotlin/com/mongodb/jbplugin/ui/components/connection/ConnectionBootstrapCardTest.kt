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
                    onRequestCreateDataSource = { requestedDataSourceCreation = true }
                )
            ) {
                _ConnectionBootstrapCard(
                    ConnectionState(emptyList(), SelectedConnectionState.Empty)
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
                ConnectionState(listOf(dataSource), SelectedConnectionState.Empty)
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
                ConnectionState(listOf(dataSource), SelectedConnectionState.Failed(dataSource, "Error message."))
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
                ConnectionState(listOf(dataSource), SelectedConnectionState.Connecting(dataSource))
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
                ConnectionState(listOf(dataSource), SelectedConnectionState.Connected(dataSource))
            )
        }

        onNodeWithText("Connected to").assertExists()
        onNodeWithTag("ConnectionComboBox").performClick()
        onNodeWithTag("ConnectionItem::${dataSource.uniqueId}").assertExists()
    }

    @Test
    fun `can edit a data source when connected`() = runComposeUiTest {
        val dataSource = mockDataSource()
        var askedForEdit = false

        setContentWithTheme {
            CompositionLocalProvider(
                LocalConnectionCallbacks provides ConnectionCallbacks(
                    onRequestEditDataSource = { askedForEdit = true }
                )
            ) {
                _ConnectionBootstrapCard(
                    ConnectionState(listOf(dataSource), SelectedConnectionState.Connected(dataSource))
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
    fun `ConnectionItem should trigger onSelectItem when clicked`() = runComposeUiTest {
        val dataSource = mockDataSource(MongoDbServerUrl("mongodb://localhost:27017"))
        var clickedDataSource: LocalDataSource? = null

        setContentWithTheme {
            CompositionLocalProvider(
                LocalConnectionCallbacks provides ConnectionCallbacks(
                    onConnect = { clickedDataSource = it }
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
    fun `DisconnectItem should trigger onSelectItem with null when clicked`() = runComposeUiTest {
        var clickedWithNull = false

        setContentWithTheme {
            CompositionLocalProvider(
                LocalConnectionCallbacks provides ConnectionCallbacks(
                    onConnect = { clickedWithNull = it == null }
                )
            ) {
                DisconnectItem()
            }
        }

        onNodeWithTag("DisconnectItem").performClick()
        assertTrue(clickedWithNull)
    }
}
