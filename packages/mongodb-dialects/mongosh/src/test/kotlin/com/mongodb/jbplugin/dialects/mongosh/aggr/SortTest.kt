package com.mongodb.jbplugin.dialects.mongosh.aggr

import com.mongodb.jbplugin.dialects.mongosh.assertGeneratedQuery
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasSorts
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import org.junit.jupiter.api.Test

class SortTest {
    @Test
    fun `can format a sort stage`() {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""

            db.getSiblingDB(database).getCollection(collection).aggregate([{"${"$"}sort": {"myField": 1, "myOtherField": -1, }}, ])
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
                                            ),
                                            Node(
                                                Unit,
                                                listOf(
                                                    HasFieldReference(
                                                        HasFieldReference.FromSchema(
                                                            Unit,
                                                            "myOtherField"
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