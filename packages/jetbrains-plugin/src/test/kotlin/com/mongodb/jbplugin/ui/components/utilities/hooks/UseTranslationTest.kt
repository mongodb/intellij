package com.mongodb.jbplugin.ui.components.utilities.hooks

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.mongodb.jbplugin.fixtures.setContentWithTheme
import com.mongodb.jbplugin.i18n.SidePanelMessages
import org.jetbrains.jewel.ui.component.Text
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class UseTranslationTest {
    @Test
    fun `should resolve translations from SidePanelMessages bundle`() = runComposeUiTest {
        setContentWithTheme {
            Text(
                modifier = Modifier.testTag("Text"),
                text = useTranslation("side-panel.connection.ConnectionBootstrapCard.subtitle")
            )
        }

        onNodeWithTag("Text").assertTextEquals(SidePanelMessages.message("side-panel.connection.ConnectionBootstrapCard.subtitle"))
    }
}
