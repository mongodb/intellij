/**
 * Linter that checks that the query is using a proper index.
 */

package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.QueryInspection
import com.mongodb.jbplugin.QueryInspectionHolder
import com.mongodb.jbplugin.QueryInspectionResult
import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan
import com.mongodb.jbplugin.accessadapter.slice.ExplainQuery
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.components.HasExplain
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType

data class IndexCheckingSettings<D>(
    val dataSource: D,
    val readModelProvider: MongoDbReadModelProvider<D>,
    val context: QueryContext,
    val explainPlanType: ExplainPlanType
)

/**
 * Linter that verifies that the query is properly using indexes in the target cluster.
 */
class IndexCheckingLinter<DataSource> : QueryInspection<IndexCheckingSettings<DataSource>> {
    override suspend fun <Source> run(
        query: Node<Source>,
        holder: QueryInspectionHolder<Source>,
        settings: IndexCheckingSettings<DataSource>
    ) {
        val explainPlanResult = settings.readModelProvider.slice(
            settings.dataSource,
            ExplainQuery.Slice(query.with(HasExplain(settings.explainPlanType)), settings.context)
        )

        when (explainPlanResult.explainPlan) {
            is ExplainPlan.CollectionScan ->
                holder.register(
                    QueryInspectionResult.PerformanceWarning(
                        query,
                        "com.mongodb.jbplugin.inspections.performance.not-using-an-index",
                        emptyList(),
                        QueryInspectionResult.CreateIndex,
                        query.source
                    )
                )
            is ExplainPlan.IneffectiveIndexUsage ->
                holder.register(
                    QueryInspectionResult.PerformanceWarning(
                        query,
                        "com.mongodb.jbplugin.inspections.performance.not-using-an-index-effectively",
                        emptyList(),
                        QueryInspectionResult.CreateIndex,
                        query.source
                    )
                )
            else -> {} // do nothing
        }
    }
}
