package com.mongodb.jbplugin.linting.environmentmismatch

import com.mongodb.jbplugin.linting.Inspection.NoCollectionSpecified
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.QueryInsightsHolder
import com.mongodb.jbplugin.linting.QueryInspection
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.parser.components.noCollection
import com.mongodb.jbplugin.mql.parser.parse

class NoCollectionSpecifiedInspection : QueryInspection<Unit, NoCollectionSpecified> {
    override suspend fun <Source> run(
        query: Node<Source>,
        holder: QueryInsightsHolder<Source, NoCollectionSpecified>,
        settings: Unit
    ) {
        val parsingResult = noCollection<Source>()
            .parse(query)

        when (parsingResult) {
            is Either.Right -> {
                holder.register(
                    QueryInsight.noCollectionSpecified(query)
                )
            }
            else -> {}
        }
    }
}
