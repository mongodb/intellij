package com.mongodb.jbplugin.dialects.mongosh

import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.components.*
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MongoshDialectFormatterTest {
    @Test
    fun `can format a simple delete query`() {
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
    fun `can format a query with a safe explain plan using queryPlanner`() {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""
            
            db.getSiblingDB(database).getCollection(collection).explain("queryPlanner").find({"myField": "myVal", })
            """.trimIndent(),
            explain = QueryContext.ExplainPlanType.SAFE
        ) {
            Node(
                Unit,
                listOf(
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
    fun `can format a query with a full explain plan using executionStats`() {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""
            
            db.getSiblingDB(database).getCollection(collection).explain("executionStats").find({"myField": "myVal", })
            """.trimIndent(),
            explain = QueryContext.ExplainPlanType.FULL
        ) {
            Node(
                Unit,
                listOf(
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
    fun `generates an index suggestion for a query given its fields`() {
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

internal fun assertGeneratedQuery(
    @Language("js") js: String,
    explain: QueryContext.ExplainPlanType = QueryContext.ExplainPlanType.NONE,
    script: () -> Node<Unit>
) {
    val generated = MongoshDialectFormatter.formatQuery(
        script(),
        QueryContext(emptyMap(), explain, false)
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
