package com.mongodb.jbplugin.mql

import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasCollectionReference.Known
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasTargetCluster
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.IsCommand.CommandType
import com.mongodb.jbplugin.mql.components.IsCommand.CommandType.FIND_ONE
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import io.github.z4kn4fein.semver.Version
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class NodeTest {
    @Test
    fun `is able to get a component if exists`() {
        val node = Node<Unit?>(null, listOf(Named(Name.EQ)))
        val named = node.component<Named>()

        assertEquals(Name.EQ, named!!.name)
    }

    @Test
    fun `returns null if a component does not exist`() {
        val node = Node<Unit?>(null, listOf())
        val named = node.component<HasFieldReference<Unit?>>()

        assertNull(named)
    }

    @Test
    fun `is able to get all components of the same type`() {
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
    fun `returns true if a component of that type exists`() {
        val node =
            Node<Unit?>(
                null,
                listOf(HasFieldReference(HasFieldReference.FromSchema(null, "field1"))),
            )
        val hasFieldReferences = node.hasComponent<HasFieldReference<Unit?>>()

        assertTrue(hasFieldReferences)
    }

    @Test
    fun `returns false if a component of that type does not exist`() {
        val node =
            Node<Unit?>(
                null,
                listOf(HasFieldReference(HasFieldReference.FromSchema(null, "field1"))),
            )
        val hasNamedComponent = node.hasComponent<Named>()

        assertFalse(hasNamedComponent)
    }

    @Test
    fun `it copies the Node by correctly mapping the underlying components`() {
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
    fun `two queries with the same field names will generate the same queryHash`() {
        val q1 = Node<Unit?>(
            null,
            listOf(
                HasFieldReference(HasFieldReference.FromSchema(Unit, "f1")),
            )
        )

        val q2 = Node<Unit?>(
            null,
            listOf(
                HasFieldReference(HasFieldReference.FromSchema(Unit, "f1")),
            )
        )

        assertEquals(q1.queryHash(), q2.queryHash())
    }

    @Test
    fun `two queries with different field names will generate different queryHash`() {
        val q1 = Node<Unit?>(
            null,
            listOf(
                HasFieldReference(HasFieldReference.FromSchema(Unit, "f1")),
            )
        )

        val q2 = Node<Unit?>(
            null,
            listOf(
                HasFieldReference(HasFieldReference.FromSchema(Unit, "f2")),
            )
        )

        assertNotEquals(q1.queryHash(), q2.queryHash())
    }

    @Test
    fun `it creates a copy of the query with overwritten database in the components`() {
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
            }
                ?: false
        )

        assertTrue(
            nodeReference
                ?.let {
                    it.reference is HasCollectionReference.Known
                }
                ?: false
        )
        assertEquals(
            "foo",
            (nodeReference?.reference as HasCollectionReference.Known).namespace.database,
        )
    }

    @Test
    fun `adds target cluster if does not exist`() {
        val targetCluster = HasTargetCluster(Version.parse("7.0.0"))
        val query = Node(Unit, emptyList()).withTargetCluster(targetCluster)

        assertEquals(targetCluster, query.component<HasTargetCluster>())
    }

    @Test
    fun `removes old target cluster and adds a new one`() {
        val oldCluster = HasTargetCluster(Version.parse("5.0.0"))
        val targetCluster = HasTargetCluster(Version.parse("7.0.0"))
        val query = Node(Unit, listOf(oldCluster)).withTargetCluster(targetCluster)

        assertEquals(targetCluster, query.component<HasTargetCluster>())
    }

    @Test
    fun `on calling queryWithInjectedCollectionSchema it returns a Node with injected CollectionSchema if CollectionReference is Known`() {
        val node = Node<Unit?>(
            null,
            listOf(
                HasCollectionReference(
                    HasCollectionReference.Known(
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

    @ParameterizedTest
    @MethodSource("allCommands")
    fun `a query is supported if the command is supported`(cmd: CommandType, supported: Boolean) {
        val query = Node(Unit, listOf(IsCommand(cmd)))
        val isSupported = runBlocking { query.isSupported() }
        assertEquals(supported, isSupported)
    }

    @ParameterizedTest
    @MethodSource("allOperations")
    fun `a query is supported if fields contain supported operators`(name: Name, supported: Boolean) {
        val query = Node(
            Unit,
            listOf(
                IsCommand(FIND_ONE),
                HasFilter(
                    listOf(
                        Node(
                            Unit,
                            listOf(
                                Named(name),
                                HasFieldReference(HasFieldReference.FromSchema(Unit, "f1")),
                                HasValueReference(HasValueReference.Constant(Unit, "f1", BsonString)),
                            )
                        )
                    )
                )
            )
        )

        val isSupported = runBlocking { query.isSupported() }
        assertEquals(supported, isSupported)
    }

    @ParameterizedTest
    @MethodSource("allOperations")
    fun `a query is unsupported if fields contains an unsupported operator`(name: Name) {
        val query = Node(
            Unit,
            listOf(
                IsCommand(FIND_ONE),
                HasFilter(
                    listOf(
                        Node(
                            Unit,
                            listOf(
                                Named(name),
                                HasFieldReference(HasFieldReference.FromSchema(Unit, "f1")),
                                HasValueReference(HasValueReference.Constant(Unit, "f1", BsonString)),
                            )
                        ),
                        Node(
                            Unit,
                            listOf(
                                Named(Name.UNKNOWN),
                                HasFieldReference(HasFieldReference.FromSchema(Unit, "f2")),
                                HasValueReference(HasValueReference.Constant(Unit, "f2", BsonString)),
                            )
                        )
                    )
                )
            )
        )

        val isSupported = runBlocking { query.isSupported() }
        assertFalse(isSupported)
    }

    companion object {
        @JvmStatic
        fun allCommands() = CommandType.entries.map { arrayOf(it, it.isSupported) }.toTypedArray()

        @JvmStatic
        fun allOperations() = Name.entries.map { arrayOf(it, it.isSupported) }.toTypedArray()
    }
}
