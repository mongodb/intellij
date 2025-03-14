package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan
import com.mongodb.jbplugin.accessadapter.slice.ExplainQuery
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

class IndexCheckingLinterTest {
    @Test
    fun `warns query plans using a collscan`() {
        val readModelProvider = mock<MongoDbReadModelProvider<Unit>>()
        val query = Node(Unit, emptyList())

        `when`(readModelProvider.slice(any(), any<ExplainQuery.Slice<Unit>>())).thenReturn(
            ExplainQuery(
                ExplainPlan.CollectionScan
            )
        )

        val result =
            IndexCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                query,
                QueryContext.empty()
            )

        assertEquals(1, result.warnings.size)
        assertInstanceOf(IndexCheckWarning.QueryNotCoveredByIndex::class.java, result.warnings[0])
    }

    @Test
    fun `warns query plans using an ineffective index`() {
        val readModelProvider = mock<MongoDbReadModelProvider<Unit>>()
        val query = Node(Unit, emptyList())

        `when`(readModelProvider.slice(any(), any<ExplainQuery.Slice<Unit>>())).thenReturn(
            ExplainQuery(
                ExplainPlan.IneffectiveIndexUsage
            )
        )

        val result =
            IndexCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                query,
                QueryContext.empty()
            )

        assertEquals(1, result.warnings.size)
        assertInstanceOf(
            IndexCheckWarning.QueryNotUsingEffectiveIndex::class.java,
            result.warnings[0]
        )
    }

    @Test
    fun `does not warn on index scans`() {
        val readModelProvider = mock<MongoDbReadModelProvider<Unit>>()
        val query = Node(Unit, emptyList())

        `when`(readModelProvider.slice(any(), any<ExplainQuery.Slice<Unit>>())).thenReturn(
            ExplainQuery(
                ExplainPlan.IndexScan
            )
        )

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
