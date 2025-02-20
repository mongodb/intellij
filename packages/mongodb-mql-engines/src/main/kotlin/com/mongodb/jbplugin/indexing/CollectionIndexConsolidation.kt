package com.mongodb.jbplugin.indexing

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

        val bestIndex = partitionOfIndex.maxBy(::numberOfFields).copy(
            coveredQueries = coveredQueries
        )

        return bestIndex
    }

    private fun <S> numberOfFields(index: IndexAnalyzer.SuggestedIndex.MongoDbIndex<S>): Int {
        return index.fields.size
    }
}

private class IndexPartitions<S> {
    private val partitions: MutableList<MutableList<IndexAnalyzer.SuggestedIndex.MongoDbIndex<S>>> =
        mutableListOf()

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
