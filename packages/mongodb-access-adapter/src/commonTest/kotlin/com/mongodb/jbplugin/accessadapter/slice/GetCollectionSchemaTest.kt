package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.QueryResult
import com.mongodb.jbplugin.accessadapter.StubMongoDbDriver
import com.mongodb.jbplugin.mql.BsonAnyOf
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.BsonDouble
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.OccurrencePercentage
import com.mongodb.jbplugin.mql.Value
import com.mongodb.jbplugin.mql.components.HasLimit
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetCollectionSchemaTest {
    @Test
    fun returns_an_empty_schema_if_the_database_is_not_provided() = runTest {
        val namespace = Namespace("", "myColl")
        val driver = StubMongoDbDriver(
            responses = mapOf(
                Map::class to { throw AssertionError("This must not be called") }
            )
        )

        val result = GetCollectionSchema.Slice(namespace, 50).queryUsingDriver(driver)

        assertEquals(namespace, result.schema.namespace)
        assertEquals(
            BsonObject(
                emptyMap(),
            ),
            result.schema.schema,
        )
    }

    @Test
    fun returns_an_empty_schema_if_the_collection_is_not_provided() = runTest {
        val namespace = Namespace("myDb", "")
        val driver = StubMongoDbDriver(
            responses = mapOf(
                Map::class to { throw AssertionError("This must not be called") }
            )
        )
        val result = GetCollectionSchema.Slice(namespace, 50).queryUsingDriver(driver)

        assertEquals(namespace, result.schema.namespace)
        assertEquals(
            BsonObject(
                emptyMap(),
            ),
            result.schema.schema,
        )
    }

    @Test
    fun should_build_a_schema_based_on_the_result_of_the_query() = runTest {
        val namespace = Namespace("myDb", "myColl")
        val driver = StubMongoDbDriver(
            responses = mapOf(
                Map::class to {
                    QueryResult.Run(
                        listOf(
                            mapOf("string" to "myString"),
                            mapOf("integer" to 52, "string" to "anotherString"),
                        )
                    )
                }
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

    @Test
    fun should_be_aware_of_different_shapes_of_sub_documents() = runTest {
        val namespace = Namespace("myDb", "myColl")
        val driver = StubMongoDbDriver(
            responses = mapOf(
                Map::class to {
                    QueryResult.Run(
                        listOf(
                            mapOf("book" to mapOf("author" to "Someone")),
                            mapOf("book" to mapOf("author" to "Someone Else", "isbn" to "XXXXXXXX"))
                        )
                    )
                }
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

    @Test
    fun should_be_aware_of_arrays_with_different_types_of_elements() = runTest {
        val namespace = Namespace("myDb", "myColl")
        val driver = StubMongoDbDriver(
            responses = mapOf(
                Map::class to {
                    QueryResult.Run(
                        listOf(
                            mapOf("array" to arrayOf(1, 2, 3, "abc")),
                            mapOf("array" to arrayOf(1.2f, "jkl")),
                        )
                    )
                }
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

    @Test
    fun should_respect_the_provided_limit_for_fetching_sample_documents() = runTest {
        val namespace = Namespace("myDb", "myColl")
        val driver = StubMongoDbDriver(
            responses = mapOf(
                Map::class to {
                    assertEquals(1, it.component<HasLimit>()?.limit)
                    QueryResult.Run(
                        listOf(
                            mapOf("string" to "myString"),
                            mapOf("integer" to 52, "string" to "anotherString"),
                        )
                    )
                }
            )
        )

        GetCollectionSchema.Slice(namespace, 1).queryUsingDriver(driver)
    }

    @Test
    fun should_hold_data_distribution_based_on_the_samples_collected() = runTest {
        val namespace = Namespace("myDb", "myColl")
        val driver = StubMongoDbDriver(
            responses = mapOf(
                Map::class to {
                    QueryResult.Run(
                        listOf(
                            mapOf("string" to "myString"),
                            mapOf("integer" to 52, "string" to "anotherString"),
                        )
                    )
                }
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
        assertEquals<Map<Value, OccurrencePercentage>>(
            mapOf(
                "myString" to 50.0,
                "anotherString" to 50.0,
            ),
            result.schema.dataDistribution.getDistributionForPath("string")!!
        )
        assertEquals(
            50.0,
            result.schema.dataDistribution.getDistributionForPath("integer")?.get(52)
        )
    }
}
