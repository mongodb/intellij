package com.mongodb.jbplugin.dialects.mongosh.aggr

import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasAddedFields

internal fun <S> MongoshBackend.emitAddFieldsStage(node: Node<S>): MongoshBackend {
    val addedFields = node.component<HasAddedFields<S>>()?.children ?: emptyList()
    val isLongFieldList = addedFields.size > 3

    emitObjectStart(long = isLongFieldList)
    emitObjectKey(registerConstant('$' + "addFields"))
    emitObjectStart(long = isLongFieldList)
    emitAsFieldValueDocument(addedFields, isLongFieldList)
    emitObjectEnd(long = isLongFieldList)
    emitObjectEnd(long = isLongFieldList)

    return this
}
