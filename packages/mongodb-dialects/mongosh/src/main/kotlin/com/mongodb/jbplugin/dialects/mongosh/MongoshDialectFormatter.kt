package com.mongodb.jbplugin.dialects.mongosh

import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.dialects.OutputQuery
import com.mongodb.jbplugin.dialects.mongosh.aggr.emitAggregateBody
import com.mongodb.jbplugin.dialects.mongosh.aggr.isAggregate
import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.dialects.mongosh.query.canUpdateDocuments
import com.mongodb.jbplugin.dialects.mongosh.query.emitCollectionReference
import com.mongodb.jbplugin.dialects.mongosh.query.emitLimit
import com.mongodb.jbplugin.dialects.mongosh.query.emitQueryFilter
import com.mongodb.jbplugin.dialects.mongosh.query.emitQueryUpdate
import com.mongodb.jbplugin.dialects.mongosh.query.emitSort
import com.mongodb.jbplugin.dialects.mongosh.query.returnsACursor
import com.mongodb.jbplugin.indexing.IndexAnalyzer
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasExplain
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType
import com.mongodb.jbplugin.mql.components.HasTargetCluster
import com.mongodb.jbplugin.mql.components.IsCommand
import io.github.z4kn4fein.semver.Version
import kotlinx.coroutines.runBlocking
import org.owasp.encoder.Encode
import java.lang.IllegalStateException

object MongoshDialectFormatter : DialectFormatter {
    override suspend fun <S> formatQuery(
        query: Node<S>,
        queryContext: QueryContext,
    ): OutputQuery {
        val isAggregate = query.isAggregate()
        val explainPlan = query.component<HasExplain>()?.explainType ?: ExplainPlanType.NONE
        val queryCommand = query.component<IsCommand>() ?: return OutputQuery.None

        val outputString = MongoshBackend(
            prettyPrint = queryContext.prettyPrint,
            automaticallyRun = queryContext.automaticallyRun
        )
            .applyQueryExpansions(queryContext)
            .apply {
                emitDbAccess()
                emitCollectionReference(query.component<HasCollectionReference<S>>())
                emitFunctionName(queryCommand.type.canonical)
                emitFunctionCall(long = false, {
                    if (isAggregate) {
                        emitAggregateBody(query, explainPlan)
                    } else {
                        emitQueryFilter(query, firstCall = true)
                    }
                }, {
                    if (queryCommand.type == IsCommand.CommandType.AGGREGATE ||
                        queryCommand.type == IsCommand.CommandType.FIND_MANY
                    ) {
                        if (explainPlan != ExplainPlanType.NONE) {
                            emitExplainPlanOption(explainPlan)
                        } else {
                            didNotEmit()
                        }
                    } else if (query.canUpdateDocuments()) {
                        emitQueryUpdate(query)
                    } else {
                        didNotEmit()
                    }
                }, {
                    if (queryCommand.type != IsCommand.CommandType.AGGREGATE &&
                        queryCommand.type != IsCommand.CommandType.FIND_MANY
                    ) {
                        if (explainPlan != ExplainPlanType.NONE) {
                            emitExplainPlanOption(explainPlan)
                        } else {
                            didNotEmit()
                        }
                    } else {
                        didNotEmit()
                    }
                })

                if (query.returnsACursor()) {
                    emitSort(query)
                    if (explainPlan == ExplainPlanType.NONE) {
                        emitLimit(query)
                    }
                }

                if (queryCommand.type == IsCommand.CommandType.FIND_MANY &&
                    explainPlan != ExplainPlanType.NONE
                ) {
                    emitPropertyAccess()
                    emitFunctionName("next")
                    emitFunctionCall()
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

    private fun MongoshBackend.emitExplainPlanOption(explainPlanType: ExplainPlanType): MongoshBackend {
        emitObjectStart()
        emitObjectKey(registerConstant("explain"))
        emitContextValue(registerConstant(explainPlanType.serverName()))
        return emitObjectEnd()
    }

    // https://www.mongodb.com/docs/manual/reference/command/explain/#command-fields
    private fun ExplainPlanType.serverName(): String {
        return when (this) {
            ExplainPlanType.NONE -> throw IllegalStateException(
                "Must not generate an explain plan when the type is NONE."
            )
            ExplainPlanType.SAFE -> "queryPlanner"
            ExplainPlanType.FULL -> "executionStats"
        }
    }
}
