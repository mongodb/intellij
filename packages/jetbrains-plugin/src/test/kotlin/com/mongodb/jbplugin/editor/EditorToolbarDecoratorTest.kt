package com.mongodb.jbplugin.editor

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.editor.models.ToolbarModel
import com.mongodb.jbplugin.editor.models.getToolbarModel
import com.mongodb.jbplugin.editor.services.MdbPluginDisposable
import com.mongodb.jbplugin.editor.services.implementations.MdbDataSourceService
import com.mongodb.jbplugin.editor.services.implementations.MdbEditorService
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.eventually
import com.mongodb.jbplugin.fixtures.withMockedService
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.Mockito.spy
import org.mockito.Mockito.`when`
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@IntegrationTest(newProjectForTest = true)
@Disabled("Will be eventually removed when the toolbar is dropped.")
class EditorToolbarDecoratorTest {
    @Test
    fun `when execute is triggered initialises toolbar for every execution`(
        testScope: TestScope,
        project: Project
    ) = runTest {
        val decorator = spy(EditorToolbarDecorator(testScope))
        decorator.execute(project)
        runCurrent()
        val toolbarOne = decorator.getToolbarForTests()
        assertTrue(toolbarOne is MdbJavaEditorToolbar)

        decorator.execute(project)
        runCurrent()
        val toolbarTwo = decorator.getToolbarForTests()
        assertTrue(toolbarOne is MdbJavaEditorToolbar)
        assertTrue(toolbarOne != toolbarTwo)
    }

    @Test
    fun `when execute is triggered sets up project subscriptions with disposable service for every execution`(
        testScope: TestScope,
        project: Project
    ) = runTest {
        val pluginDisposable = mock<MdbPluginDisposable>()
        project.withMockedService(pluginDisposable)

        val decorator = spy(EditorToolbarDecorator(testScope))
        decorator.execute(project)
        runCurrent()
        verify(decorator, times(1)).setupSubscriptionsForProject(project)

        decorator.execute(project)
        runCurrent()
        verify(decorator, times(2)).setupSubscriptionsForProject(project)
    }

    @Test
    fun `when execute is triggered toggles toolbar using ToolbarModel`(
        testScope: TestScope,
        project: Project
    ) = runTest {
        val decorator = spy(EditorToolbarDecorator(testScope))
        val toolbarModel = mock<ToolbarModel>()
        project.withMockedService(toolbarModel)
        decorator.execute(project)
        runCurrent()

        verify(toolbarModel, times(1)).projectExecuted(
            decorator.getToolbarForTests()!!,
        )
    }

    @Test
    fun `when selectionChanged is triggered toggles toolbar using ToolbarModel`(
        testScope: TestScope,
        project: Project
    ) = runTest {
        val decorator = spy(EditorToolbarDecorator(testScope))
        val toolbarModel = mock<ToolbarModel>()
        project.withMockedService(toolbarModel)
        decorator.execute(project)

        val changeEvent = mock<FileEditorManagerEvent>()
        decorator.selectionChanged(changeEvent)
        runCurrent()

        // First from execute but with false as applyReadAction and second from modificationCountChanged
        verify(toolbarModel, times(1)).selectionChanged(
            decorator.getToolbarForTests()!!,
        )
    }

    @Test
    fun `when modificationCountChanged is triggered toggles toolbar using ToolbarModel`(
        testScope: TestScope,
        project: Project
    ) = runTest {
        val decorator = spy(EditorToolbarDecorator(testScope))
        val toolbarModel = mock<ToolbarModel>()
        project.withMockedService(toolbarModel)
        decorator.execute(project)

        decorator.modificationCountChanged()
        runCurrent()

        // First from execute but with false as applyReadAction and second from modificationCountChanged
        verify(toolbarModel, times(1)).modificationCountChanged(
            decorator.getToolbarForTests()!!,
        )
    }

