package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.QueryResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class ListCollectionsTest {
    @Test
    fun `returns no collections if database is not provided`() {
        runBlocking {
            val driver = mock<MongoDbDriver>()
            val result = ListCollections.Slice("").queryUsingDriver(driver)

            assertTrue(result.collections.isEmpty())
            verify(driver, never()).runQuery<Any, Unit>(any(), any())
        }
    }

    @Test
    fun `returns collections if the database is provided`() {
        runBlocking {
            val driver = mock<MongoDbDriver>()

            `when`(driver.runQuery<Map<String, Any>, Unit>(any(), any())).thenReturn(
                QueryResult.Run(
                    mapOf(
                        "cursor" to mapOf(
                            "firstBatch" to listOf(
                                mapOf("name" to "myCollection", "type" to "collection")
                            )
                        )
                    )
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
}
