package com.mongodb.jbplugin.inspections.analysisScope

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
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.ui.viewModel.CodeEditorViewModel
import org.jetbrains.annotations.PropertyKey

private val allDialects: List<Dialect<PsiElement, Project>> = listOf(
    JavaDriverDialect,
    SpringAtQueryDialect,
    SpringCriteriaDialect
)

sealed interface AnalysisScope {
    val displayName: String

    fun getFilteredInsights(
        project: Project,
        allInsights: List<QueryInsight<PsiElement, *>>
    ): List<QueryInsight<PsiElement, *>>

    fun getAdditionalFilesInScope(project: Project): List<VirtualFile>

    companion object {
        fun default(): AnalysisScope = CurrentFile
    }

    data object CurrentFile : AnalysisScope {
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

    data object AllInsights : AnalysisScope {
        @PropertyKey(resourceBundle = "messages.SidePanelBundle")
        override val displayName = "side-panel.scope.all-insights"

        override fun getFilteredInsights(project: Project, allInsights: List<QueryInsight<PsiElement, *>>): List<QueryInsight<PsiElement, *>> {
            return allInsights
        }

        override fun getAdditionalFilesInScope(project: Project): List<VirtualFile> {
            val scope = GlobalSearchScope.projectScope(project)
            val javaFileType = FileTypeManager.getInstance().getStdFileType("JAVA")

            val allFiles = FileTypeIndex.getFiles(javaFileType, scope)

            return allFiles
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
}
