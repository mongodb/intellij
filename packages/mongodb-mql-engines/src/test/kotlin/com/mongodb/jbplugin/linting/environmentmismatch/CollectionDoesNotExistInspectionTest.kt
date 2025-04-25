package com.mongodb.jbplugin.linting.environmentmismatch

import com.mongodb.jbplugin.linting.Inspection.CollectionDoesNotExist
import com.mongodb.jbplugin.linting.QueryInspectionTest
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import org.junit.jupiter.api.Test

class CollectionDoesNotExistInspectionTest : QueryInspectionTest<CollectionDoesNotExist> {
    @Test
    fun `warns about a referenced collection not existing in cluster`() = runInspectionTest {
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

        whenDatabasesAre(listOf("database"))

        whenCollectionsAre(emptyList())

        val inspection = CollectionDoesNotExistInspection<Unit>()
        inspection.run(
            query,
            holder,
            CollectionDoesNotExistInspectionSettings(
                dataSource = Unit,
                readModelProvider = readModelProvider,
            )
        )

        onInsight(0).assertInsightDescriptionIs(
            "insight.collection-does-not-exist",
            "collection",
            "database"
        )
    }

    @Test
    fun `does not warn when the database itself does not exist in cluster`() = runInspectionTest {
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

        whenDatabasesAre(emptyList())

        whenCollectionsAre(emptyList())

        val inspection = CollectionDoesNotExistInspection<Unit>()
        inspection.run(
            query,
            holder,
            CollectionDoesNotExistInspectionSettings(
                dataSource = Unit,
                readModelProvider = readModelProvider,
            )
        )

        assertNoInsights()
    }

    @Test
    fun `does not warn when the referenced collection exists in cluster`() = runInspectionTest {
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

        whenDatabasesAre(listOf("database"))

        whenCollectionsAre(listOf("collection"))

        val inspection = CollectionDoesNotExistInspection<Unit>()
        inspection.run(
            query,
            holder,
            CollectionDoesNotExistInspectionSettings(
                dataSource = Unit,
                readModelProvider = readModelProvider,
            )
        )

        assertNoInsights()
    }
}
