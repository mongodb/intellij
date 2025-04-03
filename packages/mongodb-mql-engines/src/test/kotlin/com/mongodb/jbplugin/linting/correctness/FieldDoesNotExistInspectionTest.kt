package com.mongodb.jbplugin.linting.correctness

import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.linting.Inspection.FieldDoesNotExist
import com.mongodb.jbplugin.linting.QueryInspectionTest
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

class FieldDoesNotExistInspectionTest : QueryInspectionTest<FieldDoesNotExist> {
    @Test
    fun `warns about a referenced field not in the specified collection`() = runInspectionTest {
        val collectionNamespace = Namespace("database", "collection")

        `when`(readModelProvider.slice(any(), any<GetCollectionSchema.Slice>())).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    collectionNamespace,
                    BsonObject(
                        mapOf(
                            "myString" to BsonString,
                            "myInt" to BsonInt32,
                        ),
                    ),
                ),
            ),
        )

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, collectionNamespace)
            )
        ).with(
            HasFilter(
                listOf(
                    Node(
                        null,
                        listOf(
                            HasFieldReference(
                                HasFieldReference.FromSchema(null, "myString")
                            )
                        )
                    ),
                    Node(
                        null,
                        listOf(
                            HasFieldReference(
                                HasFieldReference.FromSchema(null, "myBoolean")
                            )
                        )
                    ),
                ),
            )
        )

        val inspection = FieldDoesNotExistInspection<Unit>()
        inspection.run(query, holder, FieldDoesNotExistInspectionSettings(Unit, readModelProvider, 50))

        onInsight(0).assertInsightDescriptionIs(
            "insight.field-does-not-exist",
            "myBoolean"
        )
    }

    @Test
    fun `does not warn when fields are in the schema`() = runInspectionTest {
        val collectionNamespace = Namespace("database", "collection")

        `when`(readModelProvider.slice(any(), any<GetCollectionSchema.Slice>())).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    collectionNamespace,
                    BsonObject(
                        mapOf(
                            "myString" to BsonString,
                            "myBoolean" to BsonBoolean,
                        ),
                    ),
                ),
            ),
        )

        val query = query.with(
            HasCollectionReference(
                HasCollectionReference.Known(null, null, collectionNamespace)
            )
        ).with(
            HasFilter(
                listOf(
                    Node(
                        null,
                        listOf(
                            HasFieldReference(
                                HasFieldReference.FromSchema(null, "myString")
                            )
                        )
                    ),
                    Node(
                        null,
                        listOf(
                            HasFieldReference(
                                HasFieldReference.FromSchema(null, "myBoolean")
                            )
                        )
                    ),
                ),
            )
        )

        val inspection = FieldDoesNotExistInspection<Unit>()
        inspection.run(query, holder, FieldDoesNotExistInspectionSettings(Unit, readModelProvider, 50))

        assertNoInsights()
    }
}
