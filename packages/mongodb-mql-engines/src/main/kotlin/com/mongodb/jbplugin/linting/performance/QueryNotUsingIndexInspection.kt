package com.mongodb.jbplugin.linting.performance

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan
import com.mongodb.jbplugin.accessadapter.slice.ExplainQuery
import com.mongodb.jbplugin.linting.Inspection.NotUsingIndex
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
        val commandDoesNotUseIndexes = query.component<IsCommand>()?.type?.usesIndexes == false
        val queryHasNoFilters = when (
            val allFilters = allFiltersRecursively<S>().parse(query)
        ) {
            is Either.Left -> true
            is Either.Right -> allFilters.value.isEmpty()
        }

        if (commandDoesNotUseIndexes || queryHasNoFilters) {
            return
        }

        val validationResults = knownCollection<S>()
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
        if (explainPlan is ExplainPlan.CollectionScan) {
            holder.register(QueryInsight.notUsingIndex(query))
        }
    }
}
