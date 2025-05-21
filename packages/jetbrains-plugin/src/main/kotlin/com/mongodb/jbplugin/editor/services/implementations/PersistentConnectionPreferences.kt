/**
 * PersistentToolbarSettings contains declaration of PersistentToolbarSettings service and a helper that helps
 * in retrieving this service from Application
 */

package com.mongodb.jbplugin.editor.services.implementations

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.editor.services.ConnectionPreferences
import java.io.Serializable

// Setting this service as a PROJECT service ensures that our state is saved per Project
@Service(Service.Level.PROJECT)
@State(
    name = "com.mongodb.jbplugin.editor.ConnectionPreferences",
    storages = [
        Storage(
            // File used to persist these settings per project
            value = "MongodbConnectionPreferences.xml",
            // Setting roamingType to DEFAULT ensures that these settings are carried forward during IntelliJ updates
            // as well
            roamingType = RoamingType.DEFAULT
        )
    ]
)
internal class ConnectionPreferencesStateComponent :
    SimplePersistentStateComponent<PersistentConnectionPreferences>(PersistentConnectionPreferences())

internal class PersistentConnectionPreferences :
    BaseState(),
    Serializable,
    ConnectionPreferences {
    override var dataSourceId by string(null)
    override var database by string(ConnectionPreferences.UNINITIALIZED_DATABASE)
}

/**
 * Helper method to retrieve the PersistentToolbarSettings instance from Application
 *
 * @return
 */
fun Project.getConnectionPreferences(): ConnectionPreferences = getService(
    ConnectionPreferencesStateComponent::class.java
).state
