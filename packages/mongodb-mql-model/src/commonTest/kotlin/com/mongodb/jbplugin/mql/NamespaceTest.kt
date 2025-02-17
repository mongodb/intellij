package com.mongodb.jbplugin.mql

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NamespaceTest {
    @Test
    fun serialises_to_a_valid_namespace_string() {
        val namespace = Namespace("mydb", "my.cool.col")
        assertEquals("mydb.my.cool.col", namespace.toString())
    }

    @Test
    fun is_not_valid_if_both_database_and_collections_are_provided() {
        val namespace = Namespace("mydb", "mycoll")
        assertTrue(namespace.isValid)
    }

    @Test
    fun is_not_valid_if_the_database_is_empty() {
        val namespace = Namespace("", "my.cool.col")
        assertFalse(namespace.isValid)
    }

    @Test
    fun is_not_valid_if_the_collection_is_empty() {
        val namespace = Namespace("mydb", "")
        assertFalse(namespace.isValid)
    }
}
