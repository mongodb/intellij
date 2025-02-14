/**
 * Represents the MongoDB Driver facade that we will use internally.
 * Usually, we won't use this class directly, only in tests. What we
 * will use is the MongoDBReadModelProvider, that provides caching
 * and safety mechanisms.
 *
 * @see com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
 */

package com.mongodb.jbplugin.accessadapter

import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Represents the result of running a query.
 */
sealed interface QueryResult<S> {
    class NotRun<S> : QueryResult<S>
    class NoResult<S> : QueryResult<S>
    data class Run<S>(val result: S) : QueryResult<S>
}

data class ConnectionString(val hosts: List<String>)

/**
 * Represents the MongoDB Driver facade that we will use internally.
 * Usually, we won't use this class directly, only in tests. What we
 * will use is the MongoDBReadModelProvider, that provides caching
 * and safety mechanisms.
 *
 * @see com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
 */
interface MongoDbDriver {
    val connected: Boolean

    suspend fun connectionString(): ConnectionString

    suspend fun <T : Any, S> runQuery(
        query: Node<S>,
        result: KClass<T>,
        queryContext: QueryContext,
        timeout: Duration
    ): QueryResult<T>

    suspend fun <T : Any, S> runQuery(
        query: Node<S>,
        result: KClass<T>,
        queryContext: QueryContext
    ): QueryResult<T> = runQuery(query, result, queryContext, 1.seconds)

    suspend fun <T : Any, S> runQuery(
        query: Node<S>,
        result: KClass<T>
    ): QueryResult<T> = runQuery(query, result, QueryContext.empty())
}

/**
 * Converts a string in form of `db.coll` to a Namespace object.
 *
 * @return
 */
fun String.toNs(): Namespace {
    val (db, coll) = trim().split(".", limit = 2)
    return Namespace(db, coll)
}
