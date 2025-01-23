package com.mongodb.jbplugin.dialects.mongosh.aggr

import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.dialects.mongosh.query.emitQueryFilter
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasFilter

internal fun <S> MongoshBackend.emitMatchStage(node: Node<S>): MongoshBackend {
    val filter = node.component<HasFilter<S>>()?.children?.getOrNull(0)
    val longFilter = filter?.component<HasFilter<S>>()?.children?.size?.let { it > 3 } == true

    emitObjectStart()
    emitObjectKey(registerConstant('$' + "match"))
    if (filter != null) {
        emitObjectStart(long = longFilter)
        emitQueryFilter(filter)
        emitObjectEnd(long = longFilter)
    } else {
        emitComment("No filter provided.")
    }
    emitObjectEnd()

    return this
}
