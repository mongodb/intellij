package com.mongodb.jbplugin.dialects.mongosh

import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.dialects.OutputQuery
import com.mongodb.jbplugin.dialects.mongosh.aggr.emitAggregateBody
import com.mongodb.jbplugin.dialects.mongosh.aggr.isAggregate
import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.dialects.mongosh.query.canUpdateDocuments
import com.mongodb.jbplugin.dialects.mongosh.query.emitCollectionReference
import com.mongodb.jbplugin.dialects.mongosh.query.emitQueryFilter
import com.mongodb.jbplugin.dialects.mongosh.query.emitQueryUpdate
import com.mongodb.jbplugin.dialects.mongosh.query.emitSort
import com.mongodb.jbplugin.dialects.mongosh.query.returnsACursor
import com.mongodb.jbplugin.indexing.IndexAnalyzer
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasTargetCluster
import com.mongodb.jbplugin.mql.components.IsCommand
import io.github.z4kn4fein.semver.Version
import kotlinx.coroutines.runBlocking
import org.owasp.encoder.Encode

object MongoshDialectFormatter : DialectFormatter {
    override suspend fun <S> formatQuery(
        query: Node<S>,
        queryContext: QueryContext,
    ): OutputQuery {
        val isAggregate = query.isAggregate()

        val outputString = MongoshBackend(prettyPrint = queryContext.prettyPrint)
            .applyQueryExpansions(queryContext)
            .apply {
                emitDbAccess()
                emitCollectionReference(query.component<HasCollectionReference<S>>())
                if (queryContext.explainPlan != QueryContext.ExplainPlanType.NONE) {
                    emitFunctionName("explain")
                    emitFunctionCall(long = false, {
                        // https://www.mongodb.com/docs/manual/reference/command/explain/#command-fields
                        when (queryContext.explainPlan) {
                            QueryContext.ExplainPlanType.FULL -> emitStringLiteral("executionStats")
                            else -> emitStringLiteral("queryPlanner")
                        }
                    })
                    emitPropertyAccess()
                    if (isAggregate) {
                        emitFunctionName("aggregate")
                    } else {
                        emitFunctionName("find")
                    }
                } else {
                    emitFunctionName(query.component<IsCommand>()?.type?.canonical ?: "find")
                }
                if (query.canUpdateDocuments() &&
                    queryContext.explainPlan == QueryContext.ExplainPlanType.NONE
                ) {
                    emitFunctionCall(long = true, {
                        emitQueryFilter(query, firstCall = true)
                    }, {
                        emitQueryUpdate(query)
                    })
                } else {
                    emitFunctionCall(long = true, {
                        if (query.isAggregate()) {
                            emitAggregateBody(query, queryContext)
                        } else {
                            emitQueryFilter(query, firstCall = true)
                        }
                    })
                }

                if (query.returnsACursor()) {
                    emitSort(query)
                }
            }.computeOutput()

        val ref = query.component<HasCollectionReference<S>>()?.reference
        return when {
            ref is HasCollectionReference.Known -> if (ref.namespace.isValid) {
                OutputQuery.CanBeRun(outputString)
            } else {
                OutputQuery.Incomplete(outputString)
            }
            else -> OutputQuery.Incomplete(outputString)
        }
    }

    override fun <S> indexCommandForQuery(query: Node<S>): String = when (
        val index = runBlocking { IndexAnalyzer.analyze(query) }
    ) {
        is IndexAnalyzer.SuggestedIndex.NoIndex -> ""
        is IndexAnalyzer.SuggestedIndex.MongoDbIndex -> {
            val targetCluster = query.component<HasTargetCluster>()
            val version = targetCluster?.majorVersion ?: Version(7)
            val docPrefix = "https://www.mongodb.com/docs/v${version.major}.${version.minor}"

            val fieldList = index.fields.joinToString { Encode.forJavaScript(it.fieldName) }
            val (dbName, collName) = when (val collRef = index.collectionReference.reference) {
                is HasCollectionReference.Unknown -> ("<database>" to "<collection>")
                is HasCollectionReference.OnlyCollection -> ("<database>" to collRef.collection)
                is HasCollectionReference.Known -> (
                    collRef.namespace.database to
                        collRef.namespace.collection
                    )
            }

            val encodedDbName = Encode.forJavaScript(dbName)
            val encodedColl = Encode.forJavaScript(collName)

            val indexTemplate = index.fields.withIndex().joinToString(
                separator = ", ",
                prefix = "{ ",
                postfix = " }"
            ) {
                """ "<your_field_${it.index + 1}>": 1 """.trim()
            }

            """
                    // Potential fields to consider indexing: $fieldList
                    // Learn about creating an index: $docPrefix/core/data-model-operations/#indexes
                    db.getSiblingDB("$encodedDbName").getCollection("$encodedColl")
                      .createIndex($indexTemplate)
            """.trimIndent()
        }
    }

    override fun formatType(type: BsonType) = ""
}
