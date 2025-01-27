package com.mongodb.jbplugin.codeActions.impl.runQuery

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.mockDataSource
import com.mongodb.jbplugin.fixtures.parseJavaQuery
import com.mongodb.jbplugin.mql.Node
import kotlinx.coroutines.CoroutineScope
import org.assertj.swing.core.Robot
import org.assertj.swing.core.matcher.JLabelMatcher
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.exception.ComponentLookupException
import org.assertj.swing.fixture.FrameFixture
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.swing.JFrame

@IntegrationTest
class RunQueryModalTest {
    @Test
    fun `has a title and subtitle`(robot: Robot, project: Project, coroutineScope: CoroutineScope) {
        val dataSource = mockDataSource()

        val query = project.parseJavaQuery(
            code = """
            public Document find() {
                return this.collection.find(eq("_id", id)).first();
            }
        """
        )

        val (fixture, _) = render(robot, query, dataSource, coroutineScope)

        fixture.label(JLabelMatcher.withText("Specify query test values")).requireVisible()
        fixture.label(
            JLabelMatcher.withText(
                "These are values in your query that are defined at runtime. Please specify test values for them here."
            )
        ).requireVisible()
    }

    @Test
    fun `if the collection can be inferred it does not show the namespace selector`(
        robot: Robot,
        project: Project,
        coroutineScope: CoroutineScope
    ) {
        val dataSource = mockDataSource()

        val query = project.parseJavaQuery(
            code = """
            public Document find() {
                return this.collection.find(eq("_id", id)).first();
            }
        """
        )

        val (fixture, _) = render(robot, query, dataSource, coroutineScope)

        assertThrows<ComponentLookupException> { fixture.comboBox("DatabaseComboBox") }
        assertThrows<ComponentLookupException> { fixture.comboBox("CollectionComboBox") }
    }

    @Test
    fun `if the collection can not be inferred it does show the namespace selector`(
        robot: Robot,
        project: Project,
        coroutineScope: CoroutineScope
    ) {
        val dataSource = mockDataSource()

        val query = project.parseJavaQuery(
            code = """
            public Document find() {
                return this.collection.find(eq("_id", id)).first();
            }
        """,
            namespace = null
        )

        val (fixture, _) = render(robot, query, dataSource, coroutineScope)

        fixture.comboBox("DatabaseComboBox").requireVisible()
        fixture.comboBox("CollectionComboBox").requireVisible()
    }

    private fun render(robot: Robot, query: Node<PsiElement>, dataSource: LocalDataSource, coroutineScope: CoroutineScope): Pair<FrameFixture, RunQueryModal> {
        return GuiActionRunner.execute<Pair<FrameFixture, RunQueryModal>> {
            val frame = JFrame()

            val modal = RunQueryModal(query, dataSource, coroutineScope)
            frame.add(modal.createCenterPanel())
            frame.isVisible = true

            FrameFixture(robot, frame) to modal
        }
    }
}
