package com.mongodb.jbplugin

import com.intellij.openapi.application.Application
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.withMockedService
import com.mongodb.jbplugin.observability.probe.PluginActivatedProbe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify

@IntegrationTest
class ActivatePluginPostStartupActivityTest {
    @Test
    fun `emits a plugin activated probe`(application: Application, project: Project) = runTest {
        val pluginActivatedProbe = mock<PluginActivatedProbe>()
        application.withMockedService(pluginActivatedProbe)

        val listener = ActivatePluginPostStartupActivity()

        listener.execute(project)
        verify(pluginActivatedProbe).pluginActivated()
    }
}
