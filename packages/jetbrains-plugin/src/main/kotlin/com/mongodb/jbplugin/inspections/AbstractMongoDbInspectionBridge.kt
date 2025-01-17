package com.mongodb.jbplugin.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.database.dataSource.localDataSource
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.mongodb.jbplugin.editor.CachedQueryService
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.editor.dialect
import com.mongodb.jbplugin.meta.service
import kotlinx.coroutines.CoroutineScope

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
                                holder,
                                query,
                                dialect.formatter,
                            )
                        } else {
                            inspection.visitMongoDbQuery(
                                coroutineScope,
                                null,
                                holder,
                                query,
                                dialect.formatter
                            )
                        }
                    }
                }
            }
        }
}
