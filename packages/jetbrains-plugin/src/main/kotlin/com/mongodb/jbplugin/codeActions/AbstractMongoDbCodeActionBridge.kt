package com.mongodb.jbplugin.codeActions

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import com.intellij.psi.util.firstLeaf
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.editor.CachedQueryService
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.editor.dialect
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.meta.withinReadActionBlocking
import com.mongodb.jbplugin.mql.Node
import kotlinx.coroutines.CoroutineScope

/**
 * Line markers need to be set up in a leaf node of the tree. Use this instead of
 * Node.source when using it to create a marker. If you want to know more about the details
 * of this quirk, take a look at the documentation of LineMarkerProvider.getLineMarkerInfo.
 *
 * We are choosing the first leaf because essentially it's the method reference, so the marker
 * will show in the line where you have the specific query method, not in the parameters or
 * somewhere else.
 *
 * @see com.intellij.codeInsight.daemon.LineMarkerProvider.getLineMarkerInfo
 */
internal val Node<PsiElement>.sourceForMarker: PsiElement
    get() = this.source.firstLeaf()

/**
 * This class is used to connect a MongoDB query action to IntelliJ.
 * It's responsible for getting the dialect of the current file and
 * do the necessary dependency injection to make the query action work.
 *
 * Usually you won't reimplement methods, just create a new empty class
 * that provides the inspection implementation, in the same file.
 *
 * @see com.mongodb.jbplugin.codeActions.impl.RunQueryCodeActionBridge
 *
 * @param codeAction
 * @param coroutineScope
 */
abstract class AbstractMongoDbCodeActionBridge(
    private val coroutineScope: CoroutineScope,
    private val codeAction: MongoDbCodeAction,
) : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement) = dispatchIfValidMongoDbQuery(element)

    private fun dispatchIfValidMongoDbQuery(expression: PsiElement): LineMarkerInfo<PsiElement>? {
        return withinReadActionBlocking {
            val fileInExpression = getParentOfType(expression, PsiFile::class.java)
                ?: return@withinReadActionBlocking null

            val dataSource = fileInExpression.dataSource
            val dialect = expression.containingFile.dialect ?: return@withinReadActionBlocking null

            val queryService by expression.project.service<CachedQueryService>()
            val query = queryService.queryAt(expression)
            if (query == null || !query.isSupportedBlocking() || dataSource?.isConnected() != true) {
                return@withinReadActionBlocking null
            }

            codeAction.visitMongoDbQuery(
                coroutineScope,
                dataSource,
                query,
                dialect.formatter
            )
        }
    }
}