    @Test
    fun `when dataSourceAdded is triggered updates ToolbarModel with the added DataSource`(
        testScope: TestScope,
        project: Project
    ) = runTest {
        val decorator = EditorToolbarDecorator(testScope)
        val dataSourceService = mock<MdbDataSourceService>()
        project.withMockedService(dataSourceService)

        // initialise the toolbar model
        project.getToolbarModel().setupEventsSubscription()

        // Mocks for our assertions
        val dataSource = mock<LocalDataSource>()
        val dataSourceManager = mock<LocalDataSourceManager>()

        // Defining an early behaviour of not having anything
        `when`(dataSourceService.listMongoDbDataSources()).thenReturn(emptyList())
        decorator.execute(project)
        runCurrent()

        val toolbarState = project.getToolbarModel().toolbarState.value
        assertFalse(
            toolbarState.dataSources.contains(dataSource)
        )

        `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
        decorator.dataSourceAdded(dataSourceManager, dataSource)
        runCurrent()

        eventually {
            val newToolbarState = project.getToolbarModel().toolbarState.value
            assertFalse(toolbarState == newToolbarState)
            assertTrue(
                newToolbarState.dataSources.contains(dataSource)
            )
        }
    }

    @Test
    fun `when dataSourceAdded is triggered updates ToolbarModel with the added DataSource, while preserving the current selection`(
        testScope: TestScope,
        project: Project
    ) = runTest {
        val decorator = EditorToolbarDecorator(testScope)
        val dataSourceService = mock<MdbDataSourceService>()
        project.withMockedService(dataSourceService)

        // initialise the toolbar model
        project.getToolbarModel().setupEventsSubscription()

        // Mocks for our assertions
        val existingDataSource = mock<LocalDataSource>()
        val existingDataSources = listOf(existingDataSource)
        val newDataSource = mock<LocalDataSource>()
        val dataSourceManager = mock<LocalDataSourceManager>()

        // Defining an early behaviour of having just one data source
        `when`(dataSourceService.listMongoDbDataSources()).thenReturn(existingDataSources)
        decorator.execute(project)
        runCurrent()

        val toolbarState = project.getToolbarModel().toolbarState.value
        assertTrue(toolbarState.dataSources.contains(existingDataSource))
        assertEquals(
            toolbarState.selectedDataSource?.uniqueId,
            existingDataSource.uniqueId
        )

        `when`(
            dataSourceService.listMongoDbDataSources()
        ).thenReturn(listOf(existingDataSource, newDataSource))
        decorator.dataSourceAdded(dataSourceManager, newDataSource)
        runCurrent()

        eventually {
            val newToolbarState = project.getToolbarModel().toolbarState.value
            assertFalse(toolbarState == newToolbarState)
            assertTrue(toolbarState.dataSources.contains(newDataSource))
            assertEquals(
                toolbarState.selectedDataSource?.uniqueId,
                existingDataSource.uniqueId
            )
        }
    }

    @Test
    fun `when dataSourceChanged is triggered updates ToolbarModel with the changed DataSource`(
        testScope: TestScope,
        project: Project
    ) = runTest {
        val decorator = EditorToolbarDecorator(testScope)
        val dataSourceService = mock<MdbDataSourceService>()
        project.withMockedService(dataSourceService)

        // initialise the toolbar model
        project.getToolbarModel().setupEventsSubscription()

        // Mocks for our assertions
        // Both instances are for same DataSource
        val dataSourceInstanceOne = mock<LocalDataSource>()
        `when`(dataSourceInstanceOne.uniqueId).thenReturn("1FOO")
        val dataSourceInstanceTwo = mock<LocalDataSource>()
        `when`(dataSourceInstanceTwo.uniqueId).thenReturn("1FOO")

        val dataSourceManager = mock<LocalDataSourceManager>()

        // Defining an early behaviour having an old data source
        `when`(
            dataSourceService.listMongoDbDataSources()
        ).thenReturn(listOf(dataSourceInstanceOne))
        decorator.execute(project)
        runCurrent()

        val toolbarState = project.getToolbarModel().toolbarState.value
        assertTrue(toolbarState.dataSources.contains(dataSourceInstanceOne))

        `when`(
            dataSourceService.listMongoDbDataSources()
        ).thenReturn(listOf(dataSourceInstanceTwo))
        decorator.dataSourceChanged(dataSourceManager, dataSourceInstanceTwo)
        runCurrent()

        eventually {
            val newToolbarState = project.getToolbarModel().toolbarState.value
            assertTrue(toolbarState != newToolbarState)
            assertTrue(toolbarState.dataSources.contains(dataSourceInstanceTwo))
        }
    }

    @Test
    fun `when dataSourceChanged is triggered updates ToolbarModel with the changed DataSource and preserves the selection, if the selection itself is the changed DataSource`(
        testScope: TestScope,
        project: Project
    ) = runTest {
        val decorator = EditorToolbarDecorator(testScope)
        val dataSourceService = mock<MdbDataSourceService>()
        project.withMockedService(dataSourceService)

        // initialise ToolbarModel
        project.getToolbarModel().setupEventsSubscription()

        // Mocks for our assertions
        // Both instances are for same DataSource
        val dataSourceInstanceOne = mock<LocalDataSource>()
        `when`(dataSourceInstanceOne.uniqueId).thenReturn("1FOO")
        `when`(dataSourceInstanceOne.name).thenReturn("Instance One")

        val dataSourceInstanceTwo = mock<LocalDataSource>()
        `when`(dataSourceInstanceTwo.uniqueId).thenReturn("1FOO")
        `when`(dataSourceInstanceTwo.name).thenReturn("Instance Two")

        val dataSourceManager = mock<LocalDataSourceManager>()

        // Defining an early behaviour of having an old instance
        `when`(
            dataSourceService.listMongoDbDataSources()
        ).thenReturn(listOf(dataSourceInstanceOne))
        decorator.execute(project)
        project.getToolbarModel().selectDataSource(dataSourceInstanceOne)
        runCurrent()

        eventually {
            val toolbarState = project.getToolbarModel().toolbarState.value
            assertTrue(toolbarState.dataSources.contains(dataSourceInstanceOne))
            assertEquals(
                toolbarState.selectedDataSource?.uniqueId,
                dataSourceInstanceOne.uniqueId
            )
        }

        `when`(
            dataSourceService.listMongoDbDataSources()
        ).thenReturn(listOf(dataSourceInstanceTwo))
        decorator.dataSourceChanged(dataSourceManager, dataSourceInstanceTwo)
        val newToolbarState = project.getToolbarModel().toolbarState.value

        eventually {
            assertTrue(newToolbarState.dataSources.contains(dataSourceInstanceTwo))
            assertEquals(
                newToolbarState.selectedDataSource?.uniqueId,
                dataSourceInstanceTwo.uniqueId
            )
        }
    }

    @Test
    fun `when dataSourceRemoved is triggered updates ToolbarModel with the removed DataSource`(
        testScope: TestScope,
        project: Project
    ) = runTest {
        val decorator = EditorToolbarDecorator(testScope)
        val dataSourceService = mock<MdbDataSourceService>()
        val editorService = mock<MdbEditorService>()
        project.withMockedService(editorService)
        project.withMockedService(dataSourceService)

        project.getToolbarModel().setupEventsSubscription()

        // Mocks for our assertions
        val dataSource = mock<LocalDataSource>()
        val dataSourceManager = mock<LocalDataSourceManager>()

        `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
        decorator.execute(project)
        runCurrent()

        eventually {
            val toolbarState = project.getToolbarModel().toolbarState.value
            assertTrue(toolbarState.dataSources.contains(dataSource))
        }

        `when`(dataSourceService.listMongoDbDataSources()).thenReturn(emptyList())
        decorator.dataSourceRemoved(dataSourceManager, dataSource)
        runCurrent()

        eventually {
            val toolbarState = project.getToolbarModel().toolbarState.value
            assertFalse(toolbarState.dataSources.contains(dataSource))
        }
    }

