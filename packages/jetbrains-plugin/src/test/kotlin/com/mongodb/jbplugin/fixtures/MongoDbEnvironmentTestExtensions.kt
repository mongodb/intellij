/**
 * Class with fixtures to test components that depend on MongoDB. Use the @RequiresMongoDbCluster annotation
 * in your test class name to enable.
 */

package com.mongodb.jbplugin.fixtures

import com.intellij.openapi.project.Project
import com.mongodb.client.MongoClients
import com.mongodb.jbplugin.accessadapter.ConnectionString
import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import kotlinx.coroutines.CoroutineScope
import org.bson.Document
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.mockito.Mockito.`when`
import org.mockito.kotlin.mock
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.lifecycle.Startable
import java.io.File

/**
 * Test environment.
 */
enum class MongoDbTestingEnvironment {
    LOCAL,
    LOCAL_ATLAS,
}

/**
 * Available testing versions.
 *
 * @property value
 */
enum class MongoDbVersion(
    val value: String,
) {
    V7_0("7.0"),
    LATEST("7.0"),
}

/**
 * Annotation that enables the MongoDB integration.
 *
 * @property value
 * @property version
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@ExtendWith(MongoDbEnvironmentTestExtensions::class)
annotation class RequiresMongoDbCluster(
    val value: MongoDbTestingEnvironment = MongoDbTestingEnvironment.LOCAL,
    val version: MongoDbVersion = MongoDbVersion.LATEST,
)

/**
 * Data class that contains all the information relevant to connect to the server.
 *
 * @property value
 */
data class MongoDbServerUrl(
    val value: String,
)

/**
 * Extension class, do not use directly.
 */
class MongoDbEnvironmentTestExtensions :
    BeforeAllCallback,
    AfterAllCallback,
    ParameterResolver {
    private var container: Startable? = null
    private var serverUrl: MongoDbServerUrl? = null

    override fun beforeAll(context: ExtensionContext) {
        val testClass = context.requiredTestClass
        val requiresCluster = testClass.getAnnotation(RequiresMongoDbCluster::class.java) ?: return

        when (requiresCluster.value) {
            MongoDbTestingEnvironment.LOCAL -> {
                container = MongoDBContainer("mongo:${requiresCluster.version.value}")
                (container as MongoDBContainer).start()
                serverUrl = MongoDbServerUrl((container as MongoDBContainer).replicaSetUrl)
            }
            MongoDbTestingEnvironment.LOCAL_ATLAS -> {
                container =
                    DockerComposeContainer(File("src/test/resources/local-atlas.yml"))
                        .withExposedService("mongod", 27_017)
                (container as DockerComposeContainer<*>).start()
                val port = (container as DockerComposeContainer<*>).getServicePort("mongod", 27_017)
                serverUrl =
                    MongoDbServerUrl(
                        "mongodb://localhost:$port/?directConnection=true",
                    )

                val client = MongoClients.create(serverUrl!!.value)
                client
                    .getDatabase("admin")
                    .getCollection("atlascli")
                    .insertOne(Document("managedClusterType", "atlasCliLocalDevCluster"))
                client.close()
            }
        }
    }

    override fun afterAll(context: ExtensionContext) {
        container?.stop()
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Boolean = parameterContext.parameter.type == MongoDbServerUrl::class.java

    override fun resolveParameter(
        parameterContext: ParameterContext?,
        extensionContext: ExtensionContext?,
    ): Any = serverUrl!!
}

suspend fun Project.withMockedUnconnectedMongoDbConnection(
    url: MongoDbServerUrl,
    coroutineScope: CoroutineScope,
): Project {
    val driver = mock<MongoDbDriver>()
    `when`(driver.connected).thenReturn(false)
    `when`(
        driver.connectionString()
    ).thenReturn(ConnectionString(listOf(url.value)))

    val readModelProvider =
        DataGripBasedReadModelProvider(
            this,
            coroutineScope = coroutineScope,
        ).apply {
            driverFactory = { _, _ -> driver }
        }

    return withMockedService(readModelProvider)
}
