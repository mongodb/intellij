package com.mongodb.jbplugin.inspections.insightAction

import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.editor.CachedQueryService
import com.mongodb.jbplugin.editor.DatagripConsoleEditor
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.mockDataSource
import com.mongodb.jbplugin.fixtures.withMockedService
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.observability.probe.CreateIndexIntentionProbe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@IntegrationTest
class CreateSuggestedIndexInsightActionTest {
    @Test
    fun `should emit a probe when a new index request is created`(
        project: Project,
        coroutineScope: TestScope,
    ) = coroutineScope.runTest {
        val dataSource = mockDataSource()
        project.connectedTo(dataSource)

        val probe = mock<CreateIndexIntentionProbe>()
        val consoleEditor = mock<DatagripConsoleEditor>()

        val insightAction = CreateSuggestedIndexInsightAction(probe, consoleEditor)
        val query = project.aQuery()

        insightAction.apply(QueryInsight.notUsingIndex(query))

        verify(probe).intentionClicked(query)
    }

    @Test
    fun `should open a console with the index script`(
        project: Project,
        coroutineScope: TestScope,
    ) = coroutineScope.runTest {
        val dataSource = mockDataSource()
        project.connectedTo(dataSource)

        val probe = mock<CreateIndexIntentionProbe>()
        val consoleEditor = mock<DatagripConsoleEditor>()
        val cachedQueryService = mock<CachedQueryService>()
        whenever(cachedQueryService.allSiblingsOf(any())).thenReturn(emptyArray())

        project.withMockedService(cachedQueryService)

        val insightAction = CreateSuggestedIndexInsightAction(probe, consoleEditor)
        val query = project.aQuery()

        insightAction.apply(QueryInsight.notUsingIndex(query))

        verify(consoleEditor).openConsoleForDataSource(project, dataSource)
    }
}
