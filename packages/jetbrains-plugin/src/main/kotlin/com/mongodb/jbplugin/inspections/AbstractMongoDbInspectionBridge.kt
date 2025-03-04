package com.mongodb.jbplugin.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.mongodb.jbplugin.QueryInspection
import com.mongodb.jbplugin.QueryInspectionHolder
import com.mongodb.jbplugin.QueryInspectionResult
import com.mongodb.jbplugin.QueryInspectionResult.ChooseConnection
import com.mongodb.jbplugin.QueryInspectionResult.CreateIndex
import com.mongodb.jbplugin.QueryInspectionResult.NavigateToQuery
import com.mongodb.jbplugin.QueryInspectionResult.NoAction
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
abstract class AbstractMongoDbInspectionBridge<Settings>(
    private val coroutineScope: CoroutineScope,
    private val inspection: QueryInspection<Settings>,
) : AbstractBaseJavaLocalInspectionTool() {

    protected abstract fun buildSettings(query: Node<PsiElement>): Settings

    protected abstract fun emitFinishedInspectionTelemetryEvent(problemsHolder: ProblemsHolder)

    override fun inspectionStarted(
        session: LocalInspectionToolSession,
        isOnTheFly: Boolean
    ) {
        val viewModel by session.file.project.service<InspectionViewModel>()

        super.inspectionStarted(session, isOnTheFly)
        coroutineScope.launch(viewModel.inspections) {
            viewModel.currentSession.clear()
        }
    }

    override fun inspectionFinished(
        session: LocalInspectionToolSession,
        problemsHolder: ProblemsHolder
    ) {
        val viewModel by session.file.project.service<InspectionViewModel>()
        coroutineScope.launch(viewModel.inspections) {
            if (viewModel.allInspections.value != viewModel.currentSession) {
                viewModel.allInspections.emit(viewModel.currentSession)
            }
        }

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
                    val viewModel by expression.project.service<InspectionViewModel>()
                    val inspectionHolder = IntelliJBasedQueryInspectionHolder(
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
                                inspectionHolder,
                                buildSettings(query)
                            )
                        }
                    }
                }
            }
        }
}

@Service(Service.Level.PROJECT)
class InspectionViewModel {
    internal val inspections = Dispatchers.IO.limitedParallelism(1)
    internal val currentSession = mutableListOf<QueryInspectionResult<PsiElement>>()
    internal val allInspections = MutableStateFlow(emptyList<QueryInspectionResult<PsiElement>>())

    suspend fun addInspection(queryInspectionResult: QueryInspectionResult<PsiElement>) {
        withContext(inspections) {
            currentSession.add(queryInspectionResult)
        }
    }
}

class IntelliJBasedQueryInspectionHolder(
    private val coroutineScope: CoroutineScope,
    private val viewModel: InspectionViewModel,
    private val problemsHolder: ProblemsHolder,
) : QueryInspectionHolder<PsiElement> {
    override suspend fun register(queryInspectionResult: QueryInspectionResult<PsiElement>) {
        viewModel.addInspection(queryInspectionResult)

        withContext(Dispatchers.EDT) {
            readAction {
                val message = InspectionsAndInlaysMessages.message(
                    queryInspectionResult.description,
                    *queryInspectionResult.descriptionArguments.toTypedArray()
                )
                if (problemsHolder.results.firstOrNull {
                        it.psiElement.isEquivalentTo(queryInspectionResult.source) &&
                            it.descriptionTemplate == message
                    } !=
                    null
                ) {
                    return@readAction
                }

                problemsHolder.registerProblem(
                    queryInspectionResult.source,
                    InspectionsAndInlaysMessages.message(
                        queryInspectionResult.description,
                        *queryInspectionResult.descriptionArguments.toTypedArray()
                    ),
                    *intellijQuickFixesFromInspection(queryInspectionResult)
                )
            }
        }
    }

    private fun intellijQuickFixesFromInspection(queryInspectionResult: QueryInspectionResult<PsiElement>): Array<LocalQuickFix> {
        return when (queryInspectionResult.action) {
            ChooseConnection -> arrayOf(OpenConnectionChooserQuickFix(coroutineScope))
            CreateIndex -> arrayOf(
                OpenConsoleWithNewIndexForQueryQuickFix(coroutineScope, queryInspectionResult.query)
            )
            NavigateToQuery -> emptyArray()
            NoAction -> emptyArray()
        }
    }
}
