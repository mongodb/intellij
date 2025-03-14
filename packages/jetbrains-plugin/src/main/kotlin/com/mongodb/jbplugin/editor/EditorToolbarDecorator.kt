package com.mongodb.jbplugin.editor

import com.intellij.database.console.JdbcDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.model.RawDataSource
import com.intellij.database.psi.DataSourceManager
import com.intellij.database.run.ConsoleRunConfiguration
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.util.PsiModificationTracker
import com.mongodb.jbplugin.editor.models.getToolbarModel
import com.mongodb.jbplugin.editor.models.maybeGetToolbarModel
import com.mongodb.jbplugin.editor.services.MdbPluginDisposable
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.TestOnly

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
    // These variables are nullable because we initialise them when the project activity starts using execute method
    // below, which can happen multiple times during an IntelliJ session. We need to keep a hold of them because other
    // listeners also use them in some way
    private var project: Project? = null
    private var toolbar: MdbJavaEditorToolbar? = null

    // Internal only because we spy on the method in tests
    internal fun setupSubscriptionsForProject(project: Project) {
        // Registering messageBus.connect to our MdbPluginDisposable ensures that the subscriptions are cleared
        // when either the plugin unloads or the project is closed
        val messageBusConnection = project.messageBus.connect(
            MdbPluginDisposable.getInstance(project)
        )
        messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
        messageBusConnection.subscribe(PsiModificationTracker.TOPIC, this)
        messageBusConnection.subscribe(DataSourceManager.TOPIC, this)
        messageBusConnection.subscribe(JdbcDriverManager.TOPIC, this)
    }

    @TestOnly
    internal fun getToolbarForTests(): MdbJavaEditorToolbar? = toolbar

    // execute can get called multiple times during an IntelliJ session which is why
    // we override the stored project and toolbar everytime it is called
    override suspend fun execute(project: Project) {
        this.project = project
        project.getToolbarModel().setupEventsSubscription()
        this.setupSubscriptionsForProject(project)

        this.toolbar = MdbJavaEditorToolbar(
            project = project,
            coroutineScope = coroutineScope,
        )

        project.maybeGetToolbarModel()?.projectExecuted(this.toolbar!!)
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        project?.maybeGetToolbarModel()?.selectionChanged(this.toolbar!!)
    }

    override fun modificationCountChanged() {
        project?.maybeGetToolbarModel()?.modificationCountChanged(this.toolbar!!)
    }

    override fun <T : RawDataSource?> dataSourceAdded(manager: DataSourceManager<T>, dataSource: T & Any) {
        // An added DataSource can't possibly change the selection state hence just reloading the DataSources
        project?.maybeGetToolbarModel()?.dataSourcesChanged()
    }

    override fun <T : RawDataSource?> dataSourceRemoved(manager: DataSourceManager<T>, dataSource: T & Any) {
        // A removed DataSource might be our selected one so we first remove it and then reload the DataSources
        // Unselection when happened is expected to trigger state change listener so that will update
        // also the attached resources to the selected editor and the stored DataSource in ToolbarSettings
        project?.maybeGetToolbarModel()?.dataSourceRemoved(dataSource as LocalDataSource)
    }

    override fun <T : RawDataSource?> dataSourceChanged(manager: DataSourceManager<T>?, dataSource: T?) {
        project?.maybeGetToolbarModel()?.dataSourcesChanged()
    }

    override fun onTerminated(dataSource: LocalDataSource, configuration: ConsoleRunConfiguration?) {
        project?.maybeGetToolbarModel()?.dataSourceTerminated(dataSource)
    }
}
