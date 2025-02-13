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

    fun assertIndexCollectionIs(expected: Namespace, actual: IndexAnalyzer.SuggestedIndex<Unit>) {
        if (actual !is IndexAnalyzer.SuggestedIndex.MongoDbIndex<Unit>) {
            throw AssertionError(
                "Collection is not equals to $expected because index is not a MongoDbIndex, but ${actual.javaClass.name}"
            )
        }

        assertCollectionIs(expected, actual.collectionReference)
    }

    fun assertNumberOfCoveredQueriesForIndex(expected: Int, actual: IndexAnalyzer.SuggestedIndex<Unit>) {
        if (actual !is IndexAnalyzer.SuggestedIndex.MongoDbIndex<Unit>) {
            throw AssertionError(
                "Could find number of covered queries because index is not a MongoDbIndex, but ${actual.javaClass.name}"
            )
        }

        assertEquals(expected, actual.coveredQueries.size)
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
