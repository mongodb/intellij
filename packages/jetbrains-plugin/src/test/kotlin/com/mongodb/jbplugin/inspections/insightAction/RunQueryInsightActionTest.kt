package com.mongodb.jbplugin.inspections.insightAction

import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.editor.DatagripConsoleEditor
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.eventually
import com.mongodb.jbplugin.fixtures.mockDataSource
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryEvent.QueryRunEvent.Console
import com.mongodb.jbplugin.observability.probe.QueryRunProbe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@IntegrationTest
class RunQueryInsightActionTest {
    @Test
    fun `should emit a probe when a requesting running a query`(
        project: Project
    ) = runTest {
        val dataSource = mockDataSource()
        project.connectedTo(dataSource)

        val probe = mock<QueryRunProbe>()
        val consoleEditor = mock<DatagripConsoleEditor>()

        val insightAction = RunQueryInsightAction(this, probe, consoleEditor)
        val query = project.aQuery()

        insightAction.apply(QueryInsight.nonExistingField(query, query.source, "anyfield"))

        eventually {
            verify(probe).queryRunRequested(
                query,
                Console.EXISTING,
                TelemetryEvent.QueryRunEvent.TriggerLocation.SIDE_PANEL
            )
        }
    }

    @Test
    fun `should open a console with the generated code snippet`(
        project: Project
    ) = runTest {
        val dataSource = mockDataSource()
        project.connectedTo(dataSource)

        val probe = mock<QueryRunProbe>()
        val consoleEditor = mock<DatagripConsoleEditor>()

        val insightAction = RunQueryInsightAction(this, probe, consoleEditor)
        val query = project.aQuery()

        insightAction.apply(QueryInsight.nonExistingField(query, query.source, "anyfield"))

        eventually {
            verify(consoleEditor).openConsoleForDataSource(project, dataSource)
        }
    }
}
