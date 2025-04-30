package com.mongodb.jbplugin.mql.parser.components

import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import com.mongodb.jbplugin.mql.parser.Parser
import com.mongodb.jbplugin.mql.parser.anyError
import com.mongodb.jbplugin.mql.parser.map
import com.mongodb.jbplugin.mql.parser.recoverError

data object HasNoNamedOperation

fun <S> hasName(name: Name): Parser<Node<S>, Any, Boolean> {
    return { input ->
        val inputName = input.component<Named>()?.name
        Either.right(inputName == name)
    }
}

fun <S> extractOperation(): Parser<Node<S>, HasNoNamedOperation, Named> {
    return { input ->
        val inputRole = input.component<Named>()
        if (inputRole != null) {
            Either.right(inputRole)
        } else {
            Either.left(HasNoNamedOperation)
        }
    }
}

fun <S> whenAllNamedOperationsAreIn(names: Set<Name>): Parser<Node<S>, Any, Boolean> {
    return allFiltersRecursively<S>()
        .anyError()
        .map { filters -> filters.all { names.contains(it.component<Named>()?.name) } }
        .recoverError { it is NoFilters }
}
