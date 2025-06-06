package com.mongodb.jbplugin.observability.probe

import com.intellij.openapi.application.Application
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.mockLogMessage
import com.mongodb.jbplugin.fixtures.withMockedService
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.mql.components.IsCommand.CommandType
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify

@IntegrationTest
class AutocompleteSuggestionAcceptedProbeTest {
    @Test
    fun `should aggregate multiple events and send them once`(application: Application) =
        runTest {
            val telemetryService = mock<TelemetryService>()

            application
                .withMockedService(telemetryService)
                .withMockedService(mockLogMessage())

            val probe = AutocompleteSuggestionAcceptedProbe(this)
            (1..2).forEach { probe.collectionCompletionAccepted(JavaDriverDialect) }
            (1..5).forEach { probe.databaseCompletionAccepted(JavaDriverDialect) }
            (1..10).forEach {
                probe.fieldCompletionAccepted(JavaDriverDialect, CommandType.FIND_ONE)
            }
            (1..10).forEach {
                probe.fieldCompletionAccepted(JavaDriverDialect, CommandType.AGGREGATE)
            }
            (1..10).forEach {
                probe.fieldCompletionAccepted(JavaDriverDialect, CommandType.UPDATE_MANY)
            }

            probe.sendEvents()

            verify(telemetryService, timeout(1000)).sendEvent(
                TelemetryEvent.AutocompleteGroupEvent(
                    HasSourceDialect.DialectName.JAVA_DRIVER,
                    "database",
                    CommandType.UNKNOWN.canonical,
                    5
                ),
            )

            verify(telemetryService, timeout(1000)).sendEvent(
                TelemetryEvent.AutocompleteGroupEvent(
                    HasSourceDialect.DialectName.JAVA_DRIVER,
                    "collection",
                    CommandType.UNKNOWN.canonical,
                    2
                ),
            )

            verify(telemetryService, timeout(1000)).sendEvent(
                TelemetryEvent.AutocompleteGroupEvent(
                    HasSourceDialect.DialectName.JAVA_DRIVER,
                    "field",
                    CommandType.FIND_ONE.canonical,
                    10
                ),
            )

            verify(telemetryService, timeout(1000)).sendEvent(
                TelemetryEvent.AutocompleteGroupEvent(
                    HasSourceDialect.DialectName.JAVA_DRIVER,
                    "field",
                    CommandType.AGGREGATE.canonical,
                    10
                ),
            )

            verify(telemetryService, timeout(1000)).sendEvent(
                TelemetryEvent.AutocompleteGroupEvent(
                    HasSourceDialect.DialectName.JAVA_DRIVER,
                    "field",
                    CommandType.UPDATE_MANY.canonical,
                    10
                ),
            )

            probe.appWillBeClosed(false)
        }

    @Test
    fun `do not send anything to telemetry if empty`(application: Application) =
        runTest {
            val telemetryService = mock<TelemetryService>()

            application
                .withMockedService(telemetryService)
                .withMockedService(mockLogMessage())

            val probe = AutocompleteSuggestionAcceptedProbe(this)

            probe.sendEvents()

            verify(telemetryService, never()).sendEvent(any())

            probe.appWillBeClosed(false)
        }
}
