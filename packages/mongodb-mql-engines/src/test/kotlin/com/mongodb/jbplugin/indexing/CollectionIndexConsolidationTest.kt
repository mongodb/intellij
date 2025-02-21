package com.mongodb.jbplugin.indexing

import com.mongodb.jbplugin.accessadapter.toNs
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.utils.ModelAssertions.assertMongoDbIndexHasPartialExpression
import com.mongodb.jbplugin.utils.ModelAssertions.assertMongoDbIndexIs
import com.mongodb.jbplugin.utils.ModelAssertions.assertNumberOfCoveredQueriesForIndex
import com.mongodb.jbplugin.utils.ModelDsl.constant
import com.mongodb.jbplugin.utils.ModelDsl.filterBy
import com.mongodb.jbplugin.utils.ModelDsl.findMany
import com.mongodb.jbplugin.utils.ModelDsl.indexOf
import com.mongodb.jbplugin.utils.ModelDsl.partialFilterExpression
import com.mongodb.jbplugin.utils.ModelDsl.predicate
import com.mongodb.jbplugin.utils.ModelDsl.query
import com.mongodb.jbplugin.utils.ModelDsl.runtime
import com.mongodb.jbplugin.utils.ModelDsl.schema
import com.mongodb.jbplugin.utils.ModelDsl.withPartialExpression
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
    fun `should return the index with the most amount of fields`() {
        val index = CollectionIndexConsolidation.apply(
            baseIndex = indexOf("shipping.shipped" to 1, "arrival.arrived" to 1).withPartialExpression {
                predicate(Name.EQ) {
                    schema("arrival.arrived")
                    constant(false)
                }

                predicate(Name.EQ) {
                    schema("shipping.shipped")
                    constant(false)
                }
            },
            indexes = listOf(
                indexOf("shipping.shipped" to 1, "arrival.arrived" to 1).withPartialExpression {
                    predicate(Name.EQ) {
                        schema("shipping.shipped")
                        constant(false)
                    }

                    predicate(Name.EQ) {
                        schema("arrival.arrived")
                        constant(true)
                    }
                },
                indexOf("shipping.shipped" to 1, "arrival.arrived" to 1, "shipping.date" to 1).withPartialExpression {
                    predicate(Name.EQ) {
                        schema("shipping.shipped")
                        constant(false)
                    }

                    predicate(Name.EQ) {
                        schema("arrival.arrived")
                        constant(true)
                    }
                }
            ),
            options = emptyOptions()
        )

        assertMongoDbIndexIs(arrayOf("shipping.shipped" to 1, "arrival.arrived" to 1, "shipping.date" to 1), index)
        assertMongoDbIndexHasPartialExpression(
            partialFilterExpression {
                predicate(Name.EQ) {
                    schema("shipping.shipped")
                    constant(false)
                }
            },
            index
        )
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

    @Test
    fun `should be able to apply a partial expression to the index`() {
        val index = CollectionIndexConsolidation.apply(
            baseIndex = indexOf("f1" to 1) {
                query {
                    findMany("my.coll".toNs()) {
                        filterBy {
                            predicate(Name.EQ) {
                                schema("f1")
                            }
                        }
                    }
                }
            }.withPartialExpression {
                predicate(Name.EQ) {
                    schema("f1")
                    constant(1)
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
                }.withPartialExpression {
                    predicate(Name.EQ) {
                        schema("f1")
                        constant(1)
                    }
                }
            ),
            options = emptyOptions()
        )

        assertNumberOfCoveredQueriesForIndex(1, index)
        assertMongoDbIndexIs(arrayOf("f1" to 1), index)
        assertMongoDbIndexHasPartialExpression(
            partialFilterExpression {
                predicate(Name.EQ) {
                    schema("f1")
                    constant(1)
                }
            },
            index
        )
    }

    @Test
    fun `should be able to apply a common partial expression`() {
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
            }.withPartialExpression {
                predicate(Name.EQ) {
                    schema("f1")
                    constant(1)
                }
                predicate(Name.EQ) {
                    schema("f2")
                    constant(2)
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
                }.withPartialExpression {
                    predicate(Name.EQ) {
                        schema("f1")
                        constant(1)
                    }
                }
            ),
            options = emptyOptions()
        )

        assertNumberOfCoveredQueriesForIndex(2, index)
        assertMongoDbIndexIs(arrayOf("f1" to 1, "f2" to 1), index)
        assertMongoDbIndexHasPartialExpression(
            partialFilterExpression {
                predicate(Name.EQ) {
                    schema("f1")
                    constant(1)
                }
            },
            index
        )
    }

    @Test
    fun `should not apply a partial expression if a runtime value is found`() {
        val index = CollectionIndexConsolidation.apply(
            baseIndex = indexOf("f1" to 1) {
                query {
                    findMany("my.coll".toNs()) {
                        filterBy {
                            predicate(Name.EQ) {
                                schema("f1")
                                runtime(BsonInt32)
                            }
                        }
                    }
                }
            }.withPartialExpression {
                predicate(Name.EQ) {
                    schema("f1")
                    runtime(BsonInt32)
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
                }.withPartialExpression {
                    predicate(Name.EQ) {
                        schema("f1")
                        constant(1)
                    }
                }
            ),
            options = emptyOptions()
        )

        assertNumberOfCoveredQueriesForIndex(2, index)
        assertMongoDbIndexIs(arrayOf("f1" to 1), index)
        assertMongoDbIndexHasPartialExpression(null, index)
    }

    @Test
    fun `should ignore sorts for a partial expression`() {
        val index = CollectionIndexConsolidation.apply(
            baseIndex = indexOf("f1" to 1) {
                query {
                    findMany("my.coll".toNs()) {
                        filterBy {
                            predicate(Name.EQ) {
                                schema("f1")
                                runtime(BsonInt32)
                            }
                        }
                    }
                }
            }.withPartialExpression {
                predicate(Name.EQ) {
                    schema("f1")
                    runtime(BsonInt32)
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
                }.withPartialExpression {
                    predicate(Name.EQ) {
                        schema("f1")
                        constant(1)
                    }
                }
            ),
            options = emptyOptions()
        )

        assertNumberOfCoveredQueriesForIndex(2, index)
        assertMongoDbIndexIs(arrayOf("f1" to 1), index)
        assertMongoDbIndexHasPartialExpression(null, index)
    }

    @Test
    fun `should not process constants with different values as a partial index`() {
        val index = CollectionIndexConsolidation.apply(
            baseIndex = indexOf("f1" to 1) {
                query {
                    findMany("my.coll".toNs()) {
                        filterBy {
                            predicate(Name.EQ) {
                                schema("f1")
                                constant(true)
                            }
                        }
                    }
                }
            }.withPartialExpression {
                predicate(Name.EQ) {
                    schema("f1")
                    constant(true)
                }
            },
            indexes = listOf(
                indexOf("f1" to 1) {
                    query {
                        findMany("my.coll".toNs()) {
                            filterBy {
                                predicate(Name.EQ) {
                                    schema("f1")
                                    constant(false)
                                }
                            }
                        }
                    }
                }.withPartialExpression {
                    predicate(Name.EQ) {
                        schema("f1")
                        constant(false)
                    }
                }
            ),
            options = emptyOptions()
        )

        assertNumberOfCoveredQueriesForIndex(2, index)
        assertMongoDbIndexIs(arrayOf("f1" to 1), index)
        assertMongoDbIndexHasPartialExpression(null, index)
    }

    private fun emptyOptions() = CollectionIndexConsolidationOptions(10)
}
