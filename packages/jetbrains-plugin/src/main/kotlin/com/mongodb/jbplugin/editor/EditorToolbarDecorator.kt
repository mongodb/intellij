package com.mongodb.jbplugin.editor

import com.intellij.database.console.JdbcDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.model.RawDataSource
import com.intellij.database.psi.DataSourceManager
import com.intellij.database.run.ConsoleRunConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.util.PsiModificationTracker
import com.mongodb.jbplugin.editor.models.implementations.ProjectDataSourceModel
import com.mongodb.jbplugin.editor.models.implementations.ProjectDatabaseModel
import com.mongodb.jbplugin.editor.services.implementations.getDataSourceService
import com.mongodb.jbplugin.editor.services.implementations.getEditorService
import com.mongodb.jbplugin.editor.services.implementations.useToolbarSettings
import io.ktor.util.collections.*
import org.jetbrains.annotations.TestOnly

import kotlinx.coroutines.CoroutineScope

private val log = logger<EditorToolbarDecorator>()

/**
 * @param coroutineScope
 */
class EditorToolbarDecorator(
    private val coroutineScope: CoroutineScope,
) : ProjectActivity,
    FileEditorManagerListener,
    PsiModificationTracker.Listener,
    DataSourceManager.Listener,
    JdbcDriverManager.Listener {
    // The indicator that the project activity has started. Is used mostly by runReadActionAfterActivityStart
    // to either trigger the listener right away or queue it for when the activity starts and initialises the necessary
    // lateinit variables
    private var activityStarted: Boolean = false

    // Set of listeners waiting for the activity to get started
    internal val onActivityStartedListeners = ConcurrentSet<() -> Unit>()

    // These variables are lateinit because we initialise them when the project activity starts using execute method
    // below. We need to keep a hold of them because other listeners also use them in some way
    private lateinit var project: Project
    private lateinit var toolbar: MdbJavaEditorToolbar

    // The activity (EditorToolbarDecorator) implements different listeners which may or may not get triggered before
    // our ProjectActivity has started which means there is a chance that those listeners may find uninitialised state
    // and start failing. To prevent that we make use of this queueing mechanism until activity starts
    private fun runReadActionAfterActivityStart(block: () -> Unit) {
        if (!activityStarted) {
            onActivityStartedListeners.add(block)
        } else {
            ApplicationManager.getApplication().runReadAction {
                block()
            }
        }
    }

    // Internal only because we spy on the method in tests
    internal fun dispatchActivityStarted() {
        activityStarted = true
        ApplicationManager.getApplication().runReadAction {
            val listeners = onActivityStartedListeners
            onActivityStartedListeners.clear()
            for (listener in listeners) {
                try {
                    listener()
                } catch (e: Exception) {
                    log.warn("Error while running onActivityStarted listener", e)
                }
            }
        }
    }

    // Internal only because we spy on the method in tests
    internal fun setupSubscriptionsForProject(project: Project) {
        val messageBusConnection = project.messageBus.connect()
        messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
        messageBusConnection.subscribe(PsiModificationTracker.TOPIC, this)
        messageBusConnection.subscribe(DataSourceManager.TOPIC, this)
        messageBusConnection.subscribe(JdbcDriverManager.TOPIC, this)
    }

    @TestOnly
    internal fun getToolbarForTests(): MdbJavaEditorToolbar = toolbar

    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().runReadAction {
            val toolbarSettings = useToolbarSettings()
            val editorService = getEditorService(project)
            val dataSourceService = getDataSourceService(project)

            val dataSourceModel = ProjectDataSourceModel(
                toolbarSettings = toolbarSettings,
                dataSourceService = dataSourceService,
                editorService = editorService
            )
            val databaseModel = ProjectDatabaseModel(
                toolbarSettings = toolbarSettings,
                dataSourceService = dataSourceService,
                editorService = editorService
            )

            if (!::toolbar.isInitialized) {
                this.project = project
                this.setupSubscriptionsForProject(project)
                this.toolbar = MdbJavaEditorToolbar(
                    dataSourceModel = dataSourceModel,
                    databaseModel = databaseModel,
                )
            }

            editorService.toggleToolbarForSelectedEditor(toolbar)
            dispatchActivityStarted()
        }
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        runReadActionAfterActivityStart {
            val editorService = getEditorService(project)
            editorService.toggleToolbarForSelectedEditor(toolbar)
        }
    }

    override fun modificationCountChanged() {
        runReadActionAfterActivityStart {
            val editorService = getEditorService(project)
            editorService.toggleToolbarForSelectedEditor(toolbar)
        }
    }

    override fun <T : RawDataSource?> dataSourceAdded(manager: DataSourceManager<T>, dataSource: T & Any) {
        runReadActionAfterActivityStart {
            // An added DataSource can't possibly change the selection state hence just reloading the DataSources
            toolbar.reloadDataSources()
        }
    }

    override fun <T : RawDataSource?> dataSourceRemoved(manager: DataSourceManager<T>, dataSource: T & Any) {
        runReadActionAfterActivityStart {
            // A removed DataSource might be our selected one so we first remove it and then reload the DataSources
            // Unselection when happened is expected to trigger state change listener so that will update
            // also the attached resources to the selected editor and the stored DataSource in ToolbarSettings
            toolbar.unselectDataSource(dataSource as LocalDataSource)
            toolbar.reloadDataSources()
        }
    }

    override fun <T : RawDataSource?> dataSourceChanged(manager: DataSourceManager<T>?, dataSource: T?) {
        runReadActionAfterActivityStart {
            toolbar.reloadDataSources()
        }
    }

    override fun onTerminated(dataSource: LocalDataSource, configuration: ConsoleRunConfiguration?) {
        runReadActionAfterActivityStart {
            toolbar.unselectDataSource(dataSource)
        }
    }
}
