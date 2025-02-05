package com.mongodb.jbplugin.settings

import com.intellij.remoterobot.RemoteRobot
import com.mongodb.jbplugin.fixtures.RequiresProject
import com.mongodb.jbplugin.fixtures.UiTest
import com.mongodb.jbplugin.fixtures.components.openBrowserSettings
import com.mongodb.jbplugin.fixtures.components.openSettings
import com.mongodb.jbplugin.fixtures.components.useSetting
import com.mongodb.jbplugin.fixtures.eventually
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@UiTest
class SettingsUiTest {
    @Test
    @RequiresProject("basic-java-project-with-mongodb")
    fun `allows toggling the telemetry`(remoteRobot: RemoteRobot) {
        val telemetryBeforeTest = remoteRobot.useSetting<Boolean>("isTelemetryEnabled")

        val settings = remoteRobot.openSettings()
        settings.enableTelemetry.click()
        settings.ok.click()

        val telemetryAfterTest = remoteRobot.useSetting<Boolean>("isTelemetryEnabled")
        assertNotEquals(telemetryBeforeTest, telemetryAfterTest)
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb")
    fun `allows toggling the full explain plan`(remoteRobot: RemoteRobot) {
        val telemetryBeforeTest = remoteRobot.useSetting<Boolean>("isFullExplainPlanEnabled")

        val settings = remoteRobot.openSettings()
        settings.enableFullExplainPlan.click()
        settings.ok.click()

        val telemetryAfterTest = remoteRobot.useSetting<Boolean>("isFullExplainPlanEnabled")
        assertNotEquals(telemetryBeforeTest, telemetryAfterTest)
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb")
    fun `allows opening the privacy policy in a browser`(remoteRobot: RemoteRobot) {
        remoteRobot.openBrowserSettings().run {
            useFakeBrowser()
        }

        val settings = remoteRobot.openSettings()
        settings.privacyPolicyButton.click()
        settings.ok.click()

        val lastBrowserUrl =
            remoteRobot.openBrowserSettings().run {
                useSystemBrowser()
                ok.click()
                lastBrowserUrl()
            }

        assertEquals("https://www.mongodb.com/legal/privacy/privacy-policy", lastBrowserUrl)
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb")
    fun `allows opening the analyze query performance page`(remoteRobot: RemoteRobot) {
        remoteRobot.openBrowserSettings().run {
            useFakeBrowser()
        }

        val settings = remoteRobot.openSettings()
        settings.analyzeQueryPerformanceButton.click()
        settings.ok.click()

        val lastBrowserUrl =
            remoteRobot.openBrowserSettings().run {
                useSystemBrowser()
                ok.click()
                lastBrowserUrl()
            }

        assertEquals(
            "https://www.mongodb.com/docs/manual/tutorial/evaluate-operation-performance/",
            lastBrowserUrl
        )
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb")
    fun `allows adjusting sample size with a valid value`(remoteRobot: RemoteRobot) {
        remoteRobot.openBrowserSettings().run {
            useFakeBrowser()
        }

        eventually {
            val settings = remoteRobot.openSettings()
            assertEquals("50", settings.sampleSizeField.text)
            settings.sampleSizeField.text = "10"
            settings.ok.click()
        }

        eventually {
            val settings = remoteRobot.openSettings()
            assertEquals("10", settings.sampleSizeField.text)
            settings.sampleSizeField.text = "50"
            settings.ok.click()
        }
    }
}
