package com.mongodb.jbplugin.dialects.mongosh.aggr

import com.mongodb.jbplugin.dialects.mongosh.assertGeneratedQuery
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasLimit
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class LimitTest {
    @Test
    fun `can format an limit stage`() = runTest {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""

            db.getSiblingDB(database).getCollection(collection).aggregate([{"${"$"}limit": 11}, ])
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
                                    Named(Name.LIMIT),
                                    HasLimit(11)
                                )
                            )
                        )
                    )
                )
            )
        }
    }
}
