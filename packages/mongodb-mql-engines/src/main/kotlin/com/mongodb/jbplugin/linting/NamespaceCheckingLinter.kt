/**
 * Linter that checks that the database and collection specified in the query do exist
 * in the current datasource.
 */

package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.QueryInspection
import com.mongodb.jbplugin.QueryInspectionHolder
import com.mongodb.jbplugin.QueryInspectionResult
import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.parser.*
import com.mongodb.jbplugin.mql.parser.components.knownCollection
import com.mongodb.jbplugin.mql.parser.components.noCollection
import com.mongodb.jbplugin.mql.parser.components.onlyCollection

data class NamespaceCheckingSettings<D>(
    val dataSource: D,
    val readModelProvider: MongoDbReadModelProvider<D>
)

/**
 * Linter that verifies that the specified database and collection in the current query does exist
 * in the connected data source.
 */
class NamespaceCheckingLinter<DataSource> : QueryInspection<NamespaceCheckingSettings<DataSource>> {
    override suspend fun <Source> run(
        query: Node<Source>,
        holder: QueryInspectionHolder<Source>,
        settings: NamespaceCheckingSettings<DataSource>
    ) {
        val dbList = settings.readModelProvider.slice(settings.dataSource, ListDatabases.Slice)

        val databaseDoesNotExistParser = knownCollection<Source>()
            .filter { databaseDoesNotExist(dbList, it) }
            .map {
                holder.register(
                    QueryInspectionResult.CorrectnessWarning(
                        query,
                        "com.mongodb.jbplugin.inspections.correctness.database-does-not-exist",
                        listOf(it.namespace.database),
                        QueryInspectionResult.ChooseConnection,
                        it.collectionSource
                    )
                )
            }.anyError()

        val collectionDoesNotExistParser = knownCollection<Source>()
            .filter { collectionDoesNotExist(settings.readModelProvider, settings.dataSource, it) }
            .map {
                holder.register(
                    QueryInspectionResult.CorrectnessWarning(
                        query,
                        "com.mongodb.jbplugin.inspections.correctness.namespace-does-not-exist",
                        listOf(it.namespace.database, it.namespace.collection),
                        QueryInspectionResult.ChooseConnection,
                        it.collectionSource
                    )
                )
            }.anyError()

        val databaseIsNotKnown = onlyCollection<Source>()
            .filter { it.collection.isNotEmpty() }
            .map {
                holder.register(
                    QueryInspectionResult.CorrectnessWarning(
                        query,
                        "com.mongodb.jbplugin.inspections.correctness.database-not-inferred",
                        listOf(),
                        QueryInspectionResult.ChooseConnection,
                        it.collectionSource
                    )
                )
            }.anyError()

        val noCollectionSpecified = noCollection<Source>()
            .map {
                holder.register(
                    QueryInspectionResult.CorrectnessWarning(
                        query,
                        "com.mongodb.jbplugin.inspections.correctness.collection-not-inferred",
                        listOf(),
                        QueryInspectionResult.ChooseConnection,
                        query.source
                    )
                )
            }.anyError()

        val namespaceErrorsParser = first(
            databaseDoesNotExistParser,
            collectionDoesNotExistParser,
            databaseIsNotKnown,
            noCollectionSpecified
        )

        namespaceErrorsParser.parse(query)
    }

    private fun <D, S> collectionDoesNotExist(
        readModelProvider: MongoDbReadModelProvider<D>,
        dataSource: D,
        ref: HasCollectionReference.Known<S>
    ): Boolean = !runCatching {
        readModelProvider.slice(
            dataSource,
            ListCollections.Slice(ref.namespace.database)
        ).collections.map { it.name }
    }.getOrDefault(emptyList())
        .contains(ref.namespace.collection)

    private fun <S> databaseDoesNotExist(
        dbList: ListDatabases,
        known: HasCollectionReference.Known<S>
    ): Boolean = dbList.databases.find { database -> known.namespace.database == database.name } ==
        null
}
