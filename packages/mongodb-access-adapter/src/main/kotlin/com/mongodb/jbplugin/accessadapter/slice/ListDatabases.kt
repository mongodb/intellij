/**
 * A slice that represents the build information of the connected cluster.
 */

package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.QueryResult
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasLimit
import com.mongodb.jbplugin.mql.components.HasRunCommand
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand

/**
 * @property databases
 */
data class ListDatabases(
    val databases: List<Database>,
) {
    object Slice : com.mongodb.jbplugin.accessadapter.Slice<ListDatabases> {
        override val id = javaClass.canonicalName

        override suspend fun queryUsingDriver(from: MongoDbDriver): ListDatabases {
            val query = Node(
                Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.RUN_COMMAND),
                    HasRunCommand(
                        database = HasValueReference(
                            HasValueReference.Constant(Unit, "admin", BsonString)
                        ),
                        commandName = HasValueReference(
                            HasValueReference.Constant(Unit, "listDatabases", BsonString)
                        ),
                    ),
                    HasLimit(1)
                )
            )

            val queryResult =
                from.runQuery(query, Map::class) as? QueryResult.Run<Map<String, Any>>
                    ?: return ListDatabases(emptyList())
            val databases = queryResult.result["databases"] as List<Map<String, String>>
            return ListDatabases(
                databases.map {
                    Database(it["name"].toString())
                },
            )
        }
    }

/**
     * @property name
     */
    data class Database(
        val name: String,
    )
}
