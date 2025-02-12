package com.mongodb.jbplugin.indexing

import com.mongodb.jbplugin.accessadapter.toNs
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.SiblingQueriesFinder
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.utils.ModelAssertions.assertCollectionIs
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
import org.junit.jupiter.api.Assertions.assertEquals
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
        ) as IndexAnalyzer.SuggestedIndex.MongoDbIndex

        assertCollectionIs("myDb.myColl".toNs(), result.collectionReference)
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
        ) as IndexAnalyzer.SuggestedIndex.MongoDbIndex

        assertCollectionIs("myDb.myColl".toNs(), result.collectionReference)
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
            }
        }

        val result = IndexAnalyzer.analyze(
            query,
            EmptySiblingQueriesFinder(),
            emptyOptions()
        ) as IndexAnalyzer.SuggestedIndex.MongoDbIndex

        assertCollectionIs("myDb.myColl".toNs(), result.collectionReference)
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
        ) as IndexAnalyzer.SuggestedIndex.MongoDbIndex

        assertCollectionIs("myDb.myColl".toNs(), result.collectionReference)
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
        ) as IndexAnalyzer.SuggestedIndex.MongoDbIndex

        assertCollectionIs("myDb.myColl".toNs(), result.collectionReference)
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
        ) as IndexAnalyzer.SuggestedIndex.MongoDbIndex
        assertCollectionIs("myDb.myColl".toNs(), result.collectionReference)
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
        ) as IndexAnalyzer.SuggestedIndex.MongoDbIndex
        assertCollectionIs("myDb.myColl".toNs(), result.collectionReference)
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
        ) as IndexAnalyzer.SuggestedIndex.MongoDbIndex

        assertCollectionIs("myDb.myColl".toNs(), result.collectionReference)
        assertMongoDbIndexIs(emptyArray(), result)
    }

    private fun emptyOptions() = CollectionIndexConsolidationOptions(10)
}
