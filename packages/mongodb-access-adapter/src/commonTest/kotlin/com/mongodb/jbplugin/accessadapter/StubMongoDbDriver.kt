package com.mongodb.jbplugin.accessadapter

import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import kotlin.reflect.KClass
import kotlin.time.Duration

data class StubMongoDbDriver(
    override val connected: Boolean = true,
    private val connectionString: String = "mongodb://localhost:27017",
    private val responses: Map<KClass<*>, (query: Node<Unit>) -> QueryResult<Any>>
) : MongoDbDriver {
    override suspend fun connectionString(): ConnectionString {
        return ConnectionString(listOf(connectionString))
    }

    override suspend fun <T : Any, S> runQuery(
        query: Node<S>,
        result: KClass<T>,
        queryContext: QueryContext,
        timeout: Duration
    ): QueryResult<T> {
        return responses[result]!!.invoke(query as Node<Unit>) as QueryResult<T>
    }
}
