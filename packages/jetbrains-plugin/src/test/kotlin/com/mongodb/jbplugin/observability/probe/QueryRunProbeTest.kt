package com.mongodb.jbplugin.observability.probe

import com.intellij.openapi.application.Application
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.mockLogMessage
import com.mongodb.jbplugin.fixtures.withMockedService
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@IntegrationTest
internal class QueryRunProbeTest {
    @Test
    fun `should send a QueryRunEvent event`(application: Application) {
        val telemetryService = mock<TelemetryService>()
        val dialect = HasSourceDialect.DialectName.entries.toTypedArray().random()
        val console = TelemetryEvent.QueryRunEvent.Console.entries.toTypedArray().random()
        val triggerLocation = TelemetryEvent.QueryRunEvent.TriggerLocation.entries.toTypedArray().random()

        val query = Node<PsiElement?>(null, listOf(HasSourceDialect(dialect))) as Node<PsiElement>

        application.withMockedService(telemetryService)
            .withMockedService(mockLogMessage())

        val probe = QueryRunProbe()

        probe.queryRunRequested(query, console, triggerLocation)

        verify(telemetryService).sendEvent(
            TelemetryEvent.QueryRunEvent(
                dialect,
                console,
                triggerLocation
            )
        )
    }
}