    @Test
    fun `when dataSourceRemoved is triggered updates ToolbarModel with the removed DataSource and also remove the current selection, if the selection is the removed DataSource`(
        testScope: TestScope,
        project: Project
    ) = runTest {
        val decorator = EditorToolbarDecorator(testScope)
        val dataSourceService = mock<MdbDataSourceService>()
        val editorService = mock<MdbEditorService>()
        project.withMockedService(editorService)
        project.withMockedService(dataSourceService)

        project.getToolbarModel().setupEventsSubscription()

        // Mocks for our assertions
        val dataSource = mock<LocalDataSource>()
        val existingDataSources = listOf(dataSource)
        val dataSourceManager = mock<LocalDataSourceManager>()

        `when`(dataSourceService.listMongoDbDataSources()).thenReturn(existingDataSources)
        decorator.execute(project)
        project.getToolbarModel().selectDataSource(dataSource)
        runCurrent()

        eventually {
            val toolbarState = project.getToolbarModel().toolbarState.value
            assertTrue(toolbarState.dataSources.contains(dataSource))
            assertEquals(
                toolbarState.selectedDataSource?.uniqueId,
                dataSource.uniqueId
            )
        }

        `when`(dataSourceService.listMongoDbDataSources()).thenReturn(emptyList())
        decorator.dataSourceRemoved(dataSourceManager, dataSource)
        runCurrent()

        eventually {
            val toolbarState = project.getToolbarModel().toolbarState.value
            assertFalse(toolbarState.dataSources.contains(dataSource))
            assertNull(toolbarState.selectedDataSource)
        }
    }

