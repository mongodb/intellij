package com.mongodb.jbplugin.indexing

import com.mongodb.jbplugin.indexing.IndexAnalyzer.SortDirection
import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.QueryRole
import com.mongodb.jbplugin.mql.parser.NoConditionFulfilled
import com.mongodb.jbplugin.mql.parser.anyError
import com.mongodb.jbplugin.mql.parser.components.aggregationStages
import com.mongodb.jbplugin.mql.parser.components.allNodesWithSchemaFieldReferences
import com.mongodb.jbplugin.mql.parser.components.extractOperation
import com.mongodb.jbplugin.mql.parser.components.extractValueReferencesRelevantForIndexing
import com.mongodb.jbplugin.mql.parser.components.fieldReference
import com.mongodb.jbplugin.mql.parser.components.hasName
import com.mongodb.jbplugin.mql.parser.filter
import com.mongodb.jbplugin.mql.parser.first
import com.mongodb.jbplugin.mql.parser.flatMap
import com.mongodb.jbplugin.mql.parser.map
import com.mongodb.jbplugin.mql.parser.mapMany
import com.mongodb.jbplugin.mql.parser.matches
import com.mongodb.jbplugin.mql.parser.nth
import com.mongodb.jbplugin.mql.parser.parse
import com.mongodb.jbplugin.mql.parser.requireNonNull
import com.mongodb.jbplugin.mql.parser.zip

internal data class IndexQueryFieldUsage<S>(
    val source: S,
    val fieldName: String,
    val fieldType: BsonType,
    val value: Any?,
    val valueType: BsonType,
    val role: QueryRole,
    val selectivity: Double?,
    val sortDirection: SortDirection,
    val reference: Node<S>?,
    val operation: Name,
) {
    companion object {
        fun <S> byRoleSelectivityAndCardinality() = Comparator<IndexQueryFieldUsage<S>> { a, b ->
            val roleComparison = a.role.ordinal.compareTo(b.role.ordinal)
            if (roleComparison != 0) return@Comparator roleComparison

            if (a.selectivity != null && b.selectivity != null) {
                val selectivityComparison = b.selectivity.compareTo(a.selectivity)
                if (selectivityComparison != 0) return@Comparator selectivityComparison
            }

            val cardinalityComparison = if (a.role == QueryRole.SORT && b.role == QueryRole.SORT) {
                a.fieldType.cardinality.compareTo(b.fieldType.cardinality)
            } else {
                a.valueType.cardinality.compareTo(b.valueType.cardinality)
            }

            if (cardinalityComparison != 0) {
                return@Comparator cardinalityComparison
            }

            return@Comparator a.fieldName.compareTo(b.fieldName)
        }

        suspend fun <S> allFieldReferences(
            query: Node<S>,
            collectionSchema: CollectionSchema?
        ): List<IndexQueryFieldUsage<S>> {
            val nodeQueryUsage = requireNonNull<Node<S>, NoConditionFulfilled>(NoConditionFulfilled)
                .zip(extractOperation<S>())
                .zip(extractValueReferencesRelevantForIndexing())
                .zip(fieldReference<HasFieldReference.FromSchema<S>, S>())
                .map { context ->
                    val reference = context.first.first.first
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

                    IndexQueryFieldUsage(
                        source = fieldRef.source,
                        fieldName = fieldRef.fieldName,
                        fieldType = fieldType,
                        value = valueRef.value,
                        valueType = valueRef.type,
                        operation = named.name,
                        role = role,
                        selectivity = selectivity,
                        sortDirection = if (valueRef.value == -1) {
                            SortDirection.Descending
                        } else {
                            SortDirection.Ascending
                        },
                        reference = reference
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
                .parse(query)
                .orElse { emptyList() }
        }
    }

    /**
     * For index suggestion, we want to promote a value that is least specific.
     * This applies for cases when we have different values provided for a field (generally
     * in an OR condition) in a query. If multiple values tie on specificity then we promote
     * the one with high cardinality so we can ultimately force the field towards the end of
     * the index definition.
     */
    fun leastSpecificUsageOf(other: IndexQueryFieldUsage<S>): IndexQueryFieldUsage<S> {
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

    fun applyValueErasureIfNecessary(other: IndexQueryFieldUsage<S>): IndexQueryFieldUsage<S> {
        if (this.value != other.value) {
            return copy(value = null, reference = null)
        }

        if (this.operation != other.operation) {
            return copy(value = null, reference = null)
        }

        return this
    }
}
