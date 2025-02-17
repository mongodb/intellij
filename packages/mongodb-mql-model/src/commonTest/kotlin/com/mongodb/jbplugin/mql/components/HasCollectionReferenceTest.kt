package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Namespace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HasCollectionReferenceTest {
    @Test
    fun when_the_underlying_reference_is_Known_it_creates_a_copy_with_the_database_modified() {
        val dbRef = 1
        val collRef = 2

        val collectionReference = HasCollectionReference(
            HasCollectionReference.Known(
                dbRef,
                collRef,
                Namespace("foo", "bar"),
            )
        )

        val modifiedReference = collectionReference.copy("goo")
        // original is not modified
        assertEquals(
            dbRef,
            (collectionReference.reference as HasCollectionReference.Known).databaseSource,
        )
        assertEquals(
            collRef,
            (collectionReference.reference as HasCollectionReference.Known).collectionSource,
        )
        assertEquals(
            "goo",
            (modifiedReference.reference as HasCollectionReference.Known).namespace.database,
        )
    }

    @Test
    fun when_the_underlying_reference_is_Known_it_creates_a_copy_with_the_collection_schema_injected() {
        val dbRef = 1
        val collRef = 2

        val collectionReference = HasCollectionReference(
            HasCollectionReference.Known(
                dbRef,
                collRef,
                Namespace("foo", "bar"),
            )
        )

        val schema = CollectionSchema(
            Namespace("foo", "bar"),
            BsonObject(emptyMap()),
        )
        val modifiedReference = collectionReference.copy(schema)
        // original is not modified
        assertEquals(
            dbRef,
            (collectionReference.reference as HasCollectionReference.Known).databaseSource,
        )
        assertEquals(
            collRef,
            (collectionReference.reference as HasCollectionReference.Known).collectionSource,
        )
        assertEquals(
            schema,
            (modifiedReference.reference as HasCollectionReference.Known).schema,
        )
    }

    @Test
    fun when_the_underlying_reference_is_OnlyCollection_it_converts_it_to_Known() {
        val collRef = 1
        val collectionReference = HasCollectionReference(
            HasCollectionReference.OnlyCollection(
                collRef,
                "bar",
            )
        )

        val modifiedReference = collectionReference.copy("foo")
        // original is not modified
        assertTrue(collectionReference.reference is HasCollectionReference.OnlyCollection)

        assertTrue(modifiedReference.reference is HasCollectionReference.Known)
        assertEquals(
            "foo",
            (modifiedReference.reference as HasCollectionReference.Known).namespace.database,
        )
        assertEquals(
            collRef,
            (modifiedReference.reference as HasCollectionReference.Known).collectionSource,
        )
    }

    @Test
    fun when_the_underlying_reference_is_Unknown_it_does_nothing() {
        val collectionReference = HasCollectionReference(
            HasCollectionReference.Unknown
        )

        val modifiedReference = collectionReference.copy("foo")
        // original is not modified
        assertTrue(collectionReference.reference is HasCollectionReference.Unknown)

        assertTrue(modifiedReference.reference is HasCollectionReference.Unknown)
    }
}
