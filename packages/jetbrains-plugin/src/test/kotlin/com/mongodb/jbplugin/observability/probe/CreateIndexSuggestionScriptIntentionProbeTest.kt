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
internal class CreateIndexSuggestionScriptIntentionProbeTest {
    @Test
    fun `should send a CreateIndexIntentionEvent event`(application: Application) {
        val telemetryService = mock<TelemetryService>()
        val dialect = HasSourceDialect.DialectName.entries.toTypedArray().random()

        val query = Node<PsiElement?>(null, listOf(HasSourceDialect(dialect))) as Node<PsiElement>

        application.withMockedService(telemetryService)
            .withMockedService(mockLogMessage())

        val probe = CreateIndexIntentionProbe()

        probe.intentionClicked(query)

        verify(telemetryService).sendEvent(TelemetryEvent.CreateIndexIntentionEvent(dialect))
    }
}
