package com.mongodb.jbplugin.linting.environmentmismatch

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.linting.Inspection.CollectionDoesNotExist
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.QueryInsightsHolder
import com.mongodb.jbplugin.linting.QueryInspection
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.parser.components.knownCollection
import com.mongodb.jbplugin.mql.parser.filter
import com.mongodb.jbplugin.mql.parser.parse

data class CollectionDoesNotExistInspectionSettings<D>(
    val dataSource: D,
    val readModelProvider: MongoDbReadModelProvider<D>,
)

class CollectionDoesNotExistInspection<D> : QueryInspection<
    CollectionDoesNotExistInspectionSettings<D>,
    CollectionDoesNotExist
    > {
    override suspend fun <Source> run(
        query: Node<Source>,
        holder: QueryInsightsHolder<Source, CollectionDoesNotExist>,
        settings: CollectionDoesNotExistInspectionSettings<D>
    ) {
        val parsingResult = knownCollection<Source>()
            .filter { knownRef ->
                val detectedDatabase = knownRef.namespace.database
                val detectedCollection = knownRef.namespace.collection
                val availableCollections = runCatching {
                    settings.readModelProvider.slice(
                        settings.dataSource,
                        ListCollections.Slice(detectedDatabase)
                    ).collections.map { it.name }
                }.getOrDefault(emptyList())

                !availableCollections.contains(detectedCollection)
            }
            .parse(query)

        when (parsingResult) {
            is Either.Right -> {
                holder.register(
                    QueryInsight.nonExistentCollection(
                        query = query,
                        source = parsingResult.value.collectionSource,
                        collection = parsingResult.value.namespace.collection,
                        database = parsingResult.value.namespace.database,
                    )
                )
            }
            else -> {}
        }
    }
}
