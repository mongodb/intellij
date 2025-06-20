/**
 * A slice that represents the build information of the connected cluster.
 */

package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.ConnectionString
import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.QueryResult
import com.mongodb.jbplugin.accessadapter.toNs
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasLimit
import com.mongodb.jbplugin.mql.components.HasRunCommand
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import kotlin.time.Duration.Companion.seconds

/**
 * Slice to be used when querying the MongoDbReadModelProvider.
 *
 * @see com.mongodb.jbplugin.accessadapter.slice.BuildInfo.Slice
 * @see com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider.slice
 * @property version
 * @property gitVersion
 * @property modules
 * @property isLocalhost
 * @property isDataLake
 * @property isEnterprise
 * @property isAtlas
 * @property isLocalAtlas
 * @property isAtlasStream
 * @property isDigitalOcean
 * @property isGenuineMongoDb
 * @property nonGenuineVariant
 * @property serverUrl
 * @property buildEnvironment
 */
data class BuildInfo(
    val version: String,
    val gitVersion: String?,
    val modules: List<String>?,
    val isLocalhost: Boolean,
    val isDataLake: Boolean,
    val isEnterprise: Boolean,
    val isAtlas: Boolean,
    val isLocalAtlas: Boolean,
    val isAtlasStream: Boolean,
    val isDigitalOcean: Boolean,
    val isGenuineMongoDb: Boolean,
    val nonGenuineVariant: String?,
    val serverUrl: ConnectionString?,
    val buildEnvironment: Map<String, String>,
) {
    val atlasHost: String?
        get() = serverUrl?.hosts?.getOrNull(0)
            ?.replace(Regex(""":\d+"""), "")
            .takeIf { isAtlas }
    object Slice : com.mongodb.jbplugin.accessadapter.Slice<BuildInfo> {
        override val id: String = javaClass.canonicalName
        private val atlasRegex = Regex(""".*\.mongodb(-dev|-qa|-stage)?\.net(:\d+)?$""")
        private val atlasStreamRegex = Regex("""^atlas-stream-.+""")
        private val isLocalhostRegex =
            Regex(
                "^(localhost" +
                    "|127.([01]?[0-9][0-9]?|2[0-4][0-9]|25[0-5])" +
                    ".([01]?[0-9][0-9]?|2[0-4][0-9]|25[0-5])" +
                    ".([01]?[0-9][0-9]?|2[0-4][0-9]|25[0-5])" +
                    "|0.0.0.0" +
                    "|\\[(?:0*:)*?:?0*1]" +
                    ")(:[0-9]+)?$",
            )
        private val digitalOceanRegex = Regex(""".*\.mongo\.ondigitalocean\.com$""")
        private val cosmosDbRegex = Regex(""".*\.cosmos\.azure\.com$""")
        private val docDbRegex = Regex(""".*docdb(-elastic)?\.amazonaws\.com$""")

        override suspend fun queryUsingDriver(from: MongoDbDriver): BuildInfo {
            val connectionString = from.connectionString()
            val isLocalHost = connectionString.hosts.all { it.matches(isLocalhostRegex) }
            val isAtlas = connectionString.hosts.all { it.matches(atlasRegex) }
            val isLocalAtlas = checkIsAtlasCliIfConnected(from)

            val isAtlasStream = connectionString.hosts.all {
                it.matches(atlasRegex) &&
                    it.matches(atlasStreamRegex)
            }
            val isDigitalOcean = connectionString.hosts.all { it.matches(digitalOceanRegex) }
            val nonGenuineVariant =
                if (connectionString.hosts.all { it.matches(cosmosDbRegex) }) {
                    "cosmosdb"
                } else if (connectionString.hosts.all { it.matches(docDbRegex) }) {
                    "documentdb"
                } else {
                    null
                }

            val buildInfoFromMongoDb =
                runBuildInfoCommandIfConnected(from)

            return BuildInfo(
                version = buildInfoFromMongoDb.version,
                gitVersion = buildInfoFromMongoDb.gitVersion,
                modules = buildInfoFromMongoDb.modules,
                buildEnvironment = buildInfoFromMongoDb.buildEnvironment,
                isLocalhost = isLocalHost,
                isEnterprise =
                buildInfoFromMongoDb.gitVersion?.contains("enterprise") == true ||
                    buildInfoFromMongoDb.modules?.contains("enterprise") == true,
                isAtlas = isAtlas,
                isLocalAtlas = isLocalAtlas,
                isAtlasStream = isAtlasStream,
                isDigitalOcean = isDigitalOcean,
                isGenuineMongoDb = nonGenuineVariant == null,
                nonGenuineVariant = nonGenuineVariant,
                isDataLake = buildInfoFromMongoDb.isDataLake == true,
                serverUrl = connectionString
            )
        }

        private suspend fun checkIsAtlasCliIfConnected(from: MongoDbDriver) =
            if (from.connected) {
                val query = Node(
                    Unit,
                    listOf(
                        HasCollectionReference(
                            HasCollectionReference.Known(Unit, Unit, "admin.atlascli".toNs())
                        ),
                        HasLimit(1),
                        IsCommand(IsCommand.CommandType.COUNT_DOCUMENTS),
                        HasFilter(
                            listOf(
                                Node(
                                    Unit,
                                    listOf(
                                        HasFieldReference(
                                            HasFieldReference.FromSchema(Unit, "managedClusterType")
                                        ),
                                        HasValueReference(
                                            HasValueReference.Constant(
                                                Unit,
                                                "atlasCliLocalDevCluster",
                                                BsonString
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )

                when (
                    val countOfAdminCli = from.runQuery(
                        query,
                        Long::class,
                        queryContext = QueryContext.empty(),
                        timeout = 1.seconds
                    )
                ) {
                    is QueryResult.Run -> countOfAdminCli.result > 0
                    else -> false
                }
            } else {
                false
            }

        private suspend fun runBuildInfoCommandIfConnected(from: MongoDbDriver) =
            if (from.connected) {
                when (
                    val result = from.runQuery(
                        Node(
                            Unit,
                            listOf(
                                IsCommand(IsCommand.CommandType.RUN_COMMAND),
                                HasRunCommand(
                                    database = HasValueReference(
                                        HasValueReference.Constant(Unit, "admin", BsonString)
                                    ),
                                    commandName = HasValueReference(
                                        HasValueReference.Constant(Unit, "buildInfo", BsonString)
                                    ),
                                ),
                                HasLimit(1)
                            )
                        ),
                        BuildInfoFromMongoDb::class
                    )
                ) {
                    is QueryResult.Run -> result.result
                    else -> empty()
                }
            } else {
                empty()
            }
    }

    companion object {
        private const val DEFAULT_VERSION_IF_INVALID = "8.0.0"

        private fun empty(): BuildInfoFromMongoDb =
            BuildInfoFromMongoDb(
                DEFAULT_VERSION_IF_INVALID,
                null,
                null,
                emptyMap(),
                false
            )
    }
}

/**
 * @property version
 * @property gitVersion
 * @property modules
 * @property buildEnvironment
 * @property isDataLake
 */
internal data class BuildInfoFromMongoDb(
    val version: String,
    val gitVersion: String?,
    val modules: List<String>?,
    val buildEnvironment: Map<String, String>,
    val isDataLake: Boolean?
)
