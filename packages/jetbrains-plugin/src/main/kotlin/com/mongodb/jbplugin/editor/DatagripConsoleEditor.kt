package com.mongodb.jbplugin.editor

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.editor.DatabaseEditorHelper
import com.intellij.database.util.DbUIUtil
import com.intellij.database.vfs.DbVFSUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected

interface DatagripConsoleEditor {
    fun isThereAnEditorForDataSource(dataSource: LocalDataSource?): Boolean
    fun openConsoleForDataSource(project: Project, dataSource: LocalDataSource): Editor?

    companion object : DatagripConsoleEditor {
        override fun isThereAnEditorForDataSource(dataSource: LocalDataSource?): Boolean {
            if (dataSource == null) {
                return false
            }

            return ApplicationManager.getApplication().runReadAction<Boolean> {
                DatabaseEditorHelper.getConsoleVirtualFile(dataSource) != null
            }
        }

        override fun openConsoleForDataSource(project: Project, dataSource: LocalDataSource): Editor? {
            if (!dataSource.isConnected()) {
                return null
            }

            var activeEditor = allConsoleEditorsForDataSource(project, dataSource).firstOrNull()
            if (activeEditor != null) {
                val currentFile = PsiDocumentManager.getInstance(project)
                    .getPsiFile(activeEditor.document)!!.virtualFile

                FileEditorManager.getInstance(project).openFile(currentFile, true)
            } else {
                activeEditor = openNewEmptyEditorForDataSource(project, dataSource)
            }

            return activeEditor
        }

        private fun allConsoleEditorsForDataSource(project: Project, dataSource: LocalDataSource): List<Editor> =
            EditorFactory.getInstance().allEditors.filter {
                val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(it.document)
                val virtualFile = psiFile?.virtualFileOrNull ?: return@filter false
                val dataSourceOfFile = DbVFSUtils.getDataSource(project, virtualFile)
                dataSource == dataSourceOfFile
            }

        private fun openNewEmptyEditorForDataSource(project: Project, dataSource: LocalDataSource): Editor? {
            val file = DbUIUtil.openInConsole(project, dataSource, null, "", true)!!
            val psiFile = PsiManager.getInstance(project).findFile(file)!!
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)!!
            return EditorFactory.getInstance().getEditors(document, project).firstOrNull()
        }
    }
}

fun Editor.appendText(text: String) {
    val textLength = document.textLength
    if (textLength > 0 && document.charsSequence[textLength - 1] != '\n') {
        WriteCommandAction.runWriteCommandAction(
            project,
            null,
            null,
            { document.insertString(textLength, "\n") }
        )
    }

    caretModel.moveToOffset(document.textLength)
    WriteCommandAction.runWriteCommandAction(
        project,
        null,
        null,
        {
            document.insertString(document.textLength, text + "\n")
        }
    )
}
