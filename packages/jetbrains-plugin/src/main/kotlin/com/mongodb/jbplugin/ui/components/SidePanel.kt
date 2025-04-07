package com.mongodb.jbplugin.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.settings.pluginSetting
import com.mongodb.jbplugin.ui.components.connection.ConnectionBootstrapCard
import com.mongodb.jbplugin.ui.components.connection.OnlyWhenConnected
import com.mongodb.jbplugin.ui.components.inspections.InspectionAccordion
import com.mongodb.jbplugin.ui.components.inspections.InspectionScopeSettings
import com.mongodb.jbplugin.ui.components.utilities.hooks.LocalProject
import org.jetbrains.jewel.bridge.addComposeTab

class SidePanel : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {
        toolWindow.setAttentionIcon()
        toolWindow.addComposeTab(isLockable = true, isCloseable = false) {
            CompositionLocalProvider(
                LocalProject provides project
            ) {
                createSidePanelComponent()
            }
        }
    }

    override suspend fun isApplicableAsync(project: Project): Boolean {
        val isEnabled by pluginSetting { ::ftEnableSidePanel }
        return isEnabled
    }

    @Composable
    private fun createSidePanelComponent() {
        Column(Modifier.padding(vertical = 4.dp, horizontal = 12.dp)) {
            ConnectionBootstrapCard()
            OnlyWhenConnected {
                InspectionScopeSettings()
                InspectionAccordion()
            }
        }
    }
}

val Project.mongoDbSidePanel: ToolWindow
    get() = ToolWindowManager.getInstance(this).getToolWindow("MongoDB")!!

fun ToolWindow.setOkIcon() {
    ApplicationManager.getApplication().invokeLater {
        setIcon(Icons.SidePanel.logo)
    }
}

fun ToolWindow.setAttentionIcon() {
    ApplicationManager.getApplication().invokeLater {
        setIcon(Icons.SidePanel.logoAttention)
    }
}
