/**
 * Represents the gutter icon that is used to generate a MongoDB query in shell syntax
 * and run it into a Datagrip console.
 */

package com.mongodb.jbplugin.codeActions.impl

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.intentions.RunQueryInConsoleIntentionAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchChildBackground
import com.intellij.openapi.rd.util.launchChildOnUi
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.codeActions.AbstractMongoDbCodeActionBridge
import com.mongodb.jbplugin.codeActions.MongoDbCodeAction
import com.mongodb.jbplugin.codeActions.impl.runQuery.RunQueryModal
import com.mongodb.jbplugin.codeActions.sourceForMarker
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.dialects.OutputQuery
import com.mongodb.jbplugin.dialects.javadriver.glossary.findAllChildrenOfType
import com.mongodb.jbplugin.dialects.mongosh.MongoshDialect
import com.mongodb.jbplugin.editor.DatagripConsoleEditor
import com.mongodb.jbplugin.editor.MdbJavaEditorToolbar
import com.mongodb.jbplugin.editor.appendText
import com.mongodb.jbplugin.editor.services.implementations.MdbEditorService
import com.mongodb.jbplugin.i18n.CodeActionsMessages
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryEvent.QueryRunEvent.Console
import com.mongodb.jbplugin.observability.probe.QueryRunProbe
import kotlinx.coroutines.CoroutineScope

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
        dataSource: LocalDataSource?,
        query: Node<PsiElement>,
        formatter: DialectFormatter
    ): LineMarkerInfo<PsiElement> {
        return LineMarkerInfo(
            query.sourceForMarker,
            query.sourceForMarker.textRange,
            Icons.runQueryGutter,
            { CodeActionsMessages.message("code.action.run.query") },
            { _, _ ->
                if (shouldDelegateToIntelliJRunQuery(query)) {
                    delegateRunQueryToIntelliJ(query)
                } else {
                    emitRunQueryEvent(query, dataSource)

                    if (dataSource == null || dataSource.isConnected() == false) {
                        return@LineMarkerInfo
                    }

                    val queryContext = RunQueryModal(
                        query,
                        dataSource,
                        coroutineScope
                    ).askForQueryContext()

                    if (queryContext != null) {
                        coroutineScope.launchChildBackground {
                            val outputQuery = MongoshDialect.formatter.formatQuery(
                                query,
                                queryContext
                            )

                            if (dataSource.isConnected() == true) {
                                coroutineScope.launchChildOnUi {
                                    openDataGripConsole(query, dataSource, outputQuery.query)
                                }
                            } else {
                                openConsoleAfterSelection(
                                    query,
                                    outputQuery,
                                    query.source.project,
                                    coroutineScope
                                )
                            }
                        }
                    }
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
        ApplicationManager.getApplication().invokeLater {
            val editor = DatagripConsoleEditor.openConsoleForDataSource(
                query.source.project,
                newDataSource
            )
            editor?.appendText(formattedQuery)
        }
    }

    private fun openConsoleAfterSelection(
        query: Node<PsiElement>,
        outputQuery: OutputQuery,
        project: Project,
        coroutineScope: CoroutineScope,
    ) {
        coroutineScope.launchChildOnUi {
            val selectedDataSource = MdbJavaEditorToolbar.showModalForSelection(
                query.source.project,
                coroutineScope,
                "Run Query"
            )

            if (selectedDataSource == null) {
                createDataSourceNotSelectedNotification {
                    openConsoleAfterSelection(query, outputQuery, project, coroutineScope)
                }.notify(query.source.project)
            } else {
                openDataGripConsole(query, selectedDataSource, outputQuery.query)
            }
        }
    }

    private fun createDataSourceNotSelectedNotification(onTryAgainAction: () -> Unit): Notification {
        return NotificationGroupManager.getInstance()
            .getNotificationGroup("com.mongodb.jbplugin.notifications.Connection")
            .createNotification(
                CodeActionsMessages.message("code.action.run.query.aborted.notification.title"),
                CodeActionsMessages.message("code.action.run.query.aborted.notification.message"),
                NotificationType.INFORMATION,
            ).addAction(
                NotificationAction.create(
                    CodeActionsMessages.message("code.action.run.query.aborted.notification.action")
                ) { _, notification ->
                    onTryAgainAction()
                    notification.expire()
                }
            )
    }

    private fun shouldDelegateToIntelliJRunQuery(query: Node<PsiElement>): Boolean {
        return query.component<HasSourceDialect>()?.name ==
            HasSourceDialect.DialectName.SPRING_QUERY
    }

    private fun delegateRunQueryToIntelliJ(query: Node<PsiElement>) {
        val editorService by query.source.project.service<MdbEditorService>()
        val editor = editorService.selectedEditor

        if (editor == null) {
            // if we are not in an editor anymore we can't trigger
            // the action
            return
        }

        val queryExpressions = query.source.findAllChildrenOfType(PsiLiteralExpression::class.java)
        if (queryExpressions.size == 1) {
            // we can not do anything, it's not a valid query
            RunQueryInConsoleIntentionAction().invoke(
                query.source.project,
                editor,
                queryExpressions[0]
            )
        }
    }
}
