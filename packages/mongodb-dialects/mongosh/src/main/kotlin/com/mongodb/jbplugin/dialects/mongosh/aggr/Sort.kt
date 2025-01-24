package com.mongodb.jbplugin.dialects.mongosh.aggr

import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasSorts

internal fun <S> MongoshBackend.emitSortStage(node: Node<S>): MongoshBackend {
    val sorts = node.component<HasSorts<S>>()?.children ?: emptyList()
    val isLongSortChain = sorts.size > 3

    emitObjectStart(long = isLongSortChain)
    emitObjectKey(registerConstant('$' + "sort"))
    emitObjectStart(long = isLongSortChain)
    emitAsFieldValueDocument(sorts, isLongSortChain)
    emitObjectEnd(long = isLongSortChain)
    emitObjectEnd(long = isLongSortChain)

    return this
}
