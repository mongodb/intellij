/**
 * A slice that represents the build information of the connected cluster.
 */

package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.QueryResult
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasLimit
import com.mongodb.jbplugin.mql.components.HasRunCommand
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand

/**
 * @property collections
 */
data class ListCollections(
    val collections: List<Collection>,
) {
    /**
     * @property name
     * @property type
     */
    data class Collection(
        val name: String,
        val type: String,
    )

    /**
     * @param database
     */
    data class Slice(
        private val database: String,
    ) : com.mongodb.jbplugin.accessadapter.Slice<ListCollections> {
        override val id: String
            get() = "${javaClass.canonicalName}::$database"

        override suspend fun queryUsingDriver(from: MongoDbDriver): ListCollections {
            if (database.isBlank()) {
                return ListCollections(emptyList())
            }

            val query = Node(
                Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.RUN_COMMAND),
                    HasRunCommand(
                        database = HasValueReference(
                            HasValueReference.Constant(Unit, database, BsonString)
                        ),
                        commandName = HasValueReference(
                            HasValueReference.Constant(Unit, "listCollections", BsonString)
                        ),
                        additionalArguments = listOf(
                            HasFieldReference(
                                HasFieldReference.FromSchema(Unit, "authorizedCollections")
                            ) to
                                HasValueReference(
                                    HasValueReference.Constant(Unit, true, BsonBoolean)
                                )
                        )
                    ),
                    HasLimit(1)
                )
            )

            val queryResult =
                from.runQuery(query, Map::class) as? QueryResult.Run<Map<String, Any>>
                    ?: return ListCollections(emptyList())

            val collectionMetadata = queryResult.result["cursor"] as Map<String, Any>
            val collections = collectionMetadata["firstBatch"] as List<Map<String, *>>

            return ListCollections(
                collections.map {
                    Collection(it["name"].toString(), it["type"].toString())
                },
            )
        }
    }
}
