/**
 * Extension for tests that depend on an Application.
 */

package com.mongodb.jbplugin.fixtures

import com.google.gson.Gson
import com.intellij.database.Dbms
import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.DatabaseDriver
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.localDataSource
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.remote.jdbc.RemoteConnection
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.Disposer
import com.intellij.pom.java.AcceptedLanguageLevelsSettings
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.cleanApplicationState
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.dialects.javadriver.glossary.findAllChildrenOfType
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.editor.MongoDbVirtualFileDataSourceProvider
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.observability.LogMessage
import com.mongodb.jbplugin.observability.LogMessageBuilder
import com.mongodb.jbplugin.observability.RuntimeInformation
import com.mongodb.jbplugin.observability.RuntimeInformationService
import com.mongodb.jbplugin.observability.TelemetryService
import com.mongodb.jbplugin.settings.PluginSettings
import com.mongodb.jbplugin.settings.PluginSettingsStateComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import org.assertj.swing.core.BasicRobot
import org.assertj.swing.core.Robot
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.*
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.milliseconds

enum class DefaultSetup(
    val imports: (String) -> String,
    val fields: (String) -> String,
    val constructor: (String) -> String,
    val dialect: Dialect<PsiElement, *>
) {
    JAVA_DRIVER(
        imports = { className ->
            """
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import java.util.*;
import java.time.*;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Accumulators.*;
import static com.mongodb.client.model.Projections.*;
import static com.mongodb.client.model.Aggregates.*;    
"""
        },
        fields = { className ->
            """
private final MongoClient client;    
"""
        },
        constructor = { className ->
"""
public $className(MongoClient client) {
    this.client = client;
} 
"""
        },
        dialect = JavaDriverDialect,
    ),
    SPRING_DATA(
        imports = { className ->
            """
package test.$className.isolated;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.aggregation.Fields;
import org.springframework.data.domain.Sort;
import java.util.List;
import java.util.Arrays;
import static org.springframework.data.mongodb.core.aggregation.Fields.*;
import static org.springframework.data.mongodb.core.query.Query.*;
import static org.springframework.data.mongodb.core.query.Criteria.*;
 
@Document
record Book() {}
"""
        },
        fields = { className ->
            """
private final MongoTemplate template;   
"""
        },
        constructor = { className ->
            """
public $className(MongoTemplate template) {
    this.template = template;
}
"""
        },
        dialect = SpringCriteriaDialect
    )
}

@Retention(AnnotationRetention.RUNTIME)
@Test
annotation class ParsingTest(
    @Language("java") val value: String,
    val setup: DefaultSetup = DefaultSetup.JAVA_DRIVER
)

/**
 * Annotation to be used within the test. It provides, as a parameter of a test,
 * either a mocked setup Application or Project.
 *
 * @see com.mongodb.jbplugin.observability.LogMessageTest
 */
@ExtendWith(IntegrationTestExtension::class)
@kotlinx.coroutines.ExperimentalCoroutinesApi
annotation class IntegrationTest(
    val newProjectForTest: Boolean = false
)

/**
 * Extension class, should not be used directly.
 */
