/**
 * This file defines all the properties and events that can be sent to
 * Segment. New properties (or fields) will be added in the TelemetryProperty
 * enum class. New events will be added inside the TelemetryEvent sealed class.
 */

package com.mongodb.jbplugin.observability

import com.google.common.base.Objects
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import org.intellij.lang.annotations.Pattern
import org.jetbrains.annotations.Nls

/**
 * Represents a field in Segment. New fields will be added here, where the
 * publicName is how it will look like in Segment.
 *
 * @property publicName Name of the field in Segment.
 */
enum class TelemetryProperty(
    @Pattern("[a-z_]+")
    val publicName: String,
) {
    PLUGIN_VERSION("plugin_version"),
    IS_ATLAS("is_atlas"),
    ATLAS_HOST("atlas_hostname"),
    IS_LOCAL_ATLAS("is_local_atlas"),
    IS_LOCALHOST("is_localhost"),
    IS_ENTERPRISE("is_enterprise"),
    IS_GENUINE("is_genuine"),
    NON_GENUINE_SERVER_NAME("non_genuine_server_name"),
    SERVER_OS_FAMILY("server_os_family"),
    VERSION("version"),
    ERROR_CODE("error_code"),
    ERROR_NAME("error_name"),
    DIALECT("dialect"),
    AUTOCOMPLETE_TYPE("ac_type"),
    AUTOCOMPLETE_COUNT("ac_count"),
    COMMAND("command"),
    CONSOLE("console"),
    TRIGGER_LOCATION("trigger_location"),
    SIGNAL_TYPE("signal_type"),
    INSPECTION_TYPE("inspection_type"),
    ERROR_FIELD_TYPE("error_field_type"),
    ACTUAL_ERROR_TYPE("actual_field_type"),
    ERROR_STATUS("error_status")
}

enum class SignalType(
    @Pattern("[a-z_]+")
    val publicName: String
) {
    MISSING_INDEX("missing_index")
}

/**
 * Represents an event that will be sent to Segment. Essentially, all
 * events will be sent as Track events to Segment but PluginActivated,
 * that will be sent as an identify event. This logic is in the
 * TelemetryService.
 *
 * @property name Name of the event
 * @property properties Map of fields sent to Segment.
 * @see TelemetryService
 */
