package com.mongodb.jbplugin.ui.viewModel

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.editor.services.MdbPluginDisposable
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

    suspend fun focusQueryInEditor(query: Node<PsiElement>) {
        withContext(Dispatchers.EDT) {
            val vFile = query.source.containingFile.virtualFile

            val manager = FileEditorManager.getInstance(query.source.project)
            val editorOfFile = manager.selectedTextEditor?.takeIf { it.virtualFile == vFile }
                ?: manager.openTextEditor(OpenFileDescriptor(query.source.project, vFile, -1), true)
                ?: return@withContext

            editorOfFile.caretModel.moveToOffset(query.source.textOffset)
        }
    }

    private fun rebuildEditorState(manager: FileEditorManager) {
        val openFiles = manager.openFiles.toList().distinctBy { it.canonicalPath }
        val focusedFiles = manager.selectedEditors.mapNotNull { it.file }.distinctBy { it.canonicalPath }.toList()

        coroutineScope.launch(Dispatchers.IO) {
            mutableEditorState.emit(EditorState(focusedFiles, openFiles))
        }
    }
}
