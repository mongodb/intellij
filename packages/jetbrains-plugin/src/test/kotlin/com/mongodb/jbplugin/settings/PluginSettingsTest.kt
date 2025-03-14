package com.mongodb.jbplugin.settings

import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.eventually
import org.assertj.swing.core.Robot
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.fixture.FrameFixture
import org.junit.jupiter.api.Test
import javax.swing.JFrame

@IntegrationTest
class PluginSettingsTest {

    @Test
    fun `renders the sample size input text with the default value`(robot: Robot) {
        val (fixture, _) = render(robot)

        eventually {
            fixture.textBox("MongoDB Sample Size")
                .requireVisible()
                .requireText("50")
        }
    }

    @Test
    fun `renders the full explain plan checkbox with the default value`(robot: Robot) {
        val (fixture, _) = render(robot)

        eventually {
            fixture.checkBox("MongoDB Enable Full Explain Plan")
                .requireVisible()
                .requireNotSelected()
        }
    }

    @Test
    fun `renders the index soft limit input with the default value`(robot: Robot) {
        val (fixture, _) = render(robot)

        eventually {
            fixture.textBox("MongoDB Soft Index Limit")
                .requireVisible()
                .requireText("10")
        }
    }

    @Test
    fun `renders the telemetry checkbox with the default value`(robot: Robot) {
        val (fixture, _) = render(robot)

        eventually {
            fixture.checkBox("MongoDB Enable Telemetry")
                .requireVisible()
                .requireSelected()
        }
    }

    @Test
    fun `renders the analyze query performance button`(robot: Robot) {
        val (fixture, _) = render(robot)

        eventually {
            fixture.button("MongoDB Analyze Query Performance")
                .requireVisible()
                .requireEnabled()
        }
    }

    @Test
    fun `renders the privacy policy button`(robot: Robot) {
        val (fixture, _) = render(robot)

        eventually {
            fixture.button("MongoDB Privacy Policy")
                .requireVisible()
                .requireEnabled()
        }
    }

    @Test
    fun `renders the side panel feature toggle`(robot: Robot) {
        val (fixture, _) = render(robot)

        eventually {
            fixture.checkBox("MongoDB Side Panel Enabled")
                .requireVisible()
                .requireEnabled()
                .requireNotSelected()
        }
    }

    private fun render(robot: Robot): Pair<FrameFixture, PluginSettingsConfigurable> {
        return GuiActionRunner.execute<Pair<FrameFixture, PluginSettingsConfigurable>> {
            val frame = JFrame()
            val modal = PluginSettingsConfigurable()
            frame.add(modal.createComponent())
            modal.reset()
            frame.isVisible = true

            frame.pack()
            FrameFixture(robot, frame) to modal
        }
    }
}
