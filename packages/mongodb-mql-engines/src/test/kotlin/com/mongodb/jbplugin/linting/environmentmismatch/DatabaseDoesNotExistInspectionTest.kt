package com.mongodb.jbplugin.linting.environmentmismatch

import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases.Database
import com.mongodb.jbplugin.linting.Inspection.DatabaseDoesNotExist
import com.mongodb.jbplugin.linting.QueryInspectionTest
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

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

        `when`(readModelProvider.slice(any(), any<ListDatabases.Slice>())).thenReturn(
            ListDatabases(emptyList())
        )

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

        `when`(readModelProvider.slice(any(), any<ListDatabases.Slice>())).thenReturn(
            ListDatabases(listOf(Database("database")))
        )

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
