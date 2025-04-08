package com.mongodb.jbplugin.linting.environmentmismatch

import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListCollections.Collection
import com.mongodb.jbplugin.linting.Inspection.NoDatabaseInferred
import com.mongodb.jbplugin.linting.QueryInspectionTest
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

class NoDatabaseInferredInspectionTest : QueryInspectionTest<NoDatabaseInferred> {
    @Test
    fun `warns when a database cannot be inferred from the query`() = runInspectionTest {
        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.OnlyCollection(
                    null,
                    "collection"
                )
            )
        )

        val inspection = NoDatabaseInferredInspection()
        inspection.run(query, holder, Unit)

        onInsight(0).assertInsightDescriptionIs(
            "insight.no-database-inferred",
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

        `when`(readModelProvider.slice(any(), any<ListCollections.Slice>())).thenReturn(
            ListCollections(
                listOf(Collection("collection", "collection"))
            )
        )

        val inspection = NoDatabaseInferredInspection()
        inspection.run(query, holder, Unit)

        assertNoInsights()
    }
}
