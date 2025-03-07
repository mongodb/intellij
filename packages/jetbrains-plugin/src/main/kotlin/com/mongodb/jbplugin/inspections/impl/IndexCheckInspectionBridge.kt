/**
 * This inspection is used for index checking. It warns if a query is not using a
 * proper index.
 */

package com.mongodb.jbplugin.inspections.impl

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.findParentOfType
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.dialects.mongosh.MongoshDialect
import com.mongodb.jbplugin.editor.CachedQueryService
import com.mongodb.jbplugin.i18n.InspectionsAndInlaysMessages
import com.mongodb.jbplugin.indexing.CollectionIndexConsolidationOptions
import com.mongodb.jbplugin.indexing.IndexAnalyzer
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridge
import com.mongodb.jbplugin.inspections.MongoDbInspection
import com.mongodb.jbplugin.inspections.quickfixes.OpenDataSourceConsoleAppendingCode
import com.mongodb.jbplugin.linting.IndexCheckWarning
import com.mongodb.jbplugin.linting.IndexCheckingLinter
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.components.HasExplain
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.probe.CreateIndexIntentionProbe
import com.mongodb.jbplugin.observability.probe.InspectionStatusChangedProbe
import com.mongodb.jbplugin.settings.pluginSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.Nls

class IndexCheckInspectionBridge(coroutineScope: CoroutineScope) :
    AbstractMongoDbInspectionBridge(
        coroutineScope,
        IndexCheckLinterInspection,
    ) {
    override fun emitFinishedInspectionTelemetryEvent(problemsHolder: ProblemsHolder) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.finishedProcessingInspections(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.FIELD_DOES_NOT_EXIST,
            problemsHolder,
        )
    }
}

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
        val isFullExplainPlanEnabled by pluginSetting { ::isFullExplainPlanEnabled }

        if (dataSource == null || !dataSource.isConnected()) {
            return
        }

        val explainPlanType = if (isFullExplainPlanEnabled) {
            ExplainPlanType.FULL
        } else {
            ExplainPlanType.SAFE
        }

        val queryContext = QueryContext(
            emptyMap(),
            prettyPrint = false,
            automaticallyRun = true
        )

        val readModelProvider by query.source.project.service<DataGripBasedReadModelProvider>()
        val result = IndexCheckingLinter.lintQuery(
            dataSource,
            readModelProvider,
            query.with(HasExplain(explainPlanType)),
            queryContext
        )

        result.warnings.forEach {
            when (it) {
                is IndexCheckWarning.QueryNotCoveredByIndex ->
                    registerQueryNotCoveredByIndex(coroutineScope, dataSource, problems, query)
                is IndexCheckWarning.QueryNotUsingEffectiveIndex ->
                    registerQueryNotUsingIndexEffectively(
                        coroutineScope,
                        dataSource,
                        problems,
                        query
                    )
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

        emitQueryNotCoveredByIndexProblem(query, problems, problemDescription, coroutineScope, localDataSource)
    }

    private fun registerQueryNotUsingIndexEffectively(
        coroutineScope: CoroutineScope,
        localDataSource: LocalDataSource,
        problems: ProblemsHolder,
        query: Node<PsiElement>
    ) {
        val problemDescription = InspectionsAndInlaysMessages.message(
            "inspection.index.checking.error.query.not.effectively.using.an.index",
        )

        emitQueryNotCoveredByIndexProblem(query, problems, problemDescription, coroutineScope, localDataSource)
    }

    private fun emitQueryNotCoveredByIndexProblem(
        query: Node<PsiElement>,
        problems: ProblemsHolder,
        problemDescription: @Nls String,
        coroutineScope: CoroutineScope,
        localDataSource: LocalDataSource
    ) {
        val probe by service<InspectionStatusChangedProbe>()
        probe.inspectionChanged(
            TelemetryEvent.InspectionStatusChangeEvent.InspectionType.QUERY_NOT_COVERED_BY_INDEX,
            query
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

                val cachedQueryService by query.source.project.service<CachedQueryService>()
                val index = runBlocking {
                    IndexAnalyzer.analyze(
                        query,
                        cachedQueryService,
                        CollectionIndexConsolidationOptions(10)
                    )
                }

                MongoshDialect.formatter.indexCommand(query, index, ::queryReferenceString)
            }
        )
    }

    private fun queryReferenceString(query: Node<PsiElement>): String? {
        return ApplicationManager.getApplication().runReadAction<String?> {
            val method = query.source.findParentOfType<PsiMethod>() ?: return@runReadAction null
            val containingClass = method.containingClass ?: return@runReadAction null
            val lineNumber = query.source.containingFile.fileDocument.getLineNumber(
                query.source.textOffset
            ) + 1

            "${containingClass.qualifiedName}#${method.name} at line $lineNumber"
        }
    }
}
