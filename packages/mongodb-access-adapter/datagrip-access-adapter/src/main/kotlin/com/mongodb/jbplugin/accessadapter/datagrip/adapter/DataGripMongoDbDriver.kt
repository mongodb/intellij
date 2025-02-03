/**
 * Represents a MongoDB driver interface that uses a DataGrip
 * connection to query MongoDB.
 */

package com.mongodb.jbplugin.accessadapter.datagrip.adapter

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.connection.ConnectionRequestor
import com.intellij.database.run.ConsoleRunConfiguration
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.jbplugin.accessadapter.ExplainPlan
import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.QueryResult
import com.mongodb.jbplugin.dialects.OutputQuery
import com.mongodb.jbplugin.dialects.mongosh.MongoshDialect
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.bson.codecs.configuration.CodecRegistries.fromRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.conversions.Bson
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings
import org.bson.types.ObjectId
import org.jetbrains.annotations.VisibleForTesting
import org.owasp.encoder.Encode
import java.math.BigDecimal
import java.time.Instant
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val TIMEOUT = 5

/**
 * Currently we are using mongosh through the GraalVM, and doesn't support parallelism. So
 * we are running the queries in a dedicated single thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
val mongosh = Dispatchers.IO.limitedParallelism(1)
private val logger: Logger = logger<DataGripMongoDbDriver>()

/**
 * The driver itself. Shouldn't be used directly, but through the
 * DataGripBasedReadModelProvider.
 *
 * @see com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
 *
 * @param project
 * @param dataSource
 */
