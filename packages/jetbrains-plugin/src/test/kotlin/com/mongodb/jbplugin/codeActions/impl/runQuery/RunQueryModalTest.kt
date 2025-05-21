package com.mongodb.jbplugin.codeActions.impl.runQuery

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.eventually
import com.mongodb.jbplugin.fixtures.mockDataSource
import com.mongodb.jbplugin.fixtures.mockReadModelProvider
import com.mongodb.jbplugin.fixtures.parseJavaQuery
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonEnum
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.Node
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.assertj.swing.core.Robot
import org.assertj.swing.core.matcher.JLabelMatcher
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.exception.ComponentLookupException
import org.assertj.swing.fixture.FrameFixture
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.swing.JFrame
import kotlin.reflect.KClass

@IntegrationTest
class RunQueryModalTest {
    @Test
    fun `has a title and subtitle`(robot: Robot, project: Project, coroutineScope: CoroutineScope) = runTest {
        val dataSource = mockDataSource()

        val query = project.parseJavaQuery(
            """
            public Document find() {
                return this.client.getDatabase("prod").getCollection("books").find(eq("_id", 1234)).first();
            }
        """
        )

        val (fixture, _) = render(robot, query, dataSource, coroutineScope)

        eventually {
            fixture.label(JLabelMatcher.withText("Specify query test values")).requireVisible()
            fixture.label(
                JLabelMatcher.withText(
                    "These are values in your query that are defined at runtime. Please specify test values for them here."
                )
            ).requireVisible()
        }
    }

    @Test
    fun `if the collection can be inferred it does not show the namespace selector`(
        robot: Robot,
        project: Project,
        coroutineScope: CoroutineScope
    ) = runTest {
        val dataSource = mockDataSource()

        val query = project.parseJavaQuery(
            """
            public Document find() {
                return this.client.getDatabase("prod").getCollection("books").find(eq("_id", 1234)).first();
            }
        """
        )

        val (fixture, _) = render(robot, query, dataSource, coroutineScope)

        assertThrows<ComponentLookupException> { fixture.comboBox("DatabaseComboBox") }
        assertThrows<ComponentLookupException> { fixture.comboBox("CollectionComboBox") }
    }

    @Test
    fun `if the collection can not be inferred it does show the namespace selector and gathers the values for the query context`(
        robot: Robot,
        project: Project,
        coroutineScope: TestScope
    ) = runTest {
        val dataSource = mockDataSource()
        val readModel = project.mockReadModelProvider()

        whenever(readModel.slice(dataSource, ListDatabases.Slice)).thenReturn(
            ListDatabases(
                listOf(
                    ListDatabases.Database("db1")
                )
            )
        )

        whenever(readModel.slice(dataSource, ListCollections.Slice("db1"))).thenReturn(
            ListCollections(
                listOf(
                    ListCollections.Collection("coll1", "collection"),
                )
            )
        )

        val query = project.parseJavaQuery(
            code = """
            public Document find(String db, String coll) {
                return this.client.getDatabase(db).getCollection(coll).find(eq("_id", 1234)).first();
            }
        """
        )

        val (fixture, modal) = render(robot, query, dataSource, coroutineScope.backgroundScope)

        eventually {
            val queryContext = modal.buildQueryContextFromModal()

            fixture.comboBox("DatabaseComboBox").requireVisible()
            fixture.comboBox("CollectionComboBox").requireVisible()

            val dbInput = queryContext.expansions.getValue("database")
            val collInput = queryContext.expansions.getValue("collection")

            assertEquals("db1", dbInput.defaultValue)
            assertEquals(BsonString, dbInput.type)

            assertEquals("coll1", collInput.defaultValue)
            assertEquals(BsonString, collInput.type)
        }
    }

    @Test
    fun `should show runtime values as inputs and build the context from there`(
        robot: Robot,
        project: Project,
        coroutineScope: CoroutineScope
    ) = runTest {
        val dataSource = mockDataSource()

        val query = project.parseJavaQuery(
            """
            public Document find(String id) {
                return this.client.getDatabase("prod").getCollection("books").find(eq("_id", id)).first();
            }
        """
        )

        val (fixture, modal) = render(robot, query, dataSource, coroutineScope)

        eventually {
            fixture.textBox("_id").requireVisible().focus().setText("myId")
        }

        val queryContext = modal.buildQueryContextFromModal()
        val idInput = queryContext.expansions.getValue("_id")

        assertEquals("myId", idInput.defaultValue)
        assertEquals(BsonString, idInput.type)
    }

