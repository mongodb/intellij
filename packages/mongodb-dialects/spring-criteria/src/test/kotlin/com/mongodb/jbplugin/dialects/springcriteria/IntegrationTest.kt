/**
 * Class that contains the JUnit5 extension to run tests
 * that use the IntelliJ Java parser.
 */

package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.AcceptedLanguageLevelsSettings
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.childrenOfType
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.mongodb.assertions.Assertions.assertNotNull
import com.mongodb.jbplugin.mql.Component
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasAccumulatedFields
import com.mongodb.jbplugin.mql.components.HasAddedFields
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasProjections
import com.mongodb.jbplugin.mql.components.HasSorts
import com.mongodb.jbplugin.mql.components.HasUpdates
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.junit.jupiter.api.fail
import java.lang.reflect.Method
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

@Retention(AnnotationRetention.RUNTIME)
@Test
annotation class ParsingTest(
    val fileName: String,
    @Language("java") val value: String,
)

@Retention(AnnotationRetention.RUNTIME)
annotation class AdditionalFile(
    val fileName: String,
    val value: String,
)

/**
 * Annotation to be used in the test, at the class level.
 *
 * @see com.mongodb.jbplugin.accessadapter.datagrip.adapter.DataGripMongoDbDriverTest
 */
@ExtendWith(IntegrationTestExtension::class)
@TestDataPath("${'$'}CONTENT_ROOT/testData")
annotation class IntegrationTest

/**
 * Extension implementation. Must not be used directly.
 */
