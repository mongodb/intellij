package com.mongodb.jbplugin.linting.performance

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan
import com.mongodb.jbplugin.accessadapter.slice.ExplainQuery
import com.mongodb.jbplugin.linting.Inspection.NotUsingIndexEffectively
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.QueryInsightsHolder
import com.mongodb.jbplugin.linting.QueryInspection
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.components.HasExplain
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType

data class QueryNotUsingIndexEffectivelyInspectionSettings<D>(
    val dataSource: D,
    val readModelProvider: MongoDbReadModelProvider<D>,
    val explainPlanType: ExplainPlanType
)

class QueryNotUsingIndexEffectivelyInspection<D> : QueryInspection<
    QueryNotUsingIndexEffectivelyInspectionSettings<D>,
    NotUsingIndexEffectively
    > {
    override suspend fun <Source> run(
        query: Node<Source>,
        holder: QueryInsightsHolder<Source, NotUsingIndexEffectively>,
        settings: QueryNotUsingIndexEffectivelyInspectionSettings<D>
    ) {
        val explainPlanResult = settings.readModelProvider.slice(
            settings.dataSource,
            ExplainQuery.Slice(
                query.with(HasExplain(settings.explainPlanType)),
                QueryContext.empty(automaticallyRun = true)
            )
        )

        val explainPlan = explainPlanResult.explainPlan
        if (explainPlan is ExplainPlan.IneffectiveIndexUsage) {
            holder.register(QueryInsight.notUsingIndexEffectively(query))
        }
    }
}
