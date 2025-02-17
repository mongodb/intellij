package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.ConnectionString
import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.QueryResult
import com.mongodb.jbplugin.mql.QueryContext
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import kotlin.time.Duration.Companion.seconds

class BuildInfoTest {
    @Test
    fun `returns a valid build info`(): Unit =
        runBlocking {
            val driver = mock<MongoDbDriver>()
            `when`(driver.connected).thenReturn(true)
            `when`(
                driver.connectionString()
            ).thenReturn(ConnectionString(listOf("mongodb://localhost/")))
            `when`(
                driver.runQuery<Long, Unit>(any(), eq(Long::class), any(), eq(1.seconds))
            ).thenReturn(QueryResult.Run(1L))
            `when`(driver.runQuery<BuildInfoFromMongoDb, Unit>(any(), any())).thenReturn(
                QueryResult.Run(defaultBuildInfo()),
            )

            val data = BuildInfo.Slice.queryUsingDriver(driver)
            assertEquals("7.8.0", data.version)
            assertEquals("1235abc", data.gitVersion)
        }

    @Test
    fun `when not connected do not run queries`(): Unit =
        runBlocking {
            val driver = mock<MongoDbDriver>()
            `when`(driver.connected).thenReturn(false)
            `when`(
                driver.connectionString()
            ).thenReturn(ConnectionString(listOf("mongodb://localhost/")))
            `when`(
                driver.runQuery<Long, Unit>(any(), eq(Long::class), any(), eq(1.seconds))
            ).doThrow(NotImplementedError())
            `when`(driver.runQuery<Any, Unit>(any(), any())).doThrow(
                NotImplementedError(),
            )

            BuildInfo.Slice.queryUsingDriver(driver)
        }

    @ParameterizedTest
    @CsvSource(
        value = [
            "URL;;isLocalhost;;isAtlas;;isAtlasStream;;isDigitalOcean;;isGenuineMongoDb;;mongodbVariant",
            "mongodb://localhost;;true;;false;;false;;false;;true;;",
            "mongodb://localhost,another-server;;false;;false;;false;;false;;true;;",
            "mongodb+srv://example-atlas-cluster.e06cc.mongodb.net;;false;;true;;false;;false;;true;;",
            "mongodb://example-atlas-cluster.e06cc.mongodb.net,another-server;;false;;false;;false;;false;;true;;",
            "mongodb+srv://atlas-stream-example-atlas-stream.e06cc.mongodb.net;;false;;true;;true;;false;;true;;",
            "mongodb://[::1];;true;;false;;false;;false;;true;;",
            "mongodb://my-cluster.mongo.ondigitalocean.com;;false;;false;;false;;true;;true;;",
            "mongodb://my-cluster.cosmos.azure.com;;false;;false;;false;;false;;false;;cosmosdb",
            "mongodb://my-cluster.docdb.amazonaws.com;;false;;false;;false;;false;;false;;documentdb",
            "mongodb://my-cluster.docdb-elastic.amazonaws.com;;false;;false;;false;;false;;false;;documentdb",
        ],
        delimiterString = ";;",
        useHeadersInDisplayName = true,
    )
    fun `parses different type of url connections properly`(
        url: String,
        isLocalhost: Boolean,
        isAtlas: Boolean,
        isAtlasStream: Boolean,
        isDigitalOcean: Boolean,
        isGenuineMongoDb: Boolean,
        mongodbVariant: String?,
    ): Unit =
        runBlocking {
            val driver = mock<MongoDbDriver>()
            `when`(driver.connected).thenReturn(true)
            `when`(driver.connectionString()).thenReturn(ConnectionString(listOf(url)))
            `when`(
                driver.runQuery<Long, Unit>(
                    any(),
                    eq(Long::class),
                    eq(QueryContext.empty()),
                    eq(1.seconds)
                )
            ).thenReturn(QueryResult.Run(1L))
            `when`(driver.runQuery<BuildInfoFromMongoDb, Unit>(any(), any())).thenReturn(
                QueryResult.Run(defaultBuildInfo()),
            )

            val data = BuildInfo.Slice.queryUsingDriver(driver)
            assertEquals(isLocalhost, data.isLocalhost, "isLocalhost does not match")
            assertEquals(isAtlas, data.isAtlas, "isAtlas does not match")
            assertEquals(isAtlasStream, data.isAtlasStream, "isAtlasStream does not match")
            assertEquals(isDigitalOcean, data.isDigitalOcean, "isDigitalOcean does not match")
            assertEquals(isGenuineMongoDb, data.isGenuineMongoDb, "isGenuineMongoDb does not match")
            assertEquals(mongodbVariant, data.nonGenuineVariant, "mongodbVariant does not match")
        }

    @ParameterizedTest
    @CsvSource(
        value = [
            "URL;;atlasHost",
            "mongodb+srv://example-atlas-cluster.e06cc.mongodb.net;;example-atlas-cluster.e06cc.mongodb.net",
            "mongodb://example-atlas-cluster-00.e06cc.mongodb.net:27107;;example-atlas-cluster-00.e06cc.mongodb.net",
            "mongodb://localhost,another-server;;",
            "mongodb+srv://ex-atlas-stream.e06cc.mongodb.net;;ex-atlas-stream.e06cc.mongodb.net",
            "mongodb://[::1];;",
            "mongodb://my-cluster.mongo.ondigitalocean.com;;",
            "mongodb://my-cluster.cosmos.azure.com;;",
            "mongodb://my-cluster.docdb.amazonaws.com;;",
            "mongodb://my-cluster.docdb-elastic.amazonaws.com;;",
        ],
        delimiterString = ";;",
        useHeadersInDisplayName = true,
    )
    fun `provides the correct connection host for atlas`(
        url: String,
        atlasHost: String?,
    ): Unit =
        runBlocking {
            val driver = mock<MongoDbDriver>()
            `when`(driver.connected).thenReturn(true)
            `when`(driver.connectionString()).thenReturn(ConnectionString(listOf(url)))
            `when`(
                driver.runQuery<Long, Unit>(any(), eq(Long::class), any(), eq(1.seconds))
            ).thenReturn(QueryResult.Run(1L))
            `when`(driver.runQuery<BuildInfoFromMongoDb, Unit>(any(), any())).thenReturn(
                QueryResult.Run(defaultBuildInfo()),
            )

            val data = BuildInfo.Slice.queryUsingDriver(driver)
            assertEquals(atlasHost, data.atlasHost, "atlasHost does not match")
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
