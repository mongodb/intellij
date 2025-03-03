package com.mongodb.jbplugin.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.database.dataSource.localDataSource
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.startOffset
import com.mongodb.jbplugin.Inspection
import com.mongodb.jbplugin.InspectionHolder
import com.mongodb.jbplugin.editor.CachedQueryService
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.editor.dialect
import com.mongodb.jbplugin.meta.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.collections.set

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
                    val inspectionHolder by expression.project.service<IntelliJBasedInspectionHolder>()
                    inspectionHolder.setProblemsHolder(holder)

                    val fileInExpression =
                        PsiTreeUtil.getParentOfType(expression, PsiFile::class.java)
                            ?: return@runReadAction
                    val dataSource = fileInExpression.dataSource
                    val dialect = expression.containingFile.dialect ?: return@runReadAction

                    val queryService by expression.project.service<CachedQueryService>()
                    val query = queryService.queryAt(expression)

                    if (query != null) {
                        if (fileInExpression.virtualFile != null) {
                            inspection.visitMongoDbQuery(
                                coroutineScope,
                                dataSource?.localDataSource,
                                inspectionHolder,
                                query,
                                dialect.formatter,
                            )
                        } else {
                            inspection.visitMongoDbQuery(
                                coroutineScope,
                                null,
                                inspectionHolder,
                                query,
                                dialect.formatter
                            )
                        }
                    }
                }
            }
        }
}

@Service(Service.Level.PROJECT)
class IntelliJBasedInspectionHolder(
    val coroutineScope: CoroutineScope
) : LocalInspectionTool(), InspectionHolder<LocalInspectionToolSession, PsiElement> {
    override val allInspections = MutableStateFlow(emptyList<Inspection<PsiElement>>())
    private val sessionContext: MutableMap<String, MutableList<Inspection<PsiElement>>> =
        mutableMapOf()
    private lateinit var problemsHolder: ProblemsHolder

    override suspend fun inspectionBegin(context: LocalInspectionToolSession) {
        readAction {
            sessionContext[context.file.name] = mutableListOf()
        }
    }

    override fun inspectionStarted(
        session: LocalInspectionToolSession,
        isOnTheFly: Boolean
    ) {
        super.inspectionStarted(session, isOnTheFly)
        coroutineScope.launch {
            inspectionBegin(session)
        }
    }

    override fun inspectionFinished(
        session: LocalInspectionToolSession,
        problemsHolder: ProblemsHolder
    ) {
        super.inspectionFinished(session, problemsHolder)
        coroutineScope.launch {
            inspectionBegin(session)
        }
    }

    override suspend fun inspectionEnd(context: LocalInspectionToolSession) {
        val (snapshot, currentState) = readAction {
            val snapshot = ArrayList(allInspections.value)
            val currentState = allInspections.value.toMutableList()
            currentState.removeAll {
                it.query.source.containingFile.name == context.file.name
            }

            currentState.addAll(sessionContext[context.file.name] ?: emptyList())
            currentState.sortWith(
                compareBy({
                    it.query.source.containingFile.name
                }, { it.query.source.startOffset })
            )

            snapshot to currentState
        }

        if (currentState != snapshot) {
            allInspections.emit(currentState)
        }
    }

    fun setProblemsHolder(holder: ProblemsHolder) {
        problemsHolder = holder
    }

    override suspend fun register(inspection: Inspection<PsiElement>) {
        readAction {
            val contextName = inspection.query.source.containingFile.name
            sessionContext.putIfAbsent(contextName, mutableListOf())

            problemsHolder.registerProblem(
                inspection.query.source,
                inspection.description
            )

            sessionContext[contextName]?.add(inspection)
        }
    }
}
