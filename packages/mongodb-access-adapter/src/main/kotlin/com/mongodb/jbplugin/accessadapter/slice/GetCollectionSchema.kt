package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.QueryResult
import com.mongodb.jbplugin.mql.*
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasLimit
import com.mongodb.jbplugin.mql.components.IsCommand

/**
 * Slice to be used when querying the schema of a given collection.
 *
 * @property schema
 */
data class GetCollectionSchema(
    val schema: CollectionSchema,
) {
    data class Slice(
        private val namespace: Namespace,
        private val documentsSampleSize: Int,
        private val preserveSampleDocuments: Boolean = true,
    ) : com.mongodb.jbplugin.accessadapter.Slice<GetCollectionSchema> {
        override val id = "${javaClass.canonicalName}::$namespace"

        override suspend fun queryUsingDriver(from: MongoDbDriver): GetCollectionSchema {
            if (namespace.database.isBlank() || namespace.collection.isBlank()) {
                return GetCollectionSchema(
                    CollectionSchema(
                        namespace,
                        BsonObject(emptyMap()),
                    )
                )
            }

            val query = Node(
                Unit,
                listOf(
                    HasCollectionReference(
                        HasCollectionReference.Known(
                            databaseSource = Unit,
                            collectionSource = Unit,
                            namespace = namespace,
                            schema = null,
                        )
                    ),
                    IsCommand(IsCommand.CommandType.FIND_MANY),
                    HasFilter(emptyList<Node<Unit>>()),
                    HasLimit(documentsSampleSize),
                )
            )

            val sampleSomeDocs: List<Map<String, Any>> =
                when (val result = from.runQuery(query, Map::class)) {
                    is QueryResult.Run -> result.result as List<Map<String, Any>>
                    else -> emptyList()
                }

            // we need to generate the schema from these docs
            val sampleSchemas = sampleSomeDocs.map(this::recursivelyBuildSchema)
            // now we want to merge them together
            val consolidatedSchema =
                sampleSchemas.reduceOrNull(::mergeSchemaTogether) ?: BsonObject(
                    emptyMap(),
                )

            // flatten schema
            val schema = flattenAnyOfReferences(consolidatedSchema) as BsonObject
            return GetCollectionSchema(
                CollectionSchema(
                    namespace = namespace,
                    schema = schema,
                    dataDistribution = DataDistribution.generate(sampleSomeDocs),
                    sampleDocuments = if (preserveSampleDocuments) sampleSomeDocs else emptyList(),
                ),
            )
        }

        private fun recursivelyBuildSchema(value: Any?): BsonType =
            when (value) {
                null -> BsonNull
                is Map<*, *> -> BsonObject(
                    value.map {
                        it.key.toString() to
                            recursivelyBuildSchema(it.value)
                    }.toMap()
                )
                is Collection<*> -> recursivelyBuildSchema(value.toTypedArray())
                is Array<*> ->
                    BsonArray(
                        value
                            .map {
                                it?.javaClass?.toBsonType(it) ?: BsonNull
                            }.toSet()
                            .let {
                                if (it.size == 1) {
                                    it.first()
                                } else {
                                    BsonAnyOf(it)
                                }
                            },
                    )

                else -> primitiveOrWrapper(value.javaClass).toBsonType()
            }
    }
}
