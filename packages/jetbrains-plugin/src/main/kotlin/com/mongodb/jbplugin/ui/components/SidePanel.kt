package com.mongodb.jbplugin.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.observability.useLogMessage
import com.mongodb.jbplugin.ui.components.connection.ConnectionBootstrapCard
import com.mongodb.jbplugin.ui.components.connection.OnlyWhenConnected
import com.mongodb.jbplugin.ui.components.inspections.InspectionAccordion
import com.mongodb.jbplugin.ui.components.inspections.InspectionScopeSettings
import com.mongodb.jbplugin.ui.components.utilities.hooks.LocalProject
import com.mongodb.jbplugin.ui.viewModel.SidePanelStatus
import com.mongodb.jbplugin.ui.viewModel.SidePanelViewModel
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.skiko.SkikoProperties

const val MDB_SIDEPANEL_ID = "MongoDB"
private val log = logger<SidePanel>()

class SidePanel : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {
        log.info(
            useLogMessage("Rendering SidePanel for the first time")
                .put("skiko.renderApi", SkikoProperties.renderApi.name)
                .build(),
        )

        val viewModel by project.service<SidePanelViewModel>()
        viewModel.setStatus(SidePanelStatus.Warning)

        try {
            doRender(toolWindow, project)
        } catch (ex: Throwable) {
            val originalSkikoApi = SkikoProperties.renderApi.name
            System.setProperty("skiko.renderApi", "SOFTWARE")
            val fallbackSkikoApi = SkikoProperties.renderApi.name

            log.warn(
                useLogMessage("Could not use GPU accelerated rendering: " + (ex.message ?: "<no error message>"))
                    .put("original.skiko.renderApi", originalSkikoApi)
                    .put("fallback.skiko.renderApi", fallbackSkikoApi)
                    .build(),
                ex
            )

            doRender(toolWindow, project)
        }
    }

    private fun doRender(toolWindow: ToolWindow, project: Project) {
        toolWindow.addComposeTab(isLockable = true, isCloseable = false) {
            CompositionLocalProvider(
                LocalProject provides project
            ) {
                createSidePanelComponent()
            }
        }
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
