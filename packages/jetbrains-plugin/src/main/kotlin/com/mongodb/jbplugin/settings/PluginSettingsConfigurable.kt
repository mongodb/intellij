/**
 * These classes represent the settings modal.
 */

package com.mongodb.jbplugin.settings

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import com.mongodb.jbplugin.i18n.SettingsMessages
import com.mongodb.jbplugin.i18n.TelemetryMessages
import com.mongodb.jbplugin.meta.service
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * This class represents a section in the settings modal. The UI will be implemented by
 * PluginSettingsComponent.
 */
class PluginSettingsConfigurable : Configurable {
    private lateinit var settingsComponent: PluginSettingsComponent

    override fun createComponent(): JComponent {
        settingsComponent = PluginSettingsComponent()
        return settingsComponent.root
    }

    override fun isModified(): Boolean {
        val savedSettings by service<PluginSettingsStateComponent>()
        return settingsComponent.isTelemetryEnabledCheckBox.isSelected !=
            savedSettings.state.isTelemetryEnabled ||
            settingsComponent.enableFullExplainPlan.isSelected !=
            savedSettings.state.isFullExplainPlanEnabled
    }

    override fun apply() {
        val savedSettings by service<PluginSettingsStateComponent>()
        savedSettings.state.apply {
            isTelemetryEnabled = settingsComponent.isTelemetryEnabledCheckBox.isSelected
            isFullExplainPlanEnabled = settingsComponent.enableFullExplainPlan.isSelected
        }
    }

    override fun reset() {
        val savedSettings by service<PluginSettingsStateComponent>()
        settingsComponent.isTelemetryEnabledCheckBox.isSelected =
            savedSettings.state.isTelemetryEnabled
        settingsComponent.enableFullExplainPlan.isSelected =
            savedSettings.state.isFullExplainPlanEnabled
    }

    override fun getDisplayName() = SettingsMessages.message("settings.display-name")
}

/**
 * The panel that is shown in the settings section for MongoDB.
 */
private class PluginSettingsComponent {
    val root: JPanel
    val isTelemetryEnabledCheckBox =
        JBCheckBox(TelemetryMessages.message("settings.telemetry-collection-checkbox"))
    val enableFullExplainPlan =
        JBCheckBox(TelemetryMessages.message("settings.full-explain-plan-checkbox"))
    val privacyPolicyButton = JButton(TelemetryMessages.message("action.view-privacy-policy"))
    val evaluateOperationPerformanceButton =
        JButton(TelemetryMessages.message("settings.view-full-explain-plan-documentation"))

    init {
        privacyPolicyButton.addActionListener {
            BrowserUtil.browse(TelemetryMessages.message("settings.telemetry-privacy-policy"))
        }

        evaluateOperationPerformanceButton.addActionListener {
            BrowserUtil.browse(
                TelemetryMessages.message("settings.full-explain-plan-documentation")
            )
        }

        root =
            FormBuilder.createFormBuilder()
                .addComponent(enableFullExplainPlan)
                .addTooltip(TelemetryMessages.message("settings.full-explain-plan-tooltip"))
                .addComponent(evaluateOperationPerformanceButton)
                .addSeparator()
                .addComponent(isTelemetryEnabledCheckBox)
                .addTooltip(TelemetryMessages.message("settings.telemetry-collection-tooltip"))
                .addComponent(privacyPolicyButton)
                .addComponentFillVertically(JPanel(), 0)
                .panel

        root.accessibleContext.accessibleName = "MongoDB Settings"
        isTelemetryEnabledCheckBox.accessibleContext.accessibleName = "MongoDB Enable Telemetry"
        enableFullExplainPlan.accessibleContext.accessibleName = "MongoDB Enable Full Explain Plan"
    }
}
