package com.mongodb.jbplugin.meta

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.findTopmostParentOfType
import com.mongodb.jbplugin.mql.Node

val Node<PsiElement>.containingFileOrNull: PsiFile?
    get() = withinReadActionBlocking { source.findTopmostParentOfType() }

val Node<PsiElement>.projectOrNull: Project?
    get() = withinReadActionBlocking { containingFileOrNull?.project }
