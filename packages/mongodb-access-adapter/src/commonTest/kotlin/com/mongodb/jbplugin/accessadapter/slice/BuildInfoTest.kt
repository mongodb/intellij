package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.QueryResult
import com.mongodb.jbplugin.accessadapter.StubMongoDbDriver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildInfoTest {
    @Test
    fun returns_a_valid_build_info() = runTest {
        val driver = StubMongoDbDriver(
            connected = true,
            connectionString = "mongodb://localhost/",
            responses = mapOf(
                Long::class to { QueryResult.Run(1L) },
                BuildInfoFromMongoDb::class to { QueryResult.Run(defaultBuildInfo()) }
            )
        )

        val data = BuildInfo.Slice.queryUsingDriver(driver)
        assertEquals("7.8.0", data.version)
        assertEquals("1235abc", data.gitVersion)
    }

    @Test
    fun when_not_connected_do_not_run_queries() = runTest {
        val driver = StubMongoDbDriver(
            connected = false,
            connectionString = "mongodb://localhost/",
            responses = mapOf(
                Long::class to { throw NotImplementedError() },
                BuildInfoFromMongoDb::class to { throw NotImplementedError() }
            )
        )
        BuildInfo.Slice.queryUsingDriver(driver)
    }

    @Test
    fun parses_different_type_of_url_connections_properly() = runTest {
        fun assertTestCase(
            data: BuildInfo,
            testCase: Array<Any?>,
        ) {
            assertEquals(testCase[1], data.isLocalhost, "isLocalhost does not match ${testCase[0]} -> ${data.isLocalhost}")
            assertEquals(testCase[2], data.isAtlas, "isAtlas does not match")
            assertEquals(testCase[3], data.isAtlasStream, "isAtlasStream does not match")
            assertEquals(testCase[4], data.isDigitalOcean, "isDigitalOcean does not match")
            assertEquals(testCase[5], data.isGenuineMongoDb, "isGenuineMongoDb does not match")
            assertEquals(
                testCase[6]?.toString(),
                data.nonGenuineVariant,
                "mongodbVariant does not match"
            )
        }

        arrayOf<Array<Any?>>(
            arrayOf("mongodb://localhost", true, false, false, false, true, null),
            arrayOf("mongodb://localhost,another-server", false, false, false, false, true, null),
            arrayOf(
                "mongodb+srv://example-atlas-cluster.e06cc.mongodb.net",
                false,
                true,
                false,
                false,
                true,
                null
            ),
            arrayOf(
                "mongodb://example-atlas-cluster.e06cc.mongodb.net,another-server",
                false,
                false,
                false,
                false,
                true,
                null
            ),
            arrayOf(
                "mongodb+srv://atlas-stream-example-atlas-stream.e06cc.mongodb.net",
                false,
                true,
                true,
                false,
                true,
                null
            ),
            arrayOf("mongodb://[::1]", true, false, false, false, true, null),
            arrayOf(
                "mongodb://my-cluster.mongo.ondigitalocean.com",
                false,
                false,
                false,
                true,
                true,
                null
            ),
            arrayOf(
                "mongodb://my-cluster.cosmos.azure.com",
                false,
                false,
                false,
                false,
                false,
                "cosmosdb"
            ),
            arrayOf(
                "mongodb://my-cluster.docdb.amazonaws.com",
                false,
                false,
                false,
                false,
                false,
                "documentdb"
            ),
            arrayOf(
                "mongodb://my-cluster.docdb-elastic.amazonaws.com",
                false,
                false,
                false,
                false,
                false,
                "documentdb"
            )
        ).forEach { testCase ->
            val driver = StubMongoDbDriver(
                connected = false,
                connectionString = testCase[0].toString(),
                responses = mapOf(
                    Long::class to { throw NotImplementedError() },
                    BuildInfoFromMongoDb::class to { throw NotImplementedError() }
                )
            )

            val data = BuildInfo.Slice.queryUsingDriver(driver)
            assertTestCase(data, testCase)
        }
    }

    @Test
    fun provides_the_correct_connection_host_for_atlas() = runTest {
        arrayOf(
            arrayOf("mongodb+srv://example-atlas-cluster.e06cc.mongodb.net", "example-atlas-cluster.e06cc.mongodb.net"),
            arrayOf("mongodb://example-atlas-cluster-00.e06cc.mongodb.net:27107", "example-atlas-cluster-00.e06cc.mongodb.net"),
            arrayOf("mongodb://localhost,another-server", null),
            arrayOf("mongodb+srv://ex-atlas-stream.e06cc.mongodb.net", "ex-atlas-stream.e06cc.mongodb.net"),
            arrayOf("mongodb://[::1]", null),
            arrayOf("mongodb://my-cluster.mongo.ondigitalocean.com", null),
            arrayOf("mongodb://my-cluster.cosmos.azure.com", null),
            arrayOf("mongodb://my-cluster.docdb.amazonaws.com", null),
            arrayOf("mongodb://my-cluster.docdb-elastic.amazonaws.com", null),
        ).forEach { testCase ->
            val driver = StubMongoDbDriver(
                connected = true,
                connectionString = testCase[0]!!,
                responses = mapOf(
                    Long::class to { QueryResult.Run(1L) },
                    BuildInfoFromMongoDb::class to { QueryResult.Run(defaultBuildInfo()) }
                )
            )

            val data = BuildInfo.Slice.queryUsingDriver(driver)
            assertEquals(testCase[1], data.atlasHost, "atlasHost does not match")
        }
    }

    private fun defaultBuildInfo() =
        BuildInfoFromMongoDb(
            "7.8.0",
            "1235abc",
            emptyList(),
            emptyMap(),
            false,
        )
}
