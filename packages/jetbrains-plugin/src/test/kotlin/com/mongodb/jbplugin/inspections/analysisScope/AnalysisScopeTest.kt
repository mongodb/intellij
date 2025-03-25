package com.mongodb.jbplugin.inspections.analysisScope

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.linting.Inspection
import com.mongodb.jbplugin.linting.QueryInsight
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class AnalysisScopeTest {
    @Test
    fun `CurrentFile only accepts insights from the provided virtual files`() {
        val vf1 = mock<VirtualFile>()
        val vf2 = mock<VirtualFile>()
        val vf3 = mock<VirtualFile>()

        val scope = AnalysisScope.CurrentFile(listOf(vf1, vf2))

        val insights = listOf(
            insightOnFile(vf1),
            insightOnFile(vf2),
            insightOnFile(vf3)
        )

        val filteredInsights = scope.getFilteredInsights(insights)
        assertTrue(filteredInsights.all { it.query.source.containingFile.virtualFile == vf1 || it.query.source.containingFile.virtualFile == vf2 })
        assertTrue(filteredInsights.isNotEmpty())
        assertEquals(2, filteredInsights.size)
    }

    private fun insightOnFile(file: VirtualFile): QueryInsight<PsiElement, *> {
        val source = mock<PsiElement>()
        val psiFile = mock<PsiFile>()

        whenever(source.containingFile).thenReturn(psiFile)
        whenever(psiFile.virtualFile).thenReturn(file)

        val qi = QueryInsight(
            com.mongodb.jbplugin.mql.Node(
                source,
                emptyList()
            ),
            "Some",
            emptyList(),
            Inspection.NotUsingIndex
        )

        return qi
    }
}
