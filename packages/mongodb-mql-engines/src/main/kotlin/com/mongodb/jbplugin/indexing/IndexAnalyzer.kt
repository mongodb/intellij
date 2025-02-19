package com.mongodb.jbplugin.indexing

import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.CollectionSchema
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

        val schema = (collectionRef.reference as? HasCollectionReference.Known<S>)?.schema

        val allFieldUsages = query.allFieldReferences(schema).groupBy(QueryFieldUsage<S>::fieldName)
            .mapValues { (_, usages) -> usages.sortedBy { it.role.ordinal } }

        val contextInferredFieldUsages = allFieldUsages.mapValues { (_, usages) ->
            val leastSpecificUsage = usages
                .filter { it.role == QueryRole.EQUALITY || it.role == QueryRole.RANGE }
                .reduceOrNull(QueryFieldUsage<S>::leastSpecificUsageOf)

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

        val indexFields = contextInferredFieldUsages.values
            .sortedWith(QueryFieldUsage.byRoleSelectivityAndCardinality())
            .map {
                SuggestedIndex.MongoDbIndexField(
                    source = it.source,
                    fieldName = it.fieldName,
                    direction = it.sortDirection,
                    reason = reasonOfSuggestion(it),
                )
            }
            .toList()

        return SuggestedIndex.MongoDbIndex(collectionRef, indexFields, listOf(query), null)
    }

    private suspend fun <S> Node<S>.allFieldReferences(
        collectionSchema: CollectionSchema?
    ): List<QueryFieldUsage<S>> {
        val nodeQueryUsage = requireNonNull<Node<S>, NoConditionFulfilled>(NoConditionFulfilled)
            .zip(extractOperation<S>())
            .zip(extractValueReferencesRelevantForIndexing<S>())
            .zip(fieldReference<HasFieldReference.FromSchema<S>, S>())
            .map { context ->
                val named = context.first.first.second
                val valueRef = context.first.second
                val fieldRef = context.second
                val role = named.queryRole(valueRef.type)
                val fieldType = collectionSchema?.typeOf(fieldRef.fieldName) ?: BsonAny
                val selectivity = if (role == QueryRole.SORT) {
                    // In case of sort the selectivity is always null because there is no value to
                    // calculate the selectivity from.
                    null
                } else {
                    collectionSchema?.dataDistribution?.getSelectivityForPath(
                        fieldRef.fieldName,
                        valueRef.value,
                    )
                }

                QueryFieldUsage(
                    source = fieldRef.source,
                    fieldName = fieldRef.fieldName,
                    fieldType = fieldType,
                    value = valueRef.value,
                    valueType = valueRef.type,
                    role = role,
                    selectivity = selectivity,
                    sortDirection = if (valueRef.value == -1) {
                        SortDirection.Descending
                    } else {
                        SortDirection.Ascending
                    }
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
        val fieldType: BsonType,
        val value: Any?,
        val valueType: BsonType,
        val role: QueryRole,
        val selectivity: Double?,
        val sortDirection: SortDirection,
    ) {
        companion object {
            fun <S> byRoleSelectivityAndCardinality() = Comparator<QueryFieldUsage<S>> { a, b ->
                val roleComparison = a.role.ordinal.compareTo(b.role.ordinal)
                if (roleComparison != 0) return@Comparator roleComparison

                if (a.selectivity != null && b.selectivity != null) {
                    val selectivityComparison = b.selectivity.compareTo(a.selectivity)
                    if (selectivityComparison != 0) return@Comparator selectivityComparison
                }

                return@Comparator when {
                    // In case of query role == Sort we compare by field types because value types
                    // are ir-relevant as they are always inferred as 1 or -1.
                    a.role == QueryRole.SORT && b.role == QueryRole.SORT ->
                        a.fieldType.cardinality.compareTo(b.fieldType.cardinality)
                    else ->
                        a.valueType.cardinality.compareTo(b.valueType.cardinality)
                }
            }
        }

        /**
         * For index suggestion, we want to promote a value that is least specific.
         * This applies for cases when we have different values provided for a field (generally
         * in an OR condition) in a query. If multiple values tie on specificity then we promote
         * the one with high cardinality so we can ultimately force the field towards the end of
         * the index definition.
         */
        fun leastSpecificUsageOf(other: QueryFieldUsage<S>): QueryFieldUsage<S> {
            return if (other.value == null && this.value == null) {
                if (this.valueType.cardinality > other.valueType.cardinality) {
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
