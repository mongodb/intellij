package com.mongodb.jbplugin.inspections.insightAction

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.findParentOfType
import com.mongodb.jbplugin.dialects.mongosh.MongoshDialect
import com.mongodb.jbplugin.editor.CachedQueryService
import com.mongodb.jbplugin.editor.DatagripConsoleEditor
import com.mongodb.jbplugin.editor.DatagripConsoleEditor.appendText
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.i18n.SidePanelMessages
import com.mongodb.jbplugin.indexing.CollectionIndexConsolidationOptions
import com.mongodb.jbplugin.indexing.IndexAnalyzer
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.observability.probe.CreateIndexIntentionProbe

object CreateSuggestedIndexInsightAction : InsightAction {
    override val displayName = SidePanelMessages.message("insight.action.create-index")

    override suspend fun apply(
        insight: QueryInsight<PsiElement, *>
    ) {
        val createIndexClicked by insight.query.source.project.service<CreateIndexIntentionProbe>()
        createIndexClicked.intentionClicked(insight.query)

        val project = insight.query.source.project
        val dataSource = insight.query.source.containingFile.dataSource ?: return
        val cachedQueryService by project.service<CachedQueryService>()

        val index = IndexAnalyzer.analyze(
            insight.query,
            cachedQueryService,
            CollectionIndexConsolidationOptions(10)
        )

        val codeToAppend = MongoshDialect.formatter.indexCommand(insight.query, index, ::queryReferenceString)

        val editor = DatagripConsoleEditor.openConsoleForDataSource(project, dataSource) ?: return
        editor.appendText(codeToAppend)
    }
}

fun queryReferenceString(query: Node<PsiElement>): String? {
    return ApplicationManager.getApplication().runReadAction<String?> {
        val method = query.source.findParentOfType<PsiMethod>() ?: return@runReadAction null
        val containingClass = method.containingClass ?: return@runReadAction null
        val lineNumber = query.source.containingFile.fileDocument.getLineNumber(
            query.source.textOffset
        ) + 1

        "${containingClass.qualifiedName}#${method.name} at line $lineNumber"
    }
}
