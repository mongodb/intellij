package com.mongodb.jbplugin.mql

import com.mongodb.jbplugin.mql.components.*
import com.mongodb.jbplugin.mql.components.HasCollectionReference.Known
import io.github.z4kn4fein.semver.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeTest {
    @Test
    fun is_able_to_get_a_component_if_exists() {
        val node = Node<Unit?>(null, listOf(Named(Name.EQ)))
        val named = node.component<Named>()

        assertEquals(Name.EQ, named!!.name)
    }

    @Test
    fun returns_null_if_a_component_does_not_exist() {
        val node = Node<Unit?>(null, listOf())
        val named = node.component<HasFieldReference<Unit?>>()

        assertNull(named)
    }

    @Test
    fun is_able_to_get_all_components_of_the_same_type() {
        val node =
            Node<Unit?>(
                null,
                listOf(
                    HasFieldReference(HasFieldReference.FromSchema(null, "field1")),
                    HasFieldReference(
                        HasFieldReference.FromSchema(
                            null,
                            "field2",
                        ),
                    ),
                ),
            )
        val fieldReferences = node.components<HasFieldReference<Unit?>>()

        assertEquals(
            "field1",
            (fieldReferences[0].reference as HasFieldReference.FromSchema).fieldName
        )
        assertEquals(
            "field2",
            (fieldReferences[1].reference as HasFieldReference.FromSchema).fieldName
        )
    }

    @Test
    fun returns_true_if_a_component_of_that_type_exists() {
        val node =
            Node<Unit?>(
                null,
                listOf(HasFieldReference(HasFieldReference.FromSchema(null, "field1"))),
            )
        val hasFieldReferences = node.hasComponent<HasFieldReference<Unit?>>()

        assertTrue(hasFieldReferences)
    }

    @Test
    fun returns_false_if_a_component_of_that_type_does_not_exist() {
        val node =
            Node<Unit?>(
                null,
                listOf(HasFieldReference(HasFieldReference.FromSchema(null, "field1"))),
            )
        val hasNamedComponent = node.hasComponent<Named>()

        assertFalse(hasNamedComponent)
    }

    @Test
    fun it_copies_the_Node_by_correctly_mapping_the_underlying_components() {
        val node = Node<Unit?>(
            null,
            listOf(
                HasCollectionReference(HasCollectionReference.OnlyCollection(1, "qwerty")),
                HasCollectionReference(HasCollectionReference.OnlyCollection(1, "qwerty")),
            )
        )

        val copiedNode = node.copy {
            HasCollectionReference(
                HasCollectionReference.Unknown
            )
        }

        // Does not modify the original node
        assertTrue(
            node.components<HasCollectionReference<*>>()
                .all { collection ->
                    collection.reference is HasCollectionReference.OnlyCollection
                }
        )

        // creates a copy with the modified components as per our logic
        assertTrue(
            copiedNode.components<HasCollectionReference<*>>()
                .all { collection -> collection.reference is HasCollectionReference.Unknown }
        )
    }

    @Test
    fun it_creates_a_copy_of_the_query_with_overwritten_database_in_the_components() {
        val node = Node<Unit?>(
            null,
            listOf(
                HasCollectionReference(HasCollectionReference.OnlyCollection(1, "qwerty")),
            )
        )

        val modifiedNode = node.queryWithOverwrittenDatabase("foo")
        val nodeReference = modifiedNode.component<HasCollectionReference<*>>()
        // Does not modify the original node
        assertTrue(
            node.component<HasCollectionReference<*>>()?.let {
                it.reference is HasCollectionReference.OnlyCollection
            } == true
        )

        assertTrue(
            nodeReference
                ?.let {
                    it.reference is Known
                } == true
        )
        assertEquals(
            "foo",
            (nodeReference.reference as Known).namespace.database,
        )
    }

    @Test
    fun adds_target_cluster_if_does_not_exist() {
        val targetCluster = HasTargetCluster(Version.parse("7.0.0"))
        val query = Node(Unit, emptyList()).withTargetCluster(targetCluster)

        assertEquals(targetCluster, query.component<HasTargetCluster>())
    }

    @Test
    fun removes_old_target_cluster_and_adds_a_new_one() {
        val oldCluster = HasTargetCluster(Version.parse("5.0.0"))
        val targetCluster = HasTargetCluster(Version.parse("7.0.0"))
        val query = Node(Unit, listOf(oldCluster)).withTargetCluster(targetCluster)

        assertEquals(targetCluster, query.component<HasTargetCluster>())
    }

    @Test
    fun on_calling_queryWithInjectedCollectionSchema_it_returns_a_Node_with_injected_CollectionSchema_if_CollectionReference_is_Known() {
        val node = Node<Unit?>(
            null,
            listOf(
                HasCollectionReference(
                    Known(
                        1,
                        2,
                        Namespace("db", "coll"),
                        null,
                    )
                ),
            )
        )

        val schema = CollectionSchema(
            Namespace("db", "coll"),
            BsonObject(emptyMap()),
        )
        val modifiedNode = node.queryWithInjectedCollectionSchema(schema)
        val nodeReference = modifiedNode.component<HasCollectionReference<*>>()
        assertEquals(schema, (nodeReference?.reference as? Known<*>)?.schema)
    }
}
