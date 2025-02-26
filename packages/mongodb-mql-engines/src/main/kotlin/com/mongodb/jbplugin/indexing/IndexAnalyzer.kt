package com.mongodb.jbplugin.indexing

import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.SiblingQueriesFinder
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import com.mongodb.jbplugin.mql.components.QueryRole
import com.mongodb.jbplugin.mql.parser.*

/**
 * The index analyzer is responsible for processing a query and return an index
 * that can cover the query correctly for efficiency.
 *
 * It can return different types of indexes depending on the query, but it will
 * suggest only one index.
 *
 * Right now it supports only MongoDB ordinary indexes, but it's open to suggest
 * also Search indexes in the future.
 **/
/**
 * The IndexAnalyzer service itself. It's stateless and can be used directly.
 */
object IndexAnalyzer {
    /**
     * Analyses a query and return a suggested index. If no index can be inferred, it will
     * return NoIndex.
     *
     * @see SuggestedIndex
     *
     * @param query
     * @return
     */
    suspend fun <S> analyze(
        query: Node<S>,
        siblingQueriesFinder: SiblingQueriesFinder<S>,
        options: CollectionIndexConsolidationOptions
    ): SuggestedIndex<S> {
        val baseIndex = guessIndexForQuery(query)
        val siblingQueries = siblingQueriesFinder.allSiblingsOf(query)
        val otherIndexes = siblingQueries.map { guessIndexForQuery(it) }

        return CollectionIndexConsolidation.apply(baseIndex, otherIndexes, options)
    }

    private suspend fun <S> guessIndexForQuery(query: Node<S>): SuggestedIndex<S> {
        val collectionRef =
            query.component<HasCollectionReference<S>>() ?: return SuggestedIndex.NoIndex.cast()

        val schema = (collectionRef.reference as? HasCollectionReference.Known<S>)?.schema

        val allFieldUsages = IndexQueryFieldUsage.allFieldReferences(query, schema)
            .groupBy(IndexQueryFieldUsage<S>::fieldName)
            .mapValues { (_, usages) -> usages.sortedBy { it.role.ordinal } }

        val contextInferredFieldUsages = allFieldUsages.mapValues { (_, usages) ->
            val leastSpecificUsage = usages
                .filter { it.role == QueryRole.EQUALITY || it.role == QueryRole.RANGE }
                .reduceOrNull(IndexQueryFieldUsage<S>::leastSpecificUsageOf)

            // There might be more than one sort direction specified in the code
            // so we take only the last defined direction.
            val lastUsedSort = usages
                .lastOrNull { it.role == QueryRole.SORT }

            usages.first().copy(
                value = leastSpecificUsage?.value ?: lastUsedSort?.value,
                valueType = leastSpecificUsage?.valueType ?: lastUsedSort?.valueType ?: BsonAny,
                selectivity = leastSpecificUsage?.selectivity,
                sortDirection = lastUsedSort?.sortDirection ?: SortDirection.Ascending
            )
        }

        val queryRefForFilterExpression = contextInferredFieldUsages.values.mapNotNull {
            it.reference
        }

        val partialFilterExpression = if (queryRefForFilterExpression.isEmpty()) {
            null
        } else if (queryRefForFilterExpression.size == 1) {
            queryRefForFilterExpression.first()
        } else {
            Node(
                Unit,
                listOf(
                    Named(Name.AND),
                    HasFilter(queryRefForFilterExpression)
                )
            )
        }

        val indexFields = contextInferredFieldUsages.values
            .sortedWith(IndexQueryFieldUsage.byRoleSelectivityAndCardinality())
            .map {
                SuggestedIndex.MongoDbIndexField(
                    source = it.source,
                    fieldName = it.fieldName,
                    direction = it.sortDirection,
                    reason = reasonOfSuggestion(it),
                )
            }
            .toList()

        return SuggestedIndex.MongoDbIndex(
            collectionRef,
            indexFields,
            listOf(query),
            partialFilterExpression as Node<S>?
        )
    }

    private fun <S> reasonOfSuggestion(it: IndexQueryFieldUsage<S>): IndexSuggestionFieldReason {
        return when (it.role) {
            QueryRole.EQUALITY -> IndexSuggestionFieldReason.RoleEquality
            QueryRole.SORT -> IndexSuggestionFieldReason.RoleSort
            QueryRole.RANGE -> IndexSuggestionFieldReason.RoleRange
            else -> IndexSuggestionFieldReason.RoleEquality
        }
    }

    sealed interface SuggestedIndex<S> {
        data object NoIndex : SuggestedIndex<Any> {
            fun <S> cast(): SuggestedIndex<S> = this as SuggestedIndex<S>
        }

        data class MongoDbIndexField<S>(
            val fieldName: String,
            val source: S,
            val direction: SortDirection,
            val reason: IndexSuggestionFieldReason
        )

        data class MongoDbIndex<S>(
            val collectionReference: HasCollectionReference<S>,
            val fields: List<MongoDbIndexField<S>>,
            val coveredQueries: List<Node<S>>,
            val partialFilterExpression: Node<S>?
        ) : SuggestedIndex<S>
    }

    sealed interface IndexSuggestionFieldReason {
        data object RoleEquality : IndexSuggestionFieldReason
        data object RoleSort : IndexSuggestionFieldReason
        data object RoleRange : IndexSuggestionFieldReason
    }

    sealed interface SortDirection {
        data object Ascending : SortDirection
        data object Descending : SortDirection
    }
}
