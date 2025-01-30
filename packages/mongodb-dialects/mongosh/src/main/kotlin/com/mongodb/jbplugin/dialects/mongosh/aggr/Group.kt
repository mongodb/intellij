package com.mongodb.jbplugin.dialects.mongosh.aggr

import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.dialects.mongosh.query.resolveFieldReference
import com.mongodb.jbplugin.dialects.mongosh.query.resolveValueReference
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasAccumulatedFields
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasLimit
import com.mongodb.jbplugin.mql.components.HasSorts
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named

internal fun <S>MongoshBackend.emitGroupStage(node: Node<S>): MongoshBackend {
    val idFieldReference = node.component<HasFieldReference<S>>()
    val idValueReference = node.component<HasValueReference<S>>()

    val accumulatedFields = node.component<HasAccumulatedFields<S>>()?.children ?: emptyList()
    val emitLongQuery = idValueReference.isLongIdValueReference() || accumulatedFields.size > 2

    // "{"
    emitObjectStart(long = emitLongQuery)
    // "{ $group : "
    emitObjectKey(registerConstant('$' + "group"))
    // "{ $group : { "
    emitObjectStart(long = emitLongQuery)
    if (idFieldReference != null && idValueReference != null) {
        // "{ $group : { _id : null, "
        emitAsFieldValueDocument(listOf(node), isLong = emitLongQuery)
        // "{ $group : { _id : null, totalCount: { $sum : 1 }, "
        emitAccumulatedFields(accumulatedFields, emitLongQuery)
    }
    // "{ $group : { _id : null, totalCount: { $sum : 1 }, }"
    emitObjectEnd(long = emitLongQuery)
    // "{ $group : { _id : null, totalCount: { $sum : 1 }, } }"
    emitObjectEnd(long = emitLongQuery)

    return this
}

private fun <S>HasValueReference<S>?.isLongIdValueReference(): Boolean {
    val computedReference = this?.reference as? HasValueReference.Computed<S>
        ?: return false
    val fieldReferences = computedReference.type.expression.components<HasFieldReference<S>>()
    return fieldReferences.size >= 3
}

private fun <S>MongoshBackend.emitAccumulatedFields(
    accumulatedFields: List<Node<S>>,
    emitLongQuery: Boolean
): MongoshBackend {
    for (accumulatedField in accumulatedFields) {
        val accumulator = accumulatedField.component<Named>() ?: continue
        when (accumulator.name) {
            Name.SUM,
            Name.AVG,
            Name.MIN,
            Name.MAX,
            Name.FIRST,
            Name.LAST,
            Name.PUSH,
            Name.ADD_TO_SET -> {
                emitKeyValueAccumulator(
                    accumulator,
                    accumulatedField,
                    emitLongQuery
                )
                // "{ $group : { _id : null, totalCount : { $sum : 1 }
                emitObjectValueEnd(long = emitLongQuery)
            }
            Name.TOP,
            Name.TOP_N,
            Name.BOTTOM,
            Name.BOTTOM_N -> {
                emitTopBottomAccumulator(
                    accumulator,
                    accumulatedField,
                    emitLongQuery
                )
                // "{ $group : { _id : null, totalCount : { $top : { sortBy: { year: -1 }, "title" } }
                emitObjectValueEnd(long = emitLongQuery)
            }
            else -> continue
        }
    }
    return this
}

/**
 * Emits for the following accumulators
 * sum, avg, first, last, max, min, push, addToSet
 */
private fun <S>MongoshBackend.emitKeyValueAccumulator(
    accumulator: Named,
    accumulatedField: Node<S>,
    emitLongQuery: Boolean,
): MongoshBackend {
    val fieldRef = accumulatedField.component<HasFieldReference<S>>() ?: return this
    val valueRef = accumulatedField.component<HasValueReference<S>>() ?: return this

    // "{ $group : { _id : null, totalCount :
    emitObjectKey(
        resolveFieldReference(
            fieldRef = fieldRef,
            fieldUsedAsValue = false,
        )
    )
    // "{ $group : { _id : null, totalCount : {
    emitObjectStart(long = emitLongQuery)
    // "{ $group : { _id : null, totalCount : { $sum :
    emitObjectKey(registerConstant('$' + accumulator.name.canonical))
    // "{ $group : { _id : null, totalCount : { $sum : 1
    emitContextValue(resolveValueReference(valueRef, fieldRef))
    // "{ $group : { _id : null, totalCount : { $sum : 1 }
    emitObjectEnd(long = emitLongQuery)
    return this
}

private fun <S>MongoshBackend.emitTopBottomAccumulator(
    accumulator: Named,
    accumulatedField: Node<S>,
    emitLongQuery: Boolean,
): MongoshBackend {
    val fieldRef = accumulatedField.component<HasFieldReference<S>>() ?: return this
    val valueRef = accumulatedField.component<HasValueReference<S>>() ?: return this
    val sorts = accumulatedField.component<HasSorts<S>>()?.children ?: emptyList()
    val limit = accumulatedField.component<HasLimit>()?.limit
    emitObjectKey(
        resolveFieldReference(
            fieldRef = fieldRef,
            fieldUsedAsValue = false,
        )
    )
    // "{"
    emitObjectStart(long = emitLongQuery)
    // "{ $top : "
    emitObjectKey(registerConstant('$' + accumulator.name.canonical))
    // "{ $top : {"
    emitObjectStart(long = emitLongQuery)
    // "{ $top : { "sortBy" : "
    emitObjectKey(registerConstant("sortBy"))
    // "{ $top : { "sortBy" : { "
    emitObjectStart(long = emitLongQuery)
    // "{ $top : { "sortBy" : { "field" : 1,"
    emitAsFieldValueDocument(sorts, emitLongQuery)
    // "{ $top : { "sortBy" : { "field" : 1, }"
    emitObjectEnd(long = emitLongQuery)
    // "{ $top : { "sortBy" : { "field" : 1, }, "
    emitObjectValueEnd(long = emitLongQuery)

    // "{ $top : { "sortBy" : { "field" : 1, }, output : "
    emitObjectKey(registerConstant("output"))
    // "{ $top : { "sortBy" : { "field" : 1, }, output : "$someField""
    emitContextValue(resolveValueReference(valueRef, fieldRef))
    // "{ $top : { "sortBy" : { "field" : 1, }, output : "$someField", "
    emitObjectValueEnd(long = emitLongQuery)

    if (limit != null) {
        // "{ $top : { "sortBy" : { "field" : 1, }, output : "$someField", n : "
        emitObjectKey(registerConstant("n"))
        // "{ $top : { "sortBy" : { "field" : 1, }, output : "$someField", n : 3"
        emitContextValue(registerConstant(limit))
        // "{ $top : { "sortBy" : { "field" : 1, }, output : "$someField", n : 3, "
        emitObjectValueEnd(long = emitLongQuery)
    }

    // "{ $top : { "sortBy" : { "field" : 1, }, output : "$someField", } " or
    // "{ $top : { "sortBy" : { "field" : 1, }, output : "$someField", n : 3, }"
    emitObjectEnd(long = emitLongQuery)

    // "{ $top : { "sortBy" : { "field" : 1, }, output : "$someField", } }" or
    // "{ $top : { "sortBy" : { "field" : 1, }, output : "$someField", n : 3, } }"
    emitObjectEnd(long = emitLongQuery)
    return this
}
