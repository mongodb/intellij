package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.QueryResult
import com.mongodb.jbplugin.accessadapter.StubMongoDbDriver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListCollectionsTest {
    @Test
    fun returns_no_collections_if_database_is_not_provided() = runTest {
        val driver = StubMongoDbDriver(
            responses = mapOf(
                Map::class to { throw AssertionError("This must not be called") }
            )
        )
        val result = ListCollections.Slice("").queryUsingDriver(driver)

        assertTrue(result.collections.isEmpty())
    }

    @Test
    fun returns_collections_if_the_database_is_provided() = runTest {
        val driver = StubMongoDbDriver(
            responses = mapOf(
                Map::class to {
                    QueryResult.Run(
                        mapOf(
                            "cursor" to mapOf(
                                "firstBatch" to listOf(
                                    mapOf("name" to "myCollection", "type" to "collection")
                                )
                            )
                        )
                    )
                }
            )
        )

        val result = ListCollections.Slice("myDb").queryUsingDriver(driver)

        assertEquals(
            listOf(
                ListCollections.Collection("myCollection", "collection")
            ),
            result.collections
        )
    }
}
