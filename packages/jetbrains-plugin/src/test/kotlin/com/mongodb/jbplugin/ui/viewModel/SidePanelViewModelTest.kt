package com.mongodb.jbplugin.ui.viewModel

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.withMockedService
import com.mongodb.jbplugin.ui.viewModel.SidePanelStatus.Ok
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@IntegrationTest
class SidePanelViewModelTest {
    @Test
    fun `should set the expected icon in the toolbar`(
        project: Project,
        coroutineScope: TestScope,
    ) {
        val twManager = mock<ToolWindowManager>()
        val tw = mock<ToolWindow>()

        whenever(twManager.getToolWindow("MongoDB")).thenReturn(tw)

        project.withMockedService(twManager)

        val viewModel = SidePanelViewModel(project, coroutineScope)
        viewModel.setStatus(Ok)

        verify(tw, timeout(1000)).setIcon(Ok.icon)
    }
}