private class IntegrationTestExtension :
    BeforeAllCallback,
    AfterAllCallback,
    BeforeTestExecutionCallback,
    AfterTestExecutionCallback,
    ParameterResolver {
    private lateinit var testFixture: CodeInsightTestFixture
    private lateinit var application: ApplicationEx
    private lateinit var settings: PluginSettings
    private lateinit var project: Project
    private lateinit var testScope: TestScope
    private lateinit var robot: Robot

    private fun ExtensionContext.requiresProjectForEachTest(): Boolean {
        return this.testClass.getOrNull()?.annotations?.firstNotNullOfOrNull {
            it as? IntegrationTest
        }?.newProjectForTest ==
            true ||
            this.parent.getOrNull()?.requiresProjectForEachTest() == true
    }

    private fun setupProject(context: ExtensionContext) {
        val projectDescriptor = MongoDbProjectDescriptor(LanguageLevel.JDK_21)

        val projectFixture =
            IdeaTestFixtureFactory
                .getFixtureFactory()
                .createLightFixtureBuilder(projectDescriptor, context.requiredTestClass.simpleName)
                .fixture

        testFixture =
            IdeaTestFixtureFactory
                .getFixtureFactory()
                .createCodeInsightFixture(
                    projectFixture,
                )

        testFixture.setUp()

        project = testFixture.project
        application = ApplicationManager.getApplication() as ApplicationEx
    }

    private fun tearDownProject() {
        application.invokeAndWait {
            runCatching {
                val fileEditorManager = FileEditorManager.getInstance(testFixture.project)
                fileEditorManager.openFiles.forEach {
                    fileEditorManager.closeFile(it)
                }
                fileEditorManager.allEditors.forEach {
                    Disposer.dispose(it)
                }

                testFixture.tearDown()
            }
        }

        application.cleanApplicationState()
    }

    override fun beforeAll(context: ExtensionContext) {
        val defaultTimeout = 50.milliseconds.inWholeMilliseconds.toInt()

        robot = BasicRobot.robotWithCurrentAwtHierarchyWithoutScreenLock()
        robot.settings().idleTimeout(defaultTimeout)
        robot.settings().timeoutToBeVisible(defaultTimeout)
        robot.settings().timeoutToFindPopup(defaultTimeout)
        robot.settings().timeoutToFindSubMenu(defaultTimeout)
        robot.settings().delayBetweenEvents(defaultTimeout)
        robot.settings().eventPostingDelay(defaultTimeout)

        if (!context.requiresProjectForEachTest()) {
            setupProject(context)
        }
    }

    override fun beforeTestExecution(context: ExtensionContext) {
        if (context.requiresProjectForEachTest()) {
            setupProject(context)
        }

        val tmpRootDir = testFixture.tempDirFixture.getFile(".")!!

        PsiTestUtil.addSourceRoot(testFixture.module, testFixture.project.guessProjectDir()!!)
        PsiTestUtil.addSourceRoot(testFixture.module, tmpRootDir)

        val settingComponent by service<PluginSettingsStateComponent>()
        settings = settingComponent.state
        settings.isTelemetryEnabled = true
        testScope = TestScope()

        val parsingTest = context.requiredTestMethod.getAnnotation(ParsingTest::class.java)
        if (parsingTest == null) {
            return
        }

        application.withMockedService(mock(TelemetryService::class.java))
        application.invokeAndWait {
            val className = context.displayName.replace(Regex("[\\s().#,]"), "_")
            val tmpRootDir = testFixture.tempDirFixture.getFile(".")!!
            val fileName = Path(
                tmpRootDir.path,
                "src",
                "main",
                "java",
                "$className.java"
            ).absolutePathString()

            testFixture.configureByText(
                fileName,
                """
${parsingTest.setup.imports(className)}

public class $className {
    ${parsingTest.setup.fields(className)}
    ${parsingTest.setup.constructor(className)}
    ${parsingTest.value}
}
              """
            )
        }
    }

    override fun afterTestExecution(context: ExtensionContext) {
        if (context.requiresProjectForEachTest()) {
            tearDownProject()
        }

        runInEdtAndWait {
            runCatching {
                robot.cleanUp()
            }
        }
    }

    override fun afterAll(context: ExtensionContext) {
        if (!context.requiresProjectForEachTest()) {
            tearDownProject()
        }

        application.cleanApplicationState()
    }

    override fun supportsParameter(
        parameterContext: ParameterContext?,
        extensionContext: ExtensionContext?,
    ): Boolean =
        parameterContext?.parameter?.type?.run {
            equals(Application::class.java) ||
                equals(Project::class.java) ||
                equals(PluginSettings::class.java) ||
                equals(TestScope::class.java) ||
                equals(CoroutineScope::class.java) ||
                equals(Robot::class.java) ||
                equals(CodeInsightTestFixture::class.java) ||
                equals(PsiFile::class.java) ||
                equals(JavaPsiFacade::class.java)
        } == true

    override fun resolveParameter(
        parameterContext: ParameterContext?,
        extensionContext: ExtensionContext?,
    ): Any =
        when (parameterContext?.parameter?.type) {
            Application::class.java -> application
            Project::class.java -> project
            PluginSettings::class.java -> settings
            TestScope::class.java -> testScope
            CoroutineScope::class.java -> testScope
            CodeInsightTestFixture::class.java -> testFixture
            PsiFile::class.java -> testFixture.file
            JavaPsiFacade::class.java -> JavaPsiFacade.getInstance(project)
            Robot::class.java -> robot
            else -> TODO()
        }
}

