package com.mongodb.jbplugin.inspections.performance

import com.google.gson.Gson
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.dialects.javadriver.glossary.findTopParentBy
import com.mongodb.jbplugin.editor.dataSource
import com.mongodb.jbplugin.inspections.AIAssist
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridgeV2
import com.mongodb.jbplugin.linting.Inspection
import com.mongodb.jbplugin.linting.QueryInsight.Companion.notUsingProject
import com.mongodb.jbplugin.linting.QueryInsightsHolder
import com.mongodb.jbplugin.linting.QueryInspection
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.meta.withinReadActionBlocking
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.parser.components.knownCollection
import com.mongodb.jbplugin.mql.parser.parse
import kotlinx.coroutines.CoroutineScope

data class QueryNotUsingProjectInspectionSettings<D>(
    val dataSource: D,
    val readModelProvider: MongoDbReadModelProvider<D>,
)

class QueryNotUsingProjectInspection<D> : QueryInspection<
    QueryNotUsingProjectInspectionSettings<D>,
    Inspection.NotUsingProject
    > {
    override suspend fun <Source> run(
        query: Node<Source>,
        holder: QueryInsightsHolder<Source, Inspection.NotUsingProject>,
        settings: QueryNotUsingProjectInspectionSettings<D>
    ) {
        val reference = when (val results = knownCollection<Source>().parse(query)) {
            is Either.Left -> return
            is Either.Right -> results.value
        }
        val sampleDocuments = reference.schema?.sampleDocuments?.also {
            if (it.isEmpty()) return
        } ?: return

        val methodString = gatherQueryMethodSource(query as Node<PsiElement>)
        val fileString = gatherSurroundingContext(query as Node<PsiElement>)
        val sampleDocsString = runCatching {
            Gson().toJson(sampleDocuments)
        }

        val aiResponse = AIAssist().suggestProjection(
            methodString,
            fileString,
            sampleDocuments
        )

        if (aiResponse.suggestion == "PROJECTION_RECOMMENDED") {
            val insightMessage = """
                ${aiResponse.suggestionMessage}
                Suggested Projection: ${aiResponse.suggestedProjection.joinToString(", ")}
            """.trimIndent()
            holder.register(
                notUsingProject(
                    query,
                    insightMessage
                )
            )
        }
    }

    private fun <S : PsiElement>gatherQueryMethodSource(query: Node<S>): String {
        return withinReadActionBlocking {
            val topMethod = query.source.findTopParentBy { it is PsiMethod } as PsiMethod
            topMethod.text ?: ""
        }
    }

    private fun <S : PsiElement>gatherSurroundingContext(query: Node<S>): String {
        return withinReadActionBlocking {
            query.source.containingFile.text
        }
    }
}

class MongoDbQueryNotUsingProject(coroutineScope: CoroutineScope) : AbstractMongoDbInspectionBridgeV2<
    QueryNotUsingProjectInspectionSettings<LocalDataSource>,
    Inspection.NotUsingProject
    >(coroutineScope, QueryNotUsingProjectInspection(), Inspection.NotUsingProject) {
    override fun buildSettings(
        query: Node<PsiElement>
    ): QueryNotUsingProjectInspectionSettings<LocalDataSource> {
        val readModelProvider by query.source.project.service<DataGripBasedReadModelProvider>()
        return QueryNotUsingProjectInspectionSettings(
            dataSource = query.source.containingFile.dataSource!!,
            readModelProvider = readModelProvider
        )
    }

    override fun emitFinishedInspectionTelemetryEvent(problemsHolder: ProblemsHolder) {
        // DO nothing for now
    }
}
