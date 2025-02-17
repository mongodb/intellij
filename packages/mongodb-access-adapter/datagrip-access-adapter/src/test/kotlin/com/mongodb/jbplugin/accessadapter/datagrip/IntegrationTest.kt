/**
 * Test extension that allows us to test with the IntelliJ environment
 * without spinning up the whole IDE. Also, sets up a MongoDB instance
 * that can be queried.
 */

package com.mongodb.jbplugin.accessadapter.datagrip

import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.database.dataSource.validation.DatabaseDriverValidator.createDownloaderTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.cleanApplicationState
import com.intellij.testFramework.common.initTestApplication
import com.intellij.util.ui.EDT
import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.DataGripMongoDbDriver
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasLimit
import com.mongodb.jbplugin.mql.components.HasRunCommand
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.*
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.lifecycle.Startables
import java.lang.reflect.Method
import java.nio.file.Files
import java.util.*

/**
 * Represents what version of MongoDB we support in the plugin.
 */
enum class MongoDbVersion(
    val versionString: String,
) {
    LATEST("7.0.9"),
}

/**
 * Annotation to be used in the test, at the class level.
 *
 * @see com.mongodb.jbplugin.accessadapter.datagrip.adapter.DataGripMongoDbDriverTest
 */
@ExtendWith(IntegrationTestExtension::class)
@Testcontainers(parallel = false)
annotation class IntegrationTest(
    val mongodb: MongoDbVersion = MongoDbVersion.LATEST,
    val sharded: Boolean = false,
)

/**
 * Extension implementation. Must not be used directly.
 */
internal class IntegrationTestExtension :
    BeforeAllCallback,
    BeforeEachCallback,
    InvocationInterceptor,
    AfterEachCallback,
    AfterAllCallback,
    ParameterResolver {
    private val namespace = ExtensionContext.Namespace.create(IntegrationTestExtension::class.java)
    private val containerKey = "CONTAINER"
    private val projectKey = "PROJECT"
    private val dataSourceKey = "DATASOURCE"
    private val driverKey = "DRIVER"
    private val versionKey = "VERSION"

    override fun beforeAll(context: ExtensionContext?) {
        initTestApplication()

        val annotation = context!!.requiredTestClass.getAnnotation(IntegrationTest::class.java)
        val container =
            MongoDBContainer("mongo:${annotation.mongodb.versionString}-jammy")
                .let {
                    if (annotation.sharded) {
                        it.withSharding()
                    } else {
                        it
                    }
                }

        Startables.deepStart(container).join()
        context.getStore(namespace).put(containerKey, container)
        context.getStore(namespace).put(versionKey, annotation.mongodb)

        val project =
            runBlocking(Dispatchers.EDT) {
                val testClassName = context.requiredTestClass.simpleName
                ProjectUtil.openOrCreateProject(
                    testClassName,
                    Files.createTempDirectory(testClassName)
                )!!
            }

        Disposer.register(ApplicationManager.getApplication(), project)
        context.getStore(namespace).put(projectKey, project)

        val dataSource = createDataSource(project, container, context)

        createDownloaderTask(dataSource, null).run(EmptyProgressIndicator())

        runBlocking(Dispatchers.EDT) {
            EDT.dispatchAllInvocationEvents()
        }

        forceConnectForTesting(project, dataSource, context)

        runBlocking(Dispatchers.EDT) {
            EDT.dispatchAllInvocationEvents()
        }
    }

    private fun createDataSource(
        project: Project,
        container: MongoDBContainer,
        context: ExtensionContext,
    ): LocalDataSource =
        runBlocking {
            val dataSourceManager = LocalDataSourceManager.byDataSource(
                project,
                LocalDataSource::class.java
            )!!
            val instance = DatabaseDriverManager.getInstance()
            val jdbcDriver = instance.getDriver("mongo")

            val dataSource =
                LocalDataSource().apply {
                    name = UUID.randomUUID().toString()
                    url = container.connectionString
                    isConfiguredByUrl = true
                    username = ""
                    passwordStorage = LocalDataSource.Storage.PERSIST
                    databaseDriver = jdbcDriver
                }

            context.getStore(namespace).put(dataSourceKey, dataSource)
            dataSourceManager.addDataSource(dataSource)
            dataSource
        }

    private fun forceConnectForTesting(
        project: Project,
        dataSource: LocalDataSource,
        context: ExtensionContext,
    ) {
        val driver = DataGripMongoDbDriver(project, dataSource)
        driver.forceConnectForTesting()
        context.getStore(namespace).put(driverKey, driver)
    }

    override fun beforeEach(context: ExtensionContext) {
        val driver = context.getStore(namespace).get(driverKey) as MongoDbDriver
        val query = Node(
            Unit,
            listOf(
                IsCommand(IsCommand.CommandType.RUN_COMMAND),
                HasRunCommand(
                    database = HasValueReference(
                        HasValueReference.Constant(Unit, "test", BsonString)
                    ),
                    commandName = HasValueReference(
                        HasValueReference.Constant(Unit, "dropDatabase", BsonString)
                    ),
                ),
                HasLimit(1)
            )
        )

        runBlocking {
            driver.runQuery(query, Unit::class)
        }
    }

    override fun interceptTestMethod(
        invocation: InvocationInterceptor.Invocation<Void>?,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        ApplicationManager.getApplication().invokeAndWait {
            invocation?.proceed()
        }
    }

    override fun afterEach(context: ExtensionContext) {
        ApplicationManager.getApplication().cleanApplicationState()
    }

    override fun afterAll(context: ExtensionContext?) {
        val project = context!!.getStore(namespace).get(projectKey) as Project
        val mongodb = context.getStore(namespace).get(containerKey) as MongoDBContainer
        val driver = context.getStore(namespace).get(driverKey) as DataGripMongoDbDriver

        ApplicationManager.getApplication().invokeAndWait({
            driver.closeConnectionForTesting()
            ProjectManager.getInstance().closeAndDispose(project)
        }, ModalityState.defaultModalityState())

        mongodb.close()
    }

    override fun supportsParameter(
        parameterContext: ParameterContext?,
        extensionContext: ExtensionContext?,
    ): Boolean =
        parameterContext?.parameter?.type == Project::class.java ||
            parameterContext?.parameter?.type == MongoDbDriver::class.java ||
            parameterContext?.parameter?.type == LocalDataSource::class.java ||
            parameterContext?.parameter?.type == MongoDbVersion::class.java

    override fun resolveParameter(
        parameterContext: ParameterContext?,
        extensionContext: ExtensionContext?,
    ): Any =
        when (parameterContext?.parameter?.type) {
            Project::class.java -> extensionContext!!.getStore(namespace).get(projectKey)
            MongoDbDriver::class.java -> extensionContext!!.getStore(namespace).get(driverKey)
            LocalDataSource::class.java -> extensionContext!!.getStore(namespace).get(dataSourceKey)
            MongoDbVersion::class.java -> extensionContext!!.getStore(namespace).get(versionKey)
            else -> TODO(
                "Parameter of type ${parameterContext?.parameter?.type?.canonicalName} is not supported."
            )
        }
}
