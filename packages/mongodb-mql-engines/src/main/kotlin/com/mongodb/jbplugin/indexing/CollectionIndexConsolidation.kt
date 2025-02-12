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

        val partition = indexes
            .filterIsInstance<IndexAnalyzer.SuggestedIndex.MongoDbIndex<S>>()
            .fold(IndexPartitions<S>()) { acc, it -> acc.addIndex(it) }
            .addIndex(baseIndex)
            .partitionOfIndex(baseIndex)

        val bestIndexForPartition = partition?.maxBy { it.cardinality() }
        return bestIndexForPartition ?: IndexAnalyzer.SuggestedIndex.NoIndex.cast()
    }

    private fun <S> IndexAnalyzer.SuggestedIndex.MongoDbIndex<S>.cardinality(): Int {
        return fields.size
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

    private fun <S> IndexAnalyzer.SuggestedIndex.MongoDbIndex<S>.isPrefixOf(other: IndexAnalyzer.SuggestedIndex.MongoDbIndex<S>): Boolean {
        return fields.zip(other.fields)
            .all { (first, second) -> first == second }
    }
}
