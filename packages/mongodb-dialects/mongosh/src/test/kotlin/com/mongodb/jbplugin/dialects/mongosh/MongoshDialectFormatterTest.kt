package com.mongodb.jbplugin.dialects.mongosh

import com.mongodb.jbplugin.indexing.CollectionIndexConsolidationOptions
import com.mongodb.jbplugin.indexing.IndexAnalyzer
import com.mongodb.jbplugin.indexing.IndexAnalyzer.SortDirection
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.SiblingQueriesFinder
import com.mongodb.jbplugin.mql.components.*
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType
import kotlinx.coroutines.runBlocking
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
            
            db.getSiblingDB(database).getCollection(collection).find({"myField": "myVal", }).limit(50).explain("queryPlanner")
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
            
            db.getSiblingDB(database).getCollection(collection).find({"myField": "myVal", }).limit(50).explain("executionStats")
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
            
            db.getSiblingDB(database).getCollection(collection).explain("queryPlanner").aggregate([{"${'$'}sort": {"myField": 1, }}, ])
            """.trimIndent(),
            explain = ExplainPlanType.SAFE
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
                                    Named(Name.SORT),
                                    HasSorts(
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
                                                        HasValueReference.Inferred(
                                                            Unit,
                                                            1,
                                                            BsonInt32
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
    fun `can format an update with a safe explain plan using queryPlanner`() = runTest {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""
            
            db.getSiblingDB(database).getCollection(collection).find({}).limit(50).explain("queryPlanner")
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
            
            db.getSiblingDB(database).getCollection(collection).find({}).limit(50).explain("queryPlanner")
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
                // Learn about creating an index: https://www.mongodb.com/docs/v7.0/core/data-model-operations/#indexes
                db.getSiblingDB("myDb").getCollection("myCollection")
                  .createIndex({ "myField": 1, "myField2": 1 })
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
                                    Named(Name.EQ),
                                    HasFieldReference(
                                        HasFieldReference.FromSchema(Unit, "myField")
                                    ),
                                    HasValueReference(
                                        HasValueReference.Runtime(Unit, BsonString)
                                    )
                                )
                            ),
                            Node(
                                Unit,
                                listOf(
                                    Named(Name.EQ),
                                    HasFieldReference(
                                        HasFieldReference.FromSchema(Unit, "myField2")
                                    ),
                                    HasValueReference(
                                        HasValueReference.Runtime(Unit, BsonString)
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
    fun `generates an index suggestion for a query given its fields and an explicit sort direction`() = runTest {
        assertGeneratedIndex(
            """
                // Learn about creating an index: https://www.mongodb.com/docs/v7.0/core/data-model-operations/#indexes
                db.getSiblingDB("myDb").getCollection("myCollection")
                  .createIndex({ "myField": -1, "myField2": 1 })
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
                                    Named(Name.EQ),
                                    HasFieldReference(
                                        HasFieldReference.FromSchema(Unit, "myField")
                                    ),
                                    HasValueReference(
                                        HasValueReference.Runtime(Unit, BsonString)
                                    )
                                )
                            ),
                            Node(
                                Unit,
                                listOf(
                                    Named(Name.EQ),
                                    HasFieldReference(
                                        HasFieldReference.FromSchema(Unit, "myField2")
                                    ),
                                    HasValueReference(
                                        HasValueReference.Runtime(Unit, BsonString)
                                    )
                                )
                            )
                        )
                    ),
                    HasSorts(
                        listOf(
                            Node(
                                Unit,
                                listOf(
                                    Named(Name.DESCENDING),
                                    HasFieldReference(
                                        HasFieldReference.FromSchema(Unit, "myField")
                                    ),
                                    HasValueReference(
                                        HasValueReference.Inferred(Unit, -1, BsonInt32)
                                    ),
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `generates an index suggestion with references to other covered queries`() = runTest {
        assertGeneratedIndexWithReferences(
            """
                // region Queries covered by this index 
                // myRef exists
                // endregion 
                // Learn about creating an index: https://www.mongodb.com/docs/v7.0/core/data-model-operations/#indexes
                db.getSiblingDB("myDb").getCollection("myCollection")
                  .createIndex({ "myField": 1, "myField2": 1 })
            """.trimIndent(),
            siblingQueries = arrayOf(
                Node(
                    Unit,
                    listOf(
                        HasCollectionReference(
                            HasCollectionReference.Known(
                                Unit,
                                Unit,
                                Namespace("myDb", "myCollection")
                            )
                        ),
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
                                            HasValueReference.Runtime(Unit, BsonString)
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            ),
            toQueryReference = {
                "myRef exists"
            }
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
                                    Named(Name.EQ),
                                    HasFieldReference(
                                        HasFieldReference.FromSchema(Unit, "myField")
                                    ),
                                    HasValueReference(
                                        HasValueReference.Runtime(Unit, BsonString)
                                    )
                                )
                            ),
                            Node(
                                Unit,
                                listOf(
                                    Named(Name.EQ),
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

    @Test
    fun `generates an index suggestion with a partial filter expression with eq`() = runTest {
        assertIndexJs(
            """
                // Learn about creating an index: https://www.mongodb.com/docs/v7.0/core/data-model-operations/#indexes
                db.getSiblingDB("myDb").getCollection("myCollection")
                  .createIndex({ "myField": 1 }, { partialFilterExpression: {"myField": true} })
            """.trimIndent(),
            IndexAnalyzer.SuggestedIndex.MongoDbIndex(
                collectionReference = HasCollectionReference(HasCollectionReference.Known(Unit, Unit, Namespace("myDb", "myCollection"))),
                fields = listOf(
                    IndexAnalyzer.SuggestedIndex.MongoDbIndexField("myField", Unit, SortDirection.Ascending, IndexAnalyzer.IndexSuggestionFieldReason.RoleEquality)
                ),
                coveredQueries = emptyList(),
                partialFilterExpression = Node(
                    Unit,
                    listOf(
                        Named(Name.EQ),
                        HasFieldReference(HasFieldReference.FromSchema(Unit, "myField")),
                        HasValueReference(HasValueReference.Constant(Unit, true, BsonBoolean))
                    )
                )
            )
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
                                    Named(Name.EQ),
                                    HasFieldReference(HasFieldReference.FromSchema(Unit, "myField")),
                                    HasValueReference(HasValueReference.Constant(Unit, true, BsonBoolean))
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `generates an index suggestion with a partial filter expression with gt`() = runTest {
        assertIndexJs(
            """
                // Learn about creating an index: https://www.mongodb.com/docs/v7.0/core/data-model-operations/#indexes
                db.getSiblingDB("myDb").getCollection("myCollection")
                  .createIndex({ "myField": 1 }, { partialFilterExpression: {"myField": {"${'$'}gt": 42}} })
            """.trimIndent(),
            IndexAnalyzer.SuggestedIndex.MongoDbIndex(
                collectionReference = HasCollectionReference(HasCollectionReference.Known(Unit, Unit, Namespace("myDb", "myCollection"))),
                fields = listOf(
                    IndexAnalyzer.SuggestedIndex.MongoDbIndexField("myField", Unit, SortDirection.Ascending, IndexAnalyzer.IndexSuggestionFieldReason.RoleEquality)
                ),
                coveredQueries = emptyList(),
                partialFilterExpression = Node(
                    Unit,
                    listOf(
                        Named(Name.GT),
                        HasFieldReference(HasFieldReference.FromSchema(Unit, "myField")),
                        HasValueReference(HasValueReference.Constant(Unit, 42, BsonInt32))
                    )
                )
            )
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
                                    Named(Name.EQ),
                                    HasFieldReference(HasFieldReference.FromSchema(Unit, "myField")),
                                    HasValueReference(HasValueReference.Constant(Unit, true, BsonBoolean))
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `can format a buildInfo run command`() = runTest {
        assertGeneratedQuery(
            """
            (function () {  return db.getSiblingDB("myDb").runCommand({"buildInfo": 1}); })()
            """.trimIndent()
        ) {
            Node(
                Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.RUN_COMMAND),
                    HasRunCommand(
                        database = HasValueReference(
                            HasValueReference.Constant(Unit, "myDb", BsonString)
                        ),
                        commandName = HasValueReference(
                            HasValueReference.Constant(Unit, "buildInfo", BsonString)
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `can format a listCollections run command`() = runTest {
        assertGeneratedQuery(
            """
            (function () {  return db.getSiblingDB("myDb").runCommand({"listCollections": 1, "authorizedCollections": true}); })()
            """.trimIndent()
        ) {
            Node(
                Unit,
                listOf(
                    IsCommand(IsCommand.CommandType.RUN_COMMAND),
                    HasRunCommand(
                        database = HasValueReference(
                            HasValueReference.Constant(Unit, "myDb", BsonString)
                        ),
                        commandName = HasValueReference(
                            HasValueReference.Constant(Unit, "listCollections", BsonString)
                        ),
                        additionalArguments = listOf(
                            HasFieldReference(
                                HasFieldReference.FromSchema(Unit, "authorizedCollections")
                            ) to
                                HasValueReference(
                                    HasValueReference.Constant(Unit, true, BsonBoolean)
                                )
                        )
                    ),
                    HasLimit(1)
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
    script: () -> Node<Unit>,
) {
    val query = script()
    val index = runBlocking {
        IndexAnalyzer.analyze(
            query,
            object : SiblingQueriesFinder<Unit> {
                override fun allSiblingsOf(query: Node<Unit>): Array<Node<Unit>> {
                    return emptyArray()
                }
            },
            CollectionIndexConsolidationOptions(10)
        )
    }

    val generated = MongoshDialectFormatter.indexCommand(query, index) { null }
    assertEquals(js, generated)
}

internal fun assertGeneratedIndexWithReferences(
    @Language("js") js: String,
    siblingQueries: Array<Node<Unit>>,
    toQueryReference: (Node<Unit>) -> String?,
    script: () -> Node<Unit>,
) {
    val query = script()
    val index = runBlocking {
        IndexAnalyzer.analyze(
            query,
            object : SiblingQueriesFinder<Unit> {
                override fun allSiblingsOf(query: Node<Unit>): Array<Node<Unit>> {
                    return siblingQueries
                }
            },
            CollectionIndexConsolidationOptions(10)
        )
    }

    val generated = MongoshDialectFormatter.indexCommand(query, index, toQueryReference)
    assertEquals(js, generated)
}

internal fun assertIndexJs(
    @Language("js") js: String,
    index: IndexAnalyzer.SuggestedIndex<Unit>,
    script: () -> Node<Unit>,
) {
    val query = script()
    val generated = MongoshDialectFormatter.indexCommand(query, index, { null })
    assertEquals(js, generated)
}
