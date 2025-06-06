/**
 * Settings state for the plugin. These classes are responsible for the persistence of the plugin
 * settings.
 */

package com.mongodb.jbplugin.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.mongodb.jbplugin.meta.service
import java.io.Serializable
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

/**
 * The state component represents the persisting state. Don't use directly, this is only necessary
 * for the state to be persisted. Use PluginSettings instead.
 *
 * @see PluginSettings
 */
@Service
@State(
    name = "com.mongodb.jbplugin.settings.PluginSettings",
    storages = [Storage(value = "MongoDBPluginSettings.xml")],
)
class PluginSettingsStateComponent : SimplePersistentStateComponent<PluginSettings>(
    PluginSettings()
)

internal const val DEFAULT_SAMPLE_SIZE = 50
internal const val DEFAULT_INDEXES_AMOUNT_SOFT_LIMIT = 10

/**
 * The settings themselves. They are tracked, so any change on the settings properties will be eventually
 * persisted by IntelliJ. To access the settings, use the pluginSetting provider. For example,
 * let's say you want to get the isTelemetryEnabled setting, you would do:
 *
 * ```kt
 * val isTelemetryEnabled by pluginSetting { ::isTelemetryEnabled }
 * ```
 *
 * You'll see the settings as the underlying type (in this case, a boolean). If you want to modify
 * the setting, specify a variable instead of a value:
 *
 * ```kt
 * var isTelemetryEnabled by pluginSetting { ::isTelemetryEnabled }
 * isTelemetryEnabled = false
 * ```
 *
 * @see pluginSetting
 */
class PluginSettings : BaseState(), Serializable {
    var isTelemetryEnabled by property(true)
    var hasTelemetryOptOutputNotificationBeenShown by property(false)
    var isFullExplainPlanEnabled by property(false)
    var sampleSize by property(DEFAULT_SAMPLE_SIZE)
    var softIndexesLimit by property(DEFAULT_INDEXES_AMOUNT_SOFT_LIMIT)
}

class SettingsDelegate<T>(private val settingProp: KMutableProperty0<T>) {
    operator fun getValue(
        thisRef: Any?,
        property: KProperty<*>
    ): T {
        return settingProp.getter.call()
    }

    operator fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: T
    ) {
        settingProp.setter.call(value)
    }
}

fun <T> pluginSetting(cb: PluginSettings.() -> KMutableProperty0<T>): SettingsDelegate<T> {
    val settings by service<PluginSettingsStateComponent>()
    return SettingsDelegate(settings.state.cb())
}
