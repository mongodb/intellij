package com.mongodb.jbplugin.indexing

import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.SiblingQueriesFinder
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.QueryRole
import com.mongodb.jbplugin.mql.parser.*
import com.mongodb.jbplugin.mql.parser.components.aggregationStages
import com.mongodb.jbplugin.mql.parser.components.allNodesWithSchemaFieldReferences
import com.mongodb.jbplugin.mql.parser.components.extractOperation
import com.mongodb.jbplugin.mql.parser.components.extractValueReferencesRelevantForIndexing
import com.mongodb.jbplugin.mql.parser.components.fieldReference
import com.mongodb.jbplugin.mql.parser.components.hasName

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
        val otherIndexes = siblingQueriesFinder.allSiblingsOf(query).map { guessIndexForQuery(it) }

        return CollectionIndexConsolidation.apply(baseIndex, otherIndexes, options)
    }

    private suspend fun <S> guessIndexForQuery(query: Node<S>): SuggestedIndex<S> {
        val collectionRef =
            query.component<HasCollectionReference<S>>() ?: return SuggestedIndex.NoIndex.cast()

        val allFieldUsages = query.allFieldReferences().groupBy(QueryFieldUsage<S>::fieldName)
            .mapValues { (_, usages) -> usages.sortedBy { it.role.ordinal } }

        val contextInferredFieldUsages = allFieldUsages.mapValues { (_, usages) ->
            usages.first().copy(value = usages.reduce(QueryFieldUsage<S>::leastSpecificUsageOf))
        }

        val indexFields = contextInferredFieldUsages.values
            .sortedWith(QueryFieldUsage.byRoleAndCardinality())
            .map {
                SuggestedIndex.MongoDbIndexField(it.fieldName, it.source, reasonOfSuggestion(it))
            }
            .toList()

        return SuggestedIndex.MongoDbIndex(collectionRef, indexFields)
    }

    private suspend fun <S> Node<S>.allFieldReferences(): List<QueryFieldUsage<S>> {
        val nodeQueryUsage = requireNonNull<Node<S>, NoConditionFulfilled>(NoConditionFulfilled)
            .zip(extractOperation<S>())
            .zip(extractValueReferencesRelevantForIndexing<S>())
            .zip(fieldReference<HasFieldReference.FromSchema<S>, S>())
            .map { context ->
                val named = context.first.first.second
                val valueRef = context.first.second
                val fieldRef = context.second

                QueryFieldUsage(
                    fieldRef.source,
                    fieldRef.fieldName,
                    valueRef.type,
                    valueRef.value,
                    named.queryRole(valueRef.type)
                )
            }.anyError()

        val extractAllFieldReferencesWithValues = allNodesWithSchemaFieldReferences<S>()
            .mapMany(nodeQueryUsage)

        val extractFromFirstMatchStage = aggregationStages<S>()
            .nth(0)
            .matches(hasName(Name.MATCH))
            .flatMap(extractAllFieldReferencesWithValues)
            .anyError()

        val extractFromFiltersWhenNoAggregation = requireNonNull<Node<S>, Any>(Unit)
            .matches(aggregationStages<S>().filter { it.isEmpty() }.matches())
            .flatMap(extractAllFieldReferencesWithValues)
            .anyError()

        val findIndexableFieldReferences = first(
            extractFromFirstMatchStage,
            extractFromFiltersWhenNoAggregation,
        )

        return findIndexableFieldReferences
            .parse(this)
            .orElse { emptyList() }
    }

    private fun <S> reasonOfSuggestion(it: QueryFieldUsage<S>): IndexSuggestionFieldReason {
        return when (it.role) {
            QueryRole.EQUALITY -> IndexSuggestionFieldReason.RoleEquality
            QueryRole.SORT -> IndexSuggestionFieldReason.RoleSort
            QueryRole.RANGE -> IndexSuggestionFieldReason.RoleRange
            else -> IndexSuggestionFieldReason.RoleEquality
        }
    }

    private data class QueryFieldUsage<S>(
        val source: S,
        val fieldName: String,
        val type: BsonType,
        val value: Any?,
        val role: QueryRole
    ) {
        companion object {
            fun <S> byRoleAndCardinality() =
                compareBy<QueryFieldUsage<S>>({ it.role.ordinal }, { it.type.cardinality })
        }

        fun leastSpecificUsageOf(other: QueryFieldUsage<S>): QueryFieldUsage<S> {
            return if (other.value == null && this.value == null) {
                if (this.type.cardinality > other.type.cardinality) {
                    this
                } else {
                    other
                }
            } else if (other.value == null) {
                other
            } else {
                this
            }
        }
    }

    sealed interface SuggestedIndex<S> {
        data object NoIndex : SuggestedIndex<Any> {
            fun <S> cast(): SuggestedIndex<S> = this as SuggestedIndex<S>
        }

        data class MongoDbIndexField<S>(
            val fieldName: String,
            val source: S,
            val reason: IndexSuggestionFieldReason
        )

        data class MongoDbIndex<S>(
            val collectionReference: HasCollectionReference<S>,
            val fields: List<MongoDbIndexField<S>>
        ) : SuggestedIndex<S>
    }

    sealed interface IndexSuggestionFieldReason {
        data object RoleEquality : IndexSuggestionFieldReason
        data object RoleSort : IndexSuggestionFieldReason
        data object RoleRange : IndexSuggestionFieldReason
    }
}
