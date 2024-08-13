package com.mongodb.jbplugin.editor

import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.connection.ConnectionRequestor
import com.intellij.database.psi.DataSourceManager
import com.intellij.database.run.ConsoleRunConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchChildBackground
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBPanel
import com.jetbrains.rd.util.AtomicReference
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isMongoDbDataSource
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.editor.inputs.DataSourceComboBox
import com.mongodb.jbplugin.editor.inputs.DatabaseComboBox
import com.mongodb.jbplugin.editor.inputs.DatabaseSelectedListener
import com.mongodb.jbplugin.editor.inputs.DatabaseUnselectedListener
import kotlinx.coroutines.CoroutineScope
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JPanel

typealias OnConnectedListener = (LocalDataSource) -> Unit
typealias OnDisconnectedListener = () -> Unit

/**
 * Represents the toolbar that will be inserted into an active Java editor.
 *
 * @param onDataSourceSelected
 * @param onDataSourceUnselected
 */
class MdbJavaEditorToolbar(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
    private val onConnected: OnConnectedListener,
    private val onDisconnected: OnDisconnectedListener,
    private val onDatabaseSelected: DatabaseSelectedListener,
    private val onDatabaseUnselected: DatabaseUnselectedListener
) : JBPanel<MdbJavaEditorToolbar>(BorderLayout()) {
    internal val dataSourceComboBox: DataSourceComboBox = DataSourceComboBox(
        onDataSourceSelected = this::onDataSourceSelected,
        onDataSourceUnselected = this::onDataSourceUnselected
    )

    internal val databaseComboBox: DatabaseComboBox = DatabaseComboBox(
        onDatabaseSelected = onDatabaseSelected,
        onDatabaseUnselected = onDatabaseUnselected
    )

    init {
        val dropdowns = JPanel()
        dropdowns.layout = BoxLayout(dropdowns, BoxLayout.X_AXIS)

        dropdowns.add(dataSourceComboBox)
        dropdowns.add(databaseComboBox)
        add(dropdowns, BorderLayout.EAST)
    }

    private fun onDataSourceSelected(dataSource: LocalDataSource) {
        if (!dataSource.isConnected()) {
            coroutineScope.launchChildBackground {
                dataSourceComboBox.connecting = true
                val connectionManager = DatabaseConnectionManager.getInstance()
                val connectionHandler =
                    connectionManager
                        .build(project, dataSource)
                        .setRequestor(ConnectionRequestor.Anonymous())
                        .setAskPassword(true)
                        .setRunConfiguration(
                            ConsoleRunConfiguration.newConfiguration(project).apply {
                                setOptionsFromDataSource(dataSource)
                            },
                        )

                val connectionJob = runCatching { connectionHandler.create()?.get() }
                connectionJob.onFailure {
                    dataSourceComboBox.connecting = false
                    dataSourceComboBox.failedConnection = dataSource
                    reloadDatabases()
                    return@launchChildBackground
                }

                val connection = connectionJob.getOrNull()
                dataSourceComboBox.connecting = false

                // could not connect, do nothing
                if (connection == null || !dataSource.isConnected()) {
                    dataSourceComboBox.selectedDataSource = null // remove data source because we didn't connect
                    reloadDatabases()
                    return@launchChildBackground
                }

                onConnected(dataSource)
                reloadDatabases()
            }
        } else {
            onConnected(dataSource)
            reloadDatabases()
        }
    }

    fun onDataSourceUnselected() {
        onDisconnected()
        reloadDatabases()
    }

    fun reloadDataSources(dataSources: List<LocalDataSource>) {
        dataSourceComboBox.dataSources = dataSources.filter { it.isMongoDbDataSource() }
    }

    fun reloadDatabases() {
        if (dataSourceComboBox.selectedDataSource == null) {
            databaseComboBox.databases = emptyList()
        } else {
            val readModel = project.getService(DataGripBasedReadModelProvider::class.java)
            val databases = readModel.slice(dataSourceComboBox.selectedDataSource!!, ListDatabases.Slice)
            databaseComboBox.databases = databases.databases.map { it.name }
        }
    }
    fun disconnect(dataSource: LocalDataSource) {
        if (dataSource.isMongoDbDataSource() &&
            !dataSource.isConnected() &&
            dataSourceComboBox.selectedDataSource?.uniqueId == dataSource.uniqueId
        ) {
            dataSourceComboBox.selectedDataSource = null
        }
    }

    companion object {
        fun showModalForSelection(editor: Editor, coroutineScope: CoroutineScope) {
            val project = editor.project ?: return

            ApplicationManager.getApplication().invokeLater {
                val selectedDataSource = AtomicReference<LocalDataSource?>(null)

                val toolbar =
                    MdbJavaEditorToolbar(
                        editor.project!!,
                        coroutineScope, {
                        selectedDataSource.getAndSet(it)
                    }, {
                        selectedDataSource.getAndSet(null)
                    }, {

                    }, {

                    })

                val localDataSourceManager =
                    DataSourceManager.byDataSource(project, LocalDataSource::class.java)
                        ?: return@invokeLater
                toolbar.dataSourceComboBox.dataSources = localDataSourceManager.dataSources.filter { it.isMongoDbDataSource() }

                val dialog = SelectConnectionDialogWrapper(project, toolbar)
                if (dialog.showAndGet()) {
                    EditorToolbarDecorator
                        .getToolbarFromEditor(editor)
                        ?.dataSourceComboBox?.selectedDataSource = selectedDataSource.get()
                }
            }
        }

/**
 * @param project
 * @param toolbar
 */
internal class SelectConnectionDialogWrapper(
            project: Project,
            private val toolbar: MdbJavaEditorToolbar,
        ) : DialogWrapper(project, false) {
            init {
                init()
            }

            override fun createCenterPanel(): JComponent =
                JPanel(BorderLayout()).apply {
                    add(toolbar)
                    (peer.window as? JDialog)?.isUndecorated = true
                }
        }
}
}
