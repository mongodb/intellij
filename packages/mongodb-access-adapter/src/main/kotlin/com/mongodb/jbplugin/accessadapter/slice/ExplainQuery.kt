/**
 * A slice that represents the build information of the connected cluster.
 */

package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.ExplainPlan
import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.QueryResult
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.components.HasExplain
import com.mongodb.jbplugin.mql.components.HasLimit

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
        override val id = "${javaClass.canonicalName}::$query"

        override suspend fun queryUsingDriver(from: MongoDbDriver): ExplainQuery {
            if (query.component<HasExplain>() == null) {
                return ExplainQuery(ExplainPlan.NotRun)
            }

            val explainPlanQueryResult = runCatching {
                // Ensuring that the query at-least has a limit if it doesn't already because
                // the explain is run automatically
                from.runQuery(query.with(HasLimit(1)), Map::class, queryContext)
            }.getOrNull() ?: return ExplainQuery(ExplainPlan.NotRun)

            val explainPlanDoc =
                (explainPlanQueryResult as? QueryResult.Run)?.result
                    ?: return ExplainQuery(ExplainPlan.NotRun)

            val queryPlanner =
                explainPlanDoc["queryPlanner"] as? Map<String, Any>
                    ?: return ExplainQuery(ExplainPlan.NotRun)
            val winningPlan =
                queryPlanner["winningPlan"] as? Map<String, Any>
                    ?: return ExplainQuery(ExplainPlan.NotRun)

            // https://www.mongodb.com/docs/manual/reference/explain-results/#explain-output-structure
            val result = planByMappingStage(
                winningPlan,
                mapOf(
                    "COLLSCAN" to ExplainPlan.CollectionScan,
                    "IXSCAN" to ExplainPlan.IndexScan,
                    "IDHACK" to ExplainPlan.IndexScan,
                    // EXPRESS_* are basically like a generalisation of IDHACK for other fields
                    "EXPRESS_IXSCAN" to ExplainPlan.IndexScan,
                    "EXPRESS_CLUSTERED_IXSCAN" to ExplainPlan.IndexScan,
                    "EXPRESS_UPDATE" to ExplainPlan.IndexScan,
                    "EXPRESS_DELETE" to ExplainPlan.IndexScan,
                )
            ) ?: ExplainPlan.NotRun

            return ExplainQuery(result)
        }

        private fun planByMappingStage(stage: Map<String, Any>, mapping: Map<String, ExplainPlan>): ExplainPlan? {
            val inputStage =
                stage["inputStage"] as? Map<String, Any>
                    ?: return mapping.getOrDefault(stage["stage"], null)
            return mapping.getOrDefault(inputStage["stage"], null)
        }
    }
}
