package com.mongodb.jbplugin.dialects.mongosh.query

import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.parser.components.whenIsCommand
import com.mongodb.jbplugin.mql.parser.map
import com.mongodb.jbplugin.mql.parser.parse

fun <S> Node<S>.returnsACursor(): Boolean {
    return whenIsCommand<S>(IsCommand.CommandType.FIND_MANY)
        .map { true }
        .parse(this).orElse { false }
}
