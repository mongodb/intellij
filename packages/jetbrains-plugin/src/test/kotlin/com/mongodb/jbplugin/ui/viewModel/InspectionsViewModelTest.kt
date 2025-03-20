package com.mongodb.jbplugin.ui.viewModel

import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.mql.Node
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class InspectionsViewModelTest {
    @Test
    fun `deduplicates insights`() = runTest {
        val viewModel = InspectionsViewModel()
        val query = mock<Node<PsiElement>>()

        viewModel.addInsight(QueryInsight.notUsingIndex(query))
        viewModel.addInsight(QueryInsight.notUsingIndex(query))

        assertEquals(1, viewModel.insights.value.size)
    }

    @Test
    fun `can hold multiple insights for the same query`() = runTest {
        val viewModel = InspectionsViewModel()
        val query = mock<Node<PsiElement>>()

        viewModel.addInsight(QueryInsight.notUsingIndex(query))
        viewModel.addInsight(QueryInsight.notUsingIndexEffectively(query))

        assertEquals(2, viewModel.insights.value.size)
    }
}
