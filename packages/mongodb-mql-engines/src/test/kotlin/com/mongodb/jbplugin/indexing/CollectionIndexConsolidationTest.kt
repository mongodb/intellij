package com.mongodb.jbplugin.indexing

import com.mongodb.jbplugin.accessadapter.toNs
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CollectionIndexConsolidationTest {
    @Test
    fun `should return itself if no other index candidates`() {
        val index = CollectionIndexConsolidation.apply(
            baseIndex = indexOf("f1" to 1),
            indexes = emptyList(),
            options = emptyOptions()
        )

        assertEquals(indexOf("f1" to 1), index)
    }

    @Test
    fun `should return the index with highest cardinality if there are multiple matches`() {
        val index = CollectionIndexConsolidation.apply(
            baseIndex = indexOf("f1" to 1),
            indexes = listOf(indexOf("f1" to 1, "f2" to 1)),
            options = emptyOptions()
        )

        assertEquals(indexOf("f1" to 1, "f2" to 1), index)
    }

    private fun indexOf(vararg fields: Pair<String, Int>): IndexAnalyzer.SuggestedIndex.MongoDbIndex<Unit> {
        return IndexAnalyzer.SuggestedIndex.MongoDbIndex(
            HasCollectionReference(
                HasCollectionReference.Known(Unit, Unit, "myDb.myColl".toNs(), null)
            ),
            fields.map {
                IndexAnalyzer.SuggestedIndex.MongoDbIndexField(
                    it.first,
                    Unit,
                    IndexAnalyzer.IndexSuggestionFieldReason.RoleEquality
                )
            }
        )
    }

    private fun emptyOptions() = CollectionIndexConsolidationOptions(10)
}
