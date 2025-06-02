/**
 * Represents the gutter icon that is used to generate a MongoDB query in shell syntax
 * and run it into a Datagrip console.
 */

package com.mongodb.jbplugin.codeActions.impl

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.codeActions.AbstractMongoDbCodeActionBridge
import com.mongodb.jbplugin.codeActions.MongoDbCodeAction
import com.mongodb.jbplugin.codeActions.impl.runQuery.RunQueryModal
import com.mongodb.jbplugin.codeActions.sourceForMarker
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.dialects.mongosh.MongoshDialect
import com.mongodb.jbplugin.editor.DatagripConsoleEditor
import com.mongodb.jbplugin.editor.appendText
import com.mongodb.jbplugin.i18n.CodeActionsMessages
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.meta.invokeInEdt
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryEvent.QueryRunEvent.Console
import com.mongodb.jbplugin.observability.probe.QueryRunProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Bridge class that connects our query action with IntelliJ.
 *
 * @param coroutineScope
 */
class RunQueryCodeActionBridge(coroutineScope: CoroutineScope) :
    AbstractMongoDbCodeActionBridge(
        coroutineScope,
        RunQueryCodeAction
    )

/**
 * Actual implementation of the code action.
 */
internal object RunQueryCodeAction : MongoDbCodeAction {
    override fun visitMongoDbQuery(
        coroutineScope: CoroutineScope,
        dataSource: LocalDataSource,
        query: Node<PsiElement>,
        formatter: DialectFormatter
    ): LineMarkerInfo<PsiElement> {
        return LineMarkerInfo(
            query.sourceForMarker,
            query.sourceForMarker.textRange,
            Icons.runQueryGutter,
            { CodeActionsMessages.message("code.action.run.query") },
            { _, _ ->
                emitRunQueryEvent(query, dataSource)

                if (!dataSource.isConnected()) {
                    return@LineMarkerInfo
                }

                val modal = RunQueryModal(query, dataSource, coroutineScope)
                val queryContext = modal.askForQueryContext() ?: return@LineMarkerInfo

                coroutineScope.launch(Dispatchers.IO) {
                    val outputQuery = MongoshDialect.formatter.formatQuery(query, queryContext)
                    invokeInEdt { openDataGripConsole(query, dataSource, outputQuery.query) }
                }
            },
            GutterIconRenderer.Alignment.RIGHT,
            { CodeActionsMessages.message("code.action.run.query") }
        )
    }

    private fun emitRunQueryEvent(
        query: Node<PsiElement>,
        dataSource: LocalDataSource?
    ) {
        val hasConsole = DatagripConsoleEditor.isThereAnEditorForDataSource(dataSource)

        val probe by service<QueryRunProbe>()
        probe.queryRunRequested(
            query,
            if (hasConsole) Console.EXISTING else Console.NEW,
            TelemetryEvent.QueryRunEvent.TriggerLocation.GUTTER
        )
    }

    private fun openDataGripConsole(
        query: Node<PsiElement>,
        newDataSource: LocalDataSource,
        formattedQuery: String
    ) {
        invokeLater {
            val editor = DatagripConsoleEditor.openConsoleForDataSource(
                query.source.project,
                newDataSource
            )
            editor?.appendText(formattedQuery)
        }
    }
}
