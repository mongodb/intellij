package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.StubReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NamespaceCheckingLinterTest {
    @Test
    fun warns_about_a_referenced_database_not_existing() = runTest {
        val readModelProvider = StubReadModelProvider<Unit>(default = { ListDatabases(emptyList()) })
        val collectionNamespace = Namespace("database", "collection")

        val result =
            NamespaceCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                Node(
                    null,
                    listOf(
                        HasCollectionReference(
                            HasCollectionReference.Known(Unit, Unit, collectionNamespace)
                        ),
                    ),
                ),
            )

        assertEquals(1, result.warnings.size)
        val warning = result.warnings[0] as NamespaceCheckWarning.DatabaseDoesNotExist
        assertEquals("database", warning.database)
    }

    @Test
    fun warns_about_a_referenced_collection_not_existing() = runTest {
        val readModelProvider = StubReadModelProvider<Unit>(
            responses = mapOf(
                ListDatabases.Slice to { ListDatabases(listOf(ListDatabases.Database("database"))) }
            ),
            default = { ListCollections(emptyList()) }
        )

        val collectionNamespace = Namespace("database", "collection")

        val result =
            NamespaceCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                Node(
                    null,
                    listOf(
                        HasCollectionReference(
                            HasCollectionReference.Known(Unit, Unit, collectionNamespace)
                        ),
                    ),
                ),
            )

        assertEquals(1, result.warnings.size)
        val warning = result.warnings[0] as NamespaceCheckWarning.CollectionDoesNotExist
        assertEquals("database", warning.database)
        assertEquals("collection", warning.collection)
    }

    @Test
    fun warns_about_an_unknown_namespace_if_only_collection_is_provided() = runTest {
        val readModelProvider = StubReadModelProvider<Unit>(
            default = { ListDatabases(emptyList()) }
        )

        val result =
            NamespaceCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                Node(
                    null,
                    listOf(
                        HasCollectionReference(
                            HasCollectionReference.OnlyCollection(Unit, "collection")
                        ),
                    ),
                ),
            )

        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings[0] is NamespaceCheckWarning.NoNamespaceInferred)
    }

    @Test
    fun warns_about_an_unknown_namespace_if_unknown() = runTest {
        val readModelProvider = StubReadModelProvider<Unit>(
            default = { ListDatabases(emptyList()) }
        )

        val result =
            NamespaceCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                Node(
                    null,
                    listOf(
                        HasCollectionReference(HasCollectionReference.Unknown),
                    ),
                ),
            )

        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings[0] is NamespaceCheckWarning.NoNamespaceInferred)
    }

    @Test
    fun warns_about_an_unknown_namespace_if_not_provided() = runTest {
        val readModelProvider = StubReadModelProvider<Unit>(
            default = { ListDatabases(emptyList()) }
        )

        val result =
            NamespaceCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                Node(
                    null,
                    emptyList(),
                ),
            )

        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings[0] is NamespaceCheckWarning.NoNamespaceInferred)
    }
}
