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

private val logger: Logger = logger<CreateIndexIntentionProbe>()

/**
 * This probe is emitted when the user clicks on the Create Index
 * intention.
 */
@Service
class CreateIndexIntentionProbe {
    fun intentionClicked(query: Node<PsiElement>) {
        val telemetry by service<TelemetryService>()

        val dialect = query.component<HasSourceDialect>() ?: return
        val event = TelemetryEvent.CreateIndexIntentionEvent(dialect.name)
        telemetry.sendEvent(event)

        logger.info(
            useLogMessage("Create index quick action clicked.")
                .mergeTelemetryEventProperties(event)
                .build()
        )
    }
}
