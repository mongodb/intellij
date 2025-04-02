package com.mongodb.jbplugin.linting.performance

import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan
import com.mongodb.jbplugin.accessadapter.slice.ExplainQuery
import com.mongodb.jbplugin.linting.Inspection.NotUsingIndexEffectively
import com.mongodb.jbplugin.linting.InspectionTestContext
import com.mongodb.jbplugin.linting.QueryInspectionTest
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType.SAFE
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

class QueryNotUsingIndexInspectionEffectivelyTest : QueryInspectionTest<NotUsingIndexEffectively> {
    @Test
    fun `warns query plans using an index but not effectively`() = runInspectionTest {
        whenExplainPlanIs(ExplainPlan.IneffectiveIndexUsage(""))

        val inspection = QueryNotUsingIndexEffectivelyInspection<Unit>()
        inspection.run(query, holder, QueryNotUsingIndexEffectivelyInspectionSettings(Unit, readModelProvider, SAFE))

        onInsight(0).assertInsightDescriptionIs("insight.not-using-index-effectively")
    }

    @Test
    fun `does not warn on query plans  with a collscan`() = runInspectionTest {
        whenExplainPlanIs(ExplainPlan.CollectionScan)

        val inspection = QueryNotUsingIndexEffectivelyInspection<Unit>()
        inspection.run(query, holder, QueryNotUsingIndexEffectivelyInspectionSettings(Unit, readModelProvider, SAFE))

        assertNoInsights()
    }

    @Test
    fun `does not warn on query plans not using an index scan`() = runInspectionTest {
        whenExplainPlanIs(ExplainPlan.IndexScan(""))

        val inspection = QueryNotUsingIndexEffectivelyInspection<Unit>()
        inspection.run(query, holder, QueryNotUsingIndexEffectivelyInspectionSettings(Unit, readModelProvider, SAFE))

        assertNoInsights()
    }

    @Test
    fun `does not warn on query plans no run`() = runInspectionTest {
        whenExplainPlanIs(ExplainPlan.NotRun)

        val inspection = QueryNotUsingIndexEffectivelyInspection<Unit>()
        inspection.run(query, holder, QueryNotUsingIndexEffectivelyInspectionSettings(Unit, readModelProvider, SAFE))

        assertNoInsights()
    }

    private fun InspectionTestContext<NotUsingIndexEffectively>.whenExplainPlanIs(explainPlan: ExplainPlan) {
        `when`(readModelProvider.slice(any(), any<ExplainQuery.Slice<Unit>>()))
            .thenReturn(ExplainQuery(explainPlan))
    }
}
