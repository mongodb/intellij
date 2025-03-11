/**
 * Linter that checks that the query is using a proper index.
 */

package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.Inspection
import com.mongodb.jbplugin.QueryInsight
import com.mongodb.jbplugin.QueryInsightsHolder
import com.mongodb.jbplugin.QueryInspection
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
        holder: QueryInsightsHolder<Source>,
        settings: IndexCheckingSettings<DataSource>
    ) {
        val explainPlanResult = settings.readModelProvider.slice(
            settings.dataSource,
            ExplainQuery.Slice(query.with(HasExplain(settings.explainPlanType)), settings.context)
        )

        when (explainPlanResult.explainPlan) {
            is ExplainPlan.CollectionScan ->
                holder.register(
                    QueryInsight(
                        query = query,
                        source = query.source,
                        description = "com.mongodb.jbplugin.inspections.performance.not-using-an-index",
                        descriptionArguments = emptyList(),
                        inspection = Inspection.NotUsingIndex,
                    )
                )
            is ExplainPlan.IneffectiveIndexUsage ->
                holder.register(
                    QueryInsight(
                        query = query,
                        source = query.source,
                        description = "com.mongodb.jbplugin.inspections.performance.not-using-an-index-effectively",
                        descriptionArguments = emptyList(),
                        inspection = Inspection.IneffectiveIndex,
                    )
                )
            else -> {} // do nothing
        }
    }
}
