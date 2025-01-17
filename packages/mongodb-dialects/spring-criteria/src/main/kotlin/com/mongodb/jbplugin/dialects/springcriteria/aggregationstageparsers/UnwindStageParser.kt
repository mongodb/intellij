package com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.mongodb.jbplugin.dialects.javadriver.glossary.tryToResolveAsConstantString
import com.mongodb.jbplugin.dialects.springcriteria.AGGREGATE_FQN
import com.mongodb.jbplugin.mql.Component
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named

class UnwindStageParser : StageParser {
    override fun isSuitableForFieldAutoComplete(
        methodCall: PsiMethodCallExpression,
        method: PsiMethod
    ) = canParse(method)

    override fun canParse(stageCallMethod: PsiMethod): Boolean {
        val owningClassFqn = stageCallMethod.containingClass?.qualifiedName ?: return false
        return owningClassFqn == AGGREGATE_FQN && stageCallMethod.name == "unwind"
    }

    override fun parse(stageCall: PsiMethodCallExpression): Node<PsiElement> {
        val psiField =
            stageCall.argumentList.expressions.getOrNull(0) ?: return unwindNode(stageCall)

        val referencedField = psiField.tryToResolveAsConstantString()
            ?: return unwindNode(stageCall)

        return unwindNode(
            stageCall,
            HasFieldReference(HasFieldReference.FromSchema(psiField, referencedField))
        )
    }

    private fun unwindNode(stageCall: PsiMethodCallExpression, vararg additionalComponents: Component): Node<PsiElement> =
        Node(stageCall, listOf(Named(Name.UNWIND)) + additionalComponents)
}
