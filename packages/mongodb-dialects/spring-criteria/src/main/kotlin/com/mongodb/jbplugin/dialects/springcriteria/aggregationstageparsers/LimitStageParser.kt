package com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.mongodb.jbplugin.dialects.javadriver.glossary.tryToResolveAsConstant
import com.mongodb.jbplugin.dialects.springcriteria.AGGREGATE_FQN
import com.mongodb.jbplugin.mql.Component
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasLimit
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named

class LimitStageParser : StageParser {
    override fun isSuitableForFieldAutoComplete(
        methodCall: PsiMethodCallExpression,
        method: PsiMethod
    ) = canParse(method)

    override fun canParse(stageCallMethod: PsiMethod): Boolean {
        val owningClassFqn = stageCallMethod.containingClass?.qualifiedName ?: return false
        return owningClassFqn == AGGREGATE_FQN && stageCallMethod.name == "limit"
    }

    override fun parse(stageCall: PsiMethodCallExpression): Node<PsiElement> {
        val psiField =
            stageCall.argumentList.expressions.getOrNull(0) ?: return limitNode(stageCall)

        val (wasResolved, limitValue) = psiField.tryToResolveAsConstant()
        val limitInt = if (wasResolved) {
            limitValue as? Int
        } else {
            null
        } ?: return limitNode(stageCall)

        return limitNode(
            stageCall,
            HasLimit(limitInt)
        )
    }

    private fun limitNode(
        stageCall: PsiMethodCallExpression,
        vararg additionalComponents: Component
    ): Node<PsiElement> =
        Node(stageCall, listOf(Named(Name.LIMIT)) + additionalComponents)
}
