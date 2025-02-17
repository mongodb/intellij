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
import com.mongodb.jbplugin.utils.ModelDsl.filterBy
import com.mongodb.jbplugin.utils.ModelDsl.findMany
import com.mongodb.jbplugin.utils.ModelDsl.include
import com.mongodb.jbplugin.utils.ModelDsl.match
import com.mongodb.jbplugin.utils.ModelDsl.predicate
import com.mongodb.jbplugin.utils.ModelDsl.project
import com.mongodb.jbplugin.utils.ModelDsl.schema
import com.mongodb.jbplugin.utils.ModelDsl.sortBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun queries_without_a_collection_reference_component_are_not_supported() = runTest {
        val query = Node(Unit, emptyList())
        val result = IndexAnalyzer.analyze(query, EmptySiblingQueriesFinder(), emptyOptions())

        assertEquals(IndexAnalyzer.SuggestedIndex.NoIndex.cast(), result)
    }

    @Test
    fun returns_the_suggested_list_of_fields_for_a_mongodb_query() = runTest {
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
    fun places_low_cardinality_types_earlier_into_the_index_for_prefix_compression() = runTest {
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
    fun puts_equality_fields_before_sorting_fields_and_them_before_range_fields() = runTest {
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
                "mySortField" to 1,
                "myRangeField" to 1
            ),
            result
        )
    }

    @Test
    fun removes_repeated_field_references() = runTest {
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
    fun promotes_repeated_field_references_into_the_most_important_stage() = runTest {
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
    fun considers_aggregation_pipelines_match_stages() = runTest {
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
    fun does_not_consider_aggregation_pipelines_match_stages_in_the_second_position() = runTest {
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
    fun does_not_consider_aggregation_pipelines_stages_that_are_not_match() = runTest {
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
    fun finds_the_index_with_more_fields_that_matches_the_current_query_from_sibling_queries() = runTest {
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
    fun finds_the_index_with_more_fields_that_matches_the_current_query_from_sibling_queries_even_if_they_are_aggregates() = runTest {
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

    private val ns = "myDb.myColl".toNs()

    @Test
    fun places_fields_with_low_selectivity_earlier_in_the_index_definition_for_prefix_compression() = runTest {
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
    fun when_all_fields_have_same_selectivity_orders_by_cardinality() = runTest {
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
    fun when_selectivity_is_not_known_for_a_value_it_places_fields_with_low_cardinality_first_in_the_index_definition() = runTest {
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
    fun maintains_ESR_order_even_when_lower_selectivity_fields_exist_in_different_roles() = runTest {
        val schema = CollectionSchema(
            namespace = ns,
            schema = BsonObject(
                mapOf(
                    "highSelectivityEquality" to BsonString,
                    "lowSelectivityEquality" to BsonBoolean,
                    "highSelectivitySort" to BsonString,
                    "lowSelectivitySort" to BsonBoolean,
                    "highSelectivityRange" to BsonString,
                    "lowSelectivityRange" to BsonBoolean
                )
            ),
            dataDistribution = DataDistribution.generate(
                listOf(
                    mapOf(
                        "highSelectivityEquality" to "rare",
                        "lowSelectivityEquality" to true,
                        "highSelectivitySort" to "rare",
                        "lowSelectivitySort" to true,
                        "highSelectivityRange" to "rare",
                        "lowSelectivityRange" to true,
                    ),
                    mapOf(
                        "highSelectivityEquality" to "common",
                        "lowSelectivityEquality" to true,
                        "highSelectivitySort" to "common",
                        "lowSelectivitySort" to true,
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
                    schema("highSelectivitySort")
                }
                ascending {
                    schema("lowSelectivitySort")
                }
            }
        }

        val result = IndexAnalyzer.analyze(query, EmptySiblingQueriesFinder(), emptyOptions())

        assertMongoDbIndexIs(
            arrayOf(
                "lowSelectivityEquality" to 1,
                "highSelectivityEquality" to 1,
                "lowSelectivitySort" to 1,
                "highSelectivitySort" to 1,
                "lowSelectivityRange" to 1,
                "highSelectivityRange" to 1
            ),
            result
        )
    }

    private fun emptyOptions() = CollectionIndexConsolidationOptions(10)
}
