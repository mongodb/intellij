package com.mongodb.jbplugin.dialects.springcriteria.aggregationparsers

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.mongodb.jbplugin.dialects.springcriteria.AGGREGATE_FQN
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named

class MatchStageParser(private val parseFilters: (PsiElement) -> List<Node<PsiElement>>) {
    private fun createMatchStageNode(
        source: PsiElement,
        filters: List<Node<PsiElement>> = emptyList()
    ) = Node(
        source = source,
        components = listOf(
            Named(Name.MATCH),
            HasFilter(filters),
        )
    )

    fun parse(matchStageCall: PsiMethodCallExpression): Node<PsiElement> {
        val filterExpression = matchStageCall.argumentList.expressions.getOrNull(0)
            ?: return createMatchStageNode(source = matchStageCall)
        return createMatchStageNode(
            source = matchStageCall,
            filters = parseFilters(filterExpression)
        )
    }

    companion object {
        fun isMatchStageCall(method: PsiMethod): Boolean {
            return method.containingClass?.qualifiedName == AGGREGATE_FQN && method.name == "match"
        }
    }
}
