package com.mongodb.jbplugin.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.mongodb.jbplugin.Inspection
import com.mongodb.jbplugin.Inspection.ChooseConnection
import com.mongodb.jbplugin.Inspection.CreateIndex
import com.mongodb.jbplugin.Inspection.NavigateToQuery
import com.mongodb.jbplugin.Inspection.NoAction
import com.mongodb.jbplugin.InspectionHolder
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.editor.CachedQueryService
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.editor.dialect
import com.mongodb.jbplugin.i18n.InspectionsAndInlaysMessages
import com.mongodb.jbplugin.inspections.quickfixes.OpenConnectionChooserQuickFix
import com.mongodb.jbplugin.inspections.quickfixes.OpenConsoleWithNewIndexForQueryQuickFix
import com.mongodb.jbplugin.meta.service
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
abstract class AbstractMongoDbInspectionBridge(
    private val coroutineScope: CoroutineScope,
    private val inspection: MongoDbInspection,
) : AbstractBaseJavaLocalInspectionTool() {
    protected abstract fun emitFinishedInspectionTelemetryEvent(problemsHolder: ProblemsHolder)

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
                    val viewModel by expression.project.service<InspectionViewModel>()
                    val inspectionHolder = IntelliJBasedInspectionHolder(
                        coroutineScope,
                        viewModel,
                        holder
                    )

                    val containingFile =
                        PsiTreeUtil.getParentOfType(expression, PsiFile::class.java)
                            ?: return@runReadAction
                    val dataSource = containingFile.dataSource
                    val dialect = containingFile.dialect ?: return@runReadAction

                    val queryService by expression.project.service<CachedQueryService>()
                    val query = queryService.queryAt(expression)

                    if (query != null &&
                        containingFile.virtualFile != null &&
                        dataSource?.isConnected() == true
                    ) {
                        runBlocking {
                            inspection.visitMongoDbQuery(
                                coroutineScope,
                                dataSource,
                                inspectionHolder,
                                query,
                                dialect.formatter,
                            )
                        }
                    }
                }
            }
        }
}

@Service(Service.Level.PROJECT)
class InspectionViewModel(
    private val coroutineScope: CoroutineScope
) : LocalInspectionTool() {
    private val inspections = Dispatchers.IO.limitedParallelism(1)

    val allInspections = MutableStateFlow(emptyList<Inspection<PsiElement>>())
    private val currentSession = mutableListOf<Inspection<PsiElement>>()

    override fun inspectionStarted(
        session: LocalInspectionToolSession,
        isOnTheFly: Boolean
    ) {
        super.inspectionStarted(session, isOnTheFly)
        coroutineScope.launch(inspections) {
            currentSession.clear()
        }
    }

    override fun inspectionFinished(
        session: LocalInspectionToolSession,
        problemsHolder: ProblemsHolder
    ) {
        coroutineScope.launch(inspections) {
            allInspections.emit(currentSession)
            currentSession.clear()
        }

        super.inspectionFinished(session, problemsHolder)
    }

    suspend fun addInspection(inspection: Inspection<PsiElement>) {
        withContext(inspections) {
            currentSession.add(inspection)
        }
    }
}

class IntelliJBasedInspectionHolder(
    private val coroutineScope: CoroutineScope,
    private val viewModel: InspectionViewModel,
    private val problemsHolder: ProblemsHolder,
) : InspectionHolder<PsiElement> {
    override suspend fun register(inspection: Inspection<PsiElement>) {
        viewModel.addInspection(inspection)

        withContext(Dispatchers.EDT) {
            readAction {
                val message = InspectionsAndInlaysMessages.message(
                    inspection.description,
                    *inspection.descriptionArguments.toTypedArray()
                )
                if (problemsHolder.results.firstOrNull {
                        it.psiElement.isEquivalentTo(inspection.source) &&
                            it.descriptionTemplate == message
                    } !=
                    null
                ) {
                    return@readAction
                }

                problemsHolder.registerProblem(
                    inspection.source,
                    InspectionsAndInlaysMessages.message(
                        inspection.description,
                        *inspection.descriptionArguments.toTypedArray()
                    ),
                    *intellijQuickFixesFromInspection(inspection)
                )
            }
        }
    }

    private fun intellijQuickFixesFromInspection(inspection: Inspection<PsiElement>): Array<LocalQuickFix> {
        return when (inspection.action) {
            ChooseConnection -> arrayOf(OpenConnectionChooserQuickFix(coroutineScope))
            CreateIndex -> arrayOf(
                OpenConsoleWithNewIndexForQueryQuickFix(coroutineScope, inspection.query)
            )
            NavigateToQuery -> emptyArray()
            NoAction -> emptyArray()
        }
    }
}
