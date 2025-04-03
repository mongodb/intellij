package com.mongodb.jbplugin.linting.correctness

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.linting.Inspection.FieldDoesNotExist
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.QueryInsightsHolder
import com.mongodb.jbplugin.linting.QueryInspection
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.parser.components.allNodesWithSchemaFieldReferences
import com.mongodb.jbplugin.mql.parser.components.knownCollection
import com.mongodb.jbplugin.mql.parser.components.schemaFieldReferences
import com.mongodb.jbplugin.mql.parser.filter
import com.mongodb.jbplugin.mql.parser.map
import com.mongodb.jbplugin.mql.parser.mapMany
import com.mongodb.jbplugin.mql.parser.parse

data class FieldDoesNotExistInspectionSettings<D>(
    val dataSource: D,
    val readModelProvider: MongoDbReadModelProvider<D>,
    val documentsSampleSize: Int
)

class FieldDoesNotExistInspection<D> : QueryInspection<
    FieldDoesNotExistInspectionSettings<D>,
    FieldDoesNotExist,
    > {
    override suspend fun <Source> run(
        query: Node<Source>,
        holder: QueryInsightsHolder<Source, FieldDoesNotExist>,
        settings: FieldDoesNotExistInspectionSettings<D>
    ) {
        val querySchema = knownCollection<Source>()
            .filter { it.namespace.isValid }
            .map {
                settings.readModelProvider.slice(
                    settings.dataSource,
                    GetCollectionSchema.Slice(it.namespace, settings.documentsSampleSize)
                ).schema
            }.parse(query)

        when (querySchema) {
            is Either.Right -> {
                val collectionSchema = querySchema.value

                val allFields = allNodesWithSchemaFieldReferences<Source>()
                    .mapMany(schemaFieldReferences())
                    .parse(query).orElse { emptyList() }
                    .flatten()

                allFields.filter {
                    collectionSchema.typeOf(it.fieldName) == BsonNull
                }.forEach {
                    holder.register(QueryInsight.nonExistingField(query, it.fieldName))
                }
            }
            else -> {}
        }
    }
}
