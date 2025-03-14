package com.mongodb.jbplugin.ui.components.connection

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.intellij.database.dataSource.LocalDataSource
import com.mongodb.jbplugin.fixtures.MongoDbServerUrl
import com.mongodb.jbplugin.fixtures.mockDataSource
import com.mongodb.jbplugin.fixtures.setContentWithTheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ConnectionBootstrapCardTest {
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
