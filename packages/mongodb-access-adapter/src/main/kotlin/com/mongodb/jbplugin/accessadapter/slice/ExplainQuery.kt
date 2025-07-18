/**
 * A slice that represents the build information of the connected cluster.
 */

package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.QueryResult
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.components.HasExplain
import com.mongodb.jbplugin.mql.components.HasLimit

/**
 * Represents the result of an explain plan command.
 */
sealed interface ExplainPlan : Comparable<ExplainPlan> {
    val cost: Int
    val indexName: String?

    override fun compareTo(other: ExplainPlan): Int {
        return cost.compareTo(other.cost)
    }

    data object NotRun : ExplainPlan {
        override val cost = 0
        override val indexName = null
    }

    data object CollectionScan : ExplainPlan {
        override val cost = 3
        override val indexName = null
    }

    data class IndexScan(override val indexName: String) : ExplainPlan {
        override val cost = 1
    }

    data class IneffectiveIndexUsage(override val indexName: String) : ExplainPlan {
        override val cost = 2
    }
}

private const val RATIO_FOR_INEFFECTIVE_INDEX_USAGE = 50

/**
 * Runs the explain plan of a query.
 *
 * @property explainPlan
 */
data class ExplainQuery(
    val explainPlan: ExplainPlan
) {
    /**
     * @param S
     * @property query
     */
    data class Slice<S>(
        val query: Node<S>,
        val queryContext: QueryContext,
    ) : com.mongodb.jbplugin.accessadapter.Slice<ExplainQuery> {
        override val id = "${javaClass.canonicalName}::${query.queryHash().toString(16)}"

        override suspend fun queryUsingDriver(from: MongoDbDriver): ExplainQuery {
            if (query.component<HasExplain>() == null) {
                return ExplainQuery(ExplainPlan.NotRun)
            }

            val explainPlanQueryResult = runCatching {
                // Ensuring that the query at-least has a limit if it doesn't already because
                // the explain is run automatically
                from.runQuery(query.withReplaced(HasLimit(1)), Map::class, queryContext)
            }.getOrNull() ?: return ExplainQuery(ExplainPlan.NotRun)

            val explainPlanDoc =
                (explainPlanQueryResult as? QueryResult.Run)?.result
                    ?: return ExplainQuery(ExplainPlan.NotRun)

            val executionStats = explainPlanDoc["executionStats"] as? Map<String, Any>

            val queryPlanner =
                explainPlanDoc["queryPlanner"] as? Map<String, Any>
                    ?: return ExplainQuery(ExplainPlan.NotRun)

            val winningPlan =
                queryPlanner["winningPlan"] as? Map<String, Any>
                    ?: return ExplainQuery(ExplainPlan.NotRun)

            val stages = winningPlan.getOrDefault("queryPlan", winningPlan) as? Map<String, Any>
                ?: return ExplainQuery(ExplainPlan.NotRun)

            val resultFromExecutionStats = checkExecutionStatsEffectiveness(executionStats, stages)
            // https://www.mongodb.com/docs/manual/reference/explain-results/#explain-output-structure
            val resultFromQueryPlanner = planByMappingStage(
                stages,
                mapOf(
                    "COLLSCAN" to { ExplainPlan.CollectionScan },
                    "IXSCAN" to { ExplainPlan.IndexScan(it ?: "unknown") },
                    "IDHACK" to { ExplainPlan.IndexScan(it ?: "unknown") },
                    "SORT" to { ExplainPlan.IneffectiveIndexUsage(it ?: "unknown") },
                    "FILTER" to { ExplainPlan.IneffectiveIndexUsage(it ?: "unknown") },
                    // EXPRESS_* are basically like a generalisation of IDHACK for other fields
                    "EXPRESS_IXSCAN" to { ExplainPlan.IndexScan(it ?: "unknown") },
                    "EXPRESS_CLUSTERED_IXSCAN" to { ExplainPlan.IndexScan(it ?: "unknown") },
                    "EXPRESS_UPDATE" to { ExplainPlan.IndexScan(it ?: "unknown") },
                    "EXPRESS_DELETE" to { ExplainPlan.IndexScan(it ?: "unknown") },
                )
            )

            return ExplainQuery(maxOf(resultFromQueryPlanner, resultFromExecutionStats))
        }

        private fun checkExecutionStatsEffectiveness(
            executionStats: Map<String, Any>?,
            stages: Map<String, Any>
        ): ExplainPlan {
            if (executionStats == null) {
                return ExplainPlan.NotRun
            }

            val nReturned = executionStats["nReturned"].toString().toInt()
            val totalDocsExamined = executionStats["totalDocsExamined"].toString().toInt()

            if (totalDocsExamined == 0 || nReturned == 0) {
                return ExplainPlan.NotRun
            }

            val ratio = totalDocsExamined / nReturned
            return if (ratio >= RATIO_FOR_INEFFECTIVE_INDEX_USAGE) {
                ExplainPlan.IneffectiveIndexUsage(findUsedIndexName(stages) ?: "unknown")
            } else {
                ExplainPlan.NotRun
            }
        }

        private fun planByMappingStage(stage: Map<String, Any>, mapping: Map<String, (String?) -> ExplainPlan>): ExplainPlan {
            val parentStage: ExplainPlan = if (stage["inputStage"] != null) {
                planByMappingStage(stage["inputStage"] as Map<String, Any>, mapping)
            } else {
                ExplainPlan.NotRun
            }

            val currentIndex = stage.getOrDefault("indexName", null) as? String
            val currentStage = mapping.getOrDefault(stage["stage"]) { ExplainPlan.NotRun }(parentStage.indexName ?: currentIndex)

            return maxOf(parentStage, currentStage)
        }

        private fun findUsedIndexName(stage: Map<String, Any>): String? {
            val parentIndexName = if (stage["inputStage"] != null) {
                findUsedIndexName(stage["inputStage"] as Map<String, Any>)
            } else {
                null
            }

            return parentIndexName ?: stage.getOrDefault("indexName", null) as? String
        }
    }
}
