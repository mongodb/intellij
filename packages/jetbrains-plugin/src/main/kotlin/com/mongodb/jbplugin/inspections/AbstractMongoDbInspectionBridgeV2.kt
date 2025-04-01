package com.mongodb.jbplugin.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
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
import com.mongodb.jbplugin.inspections.quickfixes.LocalQuickFixBridge
import com.mongodb.jbplugin.linting.Inspection
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.QueryInsightsHolder
import com.mongodb.jbplugin.linting.QueryInspection
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.ui.viewModel.InspectionsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * This class is used to connect a MongoDB inspection to IntelliJ.
 * It's responsible for getting the dialect of the current file and
 * do the necessary dependency injection to make the inspection work.
 *
 * @param coroutineScope
 * @param queryInspection
 */
abstract class AbstractMongoDbInspectionBridgeV2<Settings, I : Inspection>(
    private val coroutineScope: CoroutineScope,
    private val queryInspection: QueryInspection<Settings, I>,
    private val inspection: I
) : AbstractBaseJavaLocalInspectionTool() {
    protected abstract fun buildSettings(query: Node<PsiElement>): Settings
    protected abstract fun emitFinishedInspectionTelemetryEvent(problemsHolder: ProblemsHolder)
    protected open fun afterInsight(queryInsight: QueryInsight<PsiElement, I>) {}

    override fun inspectionStarted(
        session: LocalInspectionToolSession,
        isOnTheFly: Boolean
    ) {
        val inspectionViewModel by session.file.project.service<InspectionsViewModel>()
        inspectionViewModel.startInspectionSessionOf(session.file, inspection)
    }

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

                    val inspectionsViewModel by expression.project.service<InspectionsViewModel>()
                    val queryService by expression.project.service<CachedQueryService>()
                    val query = queryService.queryAt(expression) ?: return@runReadAction

                    if (fileInExpression.virtualFile != null && dataSource.isConnected()) {
                        val settings = buildSettings(query)
                        val problems = IntelliJBasedQueryInsightsHolder(coroutineScope, holder, inspectionsViewModel, ::afterInsight)

                        runBlocking { queryInspection.run(query, problems, settings) }
                    }
                }
            }
        }
}

internal class IntelliJBasedQueryInsightsHolder<I : Inspection>(
    private val coroutineScope: CoroutineScope,
    private val problemsHolder: ProblemsHolder,
    private val inspectionsViewModel: InspectionsViewModel,
    private val onAfterInsight: (QueryInsight<PsiElement, I>) -> Unit
) : QueryInsightsHolder<PsiElement, I> {
    override suspend fun register(insight: QueryInsight<PsiElement, I>) {
        coroutineScope.launch {
            // run and forget
            inspectionsViewModel.addInsight(insight)
        }

        withContext(Dispatchers.EDT) {
            val problemDescription = InspectionsAndInlaysMessages.message(insight.description, *insight.descriptionArguments.toTypedArray())
            problemsHolder.registerProblem(
                insight.query.source,
                problemDescription,
                ProblemHighlightType.WARNING,
                *LocalQuickFixBridge.allQuickFixes(insight)
            )
            onAfterInsight(insight)
        }
    }
}
