package com.mongodb.jbplugin.indexing

import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.QueryRole
import kotlinx.coroutines.runBlocking

data class CollectionIndexConsolidationOptions(val indexesSoftLimit: Int)

object CollectionIndexConsolidation {
    fun <S> apply(
        baseIndex: IndexAnalyzer.SuggestedIndex<S>,
        indexes: List<IndexAnalyzer.SuggestedIndex<S>>,
        options: CollectionIndexConsolidationOptions
    ): IndexAnalyzer.SuggestedIndex<S> {
        if (baseIndex !is IndexAnalyzer.SuggestedIndex.MongoDbIndex) {
            return IndexAnalyzer.SuggestedIndex.NoIndex.cast()
        }

        val partitionOfIndex = indexes
            .filterIsInstance<IndexAnalyzer.SuggestedIndex.MongoDbIndex<S>>()
            .fold(IndexPartitions(), IndexPartitions<S>::addIndex)
            .addIndex(baseIndex)
            .partitionOfIndex(baseIndex) ?: return IndexAnalyzer.SuggestedIndex.NoIndex.cast()

        val coveredQueries = (
            baseIndex.coveredQueries +
                partitionOfIndex.flatMap { it.coveredQueries }
            )
            .distinctBy { it.components }

        partitionOfIndex.sortWith(
            compareByDescending<IndexAnalyzer.SuggestedIndex.MongoDbIndex<S>> {
                it.fields.size
            }.thenByDescending {
                it.coveredQueries.size
            }
        )

        val bestIndex = partitionOfIndex.first().copy(coveredQueries = coveredQueries)
        return erasePartialExpressionIfNotSafe(bestIndex, partitionOfIndex)
    }

    private fun <S> erasePartialExpressionIfNotSafe(
        index: IndexAnalyzer.SuggestedIndex.MongoDbIndex<S>,
        partition: IndexPartition<S>
    ): IndexAnalyzer.SuggestedIndex.MongoDbIndex<S> {
        val unwindFieldUsages = partition.flatMap {
            if (it.partialFilterExpression == null) {
                emptyList()
            } else {
                runBlocking {
                    IndexQueryFieldUsage.allFieldReferences(it.partialFilterExpression, collectionSchema = null)
                }
            }
        }.groupBy { it.fieldName }

        val commonPartialExpressions = unwindFieldUsages.values.asSequence()
            .filter {
                it.size == partition.size
            }.map {
                it.filter { query -> query.role != QueryRole.SORT }
            }.filter {
                it.isNotEmpty()
            }.map {
                it.reduce(IndexQueryFieldUsage<S>::applyValueErasureIfNecessary)
            }.filter { it.reference != null && it.value != null }
            .toList()

        return if (commonPartialExpressions.isEmpty()) {
            index.copy(partialFilterExpression = null)
        } else if (commonPartialExpressions.size == 1) {
            index.copy(partialFilterExpression = commonPartialExpressions[0].reference)
        } else {
            index.copy(
                partialFilterExpression = Node(
                    commonPartialExpressions[0].reference!!.source,
                    listOf(
                        HasFilter(commonPartialExpressions.mapNotNull { it.reference })
                    )
                )
            )
        }
    }
}

private typealias IndexPartition<S> = MutableList<IndexAnalyzer.SuggestedIndex.MongoDbIndex<S>>

private class IndexPartitions<S> {
    private val partitions: MutableList<IndexPartition<S>> = mutableListOf()

    fun partitionOfIndex(index: IndexAnalyzer.SuggestedIndex<S>) = partitions.find {
        it.contains(index)
    }

    fun addIndex(index: IndexAnalyzer.SuggestedIndex.MongoDbIndex<S>): IndexPartitions<S> {
        // first check if the index can be inserted into an existing partition
        val fittingPartition = partitions.find {
            it.all { it.isPrefixOf(index) || index.isPrefixOf(it) }
        }

        // we've found a partition, so add the index and we are done
        if (fittingPartition != null) {
            fittingPartition.add(index)
        } else {
            val newPartition = mutableListOf(index)
            partitions.add(newPartition)
        }

        return this
    }
}

internal fun <S> IndexAnalyzer.SuggestedIndex.MongoDbIndex<S>.isPrefixOf(other: IndexAnalyzer.SuggestedIndex.MongoDbIndex<S>): Boolean {
    if (fields.size > other.fields.size) {
        return false
    }

    return fields.zip(other.fields).all { (first, second) -> first.fieldName == second.fieldName }
}
