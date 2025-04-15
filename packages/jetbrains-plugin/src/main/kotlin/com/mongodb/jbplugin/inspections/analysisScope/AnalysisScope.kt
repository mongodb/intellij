package com.mongodb.jbplugin.inspections.analysisScope

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.meta.withinReadActionBlocking
import com.mongodb.jbplugin.ui.viewModel.CodeEditorViewModel
import fleet.util.logging.logger
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.PropertyKey

sealed interface AnalysisScope {
    val displayName: String

    val needsRefreshOnEditorChange: Boolean
    fun refreshed(): AnalysisScope

    fun getFilteredInsights(
        project: Project,
        allInsights: List<QueryInsight<PsiElement, *>>
    ): List<QueryInsight<PsiElement, *>>

    fun getAdditionalFilesInScope(project: Project): List<VirtualFile>

    companion object {
        fun default(): AnalysisScope = CurrentFile()
    }

    class CurrentFile : AnalysisScope {
        private val log = logger<CurrentFile>()

        @PropertyKey(resourceBundle = "messages.SidePanelBundle")
        override val displayName = "side-panel.scope.current-file"

        override val needsRefreshOnEditorChange = true
        override fun refreshed() = CurrentFile()

        override fun getFilteredInsights(project: Project, allInsights: List<QueryInsight<PsiElement, *>>): List<QueryInsight<PsiElement, *>> {
            try {
                val codeEditorViewModel by project.service<CodeEditorViewModel>()
                val relevantFiles = codeEditorViewModel.editorState.value.focusedFiles

                return allInsights.filter {
                    relevantFiles.contains(it.query.source.containingFile.virtualFile)
                }
            } catch (e: Exception) {
                log.error("Unable to filter relevant insights ${e.message}")
                return allInsights
            }
        }

        override fun getAdditionalFilesInScope(project: Project): List<VirtualFile> {
            return emptyList()
        }
    }

    class CurrentQuery : AnalysisScope {
        @PropertyKey(resourceBundle = "messages.SidePanelBundle")
        override val displayName = "side-panel.scope.current-query"

        override val needsRefreshOnEditorChange = true
        override fun refreshed() = CurrentQuery()

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

        override val needsRefreshOnEditorChange = false
        override fun refreshed() = AllInsights()

        override fun getFilteredInsights(project: Project, allInsights: List<QueryInsight<PsiElement, *>>): List<QueryInsight<PsiElement, *>> {
            return allInsights
        }

        override fun getAdditionalFilesInScope(project: Project): List<VirtualFile> {
            val codeEditorViewModel by project.service<CodeEditorViewModel>()
            return runBlocking { codeEditorViewModel.allProjectFiles(project) }
        }
    }

    class RecommendedInsights : AnalysisScope {
        private val log = logger<RecommendedInsights>()

        @PropertyKey(resourceBundle = "messages.SidePanelBundle")
        override val displayName = "side-panel.scope.recommended-insights"

        override val needsRefreshOnEditorChange = true
        override fun refreshed() = RecommendedInsights()

        override fun getFilteredInsights(project: Project, allInsights: List<QueryInsight<PsiElement, *>>): List<QueryInsight<PsiElement, *>> {
            try {
                val codeEditorViewModel by project.service<CodeEditorViewModel>()
                val sharedParent =
                    findOpenFilesCommonPrefix(codeEditorViewModel) ?: return emptyList()

                return allInsights.filter {
                    it.query.source.containingFile.virtualFile.path.startsWith(sharedParent)
                }
            } catch (e: Exception) {
                log.error("Unable to filter relevant insights ${e.message}")
                return allInsights
            }
        }

        override fun getAdditionalFilesInScope(project: Project): List<VirtualFile> {
            val codeEditorViewModel by project.service<CodeEditorViewModel>()
            val sharedParent = findOpenFilesCommonPrefix(codeEditorViewModel) ?: return emptyList()
            val allProjectFiles = runBlocking { codeEditorViewModel.allProjectFiles(project) }

            return allProjectFiles.filter { it.path.startsWith(sharedParent) }
        }

        private fun findOpenFilesCommonPrefix(codeEditorViewModel: CodeEditorViewModel): String? {
            val openFiles = codeEditorViewModel.editorState.value.openFiles

            return openFiles.map { it.path }
                .fold(null as String?) { acc, new ->
                    when {
                        acc == null -> new
                        acc.startsWith(new) -> new
                        new.startsWith(acc) -> acc
                        else -> findCommonPrefix(acc, new)
                    }
                }
        }

        private fun findCommonPrefix(a: String, b: String): String {
            val minLen = minOf(a.length, b.length)
            for (i in 0..minLen) {
                if (a[i] != b[i]) {
                    return a.substring(0, i)
                }
            }

            return a.substring(minLen)
        }
    }
}
