/**
 * This file defines the set of classes that will be used to build a log message.
 * These classes are marked as internal because they shouldn't be used outside
 * this module.
 *
 * Ideally, you are injecting the LogMessage service into your probe, and when
 * sending an event, we would also send a relevant log message.
 *
 */

package com.mongodb.jbplugin.observability

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.mongodb.jbplugin.meta.BuildInformation
import com.mongodb.jbplugin.meta.service

/**
 * @param gson
 * @param message
 */
class LogMessageBuilder(private val gson: Gson, message: String) {
    val properties: MutableMap<String, Any> = mutableMapOf("message" to message)

    fun put(
        key: String,
        value: Any,
    ): LogMessageBuilder {
        properties[key] = value
        return this
    }

    inline fun <reified T : TelemetryEvent> mergeTelemetryEventProperties(event: T): LogMessageBuilder {
        put("event", event.name)
        properties.putAll(event.properties.mapKeys { it.key.publicName })
        return this
    }

    fun build(): String = gson.toJson(properties)
}

/**
 * This class will be injected in probes to build log messages. Usually like:
 * ```kt
 * @Service
 * class MyProbe {
 *  ...
 *     fun somethingProbed() {
 *        log.info(useLogMessage("My message").put("someOtherProp", 25).build())
 *     }
 *  ...
 * }
 * ```
 *
 * However, feel free to use it outside probes for additional logs that are not relevant for telemetry, like internal
 * errors.
 */
@Service
class LogMessage {
    private val gson = GsonBuilder().generateNonExecutableJson().disableJdkUnsafe().create()

    fun message(key: String): LogMessageBuilder {
        val runtimeInformationService by service<RuntimeInformationService>()
        val runtimeInformation = runtimeInformationService.get()

        return LogMessageBuilder(gson, key)
            .put("pluginVersion", BuildInformation.pluginVersion)
            .put("powerSaveMode", PowerSaveMode.isEnabled())
            .put("ideaUserId", runtimeInformation.userId)
            .put("os", runtimeInformation.osName)
            .put("arch", runtimeInformation.arch)
            .put("jvmVendor", runtimeInformation.jvmVendor)
            .put("jvmVersion", runtimeInformation.jvmVersion)
            .put("ide", runtimeInformation.applicationName)
            .put("ideVersion", runtimeInformation.buildVersion)
    }
}

/**
 * Function to access the application level log message builder. Should be used for any important log as it includes
 * in the log line additional information from the runtime environment.
 *
 * @param message
 * @return
 */
fun useLogMessage(
    message: String
) = ApplicationManager.getApplication().getService(LogMessage::class.java).message(
    message
)
