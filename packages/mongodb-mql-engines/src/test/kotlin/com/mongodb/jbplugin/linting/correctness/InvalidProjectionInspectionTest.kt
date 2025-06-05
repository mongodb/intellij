package com.mongodb.jbplugin.linting.correctness

import com.mongodb.jbplugin.linting.Inspection.InvalidProjection
import com.mongodb.jbplugin.linting.QueryInspectionTest
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFieldReference.FromSchema
import com.mongodb.jbplugin.mql.components.HasProjections
import com.mongodb.jbplugin.mql.components.Name.EXCLUDE
import com.mongodb.jbplugin.mql.components.Name.INCLUDE
import com.mongodb.jbplugin.mql.components.Name.PROJECT
import com.mongodb.jbplugin.mql.components.Named
import org.junit.jupiter.api.Test

class InvalidProjectionInspectionTest : QueryInspectionTest<InvalidProjection> {
    @Test
    fun `warns about using an inclusion in an exclusion projection`() = runInspectionTest {
        whenDatabasesAre(listOf("database"))
        whenCollectionsAre(listOf("collection"))

        val query = query.with(
            HasAggregation(
                listOf(
                    projectStage("myIncluded", "myExcluded"),
                )
            )
        )

        val inspection = InvalidProjectionInspection()
        inspection.run(query, holder, InvalidProjectionInspectionSettings())

        onInsight(0).assertInsightDescriptionIs(
            "insight.invalid-query-projection",
            "myIncluded"
        )
    }

    @Test
    fun `warns about using an inclusion in an exclusion projection when there are multiple projects`() = runInspectionTest {
        whenDatabasesAre(listOf("database"))
        whenCollectionsAre(listOf("collection"))

        val query = query.with(
            HasAggregation(
                listOf(
                    projectStage("myIncluded", "myExcluded"),
                    projectStage("myIncluded2", "myExcluded2")
                )
            )
        )

        val inspection = InvalidProjectionInspection()
        inspection.run(query, holder, InvalidProjectionInspectionSettings())

        onInsight(0).assertInsightDescriptionIs(
            "insight.invalid-query-projection",
            "myIncluded"
        )

        onInsight(1).assertInsightDescriptionIs(
            "insight.invalid-query-projection",
            "myIncluded2"
        )
    }

    private fun projectStage(
        fieldToInclude: String,
        fieldToExclude: String
    ) = Node(
        Unit,
        listOf(
            Named(PROJECT),
            HasProjections(
                listOf(
                    Node(
                        Unit,
                        listOf(
                            Named(INCLUDE),
                            HasFieldReference(FromSchema(Unit, fieldToInclude))
                        )
                    ),
                    Node(
                        Unit,
                        listOf(
                            Named(EXCLUDE),
                            HasFieldReference(FromSchema(Unit, fieldToExclude))
                        )
                    )
                )
            )
        )

    )
}
