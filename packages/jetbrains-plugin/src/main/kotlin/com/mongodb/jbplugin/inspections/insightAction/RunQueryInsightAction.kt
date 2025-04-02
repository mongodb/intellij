package com.mongodb.jbplugin.inspections.insightAction

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.codeActions.impl.runQuery.RunQueryModal
import com.mongodb.jbplugin.dialects.mongosh.MongoshDialect
import com.mongodb.jbplugin.editor.DatagripConsoleEditor
import com.mongodb.jbplugin.editor.appendText
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.i18n.SidePanelMessages
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.meta.withinReadAction
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryEvent.QueryRunEvent.Console
import com.mongodb.jbplugin.observability.probe.QueryRunProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RunQueryInsightAction(
    private val coroutineScope: CoroutineScope,
    private val runQueryIntention: QueryRunProbe,
    private val consoleEditor: DatagripConsoleEditor
) : InsightAction {
    override val displayName = SidePanelMessages.message("insight.action.run-query")

    override suspend fun apply(insight: QueryInsight<PsiElement, *>) {
        val query = insight.query
        val dataSource = withinReadAction { query.source.containingFile.dataSource } ?: return

        val queryContext = withContext(Dispatchers.EDT) {
            RunQueryModal(query, dataSource, coroutineScope).askForQueryContext()
        } ?: return

        val outputQuery = withinReadAction {
            MongoshDialect.formatter.formatQuery(
                insight.query,
                queryContext
            )
        }

        withContext(Dispatchers.EDT) {
            openDataGripConsole(query, dataSource, outputQuery.query)
        }

        runQueryIntention.queryRunRequested(query, Console.EXISTING, TelemetryEvent.QueryRunEvent.TriggerLocation.SIDE_PANEL)
    }

    private fun openDataGripConsole(
        query: Node<PsiElement>,
        dataSource: LocalDataSource,
        formattedQuery: String
    ) {
        ApplicationManager.getApplication().invokeLater {
            val editor = consoleEditor.openConsoleForDataSource(
                query.source.project,
                dataSource
            )
            editor?.appendText(formattedQuery)
        }
    }
}
