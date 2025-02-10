package com.mongodb.jbplugin.indexing

import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.QueryRole
import com.mongodb.jbplugin.mql.parser.*
import com.mongodb.jbplugin.mql.parser.components.aggregationStages
import com.mongodb.jbplugin.mql.parser.components.allNodesWithSchemaFieldReferences
import com.mongodb.jbplugin.mql.parser.components.extractOperation
import com.mongodb.jbplugin.mql.parser.components.extractValueReference
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
    suspend fun <S> analyze(query: Node<S>): SuggestedIndex<S> {
        val collectionRef =
            query.component<HasCollectionReference<S>>() ?: return SuggestedIndex.NoIndex.cast()

        val allFieldUsages = query.allFieldReferences().groupBy {
            it.fieldName
        }

        val promotedFieldUsages = allFieldUsages.mapValues { (_, usages) ->
            usages.sortedBy { it.role.ordinal }
        }

        val contextInferredFieldUsages = promotedFieldUsages.mapValues { (_, usages) ->
            usages.first().copy(
                value = usages.fold(usages.first().value) { acc, usage ->
                    if (usage.value == null) {
                        usage
                    } else {
                        acc
                    }
                }
            )
        }

        val indexFields = contextInferredFieldUsages.values
            .sortedWith(compareBy({ it.role.ordinal }, { it.type.cardinality }))
            .map { SuggestedIndex.MongoDbIndexField(it.fieldName, it.source) }
            .toList()

        return SuggestedIndex.MongoDbIndex(collectionRef, indexFields)
    }

    private suspend fun <S> Node<S>.allFieldReferences(): List<QueryFieldUsage<S>> {
        val nodeQueryUsage = requireNonNull<Node<S>, NoConditionFulfilled>(NoConditionFulfilled)
            .zip(extractOperation<S>())
            .zip(extractValueReference<S>())
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

    private data class QueryFieldUsage<S>(
        val source: S,
        val fieldName: String,
        val type: BsonType,
        val value: Any?,
        val role: QueryRole
    )

    /**
     * @param S
     */
    sealed interface SuggestedIndex<S> {
        data object NoIndex : SuggestedIndex<Any> {
            fun <S> cast(): SuggestedIndex<S> = this as SuggestedIndex<S>
        }

        /**
         * @param S
         * @property fieldName
         * @property source
         */
        data class MongoDbIndexField<S>(val fieldName: String, val source: S)

        /**
         * @param S
         * @property collectionReference
         * @property fields
         */
        data class MongoDbIndex<S>(
            val collectionReference: HasCollectionReference<S>,
            val fields: List<MongoDbIndexField<S>>
        ) : SuggestedIndex<S>
    }
}
