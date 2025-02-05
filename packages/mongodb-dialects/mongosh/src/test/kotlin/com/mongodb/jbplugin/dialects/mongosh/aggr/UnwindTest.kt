package com.mongodb.jbplugin.dialects.mongosh.aggr

import com.mongodb.jbplugin.dialects.mongosh.assertGeneratedQuery
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class UnwindTest {
    @Test
    fun `can format an unwind stage`() = runTest {
        assertGeneratedQuery(
            """
            var collection = ""
            var database = ""

            db.getSiblingDB(database).getCollection(collection).aggregate([{"${"$"}unwind": "${'$'}myField"}, ])
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
                                    Named(Name.UNWIND),
                                    HasFieldReference(
                                        HasFieldReference.FromSchema(
                                            Unit,
                                            "${'$'}myField"
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
