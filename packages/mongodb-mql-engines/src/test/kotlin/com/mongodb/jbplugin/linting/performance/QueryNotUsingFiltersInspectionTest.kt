package com.mongodb.jbplugin.linting.performance

import com.mongodb.jbplugin.linting.Inspection.NotUsingFilters
import com.mongodb.jbplugin.linting.QueryInspectionTest
import com.mongodb.jbplugin.mql.components.HasFilter
import org.junit.Test

class QueryNotUsingFiltersInspectionTest : QueryInspectionTest<NotUsingFilters> {
    @Test
    fun `warns when query does not any filters`() = runInspectionTest {
        val query = query.with(HasFilter<Unit>(emptyList()))
        val inspection = QueryNotUsingFiltersInspection()
        inspection.run(query, holder, Unit)
        onInsight(0).assertInsightDescriptionIs("insight.not-using-filters")
    }

    @Test
    fun `does not warn when query has filters`() = runInspectionTest {
        val query = query.with(commonFilter)
        val inspection = QueryNotUsingFiltersInspection()
        inspection.run(query, holder, Unit)
        assertNoInsights()
    }
}
