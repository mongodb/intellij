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
import java.awt.Color
import java.awt.Font
import java.text.NumberFormat
import java.text.ParseException
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFormattedTextField
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.text.NumberFormatter

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
        val sampleSizeInComponent = settingsComponent.sampleSize.text.ifEmpty { "0" }
        val sampleSizeSavedOnDisk = savedSettings.state.sampleSize.toString()
        return settingsComponent.isTelemetryEnabledCheckBox.isSelected !=
            savedSettings.state.isTelemetryEnabled ||
            settingsComponent.enableFullExplainPlan.isSelected !=
            savedSettings.state.isFullExplainPlanEnabled ||
            sampleSizeInComponent != sampleSizeSavedOnDisk
    }

    override fun apply() {
        val savedSettings by service<PluginSettingsStateComponent>()
        savedSettings.state.apply {
            isTelemetryEnabled = settingsComponent.isTelemetryEnabledCheckBox.isSelected
            isFullExplainPlanEnabled = settingsComponent.enableFullExplainPlan.isSelected
            sampleSize = try {
                settingsComponent.sampleSize.text.toInt()
            } catch (e: Exception) {
                DEFAULT_SAMPLE_SIZE
            }
        }
    }

    override fun reset() {
        val savedSettings by service<PluginSettingsStateComponent>()
        settingsComponent.isTelemetryEnabledCheckBox.isSelected =
            savedSettings.state.isTelemetryEnabled
        settingsComponent.enableFullExplainPlan.isSelected =
            savedSettings.state.isFullExplainPlanEnabled
        settingsComponent.sampleSize.text = savedSettings.state.sampleSize.toString()
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

    lateinit var sampleSize: JTextField

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
                .addComponent(getSampleSizeField())
                .addSeparator()
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
        sampleSize.accessibleContext.accessibleName = "MongoDB Sample Size"
    }

    fun getSampleSizeField(): JComponent {
        val mainLabel = JLabel(SettingsMessages.message("settings.sample-size.label"))
        val subtextLabel = JLabel(
            SettingsMessages.message("settings.sample-size.sub-label")
        ).apply {
            font = font.deriveFont(Font.PLAIN, 12f)
            foreground = Color.GRAY
        }

        val label = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(mainLabel)
            add(subtextLabel)
        }

        val format = NumberFormat.getIntegerInstance().apply {
            isGroupingUsed = false
        }

        val numberFormatter = object : NumberFormatter(format) {
            @Throws(ParseException::class)
            override fun stringToValue(text: String?): Any? {
                return if (text.isNullOrEmpty()) null else super.stringToValue(text)
            }
        }.apply {
            valueClass = Integer::class.java
            minimum = 0
            maximum = Integer.MAX_VALUE
            allowsInvalid = false
            commitsOnValidEdit = true
        }

        sampleSize = JFormattedTextField(numberFormatter).apply {
            focusLostBehavior = JFormattedTextField.PERSIST
            addPropertyChangeListener("value") { evt ->
                if (evt.newValue == null) {
                    this.text = ""
                }
            }
        }

        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(label)
            add(Box.createHorizontalStrut(20))
            add(sampleSize)
        }
    }
}
