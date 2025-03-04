package com.mongodb.jbplugin.inspections.quickfixes

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.findParentOfType
import com.mongodb.jbplugin.dialects.mongosh.MongoshDialect
import com.mongodb.jbplugin.editor.CachedQueryService
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.i18n.InspectionsAndInlaysMessages
import com.mongodb.jbplugin.indexing.CollectionIndexConsolidationOptions
import com.mongodb.jbplugin.indexing.IndexAnalyzer
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.observability.probe.CreateIndexIntentionProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

class OpenConsoleWithNewIndexForQueryQuickFix(
    coroutineScope: CoroutineScope,
    query: Node<PsiElement>,
) : OpenDataSourceConsoleAppendingCodeQuickFix(
    coroutineScope,
    InspectionsAndInlaysMessages.message(
        "inspection.index.checking.error.query.not.covered.by.index.quick.fix"
    ),
    query.source.containingFile.dataSource,
    {
        val createIndexClicked by query.source.project.service<CreateIndexIntentionProbe>()
        createIndexClicked.intentionClicked(query)

        val cachedQueryService by query.source.project.service<CachedQueryService>()
        val index = runBlocking {
            IndexAnalyzer.analyze(
                query,
                cachedQueryService,
                CollectionIndexConsolidationOptions(10)
            )
        }

        MongoshDialect.formatter.indexCommand(query, index, ::queryReferenceString)
    }
) {
    companion object {
        private fun queryReferenceString(query: Node<PsiElement>): String? {
            return ApplicationManager.getApplication().runReadAction<String?> {
                val method = query.source.findParentOfType<PsiMethod>() ?: return@runReadAction null
                val containingClass = method.containingClass ?: return@runReadAction null
                val lineNumber = query.source.containingFile.fileDocument.getLineNumber(
                    query.source.textOffset
                ) + 1

                "${containingClass.qualifiedName}#${method.name} at line $lineNumber"
            }
        }
    }
}
