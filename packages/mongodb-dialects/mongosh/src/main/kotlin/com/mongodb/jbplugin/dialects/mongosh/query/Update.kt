package com.mongodb.jbplugin.dialects.mongosh.query

import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasUpdates
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named

suspend fun <S> MongoshBackend.emitQueryUpdate(node: Node<S>): MongoshBackend {
    val hasUpdates = node.component<HasUpdates<S>>() ?: return this
    val allUpdates = hasUpdates.children.flatMap { it.recursivelyCollectAllUpdates() }
    val groupedUpdates = groupUpdatesByOperator(allUpdates)

    emitObjectStart(long = true)
    for (it in groupedUpdates) {
        emitEachQueryUpdate(it)
        emitObjectValueEnd()
    }
    emitObjectEnd(long = true)
    return this
}

private fun <S> Node<S>.recursivelyCollectAllUpdates(): List<Node<S>> {
    val hasUpdates = component<HasUpdates<S>>()
    if (hasUpdates != null) {
        return hasUpdates.children.flatMap { it.recursivelyCollectAllUpdates() }
    }

    return listOf(this)
}

private fun <S> groupUpdatesByOperator(updates: List<Node<S>>): Map<Name, List<Node<S>>> {
    return updates.groupBy { it.component<Named>()?.name }
        .filter { it.key != null } as Map<Name, List<Node<S>>>
}

private suspend fun <S> MongoshBackend.emitEachQueryUpdate(node: Map.Entry<Name, List<Node<S>>>): MongoshBackend {
    val name = node.key

    emitObjectKey(registerConstant("${'$'}${name.canonical}"))
    emitObjectStart(long = true)
    when (name) {
        Name.PULL -> {
            for (pullNode in node.value) {
                val fieldName = pullNode.component<HasFieldReference<S>>() ?: continue
                val fieldValue = pullNode.component<HasValueReference<S>>()
                val filter = pullNode.component<HasFilter<S>>()

                if (fieldValue != null) {
                    emitObjectKey(
                        resolveFieldReference(
                            fieldRef = fieldName,
                            fieldUsedAsValue = false,
                        )
                    )
                    emitContextValue(resolveValueReference(fieldValue, fieldName))
                } else if (filter != null && filter.children.isNotEmpty()) {
                    emitObjectKey(
                        resolveFieldReference(
                            fieldRef = fieldName,
                            fieldUsedAsValue = false,
                        )
                    )
                    emitQueryFilter(filter.children[0], firstCall = true)
                }

                emitObjectValueEnd()
            }
        }
        else -> {
            for (updateNode in node.value) {
                val fieldName = updateNode.component<HasFieldReference<S>>() ?: continue
                val fieldValue = updateNode.component<HasValueReference<S>>() ?: continue

                emitObjectKey(
                    resolveFieldReference(
                        fieldRef = fieldName,
                        fieldUsedAsValue = false,
                    )
                )
                emitContextValue(resolveValueReference(fieldValue, fieldName))
                emitObjectValueEnd()
            }
        }
    }
    emitObjectEnd(long = true)
    return this
}
