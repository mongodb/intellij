package com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.mongodb.jbplugin.dialects.springcriteria.AGGREGATE_FQN
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named

class MatchStageParser(
    private val parseFilters: (PsiElement) -> List<Node<PsiElement>>
) : StageParser {
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

    override fun canParse(stageCallMethod: PsiMethod): Boolean {
        return stageCallMethod.containingClass?.qualifiedName == AGGREGATE_FQN &&
            stageCallMethod.name == "match"
    }

    override fun parse(stageCall: PsiMethodCallExpression): Node<PsiElement> {
        val filterExpression = stageCall.argumentList.expressions.getOrNull(0)
            ?: return createMatchStageNode(source = stageCall)
        return createMatchStageNode(
            source = stageCall,
            filters = parseFilters(filterExpression)
        )
    }
}
