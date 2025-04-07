package com.mongodb.jbplugin.linting.environmentmismatch

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.linting.Inspection.DatabaseDoesNotExist
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.QueryInsightsHolder
import com.mongodb.jbplugin.linting.QueryInspection
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.parser.components.knownCollection
import com.mongodb.jbplugin.mql.parser.filter
import com.mongodb.jbplugin.mql.parser.parse

data class DatabaseDoesNotExistInspectionSettings<D>(
    val dataSource: D,
    val readModelProvider: MongoDbReadModelProvider<D>,
)

class DatabaseDoesNotExistInspection<D> : QueryInspection<
    DatabaseDoesNotExistInspectionSettings<D>,
    DatabaseDoesNotExist
    > {
    override suspend fun <Source> run(
        query: Node<Source>,
        holder: QueryInsightsHolder<Source, DatabaseDoesNotExist>,
        settings: DatabaseDoesNotExistInspectionSettings<D>
    ) {
        val parsingResult = knownCollection<Source>()
            .filter { knownRef ->
                val detectedDatabase = knownRef.namespace.database
                val availableDatabases = settings.readModelProvider.slice(
                    settings.dataSource,
                    ListDatabases.Slice
                ).databases.map { it.name }

                detectedDatabase.isNotBlank() && !availableDatabases.contains(detectedDatabase)
            }
            .parse(query)

        when (parsingResult) {
            is Either.Right -> {
                holder.register(
                    QueryInsight.nonExistentDatabase(
                        query,
                        parsingResult.value.namespace.database
                    )
                )
            }
            else -> {}
        }
    }
}
