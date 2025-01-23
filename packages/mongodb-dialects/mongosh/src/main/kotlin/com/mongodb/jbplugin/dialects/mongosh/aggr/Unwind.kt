package com.mongodb.jbplugin.dialects.mongosh.aggr

import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.dialects.mongosh.query.resolveFieldReference
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasFieldReference

internal fun <S> MongoshBackend.emitUnwindStage(node: Node<S>): MongoshBackend {
    val unwindField = node.component<HasFieldReference<S>>() ?: return this

    emitObjectStart()
    emitObjectKey(registerConstant('$' + "unwind"))
    emitContextValue(resolveFieldReference(unwindField))
    emitObjectEnd()

    return this
}