sealed class TelemetryEvent(
    @Nls(capitalization = Nls.Capitalization.Title)
    val name: String,
    val properties: Map<TelemetryProperty, Any>,
) {
    /**
     * Represents the event that is emitted when the plugin is started.
     */
    data object PluginActivated : TelemetryEvent(
        name = "PluginActivated",
        properties = emptyMap(),
    )
    override fun equals(other: Any?): Boolean =
        (other as? TelemetryEvent)?.let {
            name == it.name && properties == it.properties
        } == true

    override fun hashCode(): Int = Objects.hashCode(name, properties)

    override fun toString(): String = "$name($properties)"

    /**
     * Represents the event that is emitted when the plugin connects
     * to a cluster.
     *
     * @param isAtlas
     * @param isLocalhost
     * @param isEnterprise
     * @param isGenuine
     * @param atlasHost
     * @param nonGenuineServerName
     * @param serverOsFamily
     * @param version
     * @param isLocalAtlas
     */
    class NewConnection(
        isAtlas: Boolean,
        isLocalAtlas: Boolean,
        isLocalhost: Boolean,
        isEnterprise: Boolean,
        isGenuine: Boolean,
        atlasHost: String?,
        nonGenuineServerName: String?,
        serverOsFamily: String?,
        version: String?,
    ) : TelemetryEvent(
        name = "NewConnection",
        properties =
        mapOf(
            TelemetryProperty.IS_ATLAS to isAtlas,
            TelemetryProperty.IS_LOCAL_ATLAS to isLocalAtlas,
            TelemetryProperty.IS_LOCALHOST to isLocalhost,
            TelemetryProperty.IS_ENTERPRISE to isEnterprise,
            TelemetryProperty.IS_GENUINE to isGenuine,
            TelemetryProperty.NON_GENUINE_SERVER_NAME to (nonGenuineServerName ?: ""),
            TelemetryProperty.SERVER_OS_FAMILY to (serverOsFamily ?: ""),
            TelemetryProperty.VERSION to (version ?: ""),
        ) + atlasHostProperties(atlasHost)
    ) {
        companion object {
            fun atlasHostProperties(atlasHost: String?): Map<TelemetryProperty, String> {
                atlasHost ?: return emptyMap()
                return mapOf(TelemetryProperty.ATLAS_HOST to atlasHost)
            }
        }
    }

    /**
     * Represents the event that is emitted when the there is an error
     * during the connection to a MongoDB Cluster.
     *
     * @param isAtlas
     * @param isLocalhost
     * @param isEnterprise
     * @param isGenuine
     * @param nonGenuineServerName
     * @param serverOsFamily
     * @param version
     * @param isLocalAtlas
     * @param errorCode
     * @param errorName
     */
    class ConnectionError(
        errorCode: String,
        errorName: String,
        isAtlas: Boolean?,
        isLocalAtlas: Boolean?,
        isLocalhost: Boolean?,
        isEnterprise: Boolean?,
        isGenuine: Boolean?,
        nonGenuineServerName: String?,
        serverOsFamily: String?,
        version: String?,
    ) : TelemetryEvent(
        name = "ConnectionError",
        properties =
        mapOf(
            TelemetryProperty.IS_ATLAS to (isAtlas ?: ""),
            TelemetryProperty.IS_LOCAL_ATLAS to (isLocalAtlas ?: ""),
            TelemetryProperty.IS_LOCALHOST to (isLocalhost ?: ""),
            TelemetryProperty.IS_ENTERPRISE to (isEnterprise ?: ""),
            TelemetryProperty.IS_GENUINE to (isGenuine ?: ""),
            TelemetryProperty.NON_GENUINE_SERVER_NAME to (nonGenuineServerName ?: ""),
            TelemetryProperty.SERVER_OS_FAMILY to (serverOsFamily ?: ""),
            TelemetryProperty.VERSION to (version ?: ""),
            TelemetryProperty.ERROR_CODE to errorCode,
            TelemetryProperty.ERROR_NAME to errorName,
        ),
    )

    /**
     * Aggregated count of events of the same autocomplete type, sent to Segment
     * every hour if not empty.
     *
     * @param dialect
     * @param autocompleteType
     * @param count
     */
    class AutocompleteGroupEvent(
        dialect: HasSourceDialect.DialectName,
        autocompleteType: String,
        command: String,
        count: Int,
    ) : TelemetryEvent(
        name = "AutocompleteSelected",
        properties =
        mapOf(
            TelemetryProperty.DIALECT to dialect.name.lowercase(),
            TelemetryProperty.AUTOCOMPLETE_TYPE to autocompleteType,
            TelemetryProperty.COMMAND to command,
            TelemetryProperty.AUTOCOMPLETE_COUNT to count,
        ),
    )

    class QueryRunEvent(
        dialect: HasSourceDialect.DialectName,
        console: Console,
        triggerLocation: TriggerLocation,
    ) : TelemetryEvent(
        name = "QueryRun",
        properties =
        mapOf(
            TelemetryProperty.DIALECT to dialect.name.lowercase(),
            TelemetryProperty.CONSOLE to console.name.lowercase(),
            TelemetryProperty.TRIGGER_LOCATION to triggerLocation.name.lowercase(),
        )
    ) {
        enum class Console {
            NEW,
            EXISTING
        }

        enum class TriggerLocation {
            GUTTER,
            CONTEXT_MENU,
            SIDE_PANEL
        }
    }

    class CreateIndexIntentionEvent(
        dialect: HasSourceDialect.DialectName
    ) : TelemetryEvent(
        name = "CreateIndex",
        properties =
        mapOf(
            TelemetryProperty.DIALECT to dialect.name.lowercase(),
            TelemetryProperty.SIGNAL_TYPE to SignalType.MISSING_INDEX.publicName,
        )
    )

    class InspectionStatusChangeEvent(
        dialect: HasSourceDialect.DialectName,
        inspectionType: InspectionType,
        inspectionStatus: InspectionStatus,
        actualFieldType: String?,
        expectedFieldType: String?,
    ) : TelemetryEvent(
        name = "Inspection",
        properties =
        mapOf(
            TelemetryProperty.DIALECT to dialect.name.lowercase(),
            TelemetryProperty.INSPECTION_TYPE to inspectionType.name.lowercase(),
            TelemetryProperty.ERROR_STATUS to inspectionStatus.name.lowercase(),
            TelemetryProperty.ERROR_FIELD_TYPE to (actualFieldType ?: "<none>"),
            TelemetryProperty.ACTUAL_ERROR_TYPE to (expectedFieldType ?: "<none>"),
        )
    ) {
        enum class InspectionType {
            FIELD_DOES_NOT_EXIST,
            TYPE_MISMATCH,
            INVALID_PROJECTION,
            QUERY_NOT_COVERED_BY_INDEX,
            NO_DATABASE_INFERRED,
            NO_COLLECTION_SPECIFIED,
            COLLECTION_DOES_NOT_EXIST,
            DATABASE_DOES_NOT_EXIST
        }

        enum class InspectionStatus {
            ACTIVE,
            RESOLVED
        }
    }
}
