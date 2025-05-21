package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan
import com.mongodb.jbplugin.accessadapter.slice.ExplainQuery
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

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
        assertNotNull(insight, "Expected an insight at position $n but was null")
        return InspectionTestContextForInsight(query, insight!!)
    }

    fun InspectionTestContext<I>.whenDatabasesAre(databases: List<String>) = runBlocking {
        `when`(readModelProvider.slice(any(), any<ListDatabases.Slice>(), eq(null))).thenReturn(
            ListDatabases(
                databases.map { ListDatabases.Database(it) }
            )
        )
    }

    fun InspectionTestContext<I>.whenCollectionsAre(collections: List<String>) = runBlocking {
        `when`(readModelProvider.slice(any(), any<ListCollections.Slice>(), eq(null))).thenReturn(
            ListCollections(
                collections.map { ListCollections.Collection(it, "collection") }
            )
        )
    }

    fun InspectionTestContext<I>.whenCollectionSchemaIs(namespace: Namespace, schema: BsonObject) = runBlocking {
        `when`(readModelProvider.slice(any(), any<GetCollectionSchema.Slice>(), eq(null))).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    schema,
                ),
            ),
        )
    }

    fun InspectionTestContext<I>.whenExplainPlanIs(explainPlan: ExplainPlan) = runBlocking {
        `when`(readModelProvider.slice(any(), any<ExplainQuery.Slice<Unit>>(), eq(null)))
            .thenReturn(ExplainQuery(explainPlan))
    }

    fun InspectionTestContextForInsight<I>.assertInsightDescriptionIs(description: String, vararg arguments: String) {
        assertEquals(description, insight.description)
        assertEquals(arguments.toList(), insight.descriptionArguments)
    }
}
