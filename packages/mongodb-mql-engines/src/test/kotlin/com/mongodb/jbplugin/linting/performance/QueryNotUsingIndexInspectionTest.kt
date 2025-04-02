package com.mongodb.jbplugin.linting.performance

import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan
import com.mongodb.jbplugin.accessadapter.slice.ExplainQuery
import com.mongodb.jbplugin.linting.Inspection.NotUsingIndex
import com.mongodb.jbplugin.linting.InspectionTestContext
import com.mongodb.jbplugin.linting.QueryInspectionTest
import com.mongodb.jbplugin.mql.components.HasExplain.ExplainPlanType.SAFE
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

class QueryNotUsingIndexInspectionTest : QueryInspectionTest<NotUsingIndex> {
    @Test
    fun `warns query plans using a collscan`() = runInspectionTest {
        whenExplainPlanIs(ExplainPlan.CollectionScan)

        val inspection = QueryNotUsingIndexInspection<Unit>()
        inspection.run(query, holder, QueryNotUsingIndexInspectionSettings(Unit, readModelProvider, SAFE))

        onInsight(0).assertInsightDescriptionIs("insight.not-using-index")
    }

    @Test
    fun `does not warn on query plans not using an index effectively`() = runInspectionTest {
        whenExplainPlanIs(ExplainPlan.IneffectiveIndexUsage(""))

        val inspection = QueryNotUsingIndexInspection<Unit>()
        inspection.run(query, holder, QueryNotUsingIndexInspectionSettings(Unit, readModelProvider, SAFE))

        assertNoInsights()
    }

    @Test
    fun `does not warn on query plans not using an index scan`() = runInspectionTest {
        whenExplainPlanIs(ExplainPlan.IndexScan(""))

        val inspection = QueryNotUsingIndexInspection<Unit>()
        inspection.run(query, holder, QueryNotUsingIndexInspectionSettings(Unit, readModelProvider, SAFE))

        assertNoInsights()
    }

    @Test
    fun `does not warn on query plans no run`() = runInspectionTest {
        whenExplainPlanIs(ExplainPlan.NotRun)

        val inspection = QueryNotUsingIndexInspection<Unit>()
        inspection.run(query, holder, QueryNotUsingIndexInspectionSettings(Unit, readModelProvider, SAFE))

        assertNoInsights()
    }

    private fun InspectionTestContext<NotUsingIndex>.whenExplainPlanIs(explainPlan: ExplainPlan) {
        `when`(readModelProvider.slice(any(), any<ExplainQuery.Slice<Unit>>()))
            .thenReturn(ExplainQuery(explainPlan))
    }
}
