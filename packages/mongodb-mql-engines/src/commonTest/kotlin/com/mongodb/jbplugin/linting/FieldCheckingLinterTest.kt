package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.StubReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.mql.*
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasProjections
import com.mongodb.jbplugin.mql.components.HasSorts
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FieldCheckingLinterTest {
    @Test
    fun warns_about_a_referenced_field_not_in_the_specified_collection() = runTest {
        val collectionNamespace = Namespace("database", "collection")
        val readModelProvider = StubReadModelProvider<Unit>(default = {
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
            )
        })

        val result =
            FieldCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                Node(
                    null,
                    listOf(
                        HasCollectionReference(
                            HasCollectionReference.Known(null, null, collectionNamespace)
                        ),
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
                        ),
                    ),
                ),
                50,
            )

        assertEquals(1, result.warnings.size)
        val warning = result.warnings[0] as FieldCheckWarning.FieldDoesNotExist
        assertEquals("myBoolean", warning.field)
    }

    @Test
    fun warns_about_a_referenced_field_in_a_nested_query_not_in_the_specified_collection() = runTest {
        val collectionNamespace = Namespace("database", "collection")
        val readModelProvider = StubReadModelProvider<Unit>(default = {
            GetCollectionSchema(
                CollectionSchema(
                    collectionNamespace,
                    BsonObject(
                        mapOf(
                            "myString1" to BsonInt32,
                        ),
                    ),
                ),
            )
        })

        val result =
            FieldCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                Node(
                    null,
                    listOf(
                        HasCollectionReference(
                            HasCollectionReference.Known(null, null, collectionNamespace)
                        ),
                        HasFilter(
                            listOf(
                                Node(
                                    null,
                                    listOf(
                                        Named(Name.AND),
                                        HasFilter(
                                            listOf(
                                                Node(
                                                    null,
                                                    listOf(
                                                        HasFieldReference(
                                                            HasFieldReference.FromSchema(
                                                                null,
                                                                "myString"
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                ),
                            ),
                        ),
                    ),
                ),
                50,
            )

        assertEquals(1, result.warnings.size)
        val warning = result.warnings[0] as FieldCheckWarning.FieldDoesNotExist
        assertEquals("myString", warning.field)
    }

    @Test
    fun warns_about_a_referenced_field_not_in_the_specified_collection_alongside_a_value_reference() = runTest {
        val collectionNamespace = Namespace("database", "collection")
        val readModelProvider = StubReadModelProvider<Unit>(default = {
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
            )
        })

        val result =
            FieldCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                Node(
                    null,
                    listOf(
                        HasCollectionReference(
                            HasCollectionReference.Known(null, null, collectionNamespace)
                        ),
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
                                        ),
                                        HasValueReference(
                                            HasValueReference.Constant(null, true, BsonBoolean)
                                        )
                                    )
                                ),
                            ),
                        ),
                    ),
                ),
                50,
            )

        assertEquals(1, result.warnings.size)
        val warning = result.warnings[0] as FieldCheckWarning.FieldDoesNotExist
        assertEquals("myBoolean", warning.field)
    }

    @Test
    fun warns_about_a_value_not_matching_the_type_of_underlying_field() = runTest {
        val collectionNamespace = Namespace("database", "collection")
        val readModelProvider = StubReadModelProvider<Unit>(default = {
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
            )
        })

        val result =
            FieldCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                Node(
                    null,
                    listOf(
                        HasCollectionReference(
                            HasCollectionReference.Known(null, null, collectionNamespace)
                        ),
                        HasFilter(
                            listOf(
                                Node(
                                    null,
                                    listOf(
                                        HasFieldReference(
                                            HasFieldReference.FromSchema(null, "myInt")
                                        ),
                                        HasValueReference(
                                            HasValueReference.Constant(null, null, BsonNull)
                                        )
                                    )
                                ),
                            ),
                        ),
                    ),
                ),
                50,
            )

        assertEquals(1, result.warnings.size)
        val warning = result.warnings[0] as FieldCheckWarning.FieldValueTypeMismatch
        assertEquals("myInt", warning.field)
    }

    @Test
    fun warns_about_the_referenced_fields_in_an_Aggregation_match_not_in_the_specified_collection() = runTest {
        val collectionNamespace = Namespace("database", "collection")
        val readModelProvider = StubReadModelProvider<Unit>(default = {
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
            )
        })

        val result =
            FieldCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                Node(
                    null,
                    listOf(
                        HasCollectionReference(
                            HasCollectionReference.Known(null, null, collectionNamespace)
                        ),
                        HasAggregation(
                            children = listOf(
                                Node(
                                    null,
                                    listOf(
                                        Named(Name.MATCH),
                                        HasFilter(
                                            listOf(
                                                Node(
                                                    null,
                                                    listOf(
                                                        HasFieldReference(
                                                            HasFieldReference.FromSchema(
                                                                null,
                                                                "myString"
                                                            )
                                                        )
                                                    )
                                                ),
                                                Node(
                                                    null,
                                                    listOf(
                                                        HasFieldReference(
                                                            HasFieldReference.FromSchema(
                                                                null,
                                                                "myBoolean"
                                                            )
                                                        )
                                                    )
                                                ),
                                            ),
                                        ),
                                    )
                                )
                            )
                        )
                    ),
                ),
                50,
            )

        assertEquals(1, result.warnings.size)
        val warning = result.warnings[0] as FieldCheckWarning.FieldDoesNotExist
        assertEquals("myBoolean", warning.field)
    }

    @Test
    fun warns_about_a_value_not_matching_the_type_of_underlying_field_in_Aggregation_match() = runTest {
        val collectionNamespace = Namespace("database", "collection")
        val readModelProvider = StubReadModelProvider<Unit>(default = {
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
            )
        })

        val result =
            FieldCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                Node(
                    null,
                    listOf(
                        HasCollectionReference(
                            HasCollectionReference.Known(null, null, collectionNamespace)
                        ),
                        HasAggregation(
                            listOf(
                                Node(
                                    null,
                                    listOf(
                                        Named(Name.MATCH),
                                        HasFilter(
                                            listOf(
                                                Node(
                                                    null,
                                                    listOf(
                                                        HasFieldReference(
                                                            HasFieldReference.FromSchema(
                                                                null,
                                                                "myInt"
                                                            )
                                                        ),
                                                        HasValueReference(
                                                            HasValueReference.Constant(
                                                                null,
                                                                null,
                                                                BsonNull
                                                            )
                                                        )
                                                    )
                                                ),
                                            ),
                                        ),
                                    )
                                )
                            )
                        )
                    ),
                ),
                50,
            )

        assertEquals(1, result.warnings.size)
        val warning = result.warnings[0] as FieldCheckWarning.FieldValueTypeMismatch
        assertEquals("myInt", warning.field)
    }

    @Test
    fun warns_about_the_referenced_fields_in_an_Aggregation_project_not_in_the_specified_collection() = runTest {
        val collectionNamespace = Namespace("database", "collection")
        val readModelProvider = StubReadModelProvider<Unit>(default = {
            GetCollectionSchema(
                CollectionSchema(
                    collectionNamespace,
                    BsonObject(
                        mapOf(
                            "myInt" to BsonInt32,
                        ),
                    ),
                ),
            )
        })

        val result =
            FieldCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                Node(
                    null,
                    listOf(
                        HasCollectionReference(
                            HasCollectionReference.Known(null, null, collectionNamespace)
                        ),
                        HasAggregation(
                            children = listOf(
                                Node(
                                    null,
                                    listOf(
                                        Named(Name.PROJECT),
                                        HasProjections(
                                            listOf(
                                                Node(
                                                    null,
                                                    listOf(
                                                        Named(Name.INCLUDE),
                                                        HasFieldReference(
                                                            HasFieldReference.FromSchema(
                                                                null,
                                                                "myBoolean"
                                                            )
                                                        ),
                                                        HasValueReference(
                                                            HasValueReference.Inferred(
                                                                null,
                                                                1,
                                                                BsonInt32
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
                    ),
                ),
                50,
            )

        assertEquals(1, result.warnings.size)
        val warning = result.warnings[0] as FieldCheckWarning.FieldDoesNotExist
        assertEquals("myBoolean", warning.field)
    }

    @Test
    fun warns_about_the_referenced_fields_in_an_Aggregation_sort_not_in_the_specified_collection() = runTest {
        val collectionNamespace = Namespace("database", "collection")
        val readModelProvider = StubReadModelProvider<Unit>(default = {
            GetCollectionSchema(
                CollectionSchema(
                    collectionNamespace,
                    BsonObject(
                        mapOf(
                            "myInt" to BsonInt32,
                        ),
                    ),
                ),
            )
        })

        val result =
            FieldCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                Node(
                    null,
                    listOf(
                        HasCollectionReference(
                            HasCollectionReference.Known(null, null, collectionNamespace)
                        ),
                        HasAggregation(
                            children = listOf(
                                Node(
                                    null,
                                    listOf(
                                        Named(Name.SORT),
                                        HasSorts(
                                            listOf(
                                                Node(
                                                    null,
                                                    listOf(
                                                        Named(Name.ASCENDING),
                                                        HasFieldReference(
                                                            HasFieldReference.FromSchema(
                                                                null,
                                                                "myBoolean"
                                                            )
                                                        ),
                                                        HasValueReference(
                                                            HasValueReference.Inferred(
                                                                null,
                                                                1,
                                                                BsonInt32
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
                    ),
                ),
                50,
            )

        assertEquals(1, result.warnings.size)
        val warning = result.warnings[0] as FieldCheckWarning.FieldDoesNotExist
        assertEquals("myBoolean", warning.field)
    }

    @Test
    fun should_not_warn_about_the_referenced_fields_in_an_Aggregation_addFields() = runTest {
        val collectionNamespace = Namespace("database", "collection")
        val readModelProvider = StubReadModelProvider<Unit>(default = {
            GetCollectionSchema(
                CollectionSchema(
                    collectionNamespace,
                    BsonObject(
                        mapOf(
                            "myInt" to BsonInt32,
                        ),
                    ),
                ),
            )
        })

        val result =
            FieldCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                Node(
                    null,
                    listOf(
                        HasCollectionReference(
                            HasCollectionReference.Known(null, null, collectionNamespace)
                        ),
                        HasAggregation(
                            children = listOf(
                                Node(
                                    null,
                                    listOf(
                                        Named(Name.SORT),
                                        HasSorts(
                                            listOf(
                                                Node(
                                                    null,
                                                    listOf(
                                                        Named(Name.ASCENDING),
                                                        HasFieldReference(
                                                            HasFieldReference.Computed(
                                                                null,
                                                                "myBoolean"
                                                            )
                                                        ),
                                                        HasValueReference(
                                                            HasValueReference.Constant(
                                                                null,
                                                                1,
                                                                BsonInt32
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
                    ),
                ),
                50,
            )

        assertEquals(0, result.warnings.size)
    }

    @Test
    fun should_not_warn_about_the_referenced_fields_in_an_Aggregation_unwind() = runTest {
        val collectionNamespace = Namespace("database", "collection")
        val readModelProvider = StubReadModelProvider<Unit>(default = {
            GetCollectionSchema(
                CollectionSchema(
                    collectionNamespace,
                    BsonObject(
                        mapOf(
                            "myInt" to BsonInt32,
                        ),
                    ),
                ),
            )
        })

        val result =
            FieldCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                Node(
                    null,
                    listOf(
                        HasCollectionReference(
                            HasCollectionReference.Known(null, null, collectionNamespace)
                        ),
                        HasAggregation(
                            children = listOf(
                                Node(
                                    null,
                                    listOf(
                                        Named(Name.UNWIND),
                                        HasFieldReference(
                                            HasFieldReference.FromSchema(
                                                null,
                                                "myBoolean",
                                                "${'$'}myBoolean"
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    ),
                ),
                50,
            )

        assertEquals(1, result.warnings.size)
    }
}
