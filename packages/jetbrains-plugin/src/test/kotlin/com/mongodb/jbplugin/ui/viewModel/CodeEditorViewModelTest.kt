package com.mongodb.jbplugin.ui.viewModel

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.eventually
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@IntegrationTest
class CodeEditorViewModelTest {
    @Test
    fun `should emit the open files when a file is opened`(
        project: Project,
        coroutineScope: TestScope
    ) {
        val file1 = fileAt("MyFile.java")
        val file2 = fileAt("MyOpenFile.java")
        val viewModel = CodeEditorViewModel(project, coroutineScope)

        val manager = fileManagerWith(
            openFiles = listOf(file1, file2),
            filesInEditor = listOf(file2)
        )

        viewModel.fileOpened(manager, file1)
        eventually(coroutineScope = coroutineScope) {
            val editorState = viewModel.editorState.value
            assertEquals(editorState.openFiles[0], file1)
            assertEquals(editorState.openFiles[1], file2)
            assertEquals(editorState.focusedFiles[0], file2)
        }
    }

    @Test
    fun `should emit the open files when a file is closed`(
        project: Project,
        coroutineScope: TestScope
    ) {
        val file1 = fileAt("MyFile.java")
        val file2 = fileAt("MyOpenFile.java")
        val viewModel = CodeEditorViewModel(project, coroutineScope)

        val manager = fileManagerWith(
            openFiles = listOf(file2),
            filesInEditor = listOf(file2)
        )

        viewModel.fileClosed(manager, file1)
        eventually(coroutineScope = coroutineScope) {
            val editorState = viewModel.editorState.value
            assertEquals(editorState.openFiles[0], file2)
            assertEquals(editorState.focusedFiles[0], file2)
        }
    }

    @Test
    fun `should emit the open files when the selected editor changed`(
        project: Project,
        coroutineScope: TestScope
    ) {
        val file1 = fileAt("MyFile.java")
        val file2 = fileAt("MyOpenFile.java")
        val viewModel = CodeEditorViewModel(project, coroutineScope)

        val manager = fileManagerWith(
            openFiles = listOf(file1, file2),
            filesInEditor = listOf(file2)
        )

        viewModel.selectionChanged(FileEditorManagerEvent(manager, null, null))
        eventually(coroutineScope = coroutineScope) {
            val editorState = viewModel.editorState.value
            assertEquals(editorState.openFiles[0], file1)
            assertEquals(editorState.openFiles[1], file2)
            assertEquals(editorState.focusedFiles[0], file2)
        }
    }

    private fun fileAt(path: String): VirtualFile {
        val file = mock<VirtualFile>()
        whenever(file.canonicalPath).thenReturn(path)

        return file
    }

    private fun fileManagerWith(openFiles: List<VirtualFile>, filesInEditor: List<VirtualFile>): FileEditorManager {
        val manager = mock<FileEditorManager>()
        val openFilesArray = openFiles.toTypedArray()
        val editorsArray = filesInEditor.map {
            val editor = mock<FileEditor>()
            whenever(editor.file).thenReturn(it)
            editor
        }.toTypedArray()

        whenever(manager.openFiles).thenReturn(openFilesArray)
        whenever(manager.selectedEditors).thenReturn(editorsArray)

        return manager
    }
}
