package com.mongodb.jbplugin.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.psi.*
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.editor.CachedQueryService
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.editor.dialect
import com.mongodb.jbplugin.i18n.InspectionsAndInlaysMessages
import com.mongodb.jbplugin.inspections.quickfixes.CreateSuggestedIndexQuickFix
import com.mongodb.jbplugin.linting.Inspection
import com.mongodb.jbplugin.linting.InspectionAction
import com.mongodb.jbplugin.linting.InspectionAction.ChooseConnection
import com.mongodb.jbplugin.linting.InspectionAction.CreateIndexSuggestionScript
import com.mongodb.jbplugin.linting.InspectionAction.NoAction
import com.mongodb.jbplugin.linting.InspectionAction.RunQuery
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.QueryInsightsHolder
import com.mongodb.jbplugin.linting.QueryInspection
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * This class is used to connect a MongoDB inspection to IntelliJ.
 * It's responsible for getting the dialect of the current file and
 * do the necessary dependency injection to make the inspection work.
 *
 * Usually you won't reimplement methods, just create a new empty class
 * that provides the inspection implementation, in the same file.
 *
 * @see com.mongodb.jbplugin.inspections.impl.FieldCheckInspectionBridge as an example
 *
 * @param inspection
 * @param coroutineScope
 */
abstract class AbstractMongoDbInspectionBridgeV2<Settings, I : Inspection>(
    private val coroutineScope: CoroutineScope,
    private val inspection: QueryInspection<Settings, I>,
) : AbstractBaseJavaLocalInspectionTool() {
    protected abstract fun buildSettings(query: Node<PsiElement>): Settings
    protected abstract fun emitFinishedInspectionTelemetryEvent(problemsHolder: ProblemsHolder)
    protected open fun afterInsight(queryInsight: QueryInsight<PsiElement, I>) {}

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession,
    ): PsiElementVisitor =
        object : JavaElementVisitor() {
            override fun visitJavaFile(file: PsiJavaFile) {
                super.visitJavaFile(file)
                emitFinishedInspectionTelemetryEvent(holder)
            }

            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                dispatchIfValidMongoDbQuery(expression)
            }

            override fun visitMethod(method: PsiMethod) {
                dispatchIfValidMongoDbQuery(method)
            }

            private fun dispatchIfValidMongoDbQuery(expression: PsiElement) {
                ApplicationManager.getApplication().runReadAction {
                    val fileInExpression = runCatching { expression.containingFile }.getOrNull()
                    val dataSource = fileInExpression?.dataSource ?: return@runReadAction
                    fileInExpression.dialect ?: return@runReadAction

                    val queryService by expression.project.service<CachedQueryService>()
                    val query = queryService.queryAt(expression) ?: return@runReadAction

                    if (fileInExpression.virtualFile != null && dataSource.isConnected()) {
                        val settings = buildSettings(query)
                        val problems = IntelliJBasedQueryInsightsHolder(coroutineScope, holder, ::afterInsight)

                        runBlocking { inspection.run(query, problems, settings) }
                    }
                }
            }
        }
}

class IntelliJBasedQueryInsightsHolder<I : Inspection>(
    private val coroutineScope: CoroutineScope,
    private val problemsHolder: ProblemsHolder,
    private val onAfterInsight: (QueryInsight<PsiElement, I>) -> Unit
) : QueryInsightsHolder<PsiElement, I> {
    override suspend fun register(insight: QueryInsight<PsiElement, I>) {
        withContext(Dispatchers.EDT) {
            val problemDescription = InspectionsAndInlaysMessages.message(insight.description, *insight.descriptionArguments.toTypedArray())
            problemsHolder.registerProblem(
                insight.query.source,
                problemDescription,
                ProblemHighlightType.WARNING,
                *toQuickFixes(insight)
            )
            onAfterInsight(insight)
        }
    }

    private fun toQuickFixes(insight: QueryInsight<PsiElement, I>): Array<LocalQuickFix> {
        val allActions = listOf(insight.inspection.primaryAction) + insight.inspection.secondaryActions
        return allActions.mapNotNull { toQuickFix(insight, it) }.toTypedArray()
    }

    private fun toQuickFix(insight: QueryInsight<PsiElement, I>, action: InspectionAction): LocalQuickFix? {
        return when (action) {
            NoAction -> null
            RunQuery -> null
            ChooseConnection -> null
            CreateIndexSuggestionScript -> CreateSuggestedIndexQuickFix(
                coroutineScope,
                insight.query.source.containingFile.dataSource!!,
                insight.query
            )
        }
    }
}
