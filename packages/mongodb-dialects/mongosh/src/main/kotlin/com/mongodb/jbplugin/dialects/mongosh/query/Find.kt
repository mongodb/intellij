package com.mongodb.jbplugin.dialects.mongosh.query

import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.mql.Component
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import com.mongodb.jbplugin.mql.parser.components.allFiltersRecursively
import com.mongodb.jbplugin.mql.parser.parse

suspend fun <S> MongoshBackend.emitQueryFilter(node: Node<S>, firstCall: Boolean = false): MongoshBackend {
    val named = node.component<Named>()
    val fieldRef = node.component<HasFieldReference<S>>()
    val valueRef = node.component<HasValueReference<S>>()
    val hasFilter = node.component<HasFilter<S>>()
    val isLong = allFiltersRecursively<S>().parse(node).orElse { emptyList() }.size > 3

    if (firstCall && hasFilter == null && fieldRef == null && valueRef == null) {
        emitObjectStart()
        emitObjectEnd()
        return this
    }

    if (hasFilter != null && fieldRef == null && valueRef == null && named == null) {
        // 1. has children, nothing else (root node)
        if (firstCall) {
            emitObjectStart(long = isLong)
        }

        hasFilter.children.forEach {
            emitQueryFilter(it)
            emitObjectValueEnd()
        }
        if (firstCall) {
            emitObjectEnd(long = isLong)
        }
    } else if (hasFilter == null && fieldRef != null && valueRef != null && named == null) {
        // 2. no children, only a field: value case
        if (firstCall) {
            emitObjectStart(long = isLong)
        }
        emitObjectKey(
            resolveFieldReference(
                fieldRef = fieldRef,
                fieldUsedAsValue = false,
            )
        )
        emitContextValue(resolveValueReference(valueRef, fieldRef))
        if (firstCall) {
            emitObjectEnd(long = isLong)
        }
    } else {
        named?.let {
// 3. children and named
            if (named.name == Name.EQ) {
// normal a: b case
                if (firstCall) {
                    emitObjectStart(long = isLong)
                }
                if (fieldRef != null) {
                    emitObjectKey(
                        resolveFieldReference(
                            fieldRef = fieldRef,
                            fieldUsedAsValue = false,
                        )
                    )
                }

                if (valueRef != null) {
                    emitContextValue(resolveValueReference(valueRef, fieldRef))
                }

                hasFilter?.children?.forEach {
                    emitQueryFilter(it)
                    emitObjectValueEnd()
                }

                if (firstCall) {
                    emitObjectEnd(long = isLong)
                }
            } else if (setOf( // 1st basic attempt, to improve in INTELLIJ-76
                    Name.GT,
                    Name.GTE,
                    Name.LT,
                    Name.LTE
                ).contains(named.name) &&
                valueRef != null
            ) {
// a: { $gt: 1 }
                if (firstCall) {
                    emitObjectStart(long = isLong)
                }

                if (fieldRef != null) {
                    emitObjectKey(
                        resolveFieldReference(
                            fieldRef = fieldRef,
                            fieldUsedAsValue = false,
                        )
                    )
                }

                emitObjectStart()
                emitObjectKey(registerConstant('$' + named.name.canonical))
                emitContextValue(resolveValueReference(valueRef, fieldRef))
                emitObjectEnd()

                if (firstCall) {
                    emitObjectEnd(long = isLong)
                }
            } else if (setOf(
                    // 1st basic attempt, to improve in INTELLIJ-77
                    Name.AND,
                    Name.OR,
                    Name.NOR,
                ).contains(named.name)
            ) {
                if (firstCall) {
                    emitObjectStart()
                }
                emitObjectKey(registerConstant('$' + named.name.canonical))
                emitArrayStart(long = true)
                hasFilter?.children?.forEach {
                    emitObjectStart()
                    emitQueryFilter(it)
                    emitObjectEnd()
                    emitObjectValueEnd()
                    if (prettyPrint) {
                        emitNewLine()
                    }
                }
                emitArrayEnd(long = true)
                if (firstCall) {
                    emitObjectEnd()
                }
            } else if (named.name == Name.NOT && hasFilter?.children?.size == 1) {
                // the not operator is a special case
                // because we receive it as:
                // $not: { $field$: $condition$ }
                // and it needs to be:
                // $field$: { $not: $condition$ }
                // we will do a JIT translation

                val innerChild = hasFilter.children.first()
                val operation = innerChild.component<Named>()
                val fieldRef = innerChild.component<HasFieldReference<S>>()
                val valueRef = innerChild.component<HasValueReference<S>>()

                if (fieldRef == null) { // we are in an "and" / "or"...
                    // so we use $nor instead
                    emitQueryFilter(
                        Node(
                            node.source,
                            node.components<Component>().filterNot { it is Named } + Named(Name.NOR)
                        )
                    )
                    return@let
                }

                if (operation == null && valueRef == null) {
                    return@let
                }

                if (firstCall) {
                    emitObjectStart()
                }

                // emit field name first
                emitObjectKey(
                    resolveFieldReference(
                        fieldRef = fieldRef,
                        fieldUsedAsValue = false,
                    )
                )
                // emit the $not
                emitObjectStart()
                emitObjectKey(registerConstant('$' + "not"))
                emitQueryFilter(
                    Node(
                        innerChild.source,
                        listOfNotNull(
                            operation,
                            valueRef
                        )
                    )
                )
                emitObjectEnd()

                if (firstCall) {
                    emitObjectEnd()
                }
            } else if (named.name != Name.UNKNOWN && fieldRef != null && valueRef != null) {
                if (firstCall) {
                    emitObjectStart(long = isLong)
                }
                emitObjectKey(
                    resolveFieldReference(
                        fieldRef = fieldRef,
                        fieldUsedAsValue = false,
                    )
                )
                emitObjectStart(long = isLong)
                emitObjectKey(registerConstant('$' + named.name.canonical))
                emitContextValue(resolveValueReference(valueRef, fieldRef))
                emitObjectEnd(long = isLong)
                if (firstCall) {
                    emitObjectEnd(long = isLong)
                }
            }
        }
    }

    return this
}
