package com.mongodb.jbplugin.dialects.mongosh.query

import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasSorts
import com.mongodb.jbplugin.mql.components.HasValueReference

fun <S> MongoshBackend.emitSort(query: Node<S>): MongoshBackend {
    val sortComponent = query.component<HasSorts<S>>()
    if (sortComponent == null) {
        return this
    }

    fun generateSortKeyVal(node: Node<S>): MongoshBackend {
        val fieldRef = node.component<HasFieldReference<S>>() ?: return this
        val valueRef = node.component<HasValueReference<S>>() ?: return this

        emitObjectKey(
            resolveFieldReference(
                fieldRef = fieldRef,
                fieldUsedAsValue = false,
            )
        )
        emitContextValue(resolveValueReference(valueRef, fieldRef))
        return emitObjectValueEnd()
    }

    emitPropertyAccess()
    emitFunctionName("sort")
    return emitFunctionCall(long = false, {
        emitObjectStart(long = false)
        for (sortCriteria in sortComponent.children) {
            generateSortKeyVal(sortCriteria)
        }
        emitObjectEnd(long = false)
    })
}
