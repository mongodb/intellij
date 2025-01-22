package com.mongodb.jbplugin.dialects.mongosh.aggr

import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasProjections

internal fun <S> MongoshBackend.emitProjectStage(node: Node<S>): MongoshBackend {
    val projections = node.component<HasProjections<S>>()?.children ?: emptyList()
    val isLongProjection = projections.size > 3

    emitObjectStart(long = isLongProjection)
    emitObjectKey(registerConstant('$' + "project"))
    emitObjectStart(long = isLongProjection)
    emitAsFieldValueDocument(projections)
    emitObjectEnd(long = isLongProjection)
    emitObjectEnd(long = isLongProjection)

    return this
}
