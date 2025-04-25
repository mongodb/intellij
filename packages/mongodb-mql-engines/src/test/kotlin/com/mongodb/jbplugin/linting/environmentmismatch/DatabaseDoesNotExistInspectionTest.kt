package com.mongodb.jbplugin.linting.environmentmismatch

import com.mongodb.jbplugin.linting.Inspection.DatabaseDoesNotExist
import com.mongodb.jbplugin.linting.QueryInspectionTest
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import org.junit.jupiter.api.Test

class DatabaseDoesNotExistInspectionTest : QueryInspectionTest<DatabaseDoesNotExist> {
    @Test
    fun `warns about a referenced database not existing in cluster`() = runInspectionTest {
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

        val inspection = DatabaseDoesNotExistInspection<Unit>()
        inspection.run(
            query,
            holder,
            DatabaseDoesNotExistInspectionSettings(
                dataSource = Unit,
                readModelProvider = readModelProvider,
            )
        )

        onInsight(0).assertInsightDescriptionIs(
            "insight.database-does-not-exist",
            "database"
        )
    }

    @Test
    fun `does not warn when the referenced database exists in cluster`() = runInspectionTest {
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

        val inspection = DatabaseDoesNotExistInspection<Unit>()
        inspection.run(
            query,
            holder,
            DatabaseDoesNotExistInspectionSettings(
                dataSource = Unit,
                readModelProvider = readModelProvider,
            )
        )

        assertNoInsights()
    }
}
