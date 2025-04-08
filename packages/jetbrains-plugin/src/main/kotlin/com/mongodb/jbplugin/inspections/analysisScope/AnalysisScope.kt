package com.mongodb.jbplugin.inspections.analysisScope

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.meta.withinReadActionBlocking
import com.mongodb.jbplugin.ui.viewModel.CodeEditorViewModel
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.PropertyKey

sealed interface AnalysisScope {
    val displayName: String

    fun getFilteredInsights(
        project: Project,
        allInsights: List<QueryInsight<PsiElement, *>>
    ): List<QueryInsight<PsiElement, *>>

    fun getAdditionalFilesInScope(project: Project): List<VirtualFile>

    companion object {
        fun default(): AnalysisScope = CurrentFile()
    }

    class CurrentFile : AnalysisScope {
        @PropertyKey(resourceBundle = "messages.SidePanelBundle")
        override val displayName = "side-panel.scope.current-file"

        override fun getFilteredInsights(project: Project, allInsights: List<QueryInsight<PsiElement, *>>): List<QueryInsight<PsiElement, *>> {
            val codeEditorViewModel by project.service<CodeEditorViewModel>()
            val relevantFiles = codeEditorViewModel.editorState.value.focusedFiles

            return allInsights.filter {
                relevantFiles.contains(it.query.source.containingFile.virtualFile)
            }
        }

        override fun getAdditionalFilesInScope(project: Project): List<VirtualFile> {
            return emptyList()
        }
    }

    class CurrentQuery : AnalysisScope {
        @PropertyKey(resourceBundle = "messages.SidePanelBundle")
        override val displayName = "side-panel.scope.current-query"

        override fun getFilteredInsights(project: Project, allInsights: List<QueryInsight<PsiElement, *>>): List<QueryInsight<PsiElement, *>> {
            val codeEditorViewModel by project.service<CodeEditorViewModel>()
            val allFocusedQueries = withinReadActionBlocking {
                codeEditorViewModel.queriesAtCaret().map { it.queryHash() }.toSet()
            }

            return allInsights.filter { allFocusedQueries.contains(it.query.queryHash()) }
        }

        override fun getAdditionalFilesInScope(project: Project): List<VirtualFile> {
            return emptyList()
        }
    }

    class AllInsights : AnalysisScope {
        @PropertyKey(resourceBundle = "messages.SidePanelBundle")
        override val displayName = "side-panel.scope.all-insights"

        override fun getFilteredInsights(project: Project, allInsights: List<QueryInsight<PsiElement, *>>): List<QueryInsight<PsiElement, *>> {
            return allInsights
        }

        override fun getAdditionalFilesInScope(project: Project): List<VirtualFile> {
            val codeEditorViewModel by project.service<CodeEditorViewModel>()
            return runBlocking { codeEditorViewModel.allProjectFiles(project) }
        }
    }
}
