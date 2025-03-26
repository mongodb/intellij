package com.mongodb.jbplugin.inspections.insightAction

import com.intellij.openapi.application.EDT
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.findParentOfType
import com.mongodb.jbplugin.dialects.mongosh.MongoshDialect
import com.mongodb.jbplugin.editor.CachedQueryService
import com.mongodb.jbplugin.editor.DatagripConsoleEditor
import com.mongodb.jbplugin.editor.appendText
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.i18n.SidePanelMessages
import com.mongodb.jbplugin.indexing.CollectionIndexConsolidationOptions
import com.mongodb.jbplugin.indexing.IndexAnalyzer
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.meta.withinReadAction
import com.mongodb.jbplugin.meta.withinReadActionBlocking
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.observability.probe.CreateIndexIntentionProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CreateSuggestedIndexInsightAction(
    private val createIndexIntention: CreateIndexIntentionProbe,
    private val consoleEditor: DatagripConsoleEditor
) : InsightAction {
    override val displayName = SidePanelMessages.message("insight.action.create-index")

    override suspend fun apply(
        insight: QueryInsight<PsiElement, *>
    ) {
        createIndexIntention.intentionClicked(insight.query)

        val project = insight.query.source.project
        val dataSource = withinReadAction { insight.query.source.containingFile.dataSource } ?: return
        val cachedQueryService by project.service<CachedQueryService>()

        val codeToAppend = withinReadAction {
            val index = IndexAnalyzer.analyze(
                insight.query,
                cachedQueryService,
                CollectionIndexConsolidationOptions(10)
            )

            MongoshDialect.formatter.indexCommand(insight.query, index, ::queryReferenceString)
        }

        withContext(Dispatchers.EDT) {
            val editor = consoleEditor.openConsoleForDataSource(project, dataSource)
            editor?.appendText(codeToAppend)
        }
    }
}

private fun queryReferenceString(query: Node<PsiElement>): String? {
    return withinReadActionBlocking {
        val method = query.source.findParentOfType<PsiMethod>() ?: return@withinReadActionBlocking null
        val containingClass = method.containingClass ?: return@withinReadActionBlocking null
        val lineNumber = query.source.containingFile.fileDocument.getLineNumber(
            query.source.textOffset
        ) + 1

        "${containingClass.qualifiedName}#${method.name} at line $lineNumber"
    }
}
