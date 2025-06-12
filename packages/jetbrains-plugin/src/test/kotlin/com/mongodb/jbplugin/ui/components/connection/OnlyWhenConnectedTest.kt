package com.mongodb.jbplugin.ui.components.connection

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.mockDataSource
import com.mongodb.jbplugin.fixtures.setContentWithTheme
import com.mongodb.jbplugin.ui.viewModel.SelectedConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.jewel.ui.component.Text
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@IntegrationTest(true)
class OnlyWhenConnectedTest {
    @Test
    fun `body is hidden when no connection`() = runComposeUiTest {
        setContentWithTheme {
            _OnlyWhenConnected(SelectedConnectionState.Initial, mock()) {
                Text("Connected!")
            }
        }

        onNodeWithText("Connected!").assertDoesNotExist()
    }

    @Test
    fun `body is shown when connected`() = runComposeUiTest {
        setContentWithTheme {
            _OnlyWhenConnected(SelectedConnectionState.Connected(mockDataSource()), mock()) {
                Text("Connected!")
            }
        }

        onNodeWithText("Connected!").assertExists()
    }
}
