package com.mongodb.jbplugin.observability.probe

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryService
import com.mongodb.jbplugin.observability.useLogMessage

private val logger: Logger = logger<PluginActivatedProbe>()

/**
 * This probe is emitted when the plugin is activated (started).
 */
@Service
class PluginActivatedProbe {
    fun pluginActivated() {
        val telemetry by service<TelemetryService>()

        telemetry.sendEvent(TelemetryEvent.PluginActivated)

        logger.info(
            useLogMessage("Plugin activated.")
                .build()
        )
    }
}
