/**
 * Linter that checks that the query is using a proper index.
 */

package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.ExplainPlan
import com.mongodb.jbplugin.accessadapter.slice.ExplainQuery
import com.mongodb.jbplugin.linting.IndexCheckWarning.QueryNotCoveredByIndex
import com.mongodb.jbplugin.linting.IndexCheckWarning.QueryNotUsingEffectiveIndex
import com.mongodb.jbplugin.mql.*

/**
 * Marker type for the result of the linter.
 *
 * @see QueryNotCoveredByIndex as an example implementation.
 *
 * @param S Source type of the query (for intellij it's PsiElement)
 */
sealed interface IndexCheckWarning<S> {
    /**
     * If the query is not properly covered by an index. Usually COLLSCANs.
     *
     * @param S
     * @property source
     */
    data class QueryNotCoveredByIndex<S>(
        val source: S,
    ) : IndexCheckWarning<S>

    /**
     * If the used index is not effective enough, because we are sorting in memory for example.
     *
     * @param S
     * @property source
     */
    data class QueryNotUsingEffectiveIndex<S>(
        val source: S,
    ) : IndexCheckWarning<S>
}

/**
 * Wrapper ADT type of warnings that contains the results of the linter.
 *
 * @see IndexCheckingLinter
 *
 * @param S
 * @property warnings
 */
data class IndexCheckResult<S>(
    val warnings: List<IndexCheckWarning<S>>
)

/**
 * Linter that verifies that the query is properly using indexes in the target cluster.
 */
object IndexCheckingLinter {
    fun <D, S> lintQuery(
        dataSource: D,
        readModelProvider: MongoDbReadModelProvider<D>,
        query: Node<S>,
        queryContext: QueryContext,
    ): IndexCheckResult<S> {
        val explainPlanResult = readModelProvider.slice(
            dataSource,
            ExplainQuery.Slice(query, queryContext)
        )
        return when (explainPlanResult.explainPlan) {
            is ExplainPlan.CollectionScan ->
                IndexCheckResult(
                    listOf(
                        QueryNotCoveredByIndex(query.source)
                    )
                )

            is ExplainPlan.IneffectiveIndexUsage ->
                IndexCheckResult(
                    listOf(
                        QueryNotUsingEffectiveIndex(query.source)
                    )
                )

            else ->
                IndexCheckResult(
                    emptyList()
                )
        }
    }
}
