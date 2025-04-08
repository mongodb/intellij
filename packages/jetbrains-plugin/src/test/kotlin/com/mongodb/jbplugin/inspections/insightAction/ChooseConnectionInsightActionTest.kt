package com.mongodb.jbplugin.inspections.insightAction

import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.eventually
import com.mongodb.jbplugin.fixtures.withMockedService
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.ui.viewModel.SidePanelEvents.OpenConnectionComboBox
import com.mongodb.jbplugin.ui.viewModel.SidePanelViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
@IntegrationTest
class ChooseConnectionInsightActionTest {
    @Test
    fun `should open sidepanel and emit OpenConnectionComboBox on SidePanelViewModel flow`(
        project: Project,
        coroutineScope: TestScope,
    ) {
        val sidePanelViewModel = SidePanelViewModel(
            project,
            coroutineScope,
        )
        val sidePanelSpy = spy(sidePanelViewModel)
        project.withMockedService(sidePanelSpy)

        var openConnectionComboBoxEmitted = false
        sidePanelViewModel.subscribeToSidePanelEvents { event ->
            if (event == OpenConnectionComboBox) {
                openConnectionComboBoxEmitted = true
            }
        }

        runBlocking {
            ChooseConnectionInsightAction().apply(
                QueryInsight.nonExistentDatabase(project.aQuery(), "db")
            )
        }

        eventually {
            assertTrue(openConnectionComboBoxEmitted)
        }

        verify(sidePanelSpy).openSidePanel()
    }
}
