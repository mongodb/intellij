package com.mongodb.jbplugin.indexing

import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasCollectionReference.Known
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasSorts
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IndexAnalyzerTest {
    @Test
    fun `queries without a collection reference component are not supported`() = runTest {
        val query = Node(Unit, emptyList())
        val result = IndexAnalyzer.analyze(query)

        assertEquals(IndexAnalyzer.SuggestedIndex.NoIndex, result)
    }

    @Test
    fun `returns the suggested list of fields for a mongodb query`() = runTest {
        val collectionReference =
            HasCollectionReference(Known(Unit, Unit, Namespace("myDb", "myColl")))
        val query = Node(
            Unit,
            listOf(
                collectionReference,
                HasFilter(
                    listOf(
                        Node(
                            Unit,
                            listOf(
                                Named(Name.EQ),
                                HasFieldReference(HasFieldReference.FromSchema(Unit, "myField")),
                                HasValueReference(HasValueReference.Constant(Unit, 52, BsonInt32))
                            )
                        )
                    )
                )
            )
        )

        val result = IndexAnalyzer.analyze(query) as IndexAnalyzer.SuggestedIndex.MongoDbIndex

        assertEquals(1, result.fields.size)
        assertEquals(collectionReference, result.collectionReference)
        assertEquals(
            IndexAnalyzer.SuggestedIndex.MongoDbIndexField("myField", Unit),
            result.fields[0]
        )
    }

    @Test
    fun `places low cardinality types earlier into the index for prefix compression`() = runTest {
        val collectionReference =
            HasCollectionReference(Known(Unit, Unit, Namespace("myDb", "myColl")))
        val query = Node(
            Unit,
            listOf(
                collectionReference,
                HasFilter(
                    listOf(
                        Node(
                            Unit,
                            listOf(
                                Named(Name.EQ),
                                HasFieldReference(
                                    HasFieldReference.FromSchema(Unit, "highCardinality")
                                ),
                                HasValueReference(HasValueReference.Constant(Unit, 52, BsonInt32))
                            )
                        ),
                        Node(
                            Unit,
                            listOf(
                                Named(Name.EQ),
                                HasFieldReference(
                                    HasFieldReference.FromSchema(Unit, "lowCardinality")
                                ),
                                HasValueReference(
                                    HasValueReference.Constant(Unit, false, BsonBoolean)
                                )
                            )
                        )
                    )
                )
            )
        )

        val result = IndexAnalyzer.analyze(query) as IndexAnalyzer.SuggestedIndex.MongoDbIndex

        assertEquals(2, result.fields.size)
        assertEquals(collectionReference, result.collectionReference)
        assertEquals(
            IndexAnalyzer.SuggestedIndex.MongoDbIndexField("lowCardinality", Unit),
            result.fields[0]
        )
        assertEquals(
            IndexAnalyzer.SuggestedIndex.MongoDbIndexField("highCardinality", Unit),
            result.fields[1]
        )
    }

    @Test
    fun `puts equality fields before sorting fields and them before range fields`() = runTest {
        val collectionReference =
            HasCollectionReference(Known(Unit, Unit, Namespace("myDb", "myColl")))
        val query = Node(
            Unit,
            listOf(
                collectionReference,
                HasFilter(
                    listOf(
                        Node(
                            Unit,
                            listOf(
                                Named(Name.EQ),
                                HasFieldReference(HasFieldReference.FromSchema(Unit, "myField")),
                                HasValueReference(HasValueReference.Constant(Unit, 52, BsonInt32))
                            )
                        ),
                        Node(
                            Unit,
                            listOf(
                                Named(Name.GT),
                                HasFieldReference(
                                    HasFieldReference.FromSchema(Unit, "myRangeField")
                                ),
                                HasValueReference(HasValueReference.Constant(Unit, 52, BsonInt32))
                            )
                        )
                    )
                ),
                HasSorts(
                    listOf(
                        Node(
                            Unit,
                            listOf(
                                Named(Name.ASCENDING),
                                HasFieldReference(
                                    HasFieldReference.FromSchema(Unit, "mySortField")
                                ),
                                HasValueReference(HasValueReference.Inferred(Unit, 1, BsonInt32))
                            )
                        )
                    )
                )
            )
        )

        val result = IndexAnalyzer.analyze(query) as IndexAnalyzer.SuggestedIndex.MongoDbIndex

        assertEquals(3, result.fields.size)
        assertEquals(collectionReference, result.collectionReference)
        assertEquals(
            IndexAnalyzer.SuggestedIndex.MongoDbIndexField("myField", Unit),
            result.fields[0]
        )
        assertEquals(
            IndexAnalyzer.SuggestedIndex.MongoDbIndexField("mySortField", Unit),
            result.fields[1]
        )
        assertEquals(
            IndexAnalyzer.SuggestedIndex.MongoDbIndexField("myRangeField", Unit),
            result.fields[2]
        )
    }

    @Test
    fun `removes repeated field references`() = runTest {
        val collectionReference =
            HasCollectionReference(Known(Unit, Unit, Namespace("myDb", "myColl")))
        val query = Node(
            Unit,
            listOf(
                collectionReference,
                HasFilter(
                    listOf(
                        Node(
                            Unit,
                            listOf(
                                Named(Name.EQ),
                                HasFieldReference(HasFieldReference.FromSchema(Unit, "myField")),
                                HasValueReference(HasValueReference.Constant(Unit, 52, BsonInt32))
                            )
                        ),
                        Node(
                            Unit,
                            listOf(
                                Named(Name.EQ),
                                HasFieldReference(
                                    HasFieldReference.FromSchema(Unit, "mySecondField")
                                ),
                                HasValueReference(HasValueReference.Constant(Unit, 52, BsonInt32))
                            )
                        ),
                        Node(
                            Unit,
                            listOf(
                                Named(Name.EQ),
                                HasFieldReference(HasFieldReference.FromSchema(Unit, "myField")),
                                HasValueReference(HasValueReference.Constant(Unit, 52, BsonInt32))
                            )
                        )
                    )
                )
            )
        )

        val result = IndexAnalyzer.analyze(query) as IndexAnalyzer.SuggestedIndex.MongoDbIndex

        assertEquals(2, result.fields.size)
        assertEquals(collectionReference, result.collectionReference)
        assertEquals(
            IndexAnalyzer.SuggestedIndex.MongoDbIndexField("myField", Unit),
            result.fields[0]
        )
        assertEquals(
            IndexAnalyzer.SuggestedIndex.MongoDbIndexField("mySecondField", Unit),
            result.fields[1]
        )
    }

    @Test
    fun `promotes repeated field references into the most important stage`() = runTest {
        val collectionReference =
            HasCollectionReference(Known(Unit, Unit, Namespace("myDb", "myColl")))
        val query = Node(
            Unit,
            listOf(
                collectionReference,
                HasFilter(
                    listOf(
                        Node(
                            Unit,
                            listOf(
                                Named(Name.EQ),
                                HasFieldReference(HasFieldReference.FromSchema(Unit, "myField")),
                                HasValueReference(
                                    HasValueReference.Constant(Unit, true, BsonBoolean)
                                )
                            )
                        ),
                        Node(
                            Unit,
                            listOf(
                                Named(Name.EQ),
                                HasFieldReference(
                                    HasFieldReference.FromSchema(Unit, "mySecondField")
                                ),
                                HasValueReference(HasValueReference.Constant(Unit, 52, BsonInt32))
                            )
                        )
                    )
                ),
                HasSorts(
                    listOf(
                        Node(
                            Unit,
                            listOf(
                                Named(Name.ASCENDING),
                                HasFieldReference(
                                    HasFieldReference.FromSchema(Unit, "myField")
                                ),
                                HasValueReference(HasValueReference.Inferred(Unit, 1, BsonInt32))
                            )
                        )
                    )
                )
            )
        )

        val result = IndexAnalyzer.analyze(query) as IndexAnalyzer.SuggestedIndex.MongoDbIndex

        assertEquals(2, result.fields.size)
        assertEquals(collectionReference, result.collectionReference)
        assertEquals(
            IndexAnalyzer.SuggestedIndex.MongoDbIndexField("myField", Unit),
            result.fields[0]
        )
        assertEquals(
            IndexAnalyzer.SuggestedIndex.MongoDbIndexField("mySecondField", Unit),
            result.fields[1]
        )
    }

    @Test
    fun `considers aggregation pipelines match stages`() = runTest {
        val collectionReference =
            HasCollectionReference(Known(Unit, Unit, Namespace("myDb", "myColl")))
        val query = Node(
            Unit,
            listOf(
                collectionReference,
                HasAggregation(
                    listOf(
                        Node(
                            Unit,
                            listOf(
                                Named(Name.MATCH),
                                HasFilter(
                                    listOf(
                                        Node(
                                            Unit,
                                            listOf(
                                                Named(Name.EQ),
                                                HasFieldReference(
                                                    HasFieldReference.FromSchema(Unit, "myField")
                                                ),
                                                HasValueReference(
                                                    HasValueReference.Constant(Unit, 52, BsonInt32)
                                                )
                                            )
                                        ),
                                        Node(
                                            Unit,
                                            listOf(
                                                Named(Name.EQ),
                                                HasFieldReference(
                                                    HasFieldReference.FromSchema(
                                                        Unit,
                                                        "mySecondField"
                                                    )
                                                ),
                                                HasValueReference(
                                                    HasValueReference.Constant(Unit, 52, BsonInt32)
                                                )
                                            )
                                        ),
                                        Node(
                                            Unit,
                                            listOf(
                                                Named(Name.EQ),
                                                HasFieldReference(
                                                    HasFieldReference.FromSchema(Unit, "myField")
                                                ),
                                                HasValueReference(
                                                    HasValueReference.Constant(Unit, 52, BsonInt32)
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val result = IndexAnalyzer.analyze(query) as IndexAnalyzer.SuggestedIndex.MongoDbIndex

        assertEquals(2, result.fields.size)
        assertEquals(collectionReference, result.collectionReference)
        assertEquals(
            IndexAnalyzer.SuggestedIndex.MongoDbIndexField("myField", Unit),
            result.fields[0]
        )
        assertEquals(
            IndexAnalyzer.SuggestedIndex.MongoDbIndexField("mySecondField", Unit),
            result.fields[1]
        )
    }

    @Test
    fun `does not consider aggregation pipelines match stages in the second position`() = runTest {
        val collectionReference =
            HasCollectionReference(Known(Unit, Unit, Namespace("myDb", "myColl")))
        val query = Node(
            Unit,
            listOf(
                collectionReference,
                HasAggregation(
                    listOf(
                        Node(Unit, listOf()),
                        Node(
                            Unit,
                            listOf(
                                Named(Name.MATCH),
                                HasFilter(
                                    listOf(
                                        Node(
                                            Unit,
                                            listOf(
                                                HasFieldReference(
                                                    HasFieldReference.FromSchema(Unit, "myField")
                                                )
                                            )
                                        ),
                                        Node(
                                            Unit,
                                            listOf(
                                                HasFieldReference(
                                                    HasFieldReference.FromSchema(
                                                        Unit,
                                                        "mySecondField"
                                                    )
                                                )
                                            )
                                        ),
                                        Node(
                                            Unit,
                                            listOf(
                                                HasFieldReference(
                                                    HasFieldReference.FromSchema(Unit, "myField")
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val result = IndexAnalyzer.analyze(query) as IndexAnalyzer.SuggestedIndex.MongoDbIndex

        assertEquals(0, result.fields.size)
    }

    @Test
    fun `does not consider aggregation pipelines stages that are not match`() = runTest {
        val collectionReference =
            HasCollectionReference(Known(Unit, Unit, Namespace("myDb", "myColl")))
        val query = Node(
            Unit,
            listOf(
                collectionReference,
                HasAggregation(
                    listOf(
                        Node(
                            Unit,
                            listOf(
                                Named(Name.GROUP),
                                HasFilter(
                                    listOf(
                                        Node(
                                            Unit,
                                            listOf(
                                                HasFieldReference(
                                                    HasFieldReference.FromSchema(Unit, "myField")
                                                )
                                            )
                                        ),
                                        Node(
                                            Unit,
                                            listOf(
                                                HasFieldReference(
                                                    HasFieldReference.FromSchema(
                                                        Unit,
                                                        "mySecondField"
                                                    )
                                                )
                                            )
                                        ),
                                        Node(
                                            Unit,
                                            listOf(
                                                HasFieldReference(
                                                    HasFieldReference.FromSchema(Unit, "myField")
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val result = IndexAnalyzer.analyze(query) as IndexAnalyzer.SuggestedIndex.MongoDbIndex

        assertEquals(0, result.fields.size)
        assertEquals(collectionReference, result.collectionReference)
    }
}
