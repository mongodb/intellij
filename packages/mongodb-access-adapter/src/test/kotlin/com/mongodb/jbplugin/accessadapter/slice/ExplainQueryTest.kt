package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.QueryResult
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasExplain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class ExplainQueryTest {
    @Test
    fun `it is able to run an explain plan given a query and returns a collscan if no index available`() = runTest {
        val driver = mock<MongoDbDriver>()
        val namespace = Namespace("myDb", "myCollection")

        whenever(driver.runQuery<Map<String, Any>, Unit>(any(), any(), any())).thenReturn(
            QueryResult.Run(
                mapOf(
                    "queryPlanner" to mapOf(
                        "winningPlan" to mapOf(
                            "stage" to "COLLSCAN"
                        )
                    )
                )
            )
        )

        val query = Node(
            Unit,
            listOf(
                HasCollectionReference(HasCollectionReference.Known(Unit, Unit, namespace)),
                HasExplain(HasExplain.ExplainPlanType.SAFE),
            )
        )

        val explainPlanResult = ExplainQuery.Slice(query, QueryContext(emptyMap(), false, true))
            .queryUsingDriver(driver)

        assertEquals(ExplainQuery(ExplainPlan.CollectionScan), explainPlanResult)
    }

    @Test
    fun `it is able to run an explain plan given a query and returns an ixscan`() = runTest {
        val driver = mock<MongoDbDriver>()
        val namespace = Namespace("myDb", "myCollection")

        whenever(driver.runQuery<Map<String, Any>, Unit>(any(), any(), any())).thenReturn(
            QueryResult.Run(
                mapOf(
                    "queryPlanner" to mapOf(
                        "winningPlan" to mapOf(
                            "stage" to "FETCH",
                            "inputStage" to mapOf(
                                "stage" to "IXSCAN"
                            )
                        )
                    )
                )
            )
        )

        val query = Node(
            Unit,
            listOf(
                HasCollectionReference(HasCollectionReference.Known(Unit, Unit, namespace)),
                HasExplain(HasExplain.ExplainPlanType.SAFE),
            )
        )

        val explainPlanResult = ExplainQuery.Slice(query, QueryContext(emptyMap(), false, true))
            .queryUsingDriver(driver)

        assertEquals(ExplainQuery(ExplainPlan.IndexScan), explainPlanResult)
    }

    @Test
    fun `it is able to run an explain plan given a query and returns an ineffective index usage if filtering in memory`() = runTest {
        val driver = mock<MongoDbDriver>()
        val namespace = Namespace("myDb", "myCollection")

        whenever(driver.runQuery<Map<String, Any>, Unit>(any(), any(), any())).thenReturn(
            QueryResult.Run(
                mapOf(
                    "queryPlanner" to mapOf(
                        "winningPlan" to mapOf(
                            "stage" to "FILTER",
                            "inputStage" to mapOf(
                                "stage" to "IXSCAN"
                            )
                        )
                    )
                )
            )
        )

        val query = Node(
            Unit,
            listOf(
                HasCollectionReference(HasCollectionReference.Known(Unit, Unit, namespace)),
                HasExplain(HasExplain.ExplainPlanType.SAFE),
            )
        )

        val explainPlanResult = ExplainQuery.Slice(query, QueryContext(emptyMap(), false, true))
            .queryUsingDriver(driver)

        assertEquals(ExplainQuery(ExplainPlan.IneffectiveIndexUsage), explainPlanResult)
    }

    @Test
    fun `it is able to run an explain plan and checks for effective index usage with the executionStats warning when the returned vs fetched ratio is greater than 50`() = runTest {
        val driver = mock<MongoDbDriver>()
        val namespace = Namespace("myDb", "myCollection")

        whenever(driver.runQuery<Map<String, Any>, Unit>(any(), any(), any())).thenReturn(
            QueryResult.Run(
                mapOf(
                    "executionStats" to mapOf(
                        "nReturned" to 1,
                        "totalDocsExamined" to 50
                    ),
                    "queryPlanner" to mapOf(
                        "winningPlan" to mapOf(
                            "stage" to "FETCH",
                            "inputStage" to mapOf(
                                "stage" to "IXSCAN"
                            )
                        )
                    )
                )
            )
        )

        val query = Node(
            Unit,
            listOf(
                HasCollectionReference(HasCollectionReference.Known(Unit, Unit, namespace)),
                HasExplain(HasExplain.ExplainPlanType.SAFE),
            )
        )

        val explainPlanResult = ExplainQuery.Slice(query, QueryContext(emptyMap(), false, true))
            .queryUsingDriver(driver)

        assertEquals(ExplainQuery(ExplainPlan.IneffectiveIndexUsage), explainPlanResult)
    }

    @Test
    fun `it is able to run an explain plan and checks for effective index usage with the executionStats`() = runTest {
        val driver = mock<MongoDbDriver>()
        val namespace = Namespace("myDb", "myCollection")

        whenever(driver.runQuery<Map<String, Any>, Unit>(any(), any(), any())).thenReturn(
            QueryResult.Run(
                mapOf(
                    "executionStats" to mapOf(
                        "nReturned" to 1,
                        "totalDocsExamined" to 15
                    ),
                    "queryPlanner" to mapOf(
                        "winningPlan" to mapOf(
                            "stage" to "FETCH",
                            "inputStage" to mapOf(
                                "stage" to "IXSCAN"
                            )
                        )
                    )
                )
            )
        )

        val query = Node(
            Unit,
            listOf(
                HasCollectionReference(HasCollectionReference.Known(Unit, Unit, namespace)),
                HasExplain(HasExplain.ExplainPlanType.SAFE),
            )
        )

        val explainPlanResult = ExplainQuery.Slice(query, QueryContext(emptyMap(), false, true))
            .queryUsingDriver(driver)

        assertEquals(ExplainQuery(ExplainPlan.IndexScan), explainPlanResult)
    }

    @Test
    fun `it is able to run an explain plan given a query and returns an ineffective index usage for queries sorting in memory`() = runTest {
        val driver = mock<MongoDbDriver>()
        val namespace = Namespace("myDb", "myCollection")

        whenever(driver.runQuery<Map<String, Any>, Unit>(any(), any(), any())).thenReturn(
            QueryResult.Run(
                mapOf(
                    "queryPlanner" to mapOf(
                        "winningPlan" to mapOf(
                            "stage" to "SORT",
                            "inputStage" to mapOf(
                                "stage" to "IXSCAN"
                            )
                        )
                    )
                )
            )
        )

        val query = Node(
            Unit,
            listOf(
                HasCollectionReference(HasCollectionReference.Known(Unit, Unit, namespace)),
                HasExplain(HasExplain.ExplainPlanType.SAFE),
            )
        )

        val explainPlanResult = ExplainQuery.Slice(query, QueryContext(emptyMap(), false, true))
            .queryUsingDriver(driver)

        assertEquals(ExplainQuery(ExplainPlan.IneffectiveIndexUsage), explainPlanResult)
    }

    @Test
    fun `it is able to analyse nested winning plan reported by the query planner`() = runTest {
        val driver = mock<MongoDbDriver>()
        val namespace = Namespace("myDb", "myCollection")

        whenever(driver.runQuery<Map<String, Any>, Unit>(any(), any(), any())).thenReturn(
            QueryResult.Run(
                mapOf(
                    "queryPlanner" to mapOf(
                        "winningPlan" to mapOf(
                            "queryPlan" to mapOf(
                                "stage" to "FILTER",
                                "inputStage" to mapOf(
                                    "stage" to "IXSCAN"
                                )
                            ),
                            "slotBasedPlan" to mapOf(
                                "slots" to "",
                                "stages" to "",
                            )
                        )
                    )
                )
            )
        )

        val query = Node(
            Unit,
            listOf(
                HasCollectionReference(HasCollectionReference.Known(Unit, Unit, namespace)),
                HasExplain(HasExplain.ExplainPlanType.SAFE),
            )
        )

        val explainPlanResult = ExplainQuery.Slice(query, QueryContext(emptyMap(), false, true))
            .queryUsingDriver(driver)

        assertEquals(ExplainQuery(ExplainPlan.IneffectiveIndexUsage), explainPlanResult)
    }
}
