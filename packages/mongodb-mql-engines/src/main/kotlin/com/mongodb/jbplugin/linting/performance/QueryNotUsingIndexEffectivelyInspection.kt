package com.mongodb.jbplugin.linting.performance

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan
import com.mongodb.jbplugin.accessadapter.slice.ExplainQuery
import com.mongodb.jbplugin.linting.Inspection.NotUsingIndexEffectively
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.QueryInsightsHolder
import com.mongodb.jbplugin.linting.QueryInspection
import com.mongodb.jbplugin.linting.correctness.isNamespaceAvailableInCluster
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.HasExplain
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.parser.components.allFiltersRecursively
import com.mongodb.jbplugin.mql.parser.components.knownCollection
import com.mongodb.jbplugin.mql.parser.filter
import com.mongodb.jbplugin.mql.parser.parse

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
        val commandDoesNotUseIndexes = query.component<IsCommand>()?.type?.usesIndexes == false
        val queryHasNoFilters = when (
            val allFilters = allFiltersRecursively<Source>().parse(query)
        ) {
            is Either.Left -> true
            is Either.Right -> allFilters.value.isEmpty()
        }

        if (commandDoesNotUseIndexes || queryHasNoFilters) {
            return
        }

        val validationResults = knownCollection<Source>()
            .filter {
                it.namespace.isValid &&
                    it.namespace.isNamespaceAvailableInCluster(
                        dataSource = settings.dataSource,
                        readModelProvider = settings.readModelProvider,
                    )
            }
            .parse(query)

        if (validationResults is Either.Left) {
            return
        }

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
