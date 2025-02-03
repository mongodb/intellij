/**
 * Represents the MongoDB Driver facade that we will use internally.
 * Usually, we won't use this class directly, only in tests. What we
 * will use is the MongoDBReadModelProvider, that provides caching
 * and safety mechanisms.
 *
 * @see com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
 */

package com.mongodb.jbplugin.accessadapter

import com.mongodb.ConnectionString
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import org.bson.conversions.Bson
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Represents the result of an explain plan command.
 */
sealed interface ExplainPlan {
    data object NotRun : ExplainPlan
    data object CollectionScan : ExplainPlan
    data object IndexScan : ExplainPlan
}

/**
 * Represents the result of running a query.
 */
sealed interface QueryResult<S> {
    class NotRun<S> : QueryResult<S>
    data class Run<S>(val result: S) : QueryResult<S>
}

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

    suspend fun <T: Any, S> runQuery(
        query: Node<S>,
        result: KClass<T>,
        queryContext: QueryContext = QueryContext.empty(),
        timeout: Duration = 1.seconds,
        limit: Int = 50
    ): QueryResult<T>

    suspend fun <S> explain(query: Node<S>, queryContext: QueryContext): ExplainPlan

    suspend fun <T : Any> runCommand(
        database: String,
        command: Bson,
        result: KClass<T>,
        timeout: Duration = 1.seconds,
    ): T
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
