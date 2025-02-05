package com.mongodb.jbplugin.dialects.mongosh.aggr

import com.mongodb.jbplugin.dialects.mongosh.assertGeneratedQuery
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasProjections
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AggregateTest {
    @Test
    fun `can format a safe explain command for a valid aggregate query`() = runTest {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""
            
            db.getSiblingDB(database).getCollection(collection).explain("queryPlanner").aggregate([{"${'$'}match": {"myField": "myVal"}}, ])
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
    fun `can format a full explain command for a valid aggregate query`() = runTest {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""
            
            db.getSiblingDB(database).getCollection(collection).explain("executionStats").aggregate([{"${'$'}match": {"myField": "myVal"}}, ])
            """.trimIndent(),
            explain = ExplainPlanType.FULL
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
    fun `can format an aggregate with multiple stages`() = runTest {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""
            
            db.getSiblingDB(database).getCollection(collection).aggregate([{"${'$'}match": {"myField": "myVal"}}, {"${'$'}project": {"myField": 1, }}, ])
            """.trimIndent(),
            explain = ExplainPlanType.NONE
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
                                    ),
                                )
                            ),
                            Node(
                                Unit,
                                listOf(
                                    Named(Name.PROJECT),
                                    HasProjections(
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
                                                            1,
                                                            BsonInt32
                                                        )
                                                    )
                                                )
                                            )
                                        )
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
    fun `can format a full explain command for a valid aggregate query removing destructive operations`() = runTest {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""
            
            db.getSiblingDB(database).getCollection(collection).explain("executionStats").aggregate([{"${'$'}match": {"myField": "myVal"}}, ])
            """.trimIndent(),
            explain = ExplainPlanType.FULL
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
                                    ),
                                    Named(Name.PROJECT),
                                    HasProjections(
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
}