    @Test
    fun `when onTerminated is triggered updates ToolbarModel's DataSource list with the disconnected DataSource`(
        testScope: TestScope,
        project: Project
    ) = runTest {
        val decorator = EditorToolbarDecorator(testScope)
        val dataSourceService = mock<MdbDataSourceService>()
        val editorService = mock<MdbEditorService>()
        project.withMockedService(editorService)
        project.withMockedService(dataSourceService)

        project.getToolbarModel().setupEventsSubscription()

        // Mocks for our assertions
        val dataSource = mock<LocalDataSource>()

        // Defining an early behaviour of not having anything
        `when`(dataSourceService.listMongoDbDataSources()).thenReturn(listOf(dataSource))
        decorator.execute(project)
        runCurrent()

        eventually {
            val toolbarState = project.getToolbarModel().toolbarState.value
            assertTrue(toolbarState.dataSources.contains(dataSource))
        }

        decorator.onTerminated(dataSource, null)
        runCurrent()

        eventually {
            val toolbarState = project.getToolbarModel().toolbarState.value
            assertTrue(toolbarState.dataSources.contains(dataSource))
        }
    }

    @Test
    fun `when onTerminated is triggered updates ToolbarModel's DataSource list with the disconnected DataSource and also remove the current selection, if the selection is the disconnected DataSource`(
        testScope: TestScope,
        project: Project
    ) = runTest {
        val decorator = EditorToolbarDecorator(testScope)
        val dataSourceService = mock<MdbDataSourceService>()
        val editorService = mock<MdbEditorService>()
        project.withMockedService(editorService)
        project.withMockedService(dataSourceService)

        project.getToolbarModel().setupEventsSubscription()

        // Mocks for our assertions
        val dataSource = mock<LocalDataSource>()
        val existingDataSources = listOf(dataSource)

        // Defining an early behaviour of not having anything
        `when`(dataSourceService.listMongoDbDataSources()).thenReturn(existingDataSources)
        decorator.execute(project)
        project.getToolbarModel().selectDataSource(dataSource)
        runCurrent()

        eventually {
            val toolbarState = project.getToolbarModel().toolbarState.value
            assertTrue(toolbarState.dataSources.contains(dataSource))
            assertEquals(
                toolbarState.selectedDataSource?.uniqueId,
                dataSource.uniqueId
            )
        }

        decorator.onTerminated(dataSource, null)
        runCurrent()

        eventually {
            val toolbarState = project.getToolbarModel().toolbarState.value
            assertTrue(toolbarState.dataSources.contains(dataSource))
            assertNull(toolbarState.selectedDataSource)
        }
    }
}