    @ParameterizedTest
    @MethodSource("javaTypeToFormatHint")
    fun `should show hints for the specified types`(
        javaType: KClass<*>,
        expectedHint: String,
        robot: Robot,
        project: Project,
        coroutineScope: CoroutineScope,
    ) = runTest {
        val dataSource = mockDataSource()

        val query = project.parseJavaQuery(
            """
            public Document find(${javaType.simpleName} id) {
                return this.client.getDatabase("prod").getCollection("books").find(eq("_id", id)).first();
            }
        """
        )

        val (fixture, _) = render(robot, query, dataSource, coroutineScope)

        eventually {
            fixture.label(JLabelMatcher.withText(expectedHint)).requireVisible()
        }
    }

    @ParameterizedTest
    @MethodSource("complexTypeSample")
    fun `should show the warning message for complex types`(
        javaType: KClass<*>,
        robot: Robot,
        project: Project,
        coroutineScope: CoroutineScope
    ) = runTest {
        val dataSource = mockDataSource()

        val query = project.parseJavaQuery(
            """
            public Document find(${javaType.qualifiedName} id) {
                return this.client.getDatabase("prod").getCollection("books").find(eq("_id", id)).first();
            }
        """
        )

        val (fixture, _) = render(robot, query, dataSource, coroutineScope)

        eventually {
            fixture.label(
                JLabelMatcher.withText(
                    "Unable to specify. Please fill it after generating the query."
                )
            ).requireVisible()
        }
    }

    @Test
    fun `should show boolean runtime values as checkboxes`(
        robot: Robot,
        project: Project,
        coroutineScope: CoroutineScope
    ) = runTest {
        val dataSource = mockDataSource()

        val query = project.parseJavaQuery(
            """
            public Document find(boolean id) {
                return this.client.getDatabase("prod").getCollection("books").find(eq("_id", id)).first();
            }
        """
        )

        val (fixture, modal) = render(robot, query, dataSource, coroutineScope)

        eventually {
            fixture.checkBox("_id")
                .requireVisible()
                .focus()
                .check(true)

            val queryContext = modal.buildQueryContextFromModal()
            val idInput = queryContext.expansions.getValue("_id")

            assertEquals(true, idInput.defaultValue)
            assertEquals(BsonBoolean, idInput.type)
        }
    }

    @Test
    fun `should show enums as combo boxes`(
        robot: Robot,
        project: Project,
        coroutineScope: CoroutineScope
    ) = runTest {
        val dataSource = mockDataSource()

        val query = project.parseJavaQuery(
            """
            enum JavaScriptBoolean { TRUE, FALSE, UNDEFINED, NULL }

            public Document find(JavaScriptBoolean el) {
                return this.client.getDatabase("prod").getCollection("books").find(eq("bool", el)).first();
            }
        """
        )

        val (fixture, modal) = render(robot, query, dataSource, coroutineScope)

        eventually {
            fixture.comboBox("bool")
                .requireVisible()
                .requireItemCount(4)
                .requireSelection("TRUE")

            val queryContext = modal.buildQueryContextFromModal()
            val boolInput = queryContext.expansions.getValue("bool")

            assertEquals("TRUE", boolInput.defaultValue)
            assertEquals(
                BsonEnum(setOf("TRUE", "FALSE", "UNDEFINED", "NULL"), "JavaScriptBoolean"),
                boolInput.type
            )
        }
    }

    @Test
    fun `formats the sample date with timezone info`(
        robot: Robot,
        project: Project,
        coroutineScope: CoroutineScope
    ) = runTest {
        assertTrue(
            RunQueryModal.sampleDateTime().endsWith("Z")
        )
    }

    companion object {
        @JvmStatic
        fun javaTypeToFormatHint(): Array<Array<Any>> = arrayOf(
            arrayOf(ObjectId::class, "Hexadecimal ObjectId representation"),
            arrayOf(Date::class, "ISO 8601 Date: yyyy-MM-dd'T'HH:mm:ss"),
            arrayOf(LocalDate::class, "ISO 8601 Date: yyyy-MM-dd'T'HH:mm:ss"),
            arrayOf(LocalDateTime::class, "ISO 8601 Date: yyyy-MM-dd'T'HH:mm:ss"),
            arrayOf(Instant::class, "ISO 8601 Date: yyyy-MM-dd'T'HH:mm:ss"),
        )

        @JvmStatic
        fun complexTypeSample(): Array<Any> = arrayOf(
            List::class,
            Array::class,
            Map::class,
            StringBuffer::class,
        )
    }

    private fun render(robot: Robot, query: Node<PsiElement>, dataSource: LocalDataSource, coroutineScope: CoroutineScope): Pair<FrameFixture, RunQueryModal> {
        return GuiActionRunner.execute<Pair<FrameFixture, RunQueryModal>> {
            val frame = JFrame()
            val modal = RunQueryModal(query, dataSource, coroutineScope)
            frame.add(modal.createCenterPanel())
            frame.isVisible = true

            frame.pack()
            FrameFixture(robot, frame) to modal
        }
    }
}
