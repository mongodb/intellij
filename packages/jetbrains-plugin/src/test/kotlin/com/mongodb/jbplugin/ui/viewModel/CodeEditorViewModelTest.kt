package com.mongodb.jbplugin.ui.viewModel

import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.eventually
import com.mongodb.jbplugin.mql.Node
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@IntegrationTest
class CodeEditorViewModelTest {
    @Test
    fun `should emit the open files when a file is opened`(
        project: Project,
    ) {
        val file1 = fileAt("MyFile.java")
        val file2 = fileAt("MyOpenFile.java")
        val viewModel = CodeEditorViewModel(project, TestScope())

        val manager = fileManagerWith(
            openFiles = listOf(file1, file2),
            filesInEditor = listOf(file2)
        )

        viewModel.fileOpened(manager, file1)
        eventually {
            val editorState = viewModel.editorState.value
            assertEquals(editorState.openFiles[0], file1)
            assertEquals(editorState.openFiles[1], file2)
            assertEquals(editorState.focusedFiles[0], file2)
        }
    }

    @Test
    fun `should emit the open files when a file is closed`(
        project: Project
    ) {
        val file1 = fileAt("MyFile.java")
        val file2 = fileAt("MyOpenFile.java")
        val viewModel = CodeEditorViewModel(project, TestScope())

        val manager = fileManagerWith(
            openFiles = listOf(file2),
            filesInEditor = listOf(file2)
        )

        viewModel.fileClosed(manager, file1)
        eventually {
            val editorState = viewModel.editorState.value
            assertEquals(editorState.openFiles[0], file2)
            assertEquals(editorState.focusedFiles[0], file2)
        }
    }

    @Test
    fun `should emit the open files when the selected editor changed`(
        project: Project
    ) {
        val file1 = fileAt("MyFile.java")
        val file2 = fileAt("MyOpenFile.java")
        val viewModel = CodeEditorViewModel(project, TestScope())

        val manager = fileManagerWith(
            openFiles = listOf(file1, file2),
            filesInEditor = listOf(file2)
        )

        viewModel.selectionChanged(FileEditorManagerEvent(manager, null, null))
        eventually {
            val editorState = viewModel.editorState.value
            assertEquals(editorState.openFiles[0], file1)
            assertEquals(editorState.openFiles[1], file2)
            assertEquals(editorState.focusedFiles[0], file2)
        }
    }

    @Test
    fun `should open the query in the editor when the editor is already open`(
        project: Project,
    ) {
        val file = fileAt("MyFile.java")
        val viewModel = CodeEditorViewModel(project, TestScope())

        val manager = fileManagerWith(
            openFiles = listOf(file),
            filesInEditor = listOf(file)
        )

        val query = queryAt(file, project, 25)
        runBlocking {
            viewModel.focusQueryInEditor(query, manager)
        }

        eventually {
            verify(manager.selectedTextEditor!!.caretModel).moveToOffset(25)
        }
    }

    @Test
    fun `should open a new editor and move the caret to the correct position`(
        project: Project
    ) {
        val file = fileAt("MyFile.java")
        val viewModel = CodeEditorViewModel(project, TestScope())

        val manager = fileManagerWith(
            openFiles = listOf(file),
            filesInEditor = listOf()
        )

        val newEditor = editorForFile(file)
        whenever(manager.openTextEditor(any(), any())).thenReturn(newEditor)

        runBlocking {
            val query = queryAt(file, project, 25)
            viewModel.focusQueryInEditor(query, manager)
        }

        eventually {
            verify(newEditor.caretModel).moveToOffset(25)
        }
    }
}

internal fun queryAt(file: VirtualFile, project: Project, offset: Int): Node<PsiElement> {
    val psiElement = mock<PsiElement>()
    val psiFile = mock<PsiFile>()

    whenever(psiElement.containingFile).thenReturn(psiFile)
    whenever(psiFile.virtualFile).thenReturn(file)
    whenever(psiElement.project).thenReturn(project)
    whenever(psiElement.textOffset).thenReturn(offset)

    return Node(psiElement, emptyList())
}

internal fun fileAt(path: String): VirtualFile {
    val file = mock<VirtualFile>()
    whenever(file.canonicalPath).thenReturn(path)

    return file
}

internal fun fileManagerWith(openFiles: List<VirtualFile>, filesInEditor: List<VirtualFile>): FileEditorManager {
    val manager = mock<FileEditorManager>()
    val openFilesArray = openFiles.toTypedArray()
    val editorsArray = filesInEditor.map {
        val editor = mock<FileEditor>()
        whenever(editor.file).thenReturn(it)
        editor
    }.toTypedArray()

    if (filesInEditor.isNotEmpty()) {
        val editor = editorForFile(filesInEditor.first())
        whenever(manager.selectedTextEditor).thenReturn(editor)
    }

    whenever(manager.openFiles).thenReturn(openFilesArray)
    whenever(manager.selectedEditors).thenReturn(editorsArray)

    return manager
}

private fun editorForFile(
    file: VirtualFile
): Editor {
    val caretModel = mock<CaretModel>()
    val selectedTextEditor = mock<Editor>()
    whenever(selectedTextEditor.caretModel).thenReturn(caretModel)
    whenever(selectedTextEditor.virtualFile).thenReturn(file)
    return selectedTextEditor
}
