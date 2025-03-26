package com.mongodb.jbplugin.inspections.analysisScope

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.linting.QueryInsight
import org.jetbrains.annotations.PropertyKey

sealed interface AnalysisScope {
    val displayName: String

    fun getFilteredInsights(
        allInsights: List<QueryInsight<PsiElement, *>>
    ): List<QueryInsight<PsiElement, *>>

    fun getAdditionalFilesInScope(): List<VirtualFile>

    companion object {
        fun default(): AnalysisScope = CurrentFile(emptyList())
    }

    data class CurrentFile(val files: List<VirtualFile>) : AnalysisScope {
        @PropertyKey(resourceBundle = "messages.SidePanelBundle")
        override val displayName = "side-panel.scope.current-file"

        override fun getFilteredInsights(allInsights: List<QueryInsight<PsiElement, *>>): List<QueryInsight<PsiElement, *>> {
            return allInsights.filter {
                files.contains(it.query.source.containingFile.virtualFile)
            }
        }

        override fun getAdditionalFilesInScope(): List<VirtualFile> {
            return emptyList()
        }
    }
}