/**
 * Convenience function in application or project for tests. It mocks the implementation of a single service
 * with whatever implementation is passed as a parameter. For example:
 *
 * ```kt
 * application.withMockedService(mockRuntimeInformationService())
 * project.withMockedService(mockRuntimeInformationService())
 * ```
 *
 * @param serviceImpl
 * @return itself so it can be chained
 */
inline fun <reified S : ComponentManager, reified T : Any> S.withMockedService(serviceImpl: T): S {
    replaceService(T::class.java, serviceImpl, this)
    return this
}

/**
 * Generates a mock runtime information service, useful for testing. If you need
 * to create your own. You'll likely will build first an information service and
 * then inject it into a mock project, something like this:
 *
 * ```kt
 * val myInfoService = mockRuntimeInformationService(userId = "hey")
 * project.withMockedService(myInfoService)
 * ```
 *
 * @param userId
 * @param osName
 * @param arch
 * @param jvmVendor
 * @param jvmVersion
 * @param buildVersion
 * @param applicationName
 * @return A new mocked RuntimeInformationService
 */
internal fun mockRuntimeInformationService(
    userId: String = "123456",
    osName: String = "Winux OSX",
    arch: String = "x128",
    jvmVendor: String = "Obelisk",
    jvmVersion: String = "42",
    buildVersion: String = "2024.2",
    applicationName: String = "Cool IDE",
) = mock<RuntimeInformationService>().also { service ->
    `when`(service.get()).thenReturn(
        RuntimeInformation(
            userId = userId,
            osName = osName,
            arch = arch,
            jvmVendor = jvmVendor,
            jvmVersion = jvmVersion,
            buildVersion = buildVersion,
            applicationName = applicationName,
        ),
    )
}

/**
 * Generates a mock log message service.
 * You'll likely will build first a log message service and
 * then inject it into a mock project, something like this:
 *
 * ```kt
 * val myLogMessage = mockLogMessage()
 * project.withMockedService(myLogMessage)
 * ```
 *
 * @return A new mocked LogMessage
 */
internal fun mockLogMessage() =
    mock<LogMessage>().also { logMessage ->
        `when`(logMessage.message(any())).then { message ->
            LogMessageBuilder(Gson(), message.arguments[0].toString())
        }
    }

/**
 * Returns a mocked data source configured for MongoDB.
 *
 * @param url
 * @return
 */
internal fun mockDataSource(url: MongoDbServerUrl = MongoDbServerUrl("mongodb://localhost:27017")) =
    mock<LocalDataSource>().also { dataSource ->
        val driver = mock<DatabaseDriver>()
        `when`(driver.id).thenReturn("mongo")
        `when`(dataSource.dataSource).thenReturn(dataSource)
        `when`(dataSource.url).thenReturn(url.value)
        `when`(dataSource.databaseDriver).thenReturn(driver)
        `when`(dataSource.dbms).thenReturn(Dbms.MONGO)
        val testClass = Thread.currentThread().stackTrace[2].className
        `when`(dataSource.name).thenReturn(testClass + "_" + UUID.randomUUID().toString())
        `when`(dataSource.uniqueId).thenReturn(testClass + "_" + UUID.randomUUID().toString())
    }

/**
 * Returns a mocked connection for the provided dataSource.
 *
 * @param dataSource Either a mock or a real data source.
 * @return
 */
internal fun mockDatabaseConnection(dataSource: LocalDataSource) =
    mock<DatabaseConnection>().also { connection ->
        val connectionPoint = mock<DatabaseConnectionPoint>()
        val remoteConnection = mock<RemoteConnection>()

        `when`(remoteConnection.isClosed).thenReturn(false)
        `when`(remoteConnection.isValid(any())).thenReturn(true)
        `when`(connection.connectionPoint).thenReturn(connectionPoint)
        `when`(connectionPoint.dataSource).thenReturn(dataSource)
        `when`(connection.remoteConnection).thenReturn(remoteConnection)
    }

internal fun Project.mockReadModelProvider(): DataGripBasedReadModelProvider {
    val readModelProvider = Mockito.mock(DataGripBasedReadModelProvider::class.java)
    withMockedService(readModelProvider)
    return readModelProvider
}

