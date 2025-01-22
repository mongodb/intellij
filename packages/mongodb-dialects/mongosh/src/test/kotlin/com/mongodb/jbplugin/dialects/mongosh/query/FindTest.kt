package com.mongodb.jbplugin.dialects.mongosh.query

import com.mongodb.jbplugin.dialects.mongosh.assertGeneratedQuery
import com.mongodb.jbplugin.mql.BsonAnyOf
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Namespace.Companion.invoke
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasSorts
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class FindTest {
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
    fun `can sort a find query when specified`() {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""

            db.getSiblingDB(database).getCollection(collection).find().sort({"a": 1, })
            """.trimIndent()
        ) {
            Node(
                Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.FIND_MANY),
                    HasSorts(
                        listOf(
                            Node(
                                Unit,
                                listOf(
                                    HasFieldReference(HasFieldReference.FromSchema(Unit, "a")),
                                    HasValueReference(
                                        HasValueReference.Inferred(Unit, 1, BsonInt32)
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    companion object {
        @JvmStatic
        fun queryCommandsThatDoNotReturnSortableCursors(): Array<IsCommand.CommandType> {
            return arrayOf(
                IsCommand.CommandType.COUNT_DOCUMENTS,
                IsCommand.CommandType.DELETE_MANY,
                IsCommand.CommandType.DELETE_ONE,
                IsCommand.CommandType.DISTINCT,
                IsCommand.CommandType.ESTIMATED_DOCUMENT_COUNT,
                IsCommand.CommandType.FIND_ONE,
                IsCommand.CommandType.FIND_ONE_AND_DELETE,
            )
        }
    }

    @ParameterizedTest
    @MethodSource("queryCommandsThatDoNotReturnSortableCursors")
    fun `can not sort a query that does not return a cursor`(command: IsCommand.CommandType) {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""

            db.getSiblingDB(database).getCollection(collection).${command.canonical}()
            """.trimIndent()
        ) {
            Node(
                Unit,
                listOf(
                    IsCommand(command),
                    HasSorts(
                        listOf(
                            Node(
                                Unit,
                                listOf(
                                    HasFieldReference(HasFieldReference.FromSchema(Unit, "a")),
                                    HasValueReference(
                                        HasValueReference.Inferred(Unit, 1, BsonInt32)
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
