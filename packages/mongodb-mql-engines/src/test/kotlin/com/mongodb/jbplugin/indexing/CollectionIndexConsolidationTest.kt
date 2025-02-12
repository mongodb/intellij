package com.mongodb.jbplugin.indexing

import com.mongodb.jbplugin.accessadapter.toNs
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.utils.ModelAssertions.assertMongoDbIndexIs
import com.mongodb.jbplugin.utils.ModelAssertions.assertNumberOfCoveredQueriesForIndex
import com.mongodb.jbplugin.utils.ModelDsl.filterBy
import com.mongodb.jbplugin.utils.ModelDsl.findMany
import com.mongodb.jbplugin.utils.ModelDsl.indexOf
import com.mongodb.jbplugin.utils.ModelDsl.predicate
import com.mongodb.jbplugin.utils.ModelDsl.query
import com.mongodb.jbplugin.utils.ModelDsl.schema
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionIndexConsolidationTest {
    @Test
    fun `isPrefixOf should return true if they are equal`() {
        assertTrue(indexOf("f1" to 1).isPrefixOf(indexOf("f1" to 1)))
    }

    @Test
    fun `isPrefixOf should return true if the left side is a prefix to the right side`() {
        assertTrue(indexOf("f1" to 1).isPrefixOf(indexOf("f1" to 1, "f2" to 1)))
    }

    @Test
    fun `isPrefixOf should return false if the left side is bigger than the right side`() {
        assertFalse(indexOf("f1" to 1, "f2" to 1).isPrefixOf(indexOf("f1" to 1)))
    }

    @Test
    fun `isPrefixOf should return false if there is a shared prefix but additional fields`() {
        assertFalse(indexOf("f1" to 1, "f3" to 1).isPrefixOf(indexOf("f1" to 1, "f2" to 1)))
    }

    @Test
    fun `should return itself if no other index candidates`() {
        val index = CollectionIndexConsolidation.apply(
            baseIndex = indexOf("f1" to 1),
            indexes = emptyList(),
            options = emptyOptions()
        )

        assertMongoDbIndexIs(arrayOf("f1" to 1), index)
    }

    @Test
    fun `should return the index with highest cardinality if there are multiple matches`() {
        val index = CollectionIndexConsolidation.apply(
            baseIndex = indexOf("f1" to 1),
            indexes = listOf(indexOf("f1" to 1, "f2" to 1)),
            options = emptyOptions()
        )

        assertMongoDbIndexIs(arrayOf("f1" to 1, "f2" to 1), index)
    }

    @Test
    fun `should return the index with highest cardinality if there are multiple matches from a bigger index`() {
        val index = CollectionIndexConsolidation.apply(
            baseIndex = indexOf("f1" to 1, "f2" to 1),
            indexes = listOf(indexOf("f1" to 1)),
            options = emptyOptions()
        )

        assertMongoDbIndexIs(arrayOf("f1" to 1, "f2" to 1), index)
    }

    @Test
    fun `should inherit all covered queries`() {
        val index = CollectionIndexConsolidation.apply(
            baseIndex = indexOf("f1" to 1, "f2" to 1) {
                query {
                    findMany("my.coll".toNs()) {
                        filterBy {
                            predicate(Name.EQ) {
                                schema("f1")
                            }

                            predicate(Name.EQ) {
                                schema("f2")
                            }
                        }
                    }
                }
            },
            indexes = listOf(
                indexOf("f1" to 1) {
                    query {
                        findMany("my.coll".toNs()) {
                            filterBy {
                                predicate(Name.EQ) {
                                    schema("f1")
                                }
                            }
                        }
                    }
                }
            ),
            options = emptyOptions()
        )

        assertNumberOfCoveredQueriesForIndex(2, index)
        assertMongoDbIndexIs(arrayOf("f1" to 1, "f2" to 1), index)
    }

    private fun emptyOptions() = CollectionIndexConsolidationOptions(10)
}
