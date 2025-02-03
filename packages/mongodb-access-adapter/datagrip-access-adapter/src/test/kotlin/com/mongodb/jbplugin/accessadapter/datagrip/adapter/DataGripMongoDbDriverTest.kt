package com.mongodb.jbplugin.accessadapter.datagrip.adapter

import com.google.gson.Gson
import com.intellij.database.dataSource.DatabaseDriver
import com.intellij.database.dataSource.LocalDataSource
import com.mongodb.jbplugin.accessadapter.ExplainPlan
import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.datagrip.IntegrationTest
import com.mongodb.jbplugin.accessadapter.datagrip.MongoDbVersion
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasValueReference
import kotlinx.coroutines.test.runTest
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito
import java.math.BigDecimal
import java.text.DateFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.UUID

@IntegrationTest
class DataGripMongoDbDriverTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "mongo",
            "mongo.4"
        ]
    )
    fun `can detect a datasource with a known driver id`(driverId: String) {
        val dataSource = Mockito.mock(LocalDataSource::class.java)
        val driver = Mockito.mock(DatabaseDriver::class.java)

        Mockito.`when`(driver.id).thenReturn(driverId)
        Mockito.`when`(dataSource.databaseDriver).thenReturn(driver)

        assertTrue(dataSource.isMongoDbDataSource())
    }

    @Test
    fun `can detect a datasource when there is no driver`() {
        val dataSource = Mockito.mock(LocalDataSource::class.java)
        Mockito.`when`(dataSource.databaseDriver).thenReturn(null)

        assertTrue(dataSource.isMongoDbDataSource())
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "mondongo",
            "4.mongo",
            "postgres",
            "oracle",
            "sqlite",
        ]
    )
    fun `can discard a datasource with an unknown driver id`(driverId: String) {
        val dataSource = Mockito.mock(LocalDataSource::class.java)
        val driver = Mockito.mock(DatabaseDriver::class.java)

        Mockito.`when`(driver.id).thenReturn(driverId)
        Mockito.`when`(dataSource.databaseDriver).thenReturn(driver)

        assertFalse(dataSource.isMongoDbDataSource())
    }

    @Test
    fun `can connect and run a command`(
        version: MongoDbVersion,
        driver: MongoDbDriver,
    ) = runTest {
        val result =
            driver.runCommand(
                "admin",
                Document(
                    mapOf(
                        "buildInfo" to 1,
                    ),
                ),
                Map::class,
            )

        assertEquals(result["version"], version.versionString)
    }

    @Test
    fun `is able to map the result to a class`(
        version: MongoDbVersion,
        driver: MongoDbDriver,
    ) = runTest {
        data class MyBuildInfo(
            val version: String,
        )

        val result =
            driver.runCommand(
                "admin",
                Document(
                    mapOf(
                        "buildInfo" to 1,
                    ),
                ),
                MyBuildInfo::class,
            )

        assertEquals(result.version, version.versionString)
    }

    @Test
    fun `it is able to run an explain plan given a query and returns a collscan if no index available`(
        driver: MongoDbDriver,
    ) = runTest {
        val namespace = Namespace("myDb", "myCollection")

        val query = Node(
            Unit,
            listOf(
                HasCollectionReference(HasCollectionReference.Known(Unit, Unit, namespace)),
                HasFilter(
                    listOf(
                        Node(
                            Unit,
                            listOf(
                                HasFieldReference(HasFieldReference.FromSchema(Unit, "myField")),
                                HasValueReference(
                                    HasValueReference.Constant(Unit, "myVal", BsonString)
                                ),
                            )
                        )
                    )
                )
            )
        )

        driver.runCommand(
            namespace.database,
            Document(
                mapOf(
                    "insert" to namespace.collection,
                    "documents" to
                        listOf(
                            mapOf("a" to 1),
                        ),
                ),
            ),
            Unit::class,
        )

        val explainPlanResult = driver.explain(
            query,
            QueryContext(emptyMap(), QueryContext.ExplainPlanType.SAFE, false)
        )
        assertEquals(ExplainPlan.CollectionScan, explainPlanResult)
    }

    @Test
    fun `it is able to run an explain plan given a query and returns a indexscan if an index available`(
        driver: MongoDbDriver,
    ) = runTest {
        val namespace = Namespace("myDb", "myCollection")

        driver.runCommand(
            namespace.database,
            Document(
                mapOf(
                    "createIndexes" to namespace.collection,
                    "indexes" to arrayOf(
                        Document(
                            mapOf(
                                "key" to Document("myField", 1),
                                "name" to "myField_1"
                            )
                        )
                    )
                )
            ),
            Unit::class
        )

        val query = Node(
            Unit,
            listOf(
                HasCollectionReference(HasCollectionReference.Known(Unit, Unit, namespace)),
                HasFilter(
                    listOf(
                        Node(
                            Unit,
                            listOf(
                                HasFieldReference(HasFieldReference.FromSchema(Unit, "myField")),
                                HasValueReference(
                                    HasValueReference.Constant(Unit, "myVal", BsonString)
                                ),
                            )
                        )
                    )
                )
            )
        )

        val explainPlanResult = driver.explain(
            query,
            QueryContext(emptyMap(), QueryContext.ExplainPlanType.SAFE, false)
        )
        assertEquals(ExplainPlan.IndexScan, explainPlanResult)
    }

    @ParameterizedTest
    @MethodSource("ejsonMapping")
    fun `maps ejson to its expected type`(ejson: String, expected: Any) {
        val map = Gson().fromJson(ejson, Map::class.java) as Map<String, Any>
        val actual = DataGripMongoDbDriver.deserializeEJson(map)

        assertEquals(expected, actual)
    }

    companion object {
        @JvmStatic
        fun ejsonMapping() = arrayOf(
          arrayOf("{\"${'$'}oid\":\"5d505646cf6d4fe581014ab2\"}", ObjectId("5d505646cf6d4fe581014ab2")),
          arrayOf("{ \"doc\": [\"hello\",{\"${'$'}numberInt\":\"10\"}]}", mapOf("doc" to listOf("hello", 10))),
          arrayOf("{\"${'$'}date\":{\"${'$'}numberLong\":\"1565546054692\"}}", Instant.ofEpochMilli(1565546054692L)),
          arrayOf("{\"${'$'}numberDecimal\":\"10.99\"}", BigDecimal("10.99")),
          arrayOf("{\"${'$'}numberInt\":\"10\"}", 10),
          arrayOf("{\"${'$'}numberLong\":\"999999999\"}", 999999999L),
          arrayOf("{\"${'$'}uuid\":\"3b241101-e2bb-4255-8caf-4136c566a962\"}", UUID.fromString("3b241101-e2bb-4255-8caf-4136c566a962")),
        )
    }
}
