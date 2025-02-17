package com.mongodb.jbplugin.dialects.mongosh

import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.dialects.OutputQuery
import com.mongodb.jbplugin.dialects.mongosh.aggr.emitAggregateBody
import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.dialects.mongosh.query.canUpdateDocuments
import com.mongodb.jbplugin.dialects.mongosh.query.emitCollectionReference
import com.mongodb.jbplugin.dialects.mongosh.query.emitLimit
import com.mongodb.jbplugin.dialects.mongosh.query.emitQueryFilter
import com.mongodb.jbplugin.dialects.mongosh.query.emitQueryUpdate
import com.mongodb.jbplugin.dialects.mongosh.query.emitSort
import com.mongodb.jbplugin.dialects.mongosh.query.resolveFieldReference
import com.mongodb.jbplugin.dialects.mongosh.query.resolveValueReference
import com.mongodb.jbplugin.indexing.IndexAnalyzer
import com.mongodb.jbplugin.indexing.IndexAnalyzer.SortDirection
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasExplain
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType
import com.mongodb.jbplugin.mql.components.HasRunCommand
import com.mongodb.jbplugin.mql.components.HasTargetCluster
import com.mongodb.jbplugin.mql.components.IsCommand
import io.github.z4kn4fein.semver.Version
import org.owasp.encoder.Encode

object MongoshDialectFormatter : DialectFormatter {
    override suspend fun <S> formatQuery(
        query: Node<S>,
        queryContext: QueryContext,
    ): OutputQuery {
        val queryCommand = query.component<IsCommand>() ?: return OutputQuery.None
        val explainPlan = query.component<HasExplain>()?.explainType ?: ExplainPlanType.NONE
        val isAggregate = queryCommand.type == IsCommand.CommandType.AGGREGATE

        if (queryCommand.type == IsCommand.CommandType.RUN_COMMAND) {
            return generateRunCommand(query, queryContext)
        }

        // When the query is asking to be explained we simply disregard what the actual query is
        // and assume that it is either aggregate (when mentioned to be as aggregate) otherwise a
        // find because only on these two can we actually chain an explain. This is a limitation on
        // the Java Shell.
        val (isAFindQuery, functionName) = when (explainPlan) {
            ExplainPlanType.NONE -> (queryCommand.type == IsCommand.CommandType.FIND_MANY) to
                (queryCommand.type.canonical)
            else -> if (isAggregate) false to "aggregate" else true to "find"
        }

        val usesSuffixExplainPlan = explainPlan != ExplainPlanType.NONE && isAFindQuery
        val usesPrefixExplainPlan =
            explainPlan != ExplainPlanType.NONE && isAggregate && !isAFindQuery

        val outputString = MongoshBackend(
            prettyPrint = queryContext.prettyPrint,
            automaticallyRun = queryContext.automaticallyRun
        )
            .applyQueryExpansions(queryContext)
            .apply {
                emitDbAccess()
                emitCollectionReference(query.component<HasCollectionReference<S>>())
                if (usesPrefixExplainPlan) {
                    emitExplainPlan(explainPlan)
                }

                emitPropertyAccess()
                emitFunctionName(functionName)
                emitFunctionCall(long = false, {
                    if (isAggregate) {
                        emitAggregateBody(query, explainPlan)
                    } else {
                        emitQueryFilter(query, firstCall = true)
                    }
                }, {
                    if (query.canUpdateDocuments() && !isAFindQuery) { // only if it was not converted for an explain plan
                        emitQueryUpdate(query)
                    } else {
                        didNotEmit()
                    }
                })

                if (isAFindQuery) {
                    emitSort(query)
                    emitLimit(query)
                }

                if (usesSuffixExplainPlan) {
                    emitExplainPlan(explainPlan)
                }

                if (isAFindQuery && explainPlan == ExplainPlanType.NONE && automaticallyRun) {
                    emitPropertyAccess()
                    emitFunctionName("toArray")
                    emitFunctionCall(long = false)
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

    private suspend fun <S> generateRunCommand(query: Node<S>, queryContext: QueryContext): OutputQuery {
        val runCommand = query.component<HasRunCommand<S>>() ?: return OutputQuery.None
        val outputString = MongoshBackend(
            prettyPrint = queryContext.prettyPrint,
            automaticallyRun = true
        )
            .applyQueryExpansions(queryContext)
            .apply {
                emitDbAccess()
                emitDatabaseAccess(resolveValueReference(runCommand.database, null))
                emitFunctionName("runCommand")
                emitFunctionCall(long = false, {
                    emitObjectStart()
                    emitObjectKey(resolveValueReference(runCommand.commandName, null))
                    emitContextValue(registerConstant(1))
                    for ((field, value) in runCommand.additionalArguments) {
                        emitObjectValueEnd()
                        emitObjectKey(resolveFieldReference(field, false))
                        emitContextValue(resolveValueReference(value, field))
                    }
                    emitObjectEnd()
                })
            }.computeOutput()

        return OutputQuery.CanBeRun(outputString)
    }

    override fun <S> indexCommand(query: Node<S>, index: IndexAnalyzer.SuggestedIndex<S>, toQueryReference: (Node<S>) -> String?): String = when (index) {
        is IndexAnalyzer.SuggestedIndex.NoIndex -> ""
        is IndexAnalyzer.SuggestedIndex.MongoDbIndex -> {
            val targetCluster = query.component<HasTargetCluster>()
            val version = targetCluster?.majorVersion ?: Version(7)
            val docPrefix = "https://www.mongodb.com/docs/v${version.major}.${version.minor}"

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

            val otherCoveredQueries = index.coveredQueries.mapNotNull {
                toQueryReference(it)
            }.distinct()
            var prelude = ""
            if (otherCoveredQueries.isNotEmpty()) {
                prelude += "// region Queries covered by this index \n"
                prelude += otherCoveredQueries.joinToString("") { "// $it\n" }
                prelude += "// endregion \n"
            }
            prelude +=
                "// Learn about creating an index: $docPrefix/core/data-model-operations/#indexes"

            val indexTemplate = index.fields.joinToString(
                separator = ", ",
                prefix = "{ ",
                postfix = " }"
            ) {
                val indexDirection = when (it.direction) {
                    SortDirection.Ascending -> "1"
                    SortDirection.Descending -> "-1"
                }
                """"${Encode.forJavaScript(
                    it.fieldName
                )}": ${Encode.forJavaScript(indexDirection)} """.trim()
            }
            """
$prelude
db.getSiblingDB("$encodedDbName").getCollection("$encodedColl")
  .createIndex($indexTemplate)
            """.trimIndent()
        }
    }

    override fun formatType(type: BsonType) = ""

    private suspend fun MongoshBackend.emitExplainPlan(explainPlanType: ExplainPlanType): MongoshBackend {
        emitPropertyAccess()
        emitFunctionName("explain")
        emitFunctionCall(long = false, {
            emitContextValue(registerConstant(explainPlanType.serverName()))
        })
        return this
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
