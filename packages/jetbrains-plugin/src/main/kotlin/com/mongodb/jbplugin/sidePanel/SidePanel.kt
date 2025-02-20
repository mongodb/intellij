package com.mongodb.jbplugin.sidePanel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.unit.dp
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.designsystem.Card
import com.mongodb.jbplugin.designsystem.Connection
import com.mongodb.jbplugin.designsystem.ConnectionComboBox
import com.mongodb.jbplugin.designsystem.ConnectionState.CONNECTED
import com.mongodb.jbplugin.designsystem.ConnectionState.IDLE
import com.mongodb.jbplugin.editor.models.getToolbarModel
import com.mongodb.jbplugin.settings.pluginSetting
import kotlinx.coroutines.flow.map
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.ui.icons.AllIconsKeys

class SidePanel : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project): Boolean {
        val isSidePanelActive by pluginSetting { ::useNewSidePanel }
        return isSidePanelActive
    }

    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {

        val activeConnection = project.getToolbarModel().toolbarState.map {
            it.selectedDataSource?.let {
                Connection(
                    it.uniqueId,
                    it.name,
                    if (it.isConnected()) {
                        CONNECTED
                    } else {
                        IDLE
                    }
                )
            }
        }

        val connections = project.getToolbarModel().toolbarState.map {
            it.dataSources.map {
                Connection(
                    it.uniqueId,
                    it.name,
                    if (it.isConnected()) {
                        CONNECTED
                    } else {
                        IDLE
                    }
                )
            }
        }

        val composePanel = ComposePanel()
        composePanel.setContent {
            SwingBridgeTheme {
                Box(modifier = Modifier.padding(8.dp)) {
                    Card(AllIconsKeys.General.Error, "MongoDB Connection Unavailable") {
                        ConnectionComboBox(activeConnection, connections) { connection ->
                            project.getToolbarModel().selectDataSource(
                                project.getToolbarModel().toolbarState.value.dataSources.first {
                                    it.uniqueId == connection.id
                                }
                            )
                        }
                    }
                }
            }
        }

        val manager = toolWindow.contentManager
        val content = manager.factory.createContent(
            composePanel,
            null,
            false
        )

        manager.addContent(content)
    }
}
