package com.mongodb.jbplugin.observability.probe

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryService
import com.mongodb.jbplugin.observability.useLogMessage

private val logger: Logger = logger<QueryRunProbe>()

/**
 * This probe is emitted when the user requests to run a query through the gutter icon
 * action.
 */
@Service
class QueryRunProbe {
    fun queryRunRequested(
        query: Node<PsiElement>,
        console: TelemetryEvent.QueryRunEvent.Console,
        trigger: TelemetryEvent.QueryRunEvent.TriggerLocation
    ) {
        val telemetry by service<TelemetryService>()

        var dialect = query.component<HasSourceDialect>() ?: return
        val event = TelemetryEvent.QueryRunEvent(dialect.name, console, trigger)
        telemetry.sendEvent(event)

        logger.info(
            useLogMessage("Query run requested.")
                .mergeTelemetryEventProperties(event)
                .build()
        )
    }
}
