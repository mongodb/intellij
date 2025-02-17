package com.mongodb.jbplugin.autocomplete

import com.mongodb.jbplugin.StubReadModelProvider
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
import kotlin.test.Test
import kotlin.test.assertEquals

class AutocompletionTest {
    @Test
    fun returns_the_list_of_all_available_databases() {
        val readModelProvider = StubReadModelProvider<Any?>(
            mapOf(ListDatabases.Slice to { ListDatabases(listOf(ListDatabases.Database("myDb"))) })
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
    fun notifies_when_the_provided_database_does_not_exist() {
        val slice = ListCollections.Slice("myDb")
        val readModelProvider = StubReadModelProvider<Any?>(
            mapOf(slice to { throw RuntimeException("") })
        )

        val result = autocompleteCollections(null, readModelProvider, "myDb")

        assertEquals(
            AutocompletionResult.DatabaseDoesNotExist("myDb"),
            result,
        )
    }

    @Test
    fun returns_the_list_of_collections_for_the_given_database() {
        val slice = ListCollections.Slice("myDb")
        val readModelProvider = StubReadModelProvider<Any?>(
            mapOf(
                slice to {
                    ListCollections(
                        listOf(ListCollections.Collection("myColl", "collection")),
                    )
                }
            )
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
    fun returns_the_list_of_fields_for_sample_documents() {
        val namespace = Namespace("myDb", "myColl")
        val slice = GetCollectionSchema.Slice(namespace, 50)
        val readModelProvider = StubReadModelProvider<Any?>(
            mapOf(
                slice to {
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
                    )
                }
            )
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
