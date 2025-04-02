/**
 * Node and components are the main building blocks of the query model.
 */

package com.mongodb.jbplugin.mql

import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasTargetCluster
import com.mongodb.jbplugin.mql.parser.components.allNodesWithSchemaFieldReferences
import com.mongodb.jbplugin.mql.parser.filterNotNullMany
import com.mongodb.jbplugin.mql.parser.mapMany
import com.mongodb.jbplugin.mql.parser.parse
import kotlinx.coroutines.runBlocking

/** A component represents the semantics of a Node. When a Node has some special meaning, we will attach a component
 * that adds that specific meaning. For example, take into consideration the following Java Query:
 * ```java
 * Filters.eq("myField", 42)
 * ```
 * This query contains three semantic units:
 * * Named: The operation that is executing has a name.
 * * HasFieldReference: It refers to a field in a document.
 * * HasValueReference: It refers to a value in code.
 *
 * @see Node
 * @see com.mongodb.jbplugin.mql.components.Named
 * @see com.mongodb.jbplugin.mql.components.HasFieldReference
 * @see com.mongodb.jbplugin.mql.components.HasValueReference
 */
interface Component

/**
 * HasChildren component encapsulates the idea that a Component has children. For example:
 * ```java
 * Filters.and(Filters.eq("year", 1994), Filters.eq("name", "something"))
 * ```
 * The above query has a filter that have nested filters within it hence the Node that represents
 * `Filters.and` implements `HasChildren` interface.
 */
interface HasChildren<S> : Component {
    val children: List<Node<S>>
}

/**
 * Represents the building block of a query in this model. Nodes don't have any semantic per se, but they can
 * hold Components that will give them specific meaning.
 *
 * @see Component
 *
 * @param S
 * @property source
 * @property components
 */
data class Node<S>(
    val source: S,
    val components: List<Component>,
) {
    inline fun <reified C : Component> component(): C? = components.firstOrNull { it is C } as C?

    fun <C : Component> component(withClass: Class<C>): C? = components.firstOrNull {
        withClass.isInstance(it)
    } as C?

    inline fun <reified C : Component> components(): List<C> = components.filterIsInstance<C>()

    fun with(component: Component): Node<S> {
        return copy(components = components + component)
    }

    fun componentsWithChildren(): List<HasChildren<S>> = components.filterIsInstance<HasChildren<S>>()

    inline fun <reified C : Component> hasComponent(): Boolean = component<C>() != null

    fun withTargetCluster(cluster: HasTargetCluster): Node<S> = copy(
        source = source,
        components =
        components.filter {
            it !is HasTargetCluster
        } + cluster
    )

    fun queryWithInjectedCollectionSchema(collectionSchema: CollectionSchema) = copy { component ->
        if (component is HasCollectionReference<*>) {
            component.copy(collectionSchema = collectionSchema)
        } else {
            component
        }
    }

    /**
     * Creates a copy of the query and modifies the database reference in every HasCollectionReference component
     * with the provided database
     *
     * @param database
     * @return
     */
    fun queryWithOverwrittenDatabase(database: String) = this.copy { component ->
        if (component is HasCollectionReference<*>) {
            component.copy(database = database)
        } else {
            component
        }
    }

    /**
     * Creates a copy of the Node by modifying the underlying component list
     *
     * @param componentModifier A mapper function that is provided with a component (one at a time) from the Node's
     * component list and is expected to either provide a modified component or the same component. The return value of
     * this function is used to create a new component list for the copied Node
     */
    fun copy(componentModifier: (component: Component) -> Component): Node<S> =
        copy(source = source, components = components.map(componentModifier))

    override fun toString(): String {
        return "Node(components=$components)"
    }

    fun queryHash(): Int {
        val allFieldNamesParser = allNodesWithSchemaFieldReferences<S>()
            .mapMany { Either.right<Any, HasFieldReference.FieldReference<S>?>(it.component<HasFieldReference<S>>()?.reference) }
            .mapMany {
                Either.right<Any, String?>(
                    when (it) {
                        is HasFieldReference.FromSchema -> it.fieldName
                        else -> null
                    }
                )
            }
            .filterNotNullMany()

        val allFieldNames = runBlocking {
            allFieldNamesParser.parse(this@Node).orElse { emptyList() }
        }

        return allFieldNames.hashCode()
    }
}

/**
 * A data holder for the information required to run a query. To be used alongside with
 * a DialectFormatter.
 */
data class QueryContext(
    val expansions: Map<String, LocalVariable>,
    val prettyPrint: Boolean,
    // This boolean dictates whether the query to which this context will be attached is supposed
    // to be run automatically or not. A query will run automatically where there is no user
    // interactions involved like - sampling, explaining a query for indexes, etc.
    val automaticallyRun: Boolean,
) {
    data class AsIs(val value: String) {
        val isEmpty = value.isBlank()
    }

    data class LocalVariable(val type: BsonType, val defaultValue: Any?)

    fun willAutomaticallyRun() = copy(automaticallyRun = true)

    companion object {
        fun empty(prettyPrint: Boolean = false, automaticallyRun: Boolean = false): QueryContext =
            QueryContext(emptyMap(), prettyPrint, automaticallyRun)
    }
}