internal class DataGripMongoDbDriver(
    private val project: Project,
    private val dataSource: LocalDataSource,
) : MongoDbDriver {
    override val connected: Boolean
        get() =
            DatabaseConnectionManager.getInstance().activeConnections.any {
                it.connectionPoint.dataSource == dataSource
            }

    private val codecRegistry: CodecRegistry =
        fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry()
        )
    private val jsonWriterSettings =
        JsonWriterSettings
            .builder()
            .outputMode(JsonMode.EXTENDED)
            .indent(false)
            .build()

    private fun String.encodeForJs(): String = Encode.forJavaScript(this)

    private fun Bson.toJson(): String =
        this
            .toBsonDocument(Bson::class.java, codecRegistry)
            .toJson(jsonWriterSettings)
            .encodeForJs()

    override suspend fun connectionString(): ConnectionString = ConnectionString(dataSource.url!!)

    override suspend fun <T : Any, S> runQuery(
        query: Node<S>,
        result: KClass<T>,
        queryContext: QueryContext,
        timeout: Duration,
        limit: Int
    ): QueryResult<T> = withContext(Dispatchers.IO) {
        val queryScript = MongoshDialect.formatter.formatQuery(query, queryContext)

        if (queryScript !is OutputQuery.CanBeRun) {
            return@withContext QueryResult.NotRun()
        }

        val wrappedScriptInEJson = """
            EJSON.serialize(${queryScript.query}, { relaxed: false })
        """.trimIndent()

        val result = runQueryScript(wrappedScriptInEJson, result, timeout)
        if (limit == 1) {
            QueryResult.Run(result[0])
        } else {
            QueryResult.Run(result as T)
        }
    }

    override suspend fun <S> explain(query: Node<S>, queryContext: QueryContext): ExplainPlan = withContext(
        Dispatchers.IO
    ) {

        val queryScript = readAction {
            runBlocking { MongoshDialect.formatter.formatQuery(query, queryContext) }
        }

        if (queryScript !is OutputQuery.CanBeRun) {
            return@withContext ExplainPlan.NotRun
        }

        val explainPlanBson = runQueryScript(
            queryScript.query,
            Map::class,
            timeout = 1.seconds
        ).firstOrNull() as? Map<String, Any>

        explainPlanBson ?: return@withContext ExplainPlan.NotRun

        val queryPlanner =
            explainPlanBson["queryPlanner"] as? Map<String, Any>
                ?: return@withContext ExplainPlan.NotRun
        val winningPlan =
            queryPlanner["winningPlan"] as? Map<String, Any>
                ?: return@withContext ExplainPlan.NotRun

        planByMappingStage(
            winningPlan,
            mapOf(
                "COLLSCAN" to ExplainPlan.CollectionScan,
                "IXSCAN" to ExplainPlan.IndexScan,
                "IDHACK" to ExplainPlan.IndexScan
            )
        ) ?: ExplainPlan.NotRun
    }

    override suspend fun <T : Any> runCommand(
        database: String,
        command: Bson,
        result: KClass<T>,
        timeout: Duration,
    ): T =
        withContext(
            mongosh,
        ) {
            runQueryScript(
                """
                EJSON.serialize(
                    db.getSiblingDB("${database.encodeForJs()}")
                      .runCommand(EJSON.parse("${command.toJson()}"))
                , { relaxed: false })
                """.trimIndent(),
                result,
                timeout,
            )[0]
        }

    @VisibleForTesting
    internal suspend fun <T : Any> runQueryScript(
        queryString: String,
        resultClass: KClass<T>,
        timeout: Duration,
    ): List<T> =
        withContext(mongosh) {
            val connection = getConnection()
            val remoteConnection = connection.remoteConnection
            val statement = remoteConnection.prepareStatement(queryString.trimIndent())

            withTimeout(timeout) {
                val listOfResults = mutableListOf<T>()
                val queryResult = runCatching { statement.executeQuery() }
                if (queryResult.isFailure) {
                    logger.error(
                        "Can not query MongoDB: $queryString",
                        queryResult.exceptionOrNull()
                    )
                    return@withTimeout emptyList()
                }

                val resultSet = queryResult.getOrNull() ?: return@withTimeout emptyList()
                if (resultClass.java == Unit::class.java) {
                    listOfResults.add(Unit as T)
                    return@withTimeout listOfResults
                }

                while (resultSet.next()) {
                    val hashMap = resultSet.getObject(1) as Map<String, Any>
                    val parsedEJson = deserializeEJson(hashMap)
                    val result = mapToClass<T>(parsedEJson, resultClass)
                    if (result != null) {
                        listOfResults.add(result)
                    }
                }

                listOfResults
            }
        }

    companion object {
        /**
         * See https://www.mongodb.com/docs/manual/reference/mongodb-extended-json/#bson-data-types-and-associated-representations
         * for the actual mappings.
         */
        @VisibleForTesting
        internal fun deserializeEJson(doc: Any?): Any? {
            if (doc is Map<*, *>) {
                val mappings = doc.map { (key, value) ->
                    when (key) {
                        "${'$'}binary" -> null
                        "${'$'}date" -> Instant.ofEpochMilli(deserializeEJson(value) as Long)
                        "${'$'}numberDecimal" -> BigDecimal(value.toString())
                        "${'$'}numberDouble" -> value.toString().toDouble()
                        "${'$'}numberLong" -> value.toString().toLong()
                        "${'$'}numberInt" -> value.toString().toInt()
                        "${'$'}maxKey" -> null
                        "${'$'}minKey" -> null
                        "${'$'}oid" -> ObjectId(value.toString())
                        "${'$'}regularExpression" -> null
                        "${'$'}timestamp" -> null
                        "${'$'}uuid" -> UUID.fromString(value.toString())
                        "_id" -> if (value is Map<*, *> && value.isEmpty()) {
                            key to ObjectId()
                        } else {
                            key to deserializeEJson(value)
                        }
                        else -> key to deserializeEJson(value)
                    }
                }

                val isAllPairs = mappings.all { it is Pair<*, *> }
                val isMixed = mappings.any { it is Pair<*, *> } && !isAllPairs

                return if (isAllPairs) {
                    (mappings as List<Pair<String, Any?>>).toMap()
                } else if (!isMixed) {
                    if (mappings.size == 1) {
                        mappings[0]
                    } else {
                        mappings
                    }
                } else {
                    throw IllegalStateException("Can not parse a mix of pairs and values")
                }
            } else if (doc is Iterable<*>) {
                return doc.map { deserializeEJson(it) }
            } else {
                return doc
            }
        }

        @VisibleForTesting
        internal fun <T : Any> mapToClass(value: Any?, kClass: KClass<T>): T? {
            if (value == null) {
                return null
            }

            if (value is Iterable<*>) {
                return kClass.java.cast(value.toList())
            } else if (value is Map<*, *> && kClass.java.isAssignableFrom(Map::class.java)) {
                return value as T
            } else if (value is Map<*, *>) {
                val constructor = kClass.constructors.first()
                val sortedArgs = constructor.parameters.associate {
                    it to mapToClass(value[it.name], it.type.classifier as KClass<*>)
                }

                return constructor.callBy(sortedArgs)
            } else {
                return kClass.cast(value)
            }
        }
    }

    private suspend fun getConnection(): DatabaseConnection {
        val connections = DatabaseConnectionManager.getInstance().activeConnections
        val connectionHandler =
            DatabaseConnectionManager
                .getInstance()
                .build(project, dataSource)
                .setRequestor(ConnectionRequestor.Anonymous())
                .setAskPassword(true)
                .setRunConfiguration(
                    ConsoleRunConfiguration.newConfiguration(project).apply {
                        setOptionsFromDataSource(dataSource)
                    },
                )

        return connections.firstOrNull { it.connectionPoint.dataSource == dataSource }
            ?: connectionHandler.create()!!.get()
    }

    @VisibleForTesting
    fun forceConnectForTesting() {
        runBlocking {
            val connection = getConnection()
            withActiveConnectionList {
                it.add(connection)
            }
        }
    }

    @VisibleForTesting
    fun closeConnectionForTesting() {
        runBlocking {
            withActiveConnectionList {
                it.clear()
            }
        }
    }

    @VisibleForTesting
    private fun withActiveConnectionList(fn: (MutableSet<DatabaseConnection>) -> Unit) {
        runBlocking {
            val connectionsManager = DatabaseConnectionManager.getInstance()
            val myConnectionsField =
                connectionsManager.javaClass
                    .getDeclaredField("myConnections")
                    .apply {
                        isAccessible = true
                    }
            val myConnections = myConnectionsField.get(
                connectionsManager
            ) as MutableSet<DatabaseConnection>
            fn(myConnections)
            myConnectionsField.isAccessible = false
        }
    }

    private fun planByMappingStage(stage: Map<String, Any>, mapping: Map<String, ExplainPlan>): ExplainPlan? {
        val inputStage =
            stage["inputStage"] as? Map<String, Any>
                ?: return mapping.getOrDefault(stage["stage"], null)
        return mapping.getOrDefault(inputStage["stage"], null)
    }
}

/**
 * Returns true if the provided local data source is a MongoDB data source.
 *
 * @return
 */
fun LocalDataSource.isMongoDbDataSource(): Boolean =
    this.databaseDriver?.id?.startsWith("mongo") == true || this.databaseDriver == null

/**
 * Returns true if the provided local data source has at least one active connection
 * attached to it.
 *
 * @return
 */
fun LocalDataSource.isConnected(): Boolean =
    DatabaseConnectionManager
        .getInstance()
        .activeConnections
        .any { connection ->
            connection.connectionPoint.dataSource == dataSource &&
                runCatching {
                    !connection.remoteConnection.isClosed &&
                        connection.remoteConnection.isValid(TIMEOUT)
                }.getOrDefault(false)
        }