internal fun Project.parseJavaQuery(
    @Language(
        "java"
    ) code: String,
    setup: DefaultSetup = DefaultSetup.JAVA_DRIVER
): Node<PsiElement> {
    val packageName = "parseJavaQuery.inline_${UUID.randomUUID().toString().replace(
        "-",
        "_"
    )}.isolated"

    val document = """
${setup.imports(packageName)}

public class Repository {
    ${setup.fields("Repository")}
    ${setup.constructor("Repository")}
    $code
}
    """.trimIndent()

    return ApplicationManager.getApplication().runReadAction<Node<PsiElement>> {
        val psiFile = PsiFileFactory.getInstance(this).createFileFromText(
            "src/main/java/Repository.java",
            JavaLanguage.INSTANCE,
            document,
        ) as PsiJavaFile

        val queryMethod = psiFile.classes.last().methods.last()
        val candidateQueryExpr = queryMethod.findAllChildrenOfType(
            PsiMethodCallExpression::class.java
        )
        val queryExpr = candidateQueryExpr.first {
            setup.dialect.parser.isCandidateForQuery(it)
        }

        setup.dialect.parser.parse(queryExpr)
    }
}

private class MongoDbProjectDescriptor(
    val languageLevel: LanguageLevel
) : DefaultLightProjectDescriptor() {
    override fun setUpProject(
        project: Project,
        handler: SetupHandler
    ) {
        if (languageLevel.isPreview || languageLevel == LanguageLevel.JDK_X) {
            AcceptedLanguageLevelsSettings.allowLevel(project, languageLevel)
        }

        withRepositoryLibrary("org.mongodb:mongodb-driver-sync:5.1.0")
        withRepositoryLibrary("org.springframework.data:spring-data-mongodb:4.3.2")

        super.setUpProject(project, handler)
    }

    override fun getSdk(): Sdk {
        return IdeaTestUtil.getMockJdk(languageLevel.toJavaVersion())
    }

    override fun configureModule(
        module: Module,
        model: ModifiableRootModel,
        contentEntry: ContentEntry
    ) {
        model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel =
            languageLevel

        addJetBrainsAnnotations(model)
        super.configureModule(module, model, contentEntry)
    }
}

fun CodeInsightTestFixture.setupConnection(): Pair<LocalDataSource, DataGripBasedReadModelProvider> {
    val dbPsiFacade = mock<DbPsiFacade>()
    val dbDataSource = mock<DbDataSource>()
    val dataSource = mockDataSource()
    val application = ApplicationManager.getApplication()
    val realConnectionManager = DatabaseConnectionManager.getInstance()
    val dbConnectionManager =
        mock<DatabaseConnectionManager>().also { cm ->
            `when`(cm.build(any(), any())).thenAnswer {
                realConnectionManager.build(
                    it.arguments[0] as Project,
                    it.arguments[1] as DatabaseConnectionPoint
                )
            }
        }
    val connection = mockDatabaseConnection(dataSource)
    val readModelProvider = mock<DataGripBasedReadModelProvider>()

    `when`(dbDataSource.localDataSource).thenReturn(dataSource)
    `when`(dbPsiFacade.findDataSource(any())).thenReturn(dbDataSource)
    `when`(dbConnectionManager.activeConnections).thenReturn(listOf(connection))

    file.virtualFile.putUserData(
        MongoDbVirtualFileDataSourceProvider.Keys.attachedDataSource,
        dataSource,
    )

    application.withMockedService(dbConnectionManager)
    project.withMockedService(readModelProvider)
    project.withMockedService(dbPsiFacade)

    return Pair(dataSource, readModelProvider)
}

/**
 * Set the current database name into the file.
 *
 * @param name
 */
fun CodeInsightTestFixture.specifyDatabase(name: String) {
    file.virtualFile.putUserData(
        MongoDbVirtualFileDataSourceProvider.Keys.attachedDatabase,
        name
    )
}

/**
 * Sets the current dialect into the file.
 *
 * @param dialect
 */
fun CodeInsightTestFixture.specifyDialect(dialect: Dialect<PsiElement, Project>) {
    file.virtualFile.putUserData(
        MongoDbVirtualFileDataSourceProvider.Keys.attachedDialect,
        dialect
    )
}
