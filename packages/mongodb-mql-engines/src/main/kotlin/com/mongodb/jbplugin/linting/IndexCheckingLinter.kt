/**
 * Linter that checks that the query is using a proper index.
 */

package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.Inspection
import com.mongodb.jbplugin.InspectionHolder
import com.mongodb.jbplugin.QueryInspection
import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan
import com.mongodb.jbplugin.accessadapter.slice.ExplainQuery
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext

data class IndexCheckingSettings<D>(
    val dataSource: D,
    val readModelProvider: MongoDbReadModelProvider<D>,
    val context: QueryContext
)

/**
 * Linter that verifies that the query is properly using indexes in the target cluster.
 */
class IndexCheckingLinter<DataSource> : QueryInspection<IndexCheckingSettings<DataSource>> {
    override suspend fun <Source> run(
        query: Node<Source>,
        holder: InspectionHolder<Source>,
        settings: IndexCheckingSettings<DataSource>
    ) {
        val explainPlanResult = settings.readModelProvider.slice(
            settings.dataSource,
            ExplainQuery.Slice(query, settings.context)
        )

        when (explainPlanResult.explainPlan) {
            is ExplainPlan.CollectionScan ->
                holder.register(
                    Inspection.PerformanceWarning(
                        query,
                        "com.mongodb.jbplugin.inspections.performance.not-using-an-index",
                        emptyList(),
                        Inspection.CreateIndex,
                        query.source
                    )
                )
            is ExplainPlan.IneffectiveIndexUsage ->
                holder.register(
                    Inspection.PerformanceWarning(
                        query,
                        "com.mongodb.jbplugin.inspections.performance.not-using-an-index-effectively",
                        emptyList(),
                        Inspection.CreateIndex,
                        query.source
                    )
                )
            else -> {} // do nothing
        }
    }
}
