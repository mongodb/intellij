package com.mongodb.jbplugin.dialects.mongosh.aggr

import com.mongodb.jbplugin.dialects.mongosh.assertGeneratedQuery
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasAddedFields
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AddFieldsTest {
    @Test
    fun `can format an addFields stage including fields`() = runTest {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""

            db.getSiblingDB(database).getCollection(collection).aggregate([{"${"$"}addFields": {"myField": 1, }}, ]).limit(50)
            """.trimIndent()
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
                                    Named(Name.ADD_FIELDS),
                                    HasAddedFields(
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
    fun `can format an addFields stage excluding fields`() = runTest {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""

            db.getSiblingDB(database).getCollection(collection).aggregate([{"${"$"}addFields": {"myField": -1, }}, ]).limit(50)
            """.trimIndent()
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
                                    Named(Name.ADD_FIELDS),
                                    HasAddedFields(
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
                                                            -1,
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
}
