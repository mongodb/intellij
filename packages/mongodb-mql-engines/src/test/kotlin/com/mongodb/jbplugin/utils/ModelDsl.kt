package com.mongodb.jbplugin.utils

import com.mongodb.jbplugin.accessadapter.toNs
import com.mongodb.jbplugin.indexing.IndexAnalyzer
import com.mongodb.jbplugin.indexing.IndexAnalyzer.SortDirection
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Component
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasLimit
import com.mongodb.jbplugin.mql.components.HasProjections
import com.mongodb.jbplugin.mql.components.HasSorts
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Name.AND
import com.mongodb.jbplugin.mql.components.Named
import com.mongodb.jbplugin.mql.toBsonType

class ComponentHolder(internal val components: MutableList<Component>)
class NodeHolder(internal val nodes: MutableList<Node<Unit>>)

object ModelDsl {
    fun findMany(
        ns: Namespace,
        schema: CollectionSchema? = null,
        configuration: ComponentHolder.() -> Unit
    ): Node<Unit> {
        val node = Node(
            Unit,
            listOf(
                HasCollectionReference(HasCollectionReference.Known(Unit, Unit, ns, schema)),
                HasLimit(50)
            )
        )

        val holder = ComponentHolder(mutableListOf())
        configuration(holder)
        return node.copy(components = node.components + holder.components)
    }

    fun NodeHolder.query(supplier: () -> Node<Unit>) {
        nodes.add(supplier())
    }

    fun aggregate(ns: Namespace, schema: CollectionSchema? = null, configuration: NodeHolder.() -> Unit): Node<Unit> {
        val node = Node(
            Unit,
            listOf(
                HasCollectionReference(HasCollectionReference.Known(Unit, Unit, ns, schema)),
                HasLimit(50)
            )
        )

        val holder = NodeHolder(mutableListOf())
        configuration(holder)
        return node.copy(components = node.components + HasAggregation(holder.nodes))
    }

    fun ComponentHolder.filterBy(supplier: NodeHolder.() -> Unit) {
        val nodeHolder = NodeHolder(mutableListOf())
        supplier(nodeHolder)
        components.add(HasFilter(nodeHolder.nodes))
    }

    fun NodeHolder.match(supplier: NodeHolder.() -> Unit) {
        val matchNode = Node(Unit, listOf(Named(Name.MATCH)))
        val nodeHolder = NodeHolder(mutableListOf())
        supplier(nodeHolder)

        nodes.add(matchNode.copy(components = matchNode.components + HasFilter(nodeHolder.nodes)))
    }

    fun NodeHolder.project(supplier: NodeHolder.() -> Unit) {
        val projectNode = Node(Unit, listOf(Named(Name.PROJECT)))
        val nodeHolder = NodeHolder(mutableListOf())
        supplier(nodeHolder)

        nodes.add(
            projectNode.copy(
                components =
                projectNode.components + HasProjections(nodeHolder.nodes)
            )
        )
    }

    fun ComponentHolder.sortBy(supplier: NodeHolder.() -> Unit) {
        val nodeHolder = NodeHolder(mutableListOf())
        supplier(nodeHolder)
        components.add(HasSorts(nodeHolder.nodes))
    }

    fun NodeHolder.predicate(name: Name, predicate: ComponentHolder.() -> Unit) {
        val componentHolder = ComponentHolder(mutableListOf())
        predicate(componentHolder)

        nodes.add(
            Node(
                Unit,
                listOf(
                    Named(name),
                ) + componentHolder.components
            )
        )
    }

    fun ComponentHolder.schema(name: String) {
        components.add(
            HasFieldReference(
                HasFieldReference.FromSchema(Unit, name)
            )
        )
    }

    fun <T : Any> ComponentHolder.constant(value: T) {
        components.add(
            HasValueReference(
                HasValueReference.Constant(Unit, value, value.javaClass.toBsonType(value))
            )
        )
    }

    fun ComponentHolder.runtime(type: BsonType) {
        components.add(
            HasValueReference(
                HasValueReference.Runtime(Unit, type)
            )
        )
    }

    fun NodeHolder.ascending(rule: ComponentHolder.() -> Unit) {
        val componentHolder = ComponentHolder(mutableListOf())
        rule(componentHolder)

        val ascendingOp = Named(Name.ASCENDING)
        val inferredSortValue = HasValueReference(HasValueReference.Inferred(Unit, 1, BsonInt32))
        nodes.add(Node(Unit, componentHolder.components + ascendingOp + inferredSortValue))
    }

    fun NodeHolder.descending(rule: ComponentHolder.() -> Unit) {
        val componentHolder = ComponentHolder(mutableListOf())
        rule(componentHolder)

        val descendingOp = Named(Name.DESCENDING)
        val inferredSortValue = HasValueReference(HasValueReference.Inferred(Unit, -1, BsonInt32))
        nodes.add(Node(Unit, componentHolder.components + descendingOp + inferredSortValue))
    }

    fun NodeHolder.include(rule: ComponentHolder.() -> Unit) {
        val componentHolder = ComponentHolder(mutableListOf())
        rule(componentHolder)

        val includeOp = Named(Name.INCLUDE)
        val inferredSortValue = HasValueReference(HasValueReference.Inferred(Unit, 1, BsonInt32))
        nodes.add(Node(Unit, componentHolder.components + includeOp + inferredSortValue))
    }

    fun indexOf(
        vararg fields: Pair<String, Int>,
        coveredQueries: NodeHolder.() -> Unit = {
        },
    ): IndexAnalyzer.SuggestedIndex.MongoDbIndex<Unit> {
        val nodeHolder = NodeHolder(mutableListOf())
        coveredQueries(nodeHolder)

        return IndexAnalyzer.SuggestedIndex.MongoDbIndex(
            HasCollectionReference(
                HasCollectionReference.Known(Unit, Unit, "myDb.myColl".toNs(), null)
            ),
            fields.map {
                IndexAnalyzer.SuggestedIndex.MongoDbIndexField(
                    fieldName = it.first,
                    source = Unit,
                    direction = if (it.second == -1) {
                        SortDirection.Descending
                    } else {
                        SortDirection.Ascending
                    },
                    reason = IndexAnalyzer.IndexSuggestionFieldReason.RoleEquality
                )
            },
            coveredQueries = nodeHolder.nodes,
            partialFilterExpression = null
        )
    }

    fun IndexAnalyzer.SuggestedIndex.MongoDbIndex<Unit>.withPartialExpression(expression: NodeHolder.() -> Unit): IndexAnalyzer.SuggestedIndex.MongoDbIndex<Unit> {
        return copy(partialFilterExpression = partialFilterExpression(expression))
    }

    fun partialFilterExpression(expression: NodeHolder.() -> Unit): Node<Unit> {
        val nodeHolder = NodeHolder(mutableListOf())
        expression(nodeHolder)

        if (nodeHolder.nodes.isEmpty()) {
            throw AssertionError("Expression can not be empty.")
        }

        return if (nodeHolder.nodes.size == 1) {
            nodeHolder.nodes.first()
        } else {
            Node(Unit, listOf(Named(AND), HasFilter(nodeHolder.nodes)))
        }
    }
}
