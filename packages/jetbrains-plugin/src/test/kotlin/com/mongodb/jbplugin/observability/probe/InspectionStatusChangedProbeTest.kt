package com.mongodb.jbplugin.observability.probe

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.Application
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.mockLogMessage
import com.mongodb.jbplugin.fixtures.withMockedService
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryService
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@IntegrationTest
internal class InspectionStatusChangedProbeTest {
    @Test
    fun `should send a InspectionStatusChangeEvent event when found for the first time`(
        application: Application,
        testScope: TestScope
    ) {
        val telemetryService = mock<TelemetryService>()
        val dialect = HasSourceDialect.DialectName.entries.toTypedArray().random()

        val query = Node<PsiElement?>(null, listOf(HasSourceDialect(dialect))) as Node<PsiElement>

        application.withMockedService(telemetryService)
            .withMockedService(mockLogMessage())

        val probe = InspectionStatusChangedProbe(testScope)

        probe.inspectionChanged(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.FIELD_DOES_NOT_EXIST,
            query
        )
        probe.inspectionChanged(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.FIELD_DOES_NOT_EXIST,
            query
        )

        testScope.advanceUntilIdle()

        verify(telemetryService, timeout(1000).times(1)).sendEvent(any())
    }

    @Test
    fun `should send a InspectionStatusChangeEvent event when multiple types of events`(
        application: Application,
        testScope: TestScope
    ) {
        val telemetryService = mock<TelemetryService>()
        val dialect = HasSourceDialect.DialectName.entries.toTypedArray().random()

        val query = Node<PsiElement?>(null, listOf(HasSourceDialect(dialect))) as Node<PsiElement>

        application.withMockedService(telemetryService)
            .withMockedService(mockLogMessage())

        val probe = InspectionStatusChangedProbe(testScope)

        probe.inspectionChanged(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.FIELD_DOES_NOT_EXIST,
            query
        )
        probe.inspectionChanged(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.FIELD_DOES_NOT_EXIST,
            query
        )
        probe.inspectionChanged(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.NO_NAMESPACE_INFERRED,
            query
        )
        probe.inspectionChanged(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.NO_NAMESPACE_INFERRED,
            query
        )

        testScope.advanceUntilIdle()

        verify(telemetryService, timeout(1000).times(2)).sendEvent(any())
    }

    @Test
    fun `should send a InspectionStatusChangeEvent event with type checking issues`(
        application: Application,
        testScope: TestScope
    ) {
        val telemetryService = mock<TelemetryService>()
        val dialect = HasSourceDialect.DialectName.entries.toTypedArray().random()

        val query = Node<PsiElement?>(null, listOf(HasSourceDialect(dialect))) as Node<PsiElement>

        application.withMockedService(telemetryService)
            .withMockedService(mockLogMessage())

        val probe = InspectionStatusChangedProbe(testScope)

        probe.typeMismatchInspectionActive(query, "actual", "expected")

        testScope.advanceUntilIdle()

        verify(telemetryService, timeout(1000).times(1)).sendEvent(
            TelemetryEvent.InspectionStatusChangeEvent(
                dialect,
                TelemetryEvent.InspectionStatusChangeEvent.InspectionType.TYPE_MISMATCH,
                TelemetryEvent.InspectionStatusChangeEvent.InspectionStatus.ACTIVE,
                "actual",
                "expected"
            )
        )
    }

    @Test
    fun `should send a resolved InspectionStatusChangeEvent when there is no problem anymore`(
        application: Application,
        testScope: TestScope
    ) {
        val telemetryService = mock<TelemetryService>()
        val problemsHolder = mock<ProblemsHolder>()

        val dialect = HasSourceDialect.DialectName.entries.toTypedArray().random()

        `when`(problemsHolder.results).thenReturn(emptyList())

        val query = Node<PsiElement?>(null, listOf(HasSourceDialect(dialect))) as Node<PsiElement>

        application.withMockedService(telemetryService)
            .withMockedService(mockLogMessage())

        val probe = InspectionStatusChangedProbe(testScope)

        probe.inspectionChanged(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.FIELD_DOES_NOT_EXIST,
            query
        )
        probe.finishedProcessingInspections(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.FIELD_DOES_NOT_EXIST,
            problemsHolder
        )

        testScope.advanceUntilIdle()

        verify(telemetryService, timeout(1000).times(2)).sendEvent(any())
    }
}
