package com.mongodb.jbplugin.accessadapter

import com.mongodb.jbplugin.mql.Namespace
import kotlin.test.Test
import kotlin.test.assertEquals

class MongoDbDriverTest {
    @Test
    fun cleans_up_the_url_schema_for_mongodb() {
        val conn = ConnectionString(listOf("mongodb://localhost"))
        assertEquals("localhost", conn.hosts[0])
    }

    @Test
    fun cleans_up_the_url_schema_for_mongodb_srv() {
        val conn = ConnectionString(listOf("mongodb+srv://localhost"))
        assertEquals("localhost", conn.hosts[0])
    }

    @Test
    fun parses_a_namespace() {
        val namespace = "mydb.mycoll".toNs()
        assertEquals("mydb", namespace.database)
        assertEquals("mycoll", namespace.collection)
    }

    @Test
    fun parses_a_namespace_where_collections_have_dots_in_a_name() {
        val namespace = "mydb.myco.ll".toNs()
        assertEquals("mydb", namespace.database)
        assertEquals("myco.ll", namespace.collection)
    }

    @Test
    fun removes_trailing_spaces() {
        val namespace = """ mydb.myco"ll    """.toNs()
        assertEquals("mydb", namespace.database)
        assertEquals("myco\"ll", namespace.collection)
    }

    @Test
    fun can_parse_back_a_serialised_namespace() {
        val namespace = Namespace("mydb", "my.cool.col")
        val deserialized = namespace.toString().toNs()

        assertEquals(namespace, deserialized)
    }
}
