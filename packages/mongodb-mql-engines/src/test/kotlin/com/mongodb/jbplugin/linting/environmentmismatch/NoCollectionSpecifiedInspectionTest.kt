package com.mongodb.jbplugin.linting.environmentmismatch

import com.mongodb.jbplugin.linting.Inspection.NoCollectionSpecified
import com.mongodb.jbplugin.linting.QueryInspectionTest
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import org.junit.jupiter.api.Test

class NoCollectionSpecifiedInspectionTest : QueryInspectionTest<NoCollectionSpecified> {
    @Test
    fun `warns when a collection is not specified in the query`() = runInspectionTest {
        val inspection = NoCollectionSpecifiedInspection()
        // query in this run just an empty Node which is why this linter will throw an insight
        // for collection not being specified in the query
        inspection.run(query, holder, Unit)

        onInsight(0).assertInsightDescriptionIs(
            "insight.no-collection-specified",
        )
    }

    @Test
    fun `warns when an unknown collection reference is found in the query`() = runInspectionTest {
        query.with(
            HasCollectionReference(
                HasCollectionReference.Unknown
            )
        )

        val inspection = NoCollectionSpecifiedInspection()
        inspection.run(query, holder, Unit)

        onInsight(0).assertInsightDescriptionIs(
            "insight.no-collection-specified",
        )
    }

    @Test
    fun `does not warn when the database can be inferred from the query`() = runInspectionTest {
        val collectionNamespace = Namespace("database", "collection")
        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(
                    null,
                    null,
                    collectionNamespace
                )
            )
        )

        val inspection = NoCollectionSpecifiedInspection()
        inspection.run(query, holder, Unit)

        assertNoInsights()
    }
}
