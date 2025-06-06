package com.mongodb.jbplugin.dialects.mongosh.aggr

import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasLimit

internal fun <S> MongoshBackend.emitLimitStage(node: Node<S>): MongoshBackend {
    val limit = node.component<HasLimit>()?.limit ?: return this

    emitObjectStart()
    emitObjectKey(registerConstant('$' + "limit"))
    emitContextValue(registerConstant(limit))
    emitObjectEnd()

    return this
}
