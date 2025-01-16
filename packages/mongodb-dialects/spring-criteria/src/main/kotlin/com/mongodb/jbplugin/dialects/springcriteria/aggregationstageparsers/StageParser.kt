package com.mongodb.jbplugin.dialects.springcriteria.aggregationstageparsers

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.mongodb.jbplugin.mql.Node

interface StageParser {
    fun isSuitableForFieldAutoComplete(
        methodCall: PsiMethodCallExpression,
        method: PsiMethod
    ): Boolean
    fun canParse(stageCallMethod: PsiMethod): Boolean
    fun parse(stageCall: PsiMethodCallExpression): Node<PsiElement>
}
