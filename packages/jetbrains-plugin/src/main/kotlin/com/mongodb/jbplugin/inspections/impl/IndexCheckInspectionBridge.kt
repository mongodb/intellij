/**
 * This inspection is used for index checking. It warns if a query is not using a
 * proper index.
 */

package com.mongodb.jbplugin.inspections.impl

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.dialects.mongosh.MongoshDialect
import com.mongodb.jbplugin.i18n.InspectionsAndInlaysMessages
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridge
import com.mongodb.jbplugin.inspections.MongoDbInspection
import com.mongodb.jbplugin.inspections.quickfixes.OpenDataSourceConsoleAppendingCode
import com.mongodb.jbplugin.linting.IndexCheckWarning
import com.mongodb.jbplugin.linting.IndexCheckingLinter
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.observability.probe.CreateIndexIntentionProbe
import kotlinx.coroutines.CoroutineScope

/**
 * @param coroutineScope
 */
class IndexCheckInspectionBridge(coroutineScope: CoroutineScope) :
    AbstractMongoDbInspectionBridge(
        coroutineScope,
        IndexCheckLinterInspection,
    )

/**
 * This inspection object calls the linting engine and transforms the result so they can be rendered in the IntelliJ
 * editor.
 */
internal object IndexCheckLinterInspection : MongoDbInspection {
    override fun visitMongoDbQuery(
        coroutineScope: CoroutineScope,
        dataSource: LocalDataSource?,
        problems: ProblemsHolder,
        query: Node<PsiElement>,
        formatter: DialectFormatter,
    ) {
        if (dataSource == null || !dataSource.isConnected()) {
            return
        }

        val readModelProvider by query.source.project.service<DataGripBasedReadModelProvider>()
        val result = IndexCheckingLinter.lintQuery(dataSource, readModelProvider, query)

        result.warnings.forEach {
            when (it) {
                is IndexCheckWarning.QueryNotCoveredByIndex ->
                    registerQueryNotCoveredByIndex(coroutineScope, dataSource, problems, query)
            }
        }
    }

    private fun registerQueryNotCoveredByIndex(
        coroutineScope: CoroutineScope,
        localDataSource: LocalDataSource,
        problems: ProblemsHolder,
        query: Node<PsiElement>
    ) {
        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.index.checking.error.query.not.covered.by.index",
        )

        problems.registerProblem(
            query.source,
            problemDescription,
            ProblemHighlightType.WARNING,
            OpenDataSourceConsoleAppendingCode(
                coroutineScope,
                InspectionsAndInlaysMessages.message(
                    "inspection.index.checking.error.query.not.covered.by.index.quick.fix"
                ),
                localDataSource
            ) {
                val createIndexClicked by query.source.project.service<CreateIndexIntentionProbe>()
                createIndexClicked.intentionClicked(query)

                MongoshDialect.formatter.indexCommandForQuery(query)
            }
        )
    }
}
