package com.mongodb.jbplugin.dialects.mongosh.query

import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.parser.components.whenHasAnyCommand
import com.mongodb.jbplugin.mql.parser.map
import com.mongodb.jbplugin.mql.parser.parse

suspend fun <S> Node<S>.returnsACursor(): Boolean {
    return whenHasAnyCommand<S>()
        .map { it.component<IsCommand>()!!.type }
        .map {
            it == IsCommand.CommandType.FIND_MANY ||
                it == IsCommand.CommandType.AGGREGATE
        }.parse(this).orElse { false }
}

suspend fun <S> Node<S>.canUpdateDocuments(): Boolean {
    return whenHasAnyCommand<S>()
        .map { it.component<IsCommand>()!!.type }
        .map {
            it == IsCommand.CommandType.UPDATE_ONE ||
                it == IsCommand.CommandType.UPDATE_MANY ||
                it == IsCommand.CommandType.FIND_ONE_AND_UPDATE
        }.parse(this).orElse { false }
}
