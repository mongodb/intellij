package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.mql.Node
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.Mockito.mock

class InMemoryQueryInsightsHolder<S, I : Inspection> : QueryInsightsHolder<S, I> {
    val results = mutableListOf<QueryInsight<S, I>>()

    override suspend fun register(insight: QueryInsight<S, I>) {
        results.add(insight)
    }
}

class InspectionTestContext<I : Inspection>(
    val query: Node<Unit>,
    val readModelProvider: MongoDbReadModelProvider<Unit>,
    val holder: InMemoryQueryInsightsHolder<Unit, I>,
)

class InspectionTestContextForInsight<I : Inspection>(
    val query: Node<Unit>,
    val insight: QueryInsight<Unit, I>
)

interface QueryInspectionTest<I : Inspection> {
    fun runInspectionTest(body: suspend InspectionTestContext<I>.() -> Unit) {
        runTest {
            val query = Node(Unit, emptyList())
            val readModelProvider = mock<MongoDbReadModelProvider<Unit>>()
            val holder = InMemoryQueryInsightsHolder<Unit, I>()
            val context = InspectionTestContext(query, readModelProvider, holder)
            body(context)
        }
    }

    fun InspectionTestContext<I>.assertNoInsights() {
        assertTrue(holder.results.isEmpty(), "Expected no insights, but ${holder.results.size} found.")
    }

    fun InspectionTestContext<I>.onInsight(n: Int): InspectionTestContextForInsight<I> {
        val insight = holder.results.getOrNull(n)
        assertNotNull(insight)
        return InspectionTestContextForInsight(query, insight!!)
    }

    fun InspectionTestContextForInsight<I>.assertInsightDescriptionIs(description: String, vararg arguments: String) {
        assertEquals(description, insight.description)
        assertEquals(arguments.toList(), insight.descriptionArguments)
    }
}
