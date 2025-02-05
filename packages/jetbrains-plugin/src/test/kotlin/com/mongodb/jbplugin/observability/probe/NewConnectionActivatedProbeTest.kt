/**
 * Test for the activated probe. It's run with the Atlas CLI docker images and community images.
 */

package com.mongodb.jbplugin.observability.probe

import com.intellij.database.console.session.DatabaseSession
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.openapi.application.Application
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.fixtures.*
import com.mongodb.jbplugin.observability.TelemetryProperty
import com.mongodb.jbplugin.observability.TelemetryService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.argThat
import org.mockito.kotlin.verify

/**
 * Abstract class that implements the tests, it's not run.
 *
 * @see NewConnectionActivatedProbeTestForLocalEnvironment
 * @see NewConnectionActivatedProbeTestForAtlasCliEnvironment
 */
@IntegrationTest
internal abstract class NewConnectionActivatedProbeTest(
    private val isAtlas: Boolean,
    private val isLocalAtlas: Boolean,
    private val isLocalhost: Boolean,
    private val isEnterprise: Boolean,
    private val isGenuine: Boolean,
    private val version: String,
) {
    @Test
    fun `should get proper build information of the server`(
        application: Application,
        project: Project,
        serverUrl: MongoDbServerUrl,
    ) {
        val session = mock<DatabaseSession>()
        val telemetryService = mock<TelemetryService>()
        val logMessage = mockLogMessage()
        val connectionPoint = mock<DatabaseConnectionPoint>()

        application.withMockedService(telemetryService)
        application.withMockedService(logMessage)

        val dataSource = project.connectTo(serverUrl)
        val driver = project.createDriver(dataSource)

        `when`(session.project).thenReturn(project)
        `when`(session.connectionPoint).thenReturn(connectionPoint)
        `when`(connectionPoint.dataSource).thenReturn(dataSource)

        val probe = NewConnectionActivatedProbe()

        probe.connected(session)

        verify(telemetryService).sendEvent(
            argThat { event ->
                event.properties[TelemetryProperty.IS_ATLAS] == isAtlas &&
                    event.properties[TelemetryProperty.IS_LOCAL_ATLAS] == isLocalAtlas &&
                    event.properties[TelemetryProperty.IS_LOCALHOST] == isLocalhost &&
                    event.properties[TelemetryProperty.IS_ENTERPRISE] == isEnterprise &&
                    event.properties[TelemetryProperty.IS_GENUINE] == isGenuine &&
                    event.properties[TelemetryProperty.VERSION].toString().startsWith(version) &&
                    !event.properties.containsKey(TelemetryProperty.ATLAS_HOST)
            },
        )
    }
}

@RequiresMongoDbCluster(version = MongoDbVersion.V7_0)
internal class NewConnectionActivatedProbeTestForLocalEnvironment :
    NewConnectionActivatedProbeTest(
        isAtlas = false,
        isLocalAtlas = false,
        isLocalhost = true,
        isEnterprise = false,
        isGenuine = true,
        // not having an exact version here because then we'd have to keep it updated with every
        // mongodb release
        version = "7.0.",
    )

@RequiresMongoDbCluster(
    version = MongoDbVersion.V7_0,
    value = MongoDbTestingEnvironment.LOCAL_ATLAS
)
internal class NewConnectionActivatedProbeTestForAtlasCliEnvironment :
    NewConnectionActivatedProbeTest(
        isAtlas = false,
        isLocalAtlas = true,
        isLocalhost = true,
        isEnterprise = true,
        isGenuine = true,
        // not having an exact version here because then we'd have to keep it updated with every
        // mongodb release
        version = "7.0.",
    )
