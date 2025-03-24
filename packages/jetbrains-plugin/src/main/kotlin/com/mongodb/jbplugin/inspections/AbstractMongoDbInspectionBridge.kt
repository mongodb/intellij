package com.mongodb.jbplugin.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.mongodb.jbplugin.Inspection
import com.mongodb.jbplugin.Inspection.ChooseConnection
import com.mongodb.jbplugin.Inspection.CreateIndex
import com.mongodb.jbplugin.Inspection.NavigateToQuery
import com.mongodb.jbplugin.Inspection.NoAction
import com.mongodb.jbplugin.Inspection.RunQuery
import com.mongodb.jbplugin.QueryInsight
import com.mongodb.jbplugin.QueryInsightsHolder
import com.mongodb.jbplugin.QueryInspection
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.editor.CachedQueryService
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.i18n.InspectionsAndInlaysMessages
import com.mongodb.jbplugin.inspections.quickfixes.OpenConnectionChooserQuickFix
import com.mongodb.jbplugin.inspections.quickfixes.OpenConsoleWithNewIndexForQueryQuickFix
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

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
abstract class AbstractMongoDbInspectionBridge<Settings, I : Inspection>(
    private val coroutineScope: CoroutineScope,
    private val inspection: QueryInspection<Settings, I>,
) : AbstractBaseJavaLocalInspectionTool() {
    private val isRunning = AtomicBoolean(false)

    protected abstract fun buildSettings(query: Node<PsiElement>): Settings

    protected abstract fun emitFinishedInspectionTelemetryEvent(problemsHolder: ProblemsHolder)

    override fun inspectionFinished(
        session: LocalInspectionToolSession,
        problemsHolder: ProblemsHolder
    ) {
        super.inspectionFinished(session, problemsHolder)
        emitFinishedInspectionTelemetryEvent(problemsHolder)
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession,
    ): PsiElementVisitor =
        object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                dispatchIfValidMongoDbQuery(expression)
            }

            override fun visitMethod(method: PsiMethod) {
                dispatchIfValidMongoDbQuery(method)
            }

            private fun dispatchIfValidMongoDbQuery(expression: PsiElement) {
                ApplicationManager.getApplication().runReadAction {
                    val viewModel by expression.project.service<InspectionsViewModel<I>>()
                    val insightsHolder = IntelliJBasedQueryInsightsHolder<I>(
                        coroutineScope,
                        viewModel,
                        holder
                    )

                    val containingFile =
                        PsiTreeUtil.getParentOfType(expression, PsiFile::class.java)
                            ?: return@runReadAction

                    val dataSource = containingFile.dataSource
                    val queryService by expression.project.service<CachedQueryService>()
                    val query = queryService.queryAt(expression)

                    if (query != null &&
                        containingFile.virtualFile != null &&
                        dataSource?.isConnected() == true
                    ) {
                        runBlocking {
                            inspection.run(
                                query,
                                insightsHolder,
                                buildSettings(query)
                            )
                        }
                    }
                }
            }
        }
}

class IntelliJBasedQueryInsightsHolder<I : Inspection>(
    private val coroutineScope: CoroutineScope,
    private val inspectionsViewModel: InspectionsViewModel<I>,
    private val problemsHolder: ProblemsHolder,
) : QueryInsightsHolder<PsiElement, I> {
    override suspend fun register(queryInsight: QueryInsight<PsiElement, I>) {
        inspectionsViewModel.addInsight(queryInsight)

        withContext(Dispatchers.EDT) {
            readAction {
                val message = InspectionsAndInlaysMessages.message(
                    queryInsight.description,
                    *queryInsight.descriptionArguments.toTypedArray()
                )
                if (problemsHolder.results.firstOrNull {
                        it.psiElement.isEquivalentTo(queryInsight.source) &&
                            it.descriptionTemplate == message
                    } !=
                    null
                ) {
                    return@readAction
                }

                problemsHolder.registerProblem(
                    queryInsight.source,
                    InspectionsAndInlaysMessages.message(
                        queryInsight.description,
                        *queryInsight.descriptionArguments.toTypedArray()
                    ),
                    *intellijQuickFixesFromInspection(queryInsight)
                )
            }
        }
    }

    private fun intellijQuickFixesFromInspection(queryInsight: QueryInsight<PsiElement, I>): Array<LocalQuickFix> {
        return when (queryInsight.inspection.action) {
            ChooseConnection -> arrayOf(OpenConnectionChooserQuickFix(coroutineScope))
            CreateIndex -> arrayOf(
                OpenConsoleWithNewIndexForQueryQuickFix(coroutineScope, queryInsight.query)
            )
            NavigateToQuery -> emptyArray()
            NoAction -> emptyArray()
            RunQuery -> arrayOf(
                // RunQueryQuickFix here
            )
        }
    }
}
