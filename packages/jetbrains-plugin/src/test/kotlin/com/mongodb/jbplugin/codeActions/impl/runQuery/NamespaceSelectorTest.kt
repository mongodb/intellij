package com.mongodb.jbplugin.codeActions.impl.runQuery

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.eventually
import com.mongodb.jbplugin.fixtures.mockDataSource
import com.mongodb.jbplugin.fixtures.mockReadModelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.assertj.swing.core.Robot
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.fixture.FrameFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import javax.swing.JFrame

@IntegrationTest
class NamespaceSelectorTest {
    @Test
    fun `renders both comboboxes with their default states`(
        robot: Robot,
        project: Project,
        coroutineScope: CoroutineScope
    ) = runTest {
        val dataSource = mockDataSource()
        val (fixture, _) = render(robot, project, dataSource, coroutineScope)

        eventually {
            fixture.comboBox("DatabaseComboBox").requireDisabled()
            fixture.comboBox("CollectionComboBox").requireDisabled()
        }
    }

    @Test
    fun `loads existing databases in cluster`(
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

        val (fixture, _) = render(robot, project, dataSource, coroutineScope)

        eventually {
            val databaseComboBox = fixture.comboBox("DatabaseComboBox")

            databaseComboBox.requireEnabled()
                .requireItemCount(1)
                .requireSelection("db1")
        }
    }

    @Test
    fun `loads existing collections of the selected database in cluster`(
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
                    ListCollections.Collection("coll2", "collection"),
                )
            )
        )

        val (fixture, selector) = render(robot, project, dataSource, coroutineScope)

        eventually {
            val databaseComboBox = fixture.comboBox("DatabaseComboBox")
            val collectionComboBox = fixture.comboBox("CollectionComboBox")

            databaseComboBox
                .requireEnabled()
                .requireItemCount(1)
                .requireSelection("db1")

            collectionComboBox.requireEnabled()
                .requireItemCount(2)
                .requireSelection("coll1")
        }

        assertEquals("db1", selector.selectedDatabase)
        assertEquals("coll1", selector.selectedCollection)
    }

    private fun render(robot: Robot, project: Project, dataSource: LocalDataSource, coroutineScope: CoroutineScope): Pair<FrameFixture, NamespaceSelector> {
        return GuiActionRunner.execute<Pair<FrameFixture, NamespaceSelector>> {
            val selector = NamespaceSelector(project, dataSource, coroutineScope)
            val frame = JFrame()
            frame.add(selector.databaseComboBox)
            frame.add(selector.collectionComboBox)

            selector.databaseComboBox.isVisible = true
            selector.collectionComboBox.isVisible = true
            frame.isVisible = true
            frame.pack()

            FrameFixture(robot, frame) to selector
        }
    }
}
