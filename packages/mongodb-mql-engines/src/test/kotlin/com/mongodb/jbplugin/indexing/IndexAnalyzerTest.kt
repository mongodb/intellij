package com.mongodb.jbplugin.indexing

import com.mongodb.jbplugin.accessadapter.toNs
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.DataDistribution
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.SiblingQueriesFinder
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.utils.ModelAssertions.assertIndexCollectionIs
import com.mongodb.jbplugin.utils.ModelAssertions.assertMongoDbIndexIs
import com.mongodb.jbplugin.utils.ModelDsl.aggregate
import com.mongodb.jbplugin.utils.ModelDsl.ascending
import com.mongodb.jbplugin.utils.ModelDsl.constant
import com.mongodb.jbplugin.utils.ModelDsl.descending
import com.mongodb.jbplugin.utils.ModelDsl.filterBy
import com.mongodb.jbplugin.utils.ModelDsl.findMany
import com.mongodb.jbplugin.utils.ModelDsl.include
import com.mongodb.jbplugin.utils.ModelDsl.match
import com.mongodb.jbplugin.utils.ModelDsl.predicate
import com.mongodb.jbplugin.utils.ModelDsl.project
import com.mongodb.jbplugin.utils.ModelDsl.schema
import com.mongodb.jbplugin.utils.ModelDsl.sortBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class IndexAnalyzerTest {
    class EmptySiblingQueriesFinder<S> : SiblingQueriesFinder<S> {
        override fun allSiblingsOf(query: Node<S>): Array<Node<S>> {
            return emptyArray()
        }
    }

    class PredefinedSiblingQueriesFinder<S>(private val other: Array<Node<S>>) : SiblingQueriesFinder<S> {
        override fun allSiblingsOf(query: Node<S>): Array<Node<S>> {
            return other
        }
    }

    @Test
    fun `queries without a collection reference component are not supported`() = runTest {
        val query = Node(Unit, emptyList())
        val result = IndexAnalyzer.analyze(query, EmptySiblingQueriesFinder(), emptyOptions())

        assertEquals(IndexAnalyzer.SuggestedIndex.NoIndex, result)
    }

    @Test
    fun `returns the suggested list of fields for a mongodb query`() = runTest {
        val query = findMany("myDb.myColl".toNs()) {
            filterBy {
                predicate(Name.EQ) {
                    schema("myField")
                    constant(52)
                }
            }
        }

        val result = IndexAnalyzer.analyze(
            query,
            EmptySiblingQueriesFinder(),
            emptyOptions()
        )

        assertIndexCollectionIs("myDb.myColl".toNs(), result)
        assertMongoDbIndexIs(arrayOf("myField" to 1), result)
    }

    @Test
    fun `places low cardinality types earlier into the index for prefix compression`() = runTest {
        val query = findMany("myDb.myColl".toNs()) {
            filterBy {
                predicate(Name.EQ) {
                    schema("highCardinality")
                    constant(52)
                }
                predicate(Name.EQ) {
                    schema("lowCardinality")
                    constant(true)
                }
            }
        }

        val result = IndexAnalyzer.analyze(
            query,
            EmptySiblingQueriesFinder(),
            emptyOptions()
        )

        assertIndexCollectionIs("myDb.myColl".toNs(), result)
        assertMongoDbIndexIs(arrayOf("lowCardinality" to 1, "highCardinality" to 1), result)
    }

    @Test
    fun `puts equality fields before sorting fields and them before range fields`() = runTest {
        val query = findMany("myDb.myColl".toNs()) {
            filterBy {
                predicate(Name.EQ) {
                    schema("myField")
                    constant(52)
                }
                predicate(Name.GT) {
                    schema("myRangeField")
                    constant(true)
                }
            }
            sortBy {
                ascending { schema("mySortField") }
                descending { schema("myField") }
            }
        }

        val result = IndexAnalyzer.analyze(
            query,
            EmptySiblingQueriesFinder(),
            emptyOptions()
        )

        assertIndexCollectionIs("myDb.myColl".toNs(), result)
        assertMongoDbIndexIs(
            arrayOf(
                "myField" to -1,
                "mySortField" to 1,
                "myRangeField" to 1
            ),
            result
        )
    }

    @Test
    fun `removes repeated field references`() = runTest {
        val query = findMany("myDb.myColl".toNs()) {
            filterBy {
                predicate(Name.EQ) {
                    schema("myField")
                    constant(52)
                }
                predicate(Name.EQ) {
                    schema("mySecondField")
                    constant(true)
                }
                predicate(Name.EQ) {
                    schema("myField")
                    constant(55)
                }
            }
        }

        val result = IndexAnalyzer.analyze(
            query,
            EmptySiblingQueriesFinder(),
            emptyOptions()
        )

        assertIndexCollectionIs("myDb.myColl".toNs(), result)
        assertMongoDbIndexIs(
            arrayOf(
                "mySecondField" to 1,
                "myField" to 1,
            ),
            result
        )
    }

    @Test
    fun `promotes repeated field references into the most important stage`() = runTest {
        val query = findMany("myDb.myColl".toNs()) {
            filterBy {
                predicate(Name.EQ) {
                    schema("myField")
                    constant(52)
                }
                predicate(Name.GT) {
                    schema("mySecondField")
                    constant(12)
                }
            }

            sortBy {
                ascending { schema("mySecondField") }
            }
        }

        val result = IndexAnalyzer.analyze(
            query,
            EmptySiblingQueriesFinder(),
            emptyOptions()
        )

        assertIndexCollectionIs("myDb.myColl".toNs(), result)
        assertMongoDbIndexIs(
            arrayOf(
                "myField" to 1,
                "mySecondField" to 1,
            ),
            result
        )
    }

    @Test
    fun `considers aggregation pipelines match stages`() = runTest {
        val query = aggregate("myDb.myColl".toNs()) {
            match {
                predicate(Name.EQ) {
                    schema("myField")
                    constant(52)
                }
                predicate(Name.GT) {
                    schema("mySecondField")
                    constant(12)
                }
            }
        }

        val result = IndexAnalyzer.analyze(
            query,
            EmptySiblingQueriesFinder(),
            emptyOptions()
        )

        assertIndexCollectionIs("myDb.myColl".toNs(), result)
        assertMongoDbIndexIs(
            arrayOf(
                "myField" to 1,
                "mySecondField" to 1,
            ),
            result
        )
    }

    @Test
    fun `does not consider aggregation pipelines match stages in the second position`() = runTest {
        val query = aggregate("myDb.myColl".toNs()) {
            match {
                predicate(Name.EQ) {
                    schema("myField")
                    constant(52)
                }
            }

            match {
                predicate(Name.EQ) {
                    schema("myIgnoredField")
                    constant(52)
                }
            }
        }

        val result = IndexAnalyzer.analyze(
            query,
            EmptySiblingQueriesFinder(),
            emptyOptions()
        )

        assertIndexCollectionIs("myDb.myColl".toNs(), result)
        assertMongoDbIndexIs(
            arrayOf(
                "myField" to 1,
            ),
            result
        )
    }

    @Test
    fun `does not consider aggregation pipelines stages that are not match`() = runTest {
        val query = aggregate("myDb.myColl".toNs()) {
            project {
                include {
                    schema("projectedField")
                }
            }
        }

        val result = IndexAnalyzer.analyze(
            query,
            EmptySiblingQueriesFinder(),
            emptyOptions()
        )

        assertIndexCollectionIs("myDb.myColl".toNs(), result)
        assertMongoDbIndexIs(emptyArray(), result)
    }

    @Test
    fun `finds the index with more fields that matches the current query from sibling queries`() = runTest {
        val predefinedSiblingQueries = PredefinedSiblingQueriesFinder(
            arrayOf(
                findMany("myDb.myColl".toNs()) {
                    filterBy {
                        predicate(Name.EQ) {
                            schema("myField")
                            constant(52)
                        }
                        predicate(Name.GT) {
                            schema("mySecondField")
                            constant(12)
                        }
                    }

                    sortBy {
                        ascending { schema("mySortField") }
                    }
                }
            )
        )

        val query = findMany("myDb.myColl".toNs()) {
            filterBy {
                predicate(Name.EQ) {
                    schema("myField")
                    constant(52)
                }
            }
        }

        val result = IndexAnalyzer.analyze(
            query,
            predefinedSiblingQueries,
            emptyOptions()
        )

        assertIndexCollectionIs("myDb.myColl".toNs(), result)
        assertMongoDbIndexIs(
            arrayOf(
                "myField" to 1,
                "mySortField" to 1,
                "mySecondField" to 1,
            ),
            result
        )
    }

    @Test
    fun `finds the index with more fields that matches the current query from sibling queries even if they are aggregates`() = runTest {
        val predefinedSiblingQueries = PredefinedSiblingQueriesFinder(
            arrayOf(
                aggregate("myDb.myColl".toNs()) {
                    match {
                        predicate(Name.EQ) {
                            schema("myField")
                            constant(52)
                        }
                        predicate(Name.GT) {
                            schema("mySecondField")
                            constant(12)
                        }
                    }
                }
            )
        )

        val query = findMany("myDb.myColl".toNs()) {
            filterBy {
                predicate(Name.EQ) {
                    schema("myField")
                    constant(52)
                }
            }
        }

        val result = IndexAnalyzer.analyze(
            query,
            predefinedSiblingQueries,
            emptyOptions()
        )

        assertIndexCollectionIs("myDb.myColl".toNs(), result)
        assertMongoDbIndexIs(
            arrayOf(
                "myField" to 1,
                "mySecondField" to 1
            ),
            result
        )
    }

    @Nested
    inner class WhenDataDistributionIsAvailable {
        private val ns = "myDb.myColl".toNs()

        @Test
        fun `places fields with low selectivity earlier in the index definition for prefix compression`() = runTest {
            val schema = CollectionSchema(
                namespace = ns,
                schema = BsonObject(
                    mapOf(
                        "highSelectivityHighCardinality" to BsonInt32,
                        "highSelectivityLowCardinality" to BsonBoolean,
                        "lowSelectivityHighCardinality" to BsonString,
                        "lowSelectivityLowCardinality" to BsonBoolean,
                    )
                ),
                dataDistribution = DataDistribution.generate(
                    listOf(
                        mapOf(
                            "highSelectivityHighCardinality" to 2,
                            "highSelectivityLowCardinality" to true,
                            "lowSelectivityHighCardinality" to "US",
                            "lowSelectivityLowCardinality" to true
                        ),
                        mapOf(
                            "highSelectivityHighCardinality" to 3,
                            "highSelectivityLowCardinality" to false,
                            "lowSelectivityHighCardinality" to "US",
                            "lowSelectivityLowCardinality" to true
                        ),
                        mapOf(
                            "highSelectivityHighCardinality" to 4,
                            "highSelectivityLowCardinality" to false,
                            "lowSelectivityHighCardinality" to "US",
                            "lowSelectivityLowCardinality" to true
                        ),
                        mapOf(
                            "highSelectivityHighCardinality" to 5,
                            "highSelectivityLowCardinality" to false,
                            "lowSelectivityHighCardinality" to "US",
                            "lowSelectivityLowCardinality" to true
                        ),
                    )
                )
            )

            val query = findMany(ns, schema) {
                filterBy {
                    predicate(Name.EQ) {
                        schema("highSelectivityHighCardinality")
                        constant(2)
                    }
                    predicate(Name.EQ) {
                        schema("highSelectivityLowCardinality")
                        constant(true)
                    }
                    predicate(Name.EQ) {
                        schema("lowSelectivityHighCardinality")
                        constant("US")
                    }
                    predicate(Name.EQ) {
                        schema("lowSelectivityLowCardinality")
                        constant(true)
                    }
                }
            }

            val result = IndexAnalyzer.analyze(
                query,
                EmptySiblingQueriesFinder(),
                emptyOptions()
            )

            assertMongoDbIndexIs(
                arrayOf(
                    "lowSelectivityLowCardinality" to 1,
                    "lowSelectivityHighCardinality" to 1,
                    "highSelectivityLowCardinality" to 1,
                    "highSelectivityHighCardinality" to 1,
                ),
                result
            )
        }

        @Test
        fun `when all fields have same selectivity orders by cardinality`() = runTest {
            val schema = CollectionSchema(
                namespace = ns,
                schema = BsonObject(
                    mapOf(
                        "highCardinality" to BsonInt32,
                        "lowCardinality" to BsonBoolean,
                    )
                ),
                dataDistribution = DataDistribution.generate(
                    listOf(
                        mapOf("highCardinality" to 1, "lowCardinality" to true),
                        mapOf("highCardinality" to 2, "lowCardinality" to false)
                    )
                )
            )

            val query = findMany(ns, schema) {
                filterBy {
                    predicate(Name.EQ) {
                        schema("highCardinality")
                        constant(1)
                    }
                    predicate(Name.EQ) {
                        schema("lowCardinality")
                        constant(true)
                    }
                }
            }

            val result = IndexAnalyzer.analyze(query, EmptySiblingQueriesFinder(), emptyOptions())
            assertMongoDbIndexIs(arrayOf("lowCardinality" to 1, "highCardinality" to 1), result)
        }

        @Test
        fun `when selectivity is not known for a value, it places fields with low cardinality first in the index definition`() = runTest {
            val schema = CollectionSchema(
                namespace = ns,
                schema = BsonObject(
                    mapOf(
                        "highSelectivityHighCardinality" to BsonInt32,
                        "highSelectivityLowCardinality" to BsonBoolean,
                        "unknownSelectivityHighCardinality" to BsonString,
                        "lowSelectivityLowCardinality" to BsonBoolean,
                    )
                ),
                dataDistribution = DataDistribution.generate(
                    listOf(
                        mapOf(
                            "highSelectivityHighCardinality" to 2,
                            "highSelectivityLowCardinality" to true,
                            "lowSelectivityLowCardinality" to true
                        ),
                        mapOf(
                            "highSelectivityHighCardinality" to 3,
                            "highSelectivityLowCardinality" to false,
                            "lowSelectivityLowCardinality" to true
                        ),
                        mapOf(
                            "highSelectivityHighCardinality" to 4,
                            "highSelectivityLowCardinality" to false,
                            "lowSelectivityLowCardinality" to true
                        ),
                        mapOf(
                            "highSelectivityHighCardinality" to 5,
                            "highSelectivityLowCardinality" to false,
                            "lowSelectivityLowCardinality" to true
                        ),
                    )
                )
            )

            val query = findMany(ns, schema) {
                filterBy {
                    predicate(Name.EQ) {
                        schema("highSelectivityHighCardinality")
                        constant(2)
                    }
                    predicate(Name.EQ) {
                        schema("highSelectivityLowCardinality")
                        constant(true)
                    }
                    predicate(Name.EQ) {
                        schema("unknownSelectivityHighCardinality")
                        constant("US")
                    }
                    predicate(Name.EQ) {
                        schema("lowSelectivityLowCardinality")
                        constant(true)
                    }
                }
            }

            val result = IndexAnalyzer.analyze(
                query,
                EmptySiblingQueriesFinder(),
                emptyOptions()
            )

            assertMongoDbIndexIs(
                arrayOf(
                    "lowSelectivityLowCardinality" to 1,
                    "highSelectivityLowCardinality" to 1,
                    "highSelectivityHighCardinality" to 1,
                    "unknownSelectivityHighCardinality" to 1,
                ),
                result
            )
        }

        @Test
        fun `maintains ESR order even when lower selectivity fields exist in different roles`() = runTest {
            val schema = CollectionSchema(
                namespace = ns,
                schema = BsonObject(
                    mapOf(
                        "highSelectivityEquality" to BsonString,
                        "lowSelectivityEquality" to BsonBoolean,
                        "highCardinalitySort" to BsonString,
                        "lowCardinalitySort" to BsonBoolean,
                        "highSelectivityRange" to BsonString,
                        "lowSelectivityRange" to BsonBoolean
                    )
                ),
                dataDistribution = DataDistribution.generate(
                    listOf(
                        mapOf(
                            "highSelectivityEquality" to "rare",
                            "lowSelectivityEquality" to true,
                            "highCardinalitySort" to "rare",
                            "lowCardinalitySort" to true,
                            "highSelectivityRange" to "rare",
                            "lowSelectivityRange" to true,
                        ),
                        mapOf(
                            "highSelectivityEquality" to "common",
                            "lowSelectivityEquality" to true,
                            "highCardinalitySort" to "common",
                            "lowCardinalitySort" to true,
                            "highSelectivityRange" to "common",
                            "lowSelectivityRange" to true
                        )
                    )
                )
            )

            val query = findMany(ns, schema) {
                filterBy {
                    predicate(Name.EQ) {
                        schema("highSelectivityEquality")
                        constant("rare")
                    }
                    predicate(Name.EQ) {
                        schema("lowSelectivityEquality")
                        constant(true)
                    }
                    predicate(Name.GT) {
                        schema("highSelectivityRange")
                        constant("rare")
                    }
                    predicate(Name.GT) {
                        schema("lowSelectivityRange")
                        constant(true)
                    }
                }
                sortBy {
                    ascending {
                        schema("highCardinalitySort")
                    }
                    ascending {
                        schema("lowCardinalitySort")
                    }
                }
            }

            val result = IndexAnalyzer.analyze(query, EmptySiblingQueriesFinder(), emptyOptions())

            assertMongoDbIndexIs(
                arrayOf(
                    "lowSelectivityEquality" to 1,
                    "highSelectivityEquality" to 1,
                    "lowCardinalitySort" to 1,
                    "highCardinalitySort" to 1,
                    "lowSelectivityRange" to 1,
                    "highSelectivityRange" to 1
                ),
                result
            )
        }

        @Test
        fun `maintains sort direction specified in the user code alongside the ESR order`() = runTest {
            val schema = CollectionSchema(
                namespace = ns,
                schema = BsonObject(
                    mapOf(
                        "highSelectivityEquality" to BsonString,
                        "lowSelectivityEquality" to BsonBoolean,
                        "highSelectivityRange" to BsonString,
                        "lowSelectivityRange" to BsonBoolean
                    )
                ),
                dataDistribution = DataDistribution.generate(
                    listOf(
                        mapOf(
                            "highSelectivityEquality" to "rare",
                            "lowSelectivityEquality" to true,
                            "highSelectivityRangeAndSort" to "rare",
                            "lowSelectivityRange" to true,
                        ),
                        mapOf(
                            "highSelectivityEquality" to "common",
                            "lowSelectivityEquality" to true,
                            "highSelectivityRangeAndSort" to "common",
                            "lowSelectivityRange" to true
                        )
                    )
                )
            )

            val query = findMany(ns, schema) {
                filterBy {
                    predicate(Name.EQ) {
                        schema("highSelectivityEquality")
                        constant("rare")
                    }
                    predicate(Name.EQ) {
                        schema("lowSelectivityEquality")
                        constant(true)
                    }
                    predicate(Name.GT) {
                        schema("highSelectivityRangeAndSort")
                        constant("rare")
                    }
                    predicate(Name.GT) {
                        schema("lowSelectivityRange")
                        constant(true)
                    }
                }
                sortBy {
                    descending {
                        schema("lowSelectivityEquality")
                    }
                    ascending {
                        // We are essentially promoting the range to a sort
                        schema("highSelectivityRangeAndSort")
                    }
                }
            }

            val result = IndexAnalyzer.analyze(query, EmptySiblingQueriesFinder(), emptyOptions())

            assertMongoDbIndexIs(
                arrayOf(
                    "lowSelectivityEquality" to -1,
                    "highSelectivityEquality" to 1,
                    "highSelectivityRangeAndSort" to 1,
                    "lowSelectivityRange" to 1,
                ),
                result
            )
        }
    }

    private fun emptyOptions() = CollectionIndexConsolidationOptions(10)
}
