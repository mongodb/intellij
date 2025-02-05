package com.mongodb.jbplugin.dialects.mongosh

import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.components.*
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType
import kotlinx.coroutines.test.runTest
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MongoshDialectFormatterTest {

    @Test
    fun `can format a simple delete query`() = runTest {
        val namespace = Namespace("myDb", "myColl")

        assertGeneratedQuery(
            """
            db.getSiblingDB("myDb").getCollection("myColl").deleteMany({"myField": "myVal", })
            """.trimIndent()
        ) {
            Node(
                Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.DELETE_MANY),
                    HasCollectionReference(HasCollectionReference.Known(Unit, Unit, namespace)),
                    HasFilter(
                        listOf(
                            Node(
                                Unit,
                                listOf(
                                    Named(Name.EQ),
                                    HasFieldReference(
                                        HasFieldReference.FromSchema(Unit, "myField")
                                    ),
                                    HasValueReference(
                                        HasValueReference.Constant(Unit, "myVal", BsonString)
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `can format a query with a safe explain plan using queryPlanner`() = runTest {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""
            
            db.getSiblingDB(database).getCollection(collection).find({"myField": "myVal", }, {"explain": "queryPlanner"}).next()
            """.trimIndent(),
            explain = ExplainPlanType.SAFE
        ) {
            Node(
                Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.FIND_MANY),
                    HasFilter(
                        listOf(
                            Node(
                                Unit,
                                listOf(
                                    HasFieldReference(
                                        HasFieldReference.FromSchema(Unit, "myField")
                                    ),
                                    HasValueReference(
                                        HasValueReference.Constant(Unit, "myVal", BsonString)
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `can format a query with a full explain plan using executionStats`() = runTest {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""
            
            db.getSiblingDB(database).getCollection(collection).find({"myField": "myVal", }, {"explain": "executionStats"}).next()
            """.trimIndent(),
            explain = ExplainPlanType.FULL
        ) {
            Node(
                Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.FIND_MANY),
                    HasFilter(
                        listOf(
                            Node(
                                Unit,
                                listOf(
                                    HasFieldReference(
                                        HasFieldReference.FromSchema(Unit, "myField")
                                    ),
                                    HasValueReference(
                                        HasValueReference.Constant(Unit, "myVal", BsonString)
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `can format an aggregate with a safe explain plan using queryPlanner`() = runTest {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""
            
            db.getSiblingDB(database).getCollection(collection).aggregate([], {"explain": "queryPlanner"})
            """.trimIndent(),
            explain = ExplainPlanType.SAFE
        ) {
            Node(
                Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation<List<Node<Unit>>>(emptyList())
                )
            )
        }
    }

    @Test
    fun `can format an update with a safe explain plan using queryPlanner`() = runTest {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""
            
            db.getSiblingDB(database).getCollection(collection).updateMany({}, {}, {"explain": "queryPlanner"})
            """.trimIndent(),
            explain = ExplainPlanType.SAFE
        ) {
            Node(
                Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.UPDATE_MANY),
                    HasUpdates<List<Node<Unit>>>(emptyList()),
                    HasFilter<List<Node<Unit>>>(emptyList())
                )
            )
        }
    }

    @Test
    fun `can format a delete query with a safe explain plan using queryPlanner`() = runTest {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""
            
            db.getSiblingDB(database).getCollection(collection).deleteMany({}, {"explain": "queryPlanner"})
            """.trimIndent(),
            explain = ExplainPlanType.SAFE
        ) {
            Node(
                Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.DELETE_MANY),
                    HasFilter<List<Node<Unit>>>(emptyList())
                )
            )
        }
    }

    @Test
    fun `generates an index suggestion for a query given its fields`() = runTest {
        assertGeneratedIndex(
            """
                // Potential fields to consider indexing: myField, myField2
                // Learn about creating an index: https://www.mongodb.com/docs/v7.0/core/data-model-operations/#indexes
                db.getSiblingDB("myDb").getCollection("myCollection")
                  .createIndex({ "<your_field_1>": 1, "<your_field_2>": 1 })
            """.trimIndent()
        ) {
            Node(
                Unit,
                listOf(
                    HasCollectionReference(
                        HasCollectionReference.Known(Unit, Unit, Namespace("myDb", "myCollection"))
                    ),
                    HasFilter(
                        listOf(
                            Node(
                                Unit,
                                listOf(
                                    HasFieldReference(
                                        HasFieldReference.FromSchema(Unit, "myField")
                                    ),
                                    HasValueReference(
                                        HasValueReference.Constant(Unit, "myVal", BsonString)
                                    )
                                )
                            ),
                            Node(
                                Unit,
                                listOf(
                                    HasFieldReference(
                                        HasFieldReference.FromSchema(Unit, "myField2")
                                    ),
                                    HasValueReference(
                                        HasValueReference.Constant(Unit, "myVal2", BsonString)
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }
}

internal suspend fun assertGeneratedQuery(
    @Language("js") js: String,
    explain: ExplainPlanType = ExplainPlanType.NONE,
    script: suspend () -> Node<Unit>
) {
    val generated = MongoshDialectFormatter.formatQuery(
        script().let {
            if (explain != ExplainPlanType.NONE) {
                it.with(HasExplain(explain))
            } else {
                it
            }
        },
        QueryContext(emptyMap(), false, false)
    )
    assertEquals(js, generated.query)
}

internal fun assertGeneratedIndex(
    @Language("js") js: String,
    script: () -> Node<Unit>
) {
    val generated = MongoshDialectFormatter.indexCommandForQuery(script())
    assertEquals(js, generated)
}
