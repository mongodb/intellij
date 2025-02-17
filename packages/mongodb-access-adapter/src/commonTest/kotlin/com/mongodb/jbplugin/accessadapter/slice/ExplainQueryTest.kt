package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.QueryResult
import com.mongodb.jbplugin.accessadapter.StubMongoDbDriver
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasExplain
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ExplainQueryTest {
    @Test
    fun it_is_able_to_run_an_explain_plan_given_a_query_and_returns_a_collscan_if_no_index_available() = runTest {
        val driver = StubMongoDbDriver(
            responses = mapOf(
                Map::class to {
                    QueryResult.Run(
                        mapOf(
                            "queryPlanner" to mapOf(
                                "winningPlan" to mapOf(
                                    "stage" to "COLLSCAN"
                                )
                            )
                        )
                    )
                }
            )
        )

        val namespace = Namespace("myDb", "myCollection")

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
    fun it_is_able_to_run_an_explain_plan_given_a_query_and_returns_an_ixscan() = runTest {
        val driver = StubMongoDbDriver(
            responses = mapOf(
                Map::class to {
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
                }
            )
        )

        val namespace = Namespace("myDb", "myCollection")

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
    fun it_is_able_to_run_an_explain_plan_given_a_query_and_returns_an_ineffective_index_usage_if_filtering_in_memory() = runTest {
        val driver = StubMongoDbDriver(
            responses = mapOf(
                Map::class to {
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
                }
            )
        )

        val namespace = Namespace("myDb", "myCollection")

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
    fun it_is_able_to_run_an_explain_plan_and_checks_for_effective_index_usage_with_the_executionStats_warning_when_the_returned_vs_fetched_ratio_is_greater_than_50() = runTest {
        val driver = StubMongoDbDriver(
            responses = mapOf(
                Map::class to {
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
                }
            )
        )

        val namespace = Namespace("myDb", "myCollection")

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
    fun it_is_able_to_run_an_explain_plan_and_checks_for_effective_index_usage_with_the_executionStats() = runTest {
        val driver = StubMongoDbDriver(
            responses = mapOf(
                Map::class to {
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
                }
            )
        )

        val namespace = Namespace("myDb", "myCollection")

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
    fun it_is_able_to_run_an_explain_plan_given_a_query_and_returns_an_ineffective_index_usage_for_queries_sorting_in_memory() = runTest {
        val driver = StubMongoDbDriver(
            responses = mapOf(
                Map::class to {
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
                }
            )
        )

        val namespace = Namespace("myDb", "myCollection")
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
