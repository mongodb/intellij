package com.mongodb.jbplugin.linting.performance

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan
import com.mongodb.jbplugin.accessadapter.slice.ExplainQuery
import com.mongodb.jbplugin.linting.Inspection.NotUsingIndex
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.QueryInsightsHolder
import com.mongodb.jbplugin.linting.QueryInspection
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.components.HasExplain
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType

data class QueryNotUsingIndexInspectionSettings<D>(
    val dataSource: D,
    val readModelProvider: MongoDbReadModelProvider<D>,
    val explainPlanType: ExplainPlanType
)

class QueryNotUsingIndexInspection<D> : QueryInspection<
    QueryNotUsingIndexInspectionSettings<D>,
    NotUsingIndex
    > {
    override suspend fun <S> run(
        query: Node<S>,
        holder: QueryInsightsHolder<S, NotUsingIndex>,
        settings: QueryNotUsingIndexInspectionSettings<D>
    ) {
        val explainPlanResult = settings.readModelProvider.slice(
            settings.dataSource,
            ExplainQuery.Slice(
                query.with(HasExplain(settings.explainPlanType)),
                QueryContext.empty(automaticallyRun = true)
            )
        )

        val explainPlan = explainPlanResult.explainPlan
        if (explainPlan is ExplainPlan.CollectionScan) {
            holder.register(QueryInsight.notUsingIndex(query))
        }
    }
}
