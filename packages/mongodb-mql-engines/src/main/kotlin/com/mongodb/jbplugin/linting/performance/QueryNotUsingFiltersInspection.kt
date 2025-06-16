package com.mongodb.jbplugin.linting.performance

import com.mongodb.jbplugin.linting.Inspection.NotUsingFilters
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.QueryInsightsHolder
import com.mongodb.jbplugin.linting.QueryInspection
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.parser.components.allFiltersRecursively
import com.mongodb.jbplugin.mql.parser.parse

class QueryNotUsingFiltersInspection : QueryInspection<Unit, NotUsingFilters> {
    override suspend fun <Source> run(
        query: Node<Source>,
        holder: QueryInsightsHolder<Source, NotUsingFilters>,
        settings: Unit
    ) {
        val queryHasNoFilters = when (
            val allFilters = allFiltersRecursively<Source>().parse(query)
        ) {
            is Either.Left -> true
            is Either.Right -> allFilters.value.isEmpty()
        }

        if (queryHasNoFilters) {
            holder.register(QueryInsight.notUsingFilters(query))
        }
    }
}
