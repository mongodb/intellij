package com.mongodb.jbplugin.linting.performance

import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan
import com.mongodb.jbplugin.linting.Inspection.NotUsingIndex
import com.mongodb.jbplugin.linting.QueryInspectionTest
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType.SAFE
import org.junit.jupiter.api.Test

class QueryNotUsingIndexInspectionTest : QueryInspectionTest<NotUsingIndex> {
    @Test
    fun `warns query plans using a collscan`() = runInspectionTest {
        val namespace = Namespace("database", "collection")
        whenDatabasesAre(listOf("database"))
        whenCollectionsAre(listOf("collection"))
        whenExplainPlanIs(ExplainPlan.CollectionScan)

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, namespace)
            )
        )

        val inspection = QueryNotUsingIndexInspection<Unit>()
        inspection.run(query, holder, QueryNotUsingIndexInspectionSettings(Unit, readModelProvider, SAFE))

        onInsight(0).assertInsightDescriptionIs("insight.not-using-index")
    }

    @Test
    fun `does not warn when database itself does not exist`() = runInspectionTest {
        val namespace = Namespace("database1", "collection")
        whenDatabasesAre(listOf("database"))
        whenCollectionsAre(listOf("collection"))
        whenExplainPlanIs(ExplainPlan.CollectionScan)

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, namespace)
            )
        )

        val inspection = QueryNotUsingIndexInspection<Unit>()
        inspection.run(query, holder, QueryNotUsingIndexInspectionSettings(Unit, readModelProvider, SAFE))

        assertNoInsights()
    }

    @Test
    fun `does not warn when collection itself does not exist`() = runInspectionTest {
        val namespace = Namespace("database", "collection1")
        whenDatabasesAre(listOf("database"))
        whenCollectionsAre(listOf("collection"))
        whenExplainPlanIs(ExplainPlan.CollectionScan)

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, namespace)
            )
        )

        val inspection = QueryNotUsingIndexInspection<Unit>()
        inspection.run(query, holder, QueryNotUsingIndexInspectionSettings(Unit, readModelProvider, SAFE))

        assertNoInsights()
    }

    @Test
    fun `does not warn on query plans not using an index effectively`() = runInspectionTest {
        val namespace = Namespace("database", "collection")
        whenDatabasesAre(listOf("database"))
        whenCollectionsAre(listOf("collection"))
        whenExplainPlanIs(ExplainPlan.IneffectiveIndexUsage(""))

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, namespace)
            )
        )

        val inspection = QueryNotUsingIndexInspection<Unit>()
        inspection.run(query, holder, QueryNotUsingIndexInspectionSettings(Unit, readModelProvider, SAFE))

        assertNoInsights()
    }

    @Test
    fun `does not warn on query plans not using an index scan`() = runInspectionTest {
        val namespace = Namespace("database", "collection")
        whenDatabasesAre(listOf("database"))
        whenCollectionsAre(listOf("collection"))
        whenExplainPlanIs(ExplainPlan.IndexScan(""))

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, namespace)
            )
        )

        val inspection = QueryNotUsingIndexInspection<Unit>()
        inspection.run(query, holder, QueryNotUsingIndexInspectionSettings(Unit, readModelProvider, SAFE))

        assertNoInsights()
    }

    @Test
    fun `does not warn on query plans no run`() = runInspectionTest {
        val namespace = Namespace("database", "collection")
        whenDatabasesAre(listOf("database"))
        whenCollectionsAre(listOf("collection"))
        whenExplainPlanIs(ExplainPlan.NotRun)

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, namespace)
            )
        )

        val inspection = QueryNotUsingIndexInspection<Unit>()
        inspection.run(query, holder, QueryNotUsingIndexInspectionSettings(Unit, readModelProvider, SAFE))

        assertNoInsights()
    }
}
