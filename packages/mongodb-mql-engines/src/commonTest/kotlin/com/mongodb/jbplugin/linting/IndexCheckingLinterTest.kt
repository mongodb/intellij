package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.StubReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan
import com.mongodb.jbplugin.accessadapter.slice.ExplainQuery
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndexCheckingLinterTest {
    @Test
    fun warns_query_plans_using_a_collscan() {
        val readModelProvider = StubReadModelProvider<Unit>(default = { ExplainQuery(ExplainPlan.CollectionScan) })
        val query = Node(Unit, emptyList())

        val result =
            IndexCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                query,
                QueryContext.empty()
            )

        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings[0] is IndexCheckWarning.QueryNotCoveredByIndex)
    }

    @Test
    fun warns_query_plans_using_an_ineffective_index() {
        val readModelProvider = StubReadModelProvider<Unit>(default = { ExplainQuery(ExplainPlan.IneffectiveIndexUsage) })
        val query = Node(Unit, emptyList())

        val result =
            IndexCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                query,
                QueryContext.empty()
            )

        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings[0] is IndexCheckWarning.QueryNotUsingEffectiveIndex)
    }

    @Test
    fun does_not_warn_on_index_scans() {
        val readModelProvider = StubReadModelProvider<Unit>(default = { ExplainQuery(ExplainPlan.IndexScan) })

        val query = Node(Unit, emptyList())

        val result =
            IndexCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                query,
                QueryContext.empty()
            )

        assertEquals(0, result.warnings.size)
    }
}