internal class IntegrationTestExtension :
    BeforeAllCallback,
    AfterAllCallback,
    BeforeEachCallback,
    AfterEachCallback,
    InvocationInterceptor,
    ParameterResolver {
    private val namespace = ExtensionContext.Namespace.create(IntegrationTestExtension::class.java)
    private val testFixtureKey = "TESTFIXTURE"
    private val testPathKey = "TESTPATH"

    override fun beforeAll(context: ExtensionContext) {
        TestApplicationManager.getInstance()
        val projectDescriptor = MongoDbProjectDescriptor(LanguageLevel.JDK_21)

        val projectFixture =
            IdeaTestFixtureFactory
                .getFixtureFactory()
                .createLightFixtureBuilder(projectDescriptor, context.requiredTestClass.simpleName)
                .fixture

        val testFixture =
            IdeaTestFixtureFactory
                .getFixtureFactory()
                .createCodeInsightFixture(
                    projectFixture,
                )

        context.getStore(namespace).put(testFixtureKey, testFixture)
        testFixture.setUp()

        val projectExt = LanguageLevelProjectExtension.getInstance(projectFixture.project)
        projectExt.languageLevel = LanguageLevel.JDK_21
        IndexingTestUtil.waitUntilIndexesAreReady(projectFixture.project)

        PsiTestUtil.addSourceRoot(testFixture.module, testFixture.project.guessProjectDir()!!)
        val tmpRootDir = testFixture.tempDirFixture.getFile(".")!!
        PsiTestUtil.addSourceRoot(testFixture.module, tmpRootDir)
        context.getStore(namespace).put(testPathKey, tmpRootDir.path)
    }

    override fun beforeEach(context: ExtensionContext) {
        val fixture = context.getStore(namespace).get(testFixtureKey) as CodeInsightTestFixture
        val modulePath = context.getStore(namespace).get(testPathKey).toString()

        // Configure an editor with the source code from @ParsingTest
        ApplicationManager.getApplication().invokeAndWait {
            context.requiredTestMethod.getAnnotationsByType(AdditionalFile::class.java).forEach {
                fixture.addFileToProject(it.fileName, it.value)
            }

            val parsingTest: ParsingTest? = context.requiredTestMethod.getAnnotation(
                ParsingTest::class.java
            )
            val withFile: AdditionalFile? = context.requiredTestMethod.getAnnotation(
                AdditionalFile::class.java
            )

            val cfgFileName = parsingTest?.fileName ?: withFile?.fileName ?: return@invokeAndWait
            val cfgContents = parsingTest?.value ?: withFile?.value ?: return@invokeAndWait

            val fileName = Path(
                modulePath,
                "src",
                "main",
                "java",
                cfgFileName
            ).absolutePathString()

            fixture.configureByText(
                fileName,
                cfgContents
            )
        }
    }

    override fun interceptTestMethod(
        invocation: InvocationInterceptor.Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        val fixture = extensionContext.getStore(
            namespace
        ).get(testFixtureKey) as CodeInsightTestFixture
        val dumbService = DumbService.getInstance(fixture.project)

        // Run only when the code has been analysed
        runBlocking {
            suspendCancellableCoroutine { callback ->
                dumbService.runWhenSmart {
                    runCatching {
                        callback.resume(invocation.proceed())
                    }.onFailure {
                        callback.resumeWithException(it)
                    }
                }
            }
        }
    }

    override fun afterEach(context: ExtensionContext) {
        val testFixture = context.getStore(namespace).get(testFixtureKey) as CodeInsightTestFixture
        val withFile: AdditionalFile = context.requiredTestMethod.getAnnotation(
            AdditionalFile::class.java
        ) ?: return

        val file = testFixture.findFileInTempDir(withFile.fileName)
        WriteCommandAction.runWriteCommandAction(testFixture.project) {
            file.delete(this)
        }
    }

    override fun afterAll(context: ExtensionContext) {
        val testFixture = context.getStore(namespace).get(testFixtureKey) as CodeInsightTestFixture

        ApplicationManager.getApplication().invokeAndWait {
            testFixture.tearDown()
        }
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Boolean =
        parameterContext.parameter.type == Project::class.java ||
            parameterContext.parameter.type == CodeInsightTestFixture::class.java ||
            parameterContext.parameter.type == PsiFile::class.java ||
            parameterContext.parameter.type == JavaPsiFacade::class.java

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Any {
        val fixture = extensionContext.getStore(
            namespace
        ).get(testFixtureKey) as CodeInsightTestFixture

        return when (parameterContext.parameter.type) {
            Project::class.java -> fixture.project
            CodeInsightTestFixture::class.java -> fixture
            PsiFile::class.java -> fixture.file
            JavaPsiFacade::class.java -> JavaPsiFacade.getInstance(fixture.project)
            else -> TODO(
                "Parameter of type ${parameterContext.parameter.type.canonicalName} is not supported."
            )
        }
    }
}

fun PsiFile.getClassByName(name: String): PsiClass =
    childrenOfType<PsiClass>().first {
        it.name == name
    }

fun PsiFile.getQueryAtMethod(
    className: String,
    methodName: String,
): PsiExpression {
    val actualClass = getClassByName(className)
    val method = actualClass.allMethods.first { it.name == methodName }
    val returnExpr = PsiUtil.findReturnStatements(method).last()
    return returnExpr.returnValue!!
}

fun PsiFile.caret(): PsiElement = PsiTreeUtil.findChildrenOfType(
    this,
    PsiLiteralExpression::class.java
)
    .first {
        it.textMatches(""""|"""")
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

fun Node<PsiElement>.assert(
    command: IsCommand.CommandType,
    assertions: Node<PsiElement>.() -> Unit = {
    }
) {
    val cmd = component<IsCommand>()
    assertNotNull(cmd)

    assertEquals(command, cmd!!.type)
    this.assertions()
}

fun Node<PsiElement>.stageN(
    n: Int,
    name: Name? = null,
    assertions: Node<PsiElement>.() -> Unit = {
    }
) {
    val stages = component<HasAggregation<PsiElement>>()?.children ?: emptyList()
    assertNotEquals(0, stages.size) {
        "Expected HasAggregation to have at-least $n stages"
    }

    val stage = stages.getOrNull(n)
    assertNotEquals(null, stage) {
        "Expected a stage at index $n, found null"
    }

    if (name != null) {
        val stageName = stage!!.component<Named>()
        assertNotEquals(null, stageName) {
            "Expected a stage with name $name but null found at index $n."
        }
        assertEquals(name, stageName!!.name) {
            "Expected a stage with name $name but ${stageName.name} found at index $n."
        }
    }

    stage!!.assertions()
}

fun Node<PsiElement>.filterN(
    n: Int,
    name: Name? = null,
    assertions: Node<PsiElement>.() -> Unit = {
    }
) {
    val filters = component<HasFilter<PsiElement>>()
    assertNotNull(filters)

    val filter = filters!!.children[n]

    if (name != null) {
        val qname = filter.component<Named>()
        assertNotEquals(null, qname) {
            "Expected a named operation with name $name but null found."
        }
        assertEquals(name, qname?.name) {
            "Expected a named operation with name $name but $qname found."
        }
    }

    filter.assertions()
}

fun Node<PsiElement>.projectionN(
    n: Int,
    name: Name? = null,
    assertions: Node<PsiElement>.() -> Unit = {
    }
) {
    val projections = component<HasProjections<PsiElement>>()
    assertNotNull(projections)

    val projection = projections!!.children[n]

    if (name != null) {
        val qname = projection.component<Named>()
        assertNotEquals(null, qname) {
            "Expected a named operation with name $name but null found."
        }
        assertEquals(name, qname?.name) {
            "Expected a named operation with name $name but $qname found."
        }
    }

    projection.assertions()
}

fun Node<PsiElement>.sortN(
    n: Int,
    name: Name? = null,
    stageIndex: Int? = null,
    assertions: Node<PsiElement>.() -> Unit = {
    },
) {
    val sorts = component<HasSorts<PsiElement>>()
    assertNotNull(sorts)

    val sort = sorts!!.children[n]

    if (name != null) {
        val qname = sort.component<Named>()
        assertNotEquals(null, qname) {
            "StageIndex: $stageIndex, SortCriteriaIndex: $n :: Expected a named operation with name $name but null found."
        }
        assertEquals(name, qname?.name) {
            "StageIndex: $stageIndex, SortCriteriaIndex: $n :: Expected a named operation with name $name but $qname found."
        }
    }

    sort.assertions()
}

fun Node<PsiElement>.addedFieldN(
    n: Int,
    name: Name? = null,
    stageIndex: Int? = null,
    assertions: Node<PsiElement>.() -> Unit = {
    },
) {
    val addedFields = component<HasAddedFields<PsiElement>>()
    assertNotNull(addedFields)

    val addedField = addedFields!!.children[n]

    if (name != null) {
        val qname = addedField.component<Named>()
        assertNotEquals(null, qname) {
            "StageIndex: $stageIndex, AddedFieldIndex: $n :: Expected a named operation with name $name but null found."
        }
        assertEquals(name, qname?.name) {
            "StageIndex: $stageIndex, AddedFieldIndex: $n :: Expected a named operation with name $name but $qname found."
        }
    }

    addedField.assertions()
}

fun Node<PsiElement>.accumulatedFieldN(
    n: Int,
    name: Name? = null,
    stageIndex: Int? = null,
    assertions: Node<PsiElement>.() -> Unit = {
    },
) {
    val accumulatedFields = component<HasAccumulatedFields<PsiElement>>()
    assertNotNull(accumulatedFields)

    val accumulatedField = accumulatedFields!!.children[n]

    if (name != null) {
        val qname = accumulatedField.component<Named>()
        assertNotEquals(null, qname) {
            "StageIndex: $stageIndex, AccumulatedFieldIndex: $n :: Expected a named operation with name $name but null found."
        }
        assertEquals(name, qname?.name) {
            "StageIndex: $stageIndex, AccumulatedFieldIndex: $n :: Expected a named operation with name $name but $qname found."
        }
    }

    accumulatedField.assertions()
}

fun Node<PsiElement>.updateN(
    n: Int,
    name: Name? = null,
    assertions: Node<PsiElement>.() -> Unit = {
    }
) {
    val updates = component<HasUpdates<PsiElement>>()
    assertNotNull(updates)

    val update = updates!!.children[n]

    if (name != null) {
        val qname = update.component<Named>()
        assertNotEquals(null, qname) {
            "Expected a named operation with name $name but null found."
        }
        assertEquals(name, qname?.name) {
            "Expected a named operation with name $name but $qname found."
        }
    }

    update.assertions()
}

inline fun <reified T : Component> Node<PsiElement>.component(
    assertions: T.() -> Unit = {
    }
) {
    val maybeComponent = component<T>()
    assertNotNull(maybeComponent)

    maybeComponent!!.assertions()
}

inline fun <reified T : HasCollectionReference.CollectionReference<PsiElement>> Node<PsiElement>.collection(
    assertions: T.() -> Unit
) {
    val ref = component<HasCollectionReference<PsiElement>>()
    assertNotEquals(null, ref) {
        "Could not find a HasCollectionReference component in the query."
    }

    if (ref!!.reference is T) {
        (ref.reference as T).assertions()
    } else {
        fail(
            "Collection reference was not of type ${T::class.java.canonicalName} but ${ref.reference.javaClass.canonicalName}"
        )
    }
}

inline fun <reified T : HasFieldReference.FieldReference<PsiElement>> Node<PsiElement>.field(
    assertions: T.() -> Unit
) {
    val ref = component<HasFieldReference<PsiElement>>()
    assertNotEquals(null, ref) {
        "Could not find a HasFieldReference component in the query."
    }

    if (ref!!.reference is T) {
        (ref.reference as T).assertions()
    } else {
        fail(
            "Field reference was not of type ${T::class.java.canonicalName} but ${ref::class.java.canonicalName}"
        )
    }
}

inline fun <reified T : HasValueReference.ValueReference<PsiElement>> Node<PsiElement>.value(
    assertions: T.() -> Unit
) {
    val ref = component<HasValueReference<PsiElement>>()
    assertNotEquals(null, ref) {
        "Could not find a HasValueReference component in the query."
    }

    if (ref!!.reference is T) {
        (ref.reference as T).assertions()
    } else {
        fail(
            "Value reference was not of type ${T::class.java.canonicalName} but ${ref::class.java.canonicalName}"
        )
    }
}
