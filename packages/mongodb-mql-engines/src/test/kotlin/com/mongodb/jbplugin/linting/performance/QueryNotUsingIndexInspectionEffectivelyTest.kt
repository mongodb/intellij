package com.mongodb.jbplugin.linting.performance

import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan
import com.mongodb.jbplugin.linting.Inspection.NotUsingIndexEffectively
import com.mongodb.jbplugin.linting.QueryInspectionTest
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType.SAFE
import com.mongodb.jbplugin.mql.components.HasFilter
import org.junit.jupiter.api.Test

class QueryNotUsingIndexInspectionEffectivelyTest : QueryInspectionTest<NotUsingIndexEffectively> {
    @Test
    fun `warns query plans using an index but not effectively`() = runInspectionTest {
        val namespace = Namespace("database", "collection")
        whenDatabasesAre(listOf("database"))
        whenCollectionsAre(listOf("collection"))
        whenExplainPlanIs(ExplainPlan.IneffectiveIndexUsage(""))

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, namespace)
            )
        ).with(commonFilter)

        val inspection = QueryNotUsingIndexEffectivelyInspection<Unit>()
        inspection.run(query, holder, QueryNotUsingIndexEffectivelyInspectionSettings(Unit, readModelProvider, SAFE))

        onInsight(0).assertInsightDescriptionIs("insight.not-using-index-effectively")
    }

    @Test
    fun `does not warn when database does not exist`() = runInspectionTest {
        val namespace = Namespace("database1", "collection")
        whenDatabasesAre(listOf("database"))
        whenCollectionsAre(listOf("collection"))
        whenExplainPlanIs(ExplainPlan.IneffectiveIndexUsage(""))

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, namespace)
            )
        ).with(commonFilter)

        val inspection = QueryNotUsingIndexEffectivelyInspection<Unit>()
        inspection.run(query, holder, QueryNotUsingIndexEffectivelyInspectionSettings(Unit, readModelProvider, SAFE))

        assertNoInsights()
    }

    @Test
    fun `does not warn when collection does not exist`() = runInspectionTest {
        val namespace = Namespace("database", "collection1")
        whenDatabasesAre(listOf("database"))
        whenCollectionsAre(listOf("collection"))
        whenExplainPlanIs(ExplainPlan.IneffectiveIndexUsage(""))

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, namespace)
            )
        ).with(commonFilter)

        val inspection = QueryNotUsingIndexEffectivelyInspection<Unit>()
        inspection.run(query, holder, QueryNotUsingIndexEffectivelyInspectionSettings(Unit, readModelProvider, SAFE))

        assertNoInsights()
    }

    @Test
    fun `does not warn on query plans  with a collscan`() = runInspectionTest {
        val namespace = Namespace("database", "collection1")
        whenDatabasesAre(listOf("database"))
        whenCollectionsAre(listOf("collection"))
        whenExplainPlanIs(ExplainPlan.CollectionScan)

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, namespace)
            )
        ).with(commonFilter)

        val inspection = QueryNotUsingIndexEffectivelyInspection<Unit>()
        inspection.run(query, holder, QueryNotUsingIndexEffectivelyInspectionSettings(Unit, readModelProvider, SAFE))

        assertNoInsights()
    }

    @Test
    fun `does not warn on query plans not using an index scan`() = runInspectionTest {
        val namespace = Namespace("database", "collection1")
        whenDatabasesAre(listOf("database"))
        whenCollectionsAre(listOf("collection"))
        whenExplainPlanIs(ExplainPlan.IndexScan(""))

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, namespace)
            )
        ).with(commonFilter)

        val inspection = QueryNotUsingIndexEffectivelyInspection<Unit>()
        inspection.run(query, holder, QueryNotUsingIndexEffectivelyInspectionSettings(Unit, readModelProvider, SAFE))

        assertNoInsights()
    }

    @Test
    fun `does not warn on query plans no run`() = runInspectionTest {
        val namespace = Namespace("database", "collection1")
        whenDatabasesAre(listOf("database"))
        whenCollectionsAre(listOf("collection"))
        whenExplainPlanIs(ExplainPlan.NotRun)

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, namespace)
            )
        ).with(commonFilter)

        val inspection = QueryNotUsingIndexEffectivelyInspection<Unit>()
        inspection.run(query, holder, QueryNotUsingIndexEffectivelyInspectionSettings(Unit, readModelProvider, SAFE))

        assertNoInsights()
    }

    @Test
    fun `does not warn when query has no filters`() = runInspectionTest {
        val namespace = Namespace("database", "collection")
        whenDatabasesAre(listOf("database"))
        whenCollectionsAre(listOf("collection"))
        whenExplainPlanIs(ExplainPlan.IneffectiveIndexUsage(""))

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, namespace)
            )
        ).with(HasFilter<Unit>(emptyList()))

        val inspection = QueryNotUsingIndexEffectivelyInspection<Unit>()
        inspection.run(query, holder, QueryNotUsingIndexEffectivelyInspectionSettings(Unit, readModelProvider, SAFE))

        assertNoInsights()
    }
}
