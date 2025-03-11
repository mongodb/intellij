package com.mongodb.jbplugin.ui.components

import androidx.compose.runtime.Composable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.jewel.ui.component.Text

class SidePanel : ToolWindowFactory {
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {
        toolWindow.addComposeTab(isLockable = true, isCloseable = false) {
            createSidePanelComponent()
        }
    }

    @Composable
    private fun createSidePanelComponent() {
        Text("This is just text.")
    }
}
