/**
 * Fixture that represents the plugin settings page. If you add new settings,
 * you should add new properties here.
 *
 * For usage in tests that work with settings, you can use openSettings, that will open the UI, or
 * remoteRobot.useSetting(name), that will give you the value for that specific setting.
 */

package com.mongodb.jbplugin.fixtures.components

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.mongodb.jbplugin.fixtures.findVisible
import com.mongodb.jbplugin.fixtures.openSettingsAtSection

/**
 * Component that represents the settings page.
 *
 * @param remoteRobot
 * @param remoteComponent
 */
@DefaultXpath(by = "accessible name", xpath = "//div[@accessiblename='MongoDB Settings']")
@FixtureName("MongoDBSettings")
class MongoDbSettingsFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent
) : ContainerFixture(
    remoteRobot,
    remoteComponent,
) {
    val enableTelemetry by lazy {
        findAll<JCheckboxFixture>().find { it.text == "Enable telemetry" }
            ?: throw NoSuchElementException()
    }

    val enableFullExplainPlan by lazy {
        findAll<JCheckboxFixture>().find { it.text == "Enable full explain plan" }
            ?: throw NoSuchElementException()
    }

    val privacyPolicyButton by lazy {
        findAll<JButtonFixture>().find { it.text == "View Privacy Policy" }
            ?: throw NoSuchElementException()
    }

    val analyzeQueryPerformanceButton by lazy {
        findAll<JButtonFixture>().find { it.text == "Analyze Query Performance" }
            ?: throw NoSuchElementException()
    }

    val sampleSizeField by lazy {
        find<JTextFieldFixture>(
            byXpath("//div[@class='JFormattedTextField' and @accessiblename='MongoDB Sample Size']")
        )
    }

    val ok by lazy {
        remoteRobot.findAll<JButtonFixture>().find { it.text == "OK" }
            ?: throw NoSuchElementException()
    }
}

/**
 * Opens the settings dialog and returns a fixture, so it can be interacted.
 *
 * @return
 */
fun RemoteRobot.openSettings(): MongoDbSettingsFixture {
    openSettingsAtSection("MongoDB")
    return findVisible()
}

/**
 * Returns a specific setting value from the plugin settings.
 *
 * @see com.mongodb.jbplugin.settings.PluginSettings for the possible values.
 *
 * @param name
 * @return
 */
inline fun <reified T> RemoteRobot.useSetting(name: String): T =
    callJs(
        """
        global.get('loadPluginService')(
            'com.mongodb.jbplugin.settings.PluginSettingsStateComponent'
        ).getState().$name()
        """.trimIndent(),
    )
