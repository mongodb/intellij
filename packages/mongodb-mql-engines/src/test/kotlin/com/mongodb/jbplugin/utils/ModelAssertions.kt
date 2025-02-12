package com.mongodb.jbplugin.utils

import com.mongodb.jbplugin.indexing.IndexAnalyzer
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import org.junit.jupiter.api.Assertions.assertEquals

object ModelAssertions {
    fun assertCollectionIs(expected: Namespace, actual: HasCollectionReference<Unit>) {
        val ref = actual.reference
        if (ref !is HasCollectionReference.Known<Unit>) {
            throw AssertionError(
                "Collection reference is not equals to $expected because it's not Known, but ${ref.javaClass.name}"
            )
        }

        assertEquals(expected, ref.namespace)
    }

    fun assertMongoDbIndexIs(
        expected: Array<Pair<String, Int>>,
        actual: IndexAnalyzer.SuggestedIndex<Unit>,
        additionalAssertions: (IndexAnalyzer.SuggestedIndex.MongoDbIndex<Unit>) -> Unit = {
        }
    ) {
        if (actual !is IndexAnalyzer.SuggestedIndex.MongoDbIndex<Unit>) {
            throw AssertionError(
                "Index is not equals to $expected because it's not a MongoDbIndex, but ${actual.javaClass.name}"
            )
        }

        assertEquals(expected.size, actual.fields.size)
        expected.zip(actual.fields) { expectedIdxField, actualIdxField ->
            assertEquals(expectedIdxField.first, actualIdxField.fieldName)
        }

        additionalAssertions(actual)
    }
}
