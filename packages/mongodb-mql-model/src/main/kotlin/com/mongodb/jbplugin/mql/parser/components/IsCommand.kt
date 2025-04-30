package com.mongodb.jbplugin.mql.parser.components

import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.parser.Parser
import com.mongodb.jbplugin.mql.parser.anyError
import com.mongodb.jbplugin.mql.parser.map

data object CommandDoesNotMatch
fun <S> whenHasAnyCommand(): Parser<Node<S>, CommandDoesNotMatch, Node<S>> {
    return { input ->
        when (input.component<IsCommand>()?.type) {
            null -> Either.left(CommandDoesNotMatch)
            else -> Either.right(input)
        }
    }
}

fun <S> whenCommandIsAnyOf(cmd: Set<IsCommand.CommandType>): Parser<Node<S>, Any, Boolean> {
    return whenHasAnyCommand<S>()
        .anyError()
        .map { it.component<IsCommand>()?.type }
        .map { cmd.contains(it) }
}
