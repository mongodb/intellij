package com.mongodb.jbplugin.linting.environmentmismatch

import com.mongodb.jbplugin.linting.Inspection.NoDatabaseInferred
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.QueryInsightsHolder
import com.mongodb.jbplugin.linting.QueryInspection
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.parser.components.onlyCollection
import com.mongodb.jbplugin.mql.parser.filter
import com.mongodb.jbplugin.mql.parser.parse

class NoDatabaseInferredInspection : QueryInspection<Unit, NoDatabaseInferred> {
    override suspend fun <Source> run(
        query: Node<Source>,
        holder: QueryInsightsHolder<Source, NoDatabaseInferred>,
        settings: Unit
    ) {
        val parsingResult = onlyCollection<Source>()
            .filter { it.collection.isNotEmpty() }
            .parse(query)

        when (parsingResult) {
            is Either.Right -> {
                holder.register(
                    QueryInsight.noDatabaseInferred(query)
                )
            }
            else -> {}
        }
    }
}
