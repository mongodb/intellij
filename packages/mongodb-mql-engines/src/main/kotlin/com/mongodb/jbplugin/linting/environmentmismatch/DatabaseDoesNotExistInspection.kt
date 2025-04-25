package com.mongodb.jbplugin.linting.environmentmismatch

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.linting.Inspection.DatabaseDoesNotExist
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.QueryInsightsHolder
import com.mongodb.jbplugin.linting.QueryInspection
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.parser.components.knownCollection
import com.mongodb.jbplugin.mql.parser.filter
import com.mongodb.jbplugin.mql.parser.parse

fun <D>Namespace.isDatabaseAvailableInCluster(
    dataSource: D,
    readModelProvider: MongoDbReadModelProvider<D>,
): Boolean = runCatching {
    readModelProvider.slice(dataSource, ListDatabases.Slice).databases.any {
        it.name == database
    }
}.getOrDefault(false)

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
                !knownRef.namespace.isDatabaseAvailableInCluster(
                    dataSource = settings.dataSource,
                    readModelProvider = settings.readModelProvider,
                )
            }
            .parse(query)

        when (parsingResult) {
            is Either.Right -> {
                val collectionRef = parsingResult.value
                holder.register(
                    QueryInsight.nonExistentDatabase(
                        query = query,
                        source = collectionRef.databaseSource ?: query.source,
                        database = collectionRef.namespace.database
                    )
                )
            }
            else -> {}
        }
    }
}
