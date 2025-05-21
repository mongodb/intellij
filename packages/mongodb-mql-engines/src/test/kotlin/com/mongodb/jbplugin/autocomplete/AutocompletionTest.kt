package com.mongodb.jbplugin.autocomplete

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.autocomplete.Autocompletion.autocompleteCollections
import com.mongodb.jbplugin.autocomplete.Autocompletion.autocompleteDatabases
import com.mongodb.jbplugin.autocomplete.Autocompletion.autocompleteFields
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.BsonObjectId
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Namespace
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.mock

class AutocompletionTest {
    @Test
    fun `returns the list of all available databases`() = runTest {
        val readModelProvider = mock<MongoDbReadModelProvider<Any?>>()
        `when`(readModelProvider.slice(null, ListDatabases.Slice)).thenReturn(
            ListDatabases(listOf(ListDatabases.Database("myDb"))),
        )

        val result =
            autocompleteDatabases(
                null,
                readModelProvider,
            ) as AutocompletionResult.Successful

        assertEquals(
            listOf(
                AutocompletionEntry(
                    "myDb",
                    AutocompletionEntry.AutocompletionEntryType.DATABASE,
                    null
                )
            ),
            result.entries,
        )
    }

    @Test
    fun `notifies when the provided database does not exist`() = runTest {
        val readModelProvider = mock<MongoDbReadModelProvider<Any?>>()
        val slice = ListCollections.Slice("myDb")

        // the server returns an error if the database provided to
        // runCommand does not exist
        `when`(readModelProvider.slice(null, slice)).thenThrow(RuntimeException(""))

        val result = autocompleteCollections(null, readModelProvider, "myDb")

        assertEquals(
            AutocompletionResult.DatabaseDoesNotExist("myDb"),
            result,
        )
    }

    @Test
    fun `returns the list of collections for the given database`() = runTest {
        val readModelProvider = mock<MongoDbReadModelProvider<Any?>>()
        val slice = ListCollections.Slice("myDb")

        `when`(readModelProvider.slice(null, slice)).thenReturn(
            ListCollections(
                listOf(ListCollections.Collection("myColl", "collection")),
            ),
        )

        val result =
            autocompleteCollections(
                null,
                readModelProvider,
                "myDb"
            ) as AutocompletionResult.Successful

        assertEquals(
            listOf(
                AutocompletionEntry(
                    "myColl",
                    AutocompletionEntry.AutocompletionEntryType.COLLECTION,
                    null
                )
            ),
            result.entries,
        )
    }

    @Test
    fun `returns the list of fields for sample documents`() = runTest {
        val readModelProvider = mock<MongoDbReadModelProvider<Any?>>()
        val namespace = Namespace("myDb", "myColl")
        val slice = GetCollectionSchema.Slice(namespace, 50)

        `when`(readModelProvider.slice(null, slice)).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    namespace,
                    BsonObject(
                        mapOf(
                            "_id" to BsonObjectId,
                            "text" to BsonString,
                        ),
                    ),
                ),
            ),
        )

        val result =
            autocompleteFields(
                null,
                readModelProvider,
                namespace,
                50
            ) as AutocompletionResult.Successful

        assertEquals(
            listOf(
                AutocompletionEntry(
                    "_id",
                    AutocompletionEntry.AutocompletionEntryType.FIELD,
                    BsonObjectId
                ),
                AutocompletionEntry(
                    "text",
                    AutocompletionEntry.AutocompletionEntryType.FIELD,
                    BsonString
                ),
            ),
            result.entries,
        )
    }
}
