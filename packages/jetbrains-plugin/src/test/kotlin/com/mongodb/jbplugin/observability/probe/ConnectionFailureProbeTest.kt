package com.mongodb.jbplugin.observability.probe

import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.application.Application
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.MongoDbServerUrl
import com.mongodb.jbplugin.fixtures.mockLogMessage
import com.mongodb.jbplugin.fixtures.withMockedService
import com.mongodb.jbplugin.fixtures.withMockedUnconnectedMongoDbConnection
import com.mongodb.jbplugin.observability.TelemetryProperty
import com.mongodb.jbplugin.observability.TelemetryService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.argThat
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import java.sql.SQLException

@IntegrationTest
class ConnectionFailureProbeTest {
    @Test
    fun `should infer the relevant info from the connection string`(
        application: Application,
        project: Project,
    ) = runBlocking {
        val telemetryService = mock<TelemetryService>()
        val logMessage = mockLogMessage()
        val connectionPoint = mock<DatabaseConnectionPoint>()
        val dataSource = mock<LocalDataSource>()

        `when`(connectionPoint.dataSource).thenReturn(dataSource)
        application.withMockedService(telemetryService)
        application.withMockedService(logMessage)

        project.withMockedUnconnectedMongoDbConnection(
            MongoDbServerUrl("mongodb://localhost"),
            this
        )
        val probe = ConnectionFailureProbe(TestScope())

        probe.connectionFailed(project, connectionPoint, Throwable())

        verify(telemetryService, timeout(1000)).sendEvent(
            argThat { event ->
                event.properties[TelemetryProperty.IS_ATLAS] == false &&
                    event.properties[TelemetryProperty.IS_LOCAL_ATLAS] == false &&
                    event.properties[TelemetryProperty.IS_LOCALHOST] == true &&
                    event.properties[TelemetryProperty.IS_ENTERPRISE] == false &&
                    event.properties[TelemetryProperty.IS_GENUINE] == true &&
                    event.properties[TelemetryProperty.VERSION] == "8.0.0" &&
                    event.properties[TelemetryProperty.ERROR_CODE] == "<unk>" &&
                    event.properties[TelemetryProperty.ERROR_NAME] == "Throwable"
            },
        )
    }

    @Test
    fun `should infer the relevant connection error from the exception message`(
        application: Application,
        project: Project,
    ) = runBlocking {
        val telemetryService = mock<TelemetryService>()
        val logMessage = mockLogMessage()
        val connectionPoint = mock<DatabaseConnectionPoint>()
        val dataSource = mock<LocalDataSource>()

        `when`(connectionPoint.dataSource).thenReturn(dataSource)
        application.withMockedService(telemetryService)
        application.withMockedService(logMessage)

        project.withMockedUnconnectedMongoDbConnection(
            MongoDbServerUrl("mongodb://localhost"),
            this
        )
        val probe = ConnectionFailureProbe(TestScope())

        val innerException =
            Exception(
                "com.mongodb.MongoCommandException: Command failed with error 18 (AuthenticationFailed):",
            )
        val remoteWrapper =
            com.intellij.execution.rmi.RemoteObject.ForeignException(
                "Error in the driver",
                "com.mongodb.MongoCommandException",
            )
        remoteWrapper.initCause(innerException)
        val sqlException = SQLException(remoteWrapper)

        probe.connectionFailed(project, connectionPoint, sqlException)

        verify(telemetryService, timeout(1000)).sendEvent(
            argThat { event ->
                event.properties[TelemetryProperty.IS_ATLAS] == false &&
                    event.properties[TelemetryProperty.ERROR_CODE] == "18" &&
                    event.properties[TelemetryProperty.ERROR_NAME] ==
                    "com.mongodb.MongoCommandException"
            },
        )
    }
}
