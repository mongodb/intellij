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
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollectionIndexConsolidationTest {
    @Test
    fun isPrefixOf_should_return_true_if_they_are_equal() {
        assertTrue(indexOf("f1" to 1).isPrefixOf(indexOf("f1" to 1)))
    }

    @Test
    fun isPrefixOf_should_return_true_if_the_left_side_is_a_prefix_to_the_right_side() {
        assertTrue(indexOf("f1" to 1).isPrefixOf(indexOf("f1" to 1, "f2" to 1)))
    }

    @Test
    fun isPrefixOf_should_return_false_if_the_left_side_is_bigger_than_the_right_side() {
        assertFalse(indexOf("f1" to 1, "f2" to 1).isPrefixOf(indexOf("f1" to 1)))
    }

    @Test
    fun isPrefixOf_should_return_false_if_there_is_a_shared_prefix_but_additional_fields() {
        assertFalse(indexOf("f1" to 1, "f3" to 1).isPrefixOf(indexOf("f1" to 1, "f2" to 1)))
    }

    @Test
    fun should_return_itself_if_no_other_index_candidates() {
        val index = CollectionIndexConsolidation.apply(
            baseIndex = indexOf("f1" to 1),
            indexes = emptyList(),
            options = emptyOptions()
        )

        assertMongoDbIndexIs(arrayOf("f1" to 1), index)
    }

    @Test
    fun should_return_the_index_with_highest_cardinality_if_there_are_multiple_matches() {
        val index = CollectionIndexConsolidation.apply(
            baseIndex = indexOf("f1" to 1),
            indexes = listOf(indexOf("f1" to 1, "f2" to 1)),
            options = emptyOptions()
        )

        assertMongoDbIndexIs(arrayOf("f1" to 1, "f2" to 1), index)
    }

    @Test
    fun should_return_the_index_with_highest_cardinality_if_there_are_multiple_matches_from_a_bigger_index() {
        val index = CollectionIndexConsolidation.apply(
            baseIndex = indexOf("f1" to 1, "f2" to 1),
            indexes = listOf(indexOf("f1" to 1)),
            options = emptyOptions()
        )

        assertMongoDbIndexIs(arrayOf("f1" to 1, "f2" to 1), index)
    }

    @Test
    fun should_inherit_all_covered_queries() {
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
