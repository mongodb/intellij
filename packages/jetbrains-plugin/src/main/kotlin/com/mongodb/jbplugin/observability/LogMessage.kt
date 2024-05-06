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
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.SystemInfo

/**
 * @param gson
 * @param message
 */
internal class LogMessageBuilder(private val gson: Gson, message: String) {
    private val properties: MutableMap<String, Any> = mutableMapOf("message" to message)

    fun put(key: String, value: Any): LogMessageBuilder {
        properties[key] = value
        return this
    }

    fun build(): String = gson.toJson(properties)
}

/**
 * This class will be injected in probes to build log messages. Usually like:
 * ```kt
 * @Service
 * class MyProbe(private val logMessage: LogMessage?) {
 *  ...
 *  fun somethingProbed() {
 *     log.info(logMessage?.message("My message").put("someOtherProp", 25).build())
 * }
 * ```
 */
@Service
internal class LogMessage {
    private val gson = GsonBuilder().generateNonExecutableJson().disableJdkUnsafe().create()

    fun message(key: String): LogMessageBuilder {
        val userId = getOrDefault("<userId>") { PermanentInstallationID.get() }
        val osName = getOrDefault("<osName>") { SystemInfo.getOsNameAndVersion() }
        val arch = getOrDefault("<arch>") { SystemInfo.OS_ARCH }
        val jvmVendor = getOrDefault("<jvmVendor>") { SystemInfo.JAVA_VENDOR }
        val jvmVersion = getOrDefault("<jvmVersion>") { SystemInfo.JAVA_VERSION }
        val buildVersion = getOrDefault("<fullVersion>") { ApplicationInfo.getInstance().fullVersion }
        val applicationName = getOrDefault("<fullApplicationName>") {
            ApplicationInfo.getInstance().fullApplicationName
        }

        return LogMessageBuilder(gson, key)
            .put("userId", userId)
            .put("os", osName)
            .put("arch", arch)
            .put("jvmVendor", jvmVendor)
            .put("jvmVersion", jvmVersion)
            .put("buildVersion", buildVersion)
            .put("ide", applicationName)
    }

    private fun <T> getOrDefault(default: T, supplier: () -> T): T {
        return try {
            supplier()
        } catch (ex: Throwable) {
            return default
        }
    }
}
