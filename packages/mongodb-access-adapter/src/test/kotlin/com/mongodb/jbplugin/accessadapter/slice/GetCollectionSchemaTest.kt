package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.QueryResult
import com.mongodb.jbplugin.mql.BsonAnyOf
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.BsonDouble
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.components.HasLimit
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.seconds

class GetCollectionSchemaTest {
    @Test
    fun `returns an empty schema if the database is not provided`() {
        runBlocking {
            val namespace = Namespace("", "myColl")
            val driver = mock<MongoDbDriver>()
            val result = GetCollectionSchema.Slice(namespace, 50).queryUsingDriver(driver)

            assertEquals(namespace, result.schema.namespace)
            assertEquals(
                BsonObject(
                    emptyMap(),
                ),
                result.schema.schema,
            )

            verify(driver, never()).runQuery<Any, Any>(any(), any(), any(), eq(1.seconds))
        }
    }

    @Test
    fun `returns an empty schema if the collection is not provided`() {
        runBlocking {
            val namespace = Namespace("myDb", "")
            val driver = mock<MongoDbDriver>()
            val result = GetCollectionSchema.Slice(namespace, 50).queryUsingDriver(driver)

            assertEquals(namespace, result.schema.namespace)
            assertEquals(
                BsonObject(
                    emptyMap(),
                ),
                result.schema.schema,
            )

            verify(driver, never()).runQuery<Any, Any>(any(), any(), any(), eq(1.seconds))
        }
    }

    @Test
    fun `should build a schema based on the result of the query`() {
        runBlocking {
            val namespace = Namespace("myDb", "myColl")
            val driver = mock<MongoDbDriver>()

            whenever(driver.runQuery<List<Map<String, Any>>, Any>(any(), any()))
                .thenReturn(
                    QueryResult.Run(
                        listOf(
                            mapOf("string" to "myString"),
                            mapOf("integer" to 52, "string" to "anotherString"),
                        )
                    )
                )

            val result = GetCollectionSchema.Slice(namespace, 50).queryUsingDriver(driver)

            assertEquals(namespace, result.schema.namespace)
            assertEquals(
                BsonObject(
                    mapOf(
                        "string" to BsonAnyOf(BsonNull, BsonString),
                        "integer" to BsonInt32,
                    ),
                ),
                result.schema.schema,
            )
        }
    }

    @Test
    fun `should be aware of different shapes of sub documents`() {
        runBlocking {
            val namespace = Namespace("myDb", "myColl")
            val driver = mock<MongoDbDriver>()

            `when`(driver.runQuery<List<Map<String, Any>>, Any>(any(), any())).thenReturn(
                QueryResult.Run(
                    listOf(
                        mapOf("book" to Document(mapOf("author" to "Someone"))),
                        mapOf("book" to mapOf("author" to "Someone Else", "isbn" to "XXXXXXXX"))
                    )
                )
            )

            val result = GetCollectionSchema.Slice(namespace, 50).queryUsingDriver(driver)

            assertEquals(namespace, result.schema.namespace)
            assertEquals(
                BsonObject(
                    mapOf(
                        "book" to
                            BsonObject(
                                mapOf(
                                    "author" to BsonAnyOf(BsonString, BsonNull),
                                    "isbn" to BsonAnyOf(BsonString, BsonNull),
                                ),
                            ),
                    ),
                ),
                result.schema.schema,
            )
        }
    }

    @Test
    fun `should be aware of arrays with different types of elements`() {
        runBlocking {
            val namespace = Namespace("myDb", "myColl")
            val driver = mock<MongoDbDriver>()

            `when`(driver.runQuery<List<Map<String, Any>>, Any>(any(), any())).thenReturn(
                QueryResult.Run(
                    listOf(
                        mapOf("array" to arrayOf(1, 2, 3, "abc")),
                        mapOf("array" to arrayOf(1.2f, "jkl")),
                    )
                )
            )

            val result = GetCollectionSchema.Slice(namespace, 50).queryUsingDriver(driver)

            assertEquals(namespace, result.schema.namespace)
            assertEquals(
                BsonObject(
                    mapOf(
                        "array" to BsonArray(
                            BsonAnyOf(BsonNull, BsonString, BsonDouble, BsonInt32)
                        ),
                    ),
                ),
                result.schema.schema,
            )
        }
    }

    @Test
    fun `should respect the provided limit for fetching sample documents`() {
        runBlocking {
            val namespace = Namespace("myDb", "myColl")
            val driver = mock<MongoDbDriver>()

            `when`(driver.runQuery<List<Map<String, Any>>, Any>(any(), any(), any())).thenReturn(
                QueryResult.Run(
                    listOf(
                        mapOf("string" to "myString"),
                        mapOf("integer" to 52, "string" to "anotherString"),
                    )
                )
            )

            GetCollectionSchema.Slice(namespace, 1).queryUsingDriver(driver)

            verify(driver, times(1)).runQuery<List<Map<String, Any>>, Any>(
                argThat {
                    component<HasLimit>()?.limit == 1
                },
                any()
            )
        }
    }

    @Test
    fun `should hold data distribution based on the samples collected`() {
        runBlocking {
            val namespace = Namespace("myDb", "myColl")
            val driver = mock<MongoDbDriver>()

            whenever(driver.runQuery<List<Map<String, Any>>, Any>(any(), any()))
                .thenReturn(
                    QueryResult.Run(
                        listOf(
                            mapOf("string" to "myString"),
                            mapOf("integer" to 52, "string" to "anotherString"),
                        )
                    )
                )

            val result = GetCollectionSchema.Slice(namespace, 50).queryUsingDriver(driver)

            assertEquals(namespace, result.schema.namespace)
            assertEquals(
                BsonObject(
                    mapOf(
                        "string" to BsonAnyOf(BsonNull, BsonString),
                        "integer" to BsonInt32,
                    ),
                ),
                result.schema.schema,
            )
            assertEquals(
                mapOf(
                    "myString" to 50,
                    "anotherString" to 50,
                ),
                result.schema.dataDistribution.getDistributionForPath("string")
            )
            assertEquals(
                50,
                result.schema.dataDistribution.getDistributionForPath("integer")?.get(52)
            )
        }
    }
}
