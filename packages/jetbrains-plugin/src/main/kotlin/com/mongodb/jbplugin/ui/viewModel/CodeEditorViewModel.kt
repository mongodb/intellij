package com.mongodb.jbplugin.ui.viewModel

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.mongodb.jbplugin.dialects.Dialect
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.dialects.springquery.SpringAtQueryDialect
import com.mongodb.jbplugin.editor.MongoDbVirtualFileDataSourceProvider.Keys
import com.mongodb.jbplugin.editor.services.MdbPluginDisposable
import com.mongodb.jbplugin.meta.withinReadAction
import com.mongodb.jbplugin.mql.Node
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorState(
    val focusedFiles: List<VirtualFile>,
    val openFiles: List<VirtualFile>
) {
    companion object {
        fun default(): EditorState {
            return EditorState(emptyList(), emptyList())
        }
    }
}

private val allDialects: List<Dialect<PsiElement, Project>> = listOf(
    JavaDriverDialect,
    SpringAtQueryDialect,
    SpringCriteriaDialect
)

@Service(Service.Level.PROJECT)
class CodeEditorViewModel(
    val project: Project,
    val coroutineScope: CoroutineScope,
) : FileEditorManagerListener {
    private val mutableEditorState = MutableStateFlow(EditorState.default())
    val editorState = mutableEditorState.asStateFlow()

    init {
        val messageBusConnection = project.messageBus.connect(MdbPluginDisposable.getInstance(project))
        val manager = FileEditorManager.getInstance(project)

        rebuildEditorState(manager)
        messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
    }

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        rebuildEditorState(source)
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        rebuildEditorState(event.manager)
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        rebuildEditorState(source)
    }

    suspend fun reanalyzeRelevantEditors() {
        withContext(Dispatchers.IO) {
            withinReadAction {
                val allPsiFiles = editorState.value.focusedFiles.mapNotNull {
                    it.findPsiFile(project)
                }

                for (psiFile in allPsiFiles) {
                    InlayHintsPassFactoryInternal.forceHintsUpdateOnNextPass()
                    DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
                }
            }
        }
    }

    suspend fun focusQueryInEditor(query: Node<PsiElement>, fileEditorManager: FileEditorManager? = null) {
        withContext(Dispatchers.EDT) {
            val vFile = query.source.containingFile.virtualFile

            val manager = fileEditorManager ?: FileEditorManager.getInstance(query.source.project)
            val editorOfFile = manager.selectedTextEditor?.takeIf { it.virtualFile == vFile }
                ?: manager.openTextEditor(OpenFileDescriptor(query.source.project, vFile, -1), true)
                ?: return@withContext

            editorOfFile.caretModel.moveToOffset(query.source.textOffset)
        }
    }

    suspend fun allProjectFiles(project: Project): List<VirtualFile> = withContext(Dispatchers.IO) {
        withinReadAction {
            val scope = GlobalSearchScope.projectScope(project)
            val javaFileType = FileTypeManager.getInstance().getStdFileType("JAVA")

            FileTypeIndex.getFiles(javaFileType, scope)
                .mapNotNull { it.findPsiFile(project) }
                .map { file -> file to allDialects.firstOrNull { it.isUsableForSource(file) } }
                .filter { it.second != null }
                .map {
                    it.first.virtualFile.apply {
                        putUserData(Keys.attachedDialect, it.second!!)
                    }
                }
        }
    }

    private fun rebuildEditorState(manager: FileEditorManager) {
        coroutineScope.launch(Dispatchers.IO) {
            val openFiles = withinReadAction {
                manager.openFiles.toList().distinctBy { it.canonicalPath }
            }

            val focusedFiles = withinReadAction {
                manager.selectedEditors.mapNotNull { it.file }.distinctBy { it.canonicalPath }.toList()
            }

            mutableEditorState.emit(EditorState(focusedFiles, openFiles))
        }
    }
}
