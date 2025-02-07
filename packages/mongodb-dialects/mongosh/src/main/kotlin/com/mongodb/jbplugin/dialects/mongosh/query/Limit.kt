package com.mongodb.jbplugin.dialects.mongosh.query

import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasLimit

suspend fun <S> MongoshBackend.emitLimit(query: Node<S>, defaultLimit: Int = 50): MongoshBackend {
    emitPropertyAccess()
    emitFunctionName("limit")
    return emitFunctionCall(long = false, {
        val limitComponent = query.component<HasLimit>()
        if (limitComponent == null) {
            emitContextValue(registerConstant(defaultLimit))
        } else {
            emitContextValue(registerConstant(limitComponent.limit))
        }
    })
}
