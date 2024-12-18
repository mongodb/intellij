package com.mongodb.jbplugin.dialects.mongosh

import com.mongodb.jbplugin.mql.BsonAnyOf
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.*
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class MongoshDialectFormatterTest {
    @Test
    fun `can format a query without references to a collection reference`() {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""

            db.getSiblingDB(database).getCollection(collection).find({"myField": "myVal", })
            """.trimIndent()
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
    fun `can format a simple query`() {
        val namespace = Namespace("myDb", "myColl")

        assertGeneratedQuery(
            """
            db.getSiblingDB("myDb").getCollection("myColl").find({"myField": "myVal", })
            """.trimIndent()
        ) {
            Node(
                Unit,
                listOf(
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
    fun `can format a query with an explain plan`() {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""
            
            db.getSiblingDB(database)
              .getCollection(collection)
              .explain().find(
                            {"myField": "myVal", }
              )
            """.trimIndent(),
            explain = true
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
    fun `can format an aggregate query with a match expression at the beginning`() {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""

            db.getSiblingDB(database).getCollection(collection).aggregate([{"${"$"}match": {"myField": "myVal"}}])
            """.trimIndent(),
            explain = false
        ) {
            Node(
                Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        listOf(
                            Node(
                                Unit,
                                listOf(
                                    Named(Name.MATCH),
                                    HasFilter(
                                        listOf(
                                            Node(
                                                Unit,
                                                listOf(
                                                    HasFieldReference(
                                                        HasFieldReference.FromSchema(
                                                            Unit,
                                                            "myField"
                                                        )
                                                    ),
                                                    HasValueReference(
                                                        HasValueReference.Constant(
                                                            Unit,
                                                            "myVal",
                                                            BsonString
                                                        )
                                                    )
                                                )
                                            )
                                        )
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
    fun `can format an explain command for a valid aggregate query`() {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""
            
            db.getSiblingDB(database)
              .getCollection(collection)
              .explain().aggregate(
                                 [
                                   {"${'$'}match": {"myField": "myVal"}}
                                 ]
              )
            """.trimIndent(),
            explain = true
        ) {
            Node(
                Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.AGGREGATE),
                    HasAggregation(
                        listOf(
                            Node(
                                Unit,
                                listOf(
                                    Named(Name.MATCH),
                                    HasFilter(
                                        listOf(
                                            Node(
                                                Unit,
                                                listOf(
                                                    HasFieldReference(
                                                        HasFieldReference.FromSchema(
                                                            Unit,
                                                            "myField"
                                                        )
                                                    ),
                                                    HasValueReference(
                                                        HasValueReference.Constant(
                                                            Unit,
                                                            "myVal",
                                                            BsonString
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["and", "or", "nor"])
    fun `can format a query with subquery operators`(operator: String) {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""

            db.getSiblingDB(database).getCollection(collection).find({"${"$"}$operator": [{"myField": "myVal"}, ]})
            """.trimIndent()
        ) {
            Node(
                Unit,
                listOf(
                    Named(Name.from(operator)),
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
    fun `can format query using the not operator`() {
        val namespace = Namespace("myDb", "myColl")

        assertGeneratedQuery(
            """
            db.getSiblingDB("myDb").getCollection("myColl").find({"myField": {"${"$"}not": "myVal"}, })
            """.trimIndent()
        ) {
            Node(
                Unit,
                listOf(
                    HasCollectionReference(HasCollectionReference.Known(Unit, Unit, namespace)),
                    HasFilter(
                        listOf(
                            Node(
                                Unit,
                                listOf(
                                    Named(Name.NOT),
                                    HasFilter(
                                        listOf(
                                            Node(
                                                Unit,
                                                listOf(
                                                    Named(Name.EQ),
                                                    HasFieldReference(
                                                        HasFieldReference.FromSchema(
                                                            Unit,
                                                            "myField"
                                                        )
                                                    ),
                                                    HasValueReference(
                                                        HasValueReference.Constant(
                                                            Unit,
                                                            "myVal",
                                                            BsonString
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["lt", "lte", "gt", "gte"])
    fun `can format a query with range operators`(operator: String) {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""

            db.getSiblingDB(database).getCollection(collection).find({"myField": {"${"$"}$operator": "myVal"}, })
            """.trimIndent()
        ) {
            Node(
                Unit,
                listOf(
                    HasFilter(
                        listOf(
                            Node(
                                Unit,
                                listOf(
                                    Named(Name.from(operator)),
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

    @ParameterizedTest
    @ValueSource(strings = ["in", "nin"])
    fun `can format a query with the in or nin operator`(operator: String) {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""

            db.getSiblingDB(database).getCollection(collection).find({"myField": {"${"$"}$operator": [1, 2]}, })
            """.trimIndent()
        ) {
            Node(
                Unit,
                listOf(
                    HasFilter(
                        listOf(
                            Node(
                                Unit,
                                listOf(
                                    Named(Name.from(operator)),
                                    HasFieldReference(
                                        HasFieldReference.FromSchema(Unit, "myField")
                                    ),
                                    HasValueReference(
                                        HasValueReference.Constant(
                                            Unit,
                                            listOf(1, 2),
                                            BsonArray(BsonAnyOf(BsonInt32))
                                        )
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

private fun assertGeneratedQuery(
    @Language("js") js: String,
    explain: Boolean = false,
    script: () -> Node<Unit>
) {
    val generated = MongoshDialectFormatter.formatQuery(script(), explain)
    assertEquals(js, generated.query)
}

private fun assertGeneratedIndex(
    @Language("js") js: String,
    script: () -> Node<Unit>
) {
    val generated = MongoshDialectFormatter.indexCommandForQuery(script())
    assertEquals(js, generated)
}
