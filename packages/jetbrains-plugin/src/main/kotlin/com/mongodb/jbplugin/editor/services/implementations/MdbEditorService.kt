package com.mongodb.jbplugin.editor.services.implementations

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.openapi.util.removeUserData
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.ConnectionContextRequirement
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.dialects.springquery.SpringAtQueryDialect
import com.mongodb.jbplugin.editor.MdbJavaEditorToolbar
import com.mongodb.jbplugin.editor.MongoDbVirtualFileDataSourceProvider.Keys
import com.mongodb.jbplugin.editor.models.getToolbarModel
import com.mongodb.jbplugin.editor.services.EditorService
import com.mongodb.jbplugin.observability.useLogMessage
import com.mongodb.jbplugin.settings.pluginSetting

private val allDialects = listOf(
    JavaDriverDialect,
    SpringAtQueryDialect,
    SpringCriteriaDialect
)

private val log = logger<MdbEditorService>()

/**
 * @param project
 */
@Service(Service.Level.PROJECT)
class MdbEditorService(private val project: Project) : EditorService {
    override val selectedEditor: Editor?
        get() = (FileEditorManager.getInstance(this.project).selectedEditor as? TextEditor)?.editor

    override val inferredDatabase: String?
        get() = ApplicationManager.getApplication().runReadAction<String?> {
            val context = getDialectForSelectedEditor()?.connectionContextExtractor?.gatherContext(
                this.project
            )
            return@runReadAction context?.database
        }

    override fun reAnalyzeSelectedEditor(applyReadAction: Boolean) {
        val selectedEditor = selectedEditor ?: return
        val analyzeFile = { psiReadAction: Boolean ->
            try {
                val psiFile =
                    getPsiFile(selectedEditor, applyReadAction = psiReadAction)
                        ?: throw Exception("PsiFile not found")
                DaemonCodeAnalyzer.getInstance(this.project).restart(psiFile)
            } catch (exception: Exception) {
                log.info(useLogMessage("Could not analyze file: ${exception.message}").build())
            }
        }
        if (applyReadAction) {
            ApplicationManager.getApplication().runReadAction {
                analyzeFile(false)
            }
        } else {
            analyzeFile(true)
        }
    }

    override fun getDialectForSelectedEditor(): Dialect<PsiElement, Project>? {
        val selectedEditor = selectedEditor ?: return null
        return selectedEditor.virtualFile?.getOrCreateUserData(Keys.attachedDialect) {
            try {
                val psiFile = getPsiFile(selectedEditor) ?: throw Exception("PsiFile not found")
                return@getOrCreateUserData allDialects.find { it.isUsableForSource(psiFile) }
            } catch (exception: Exception) {
                log.info("Could not attach a dialect to VirtualFile", exception)
                return@getOrCreateUserData null
            }
        }
    }

    override fun removeDialectForSelectedEditor() {
        selectedEditor?.virtualFile?.removeUserData(Keys.attachedDialect)
    }

    override fun attachDataSourceToSelectedEditor(dataSource: LocalDataSource) {
        selectedEditor?.virtualFile?.putUserData(Keys.attachedDataSource, dataSource)
    }

    override fun detachDataSourceFromSelectedEditor(dataSource: LocalDataSource) {
        val attachedDataSource = selectedEditor?.virtualFile?.getUserData(Keys.attachedDataSource)
        if (attachedDataSource?.uniqueId == dataSource.uniqueId) {
            selectedEditor?.virtualFile?.removeUserData(Keys.attachedDataSource)
        }
    }

    override fun attachDatabaseToSelectedEditor(database: String) {
        selectedEditor?.virtualFile?.putUserData(Keys.attachedDatabase, database)
    }

    override fun detachDatabaseFromSelectedEditor(database: String) {
        val attachedDatabase = selectedEditor?.virtualFile?.getUserData(Keys.attachedDatabase)
        if (attachedDatabase == database) {
            selectedEditor?.virtualFile?.removeUserData(Keys.attachedDatabase)
        }
    }

    override fun isDatabaseComboBoxVisibleForSelectedEditor(): Boolean {
        val dialect = getDialectForSelectedEditor() ?: return false
        val requirements = dialect.connectionContextExtractor?.requirements() ?: emptySet()
        return requirements.contains(ConnectionContextRequirement.DATABASE)
    }

    override fun getToolbarFromSelectedEditor(): MdbJavaEditorToolbar? = selectedEditor?.virtualFile?.getUserData(
        Keys.attachedToolbar
    )

    override fun toggleToolbarForSelectedEditor(
        toolbar: MdbJavaEditorToolbar,
        applyReadActionForFileAnalyses: Boolean
    ) {
        val currentEditor = selectedEditor ?: return
        val selectedEditorDialect = getDialectForSelectedEditor()
        val isSidePanelEnabled by pluginSetting { ::ftEnableSidePanel }

        selectedEditorDialect?.let {
            val databaseComboBoxVisible = isDatabaseComboBoxVisibleForSelectedEditor()
            if (!isSidePanelEnabled) {
                toolbar.attachToEditor(currentEditor, databaseComboBoxVisible)
            }
            currentEditor.virtualFile?.putUserData(Keys.attachedToolbar, toolbar)
            // If we already have some toolbar state then we preserve that as well
            // when toggling the toolbar for new editor
            val toolbarState = project.getToolbarModel().toolbarState.value
            toolbarState.selectedDataSource?.let {
                attachDataSourceToSelectedEditor(it)
            }
            if (toolbarState.selectedDatabase != null && databaseComboBoxVisible) {
                attachDatabaseToSelectedEditor(toolbarState.selectedDatabase)
            }
            reAnalyzeSelectedEditor(applyReadActionForFileAnalyses)
        } ?: run {
            toolbar.detachFromEditor(currentEditor)
            currentEditor.virtualFile?.removeUserData(Keys.attachedToolbar)
            reAnalyzeSelectedEditor(applyReadActionForFileAnalyses)
        }
    }

    private fun getPsiFile(editor: Editor, applyReadAction: Boolean = true): PsiFile? {
        if (applyReadAction) {
            return ApplicationManager.getApplication().runReadAction<PsiFile?> {
                return@runReadAction editor.virtualFile.findPsiFile(this.project)
            }
        } else {
            return editor.virtualFile.findPsiFile(this.project)
        }
    }
}

/**
 * Helper method to retrieve the MdbEditorService instance from Application
 *
 */
fun Project.getEditorService(): EditorService = getService(
    MdbEditorService::class.java
)
