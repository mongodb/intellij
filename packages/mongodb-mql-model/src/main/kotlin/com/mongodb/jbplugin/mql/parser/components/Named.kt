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

data object NoNamedComponents

fun <S> allNamedComponentsRecursively(): Parser<Node<S>, NoNamedComponents, List<Named>> {
    return { input ->
        fun namedComponentsFromNode(node: Node<S>): List<Named> {
            val directNamedComponents: List<Named> = node.components<Named>()
            val nestedNamedComponents = mutableListOf<Named>()
            for (componentWithChildren in node.componentsWithChildren()) {
                nestedNamedComponents.addAll(
                    componentWithChildren.children.flatMap(
                        ::namedComponentsFromNode
                    )
                )
            }
            return directNamedComponents + nestedNamedComponents
        }

        val allNamedComponents = namedComponentsFromNode(input)
        if (allNamedComponents.isEmpty()) {
            Either.left(NoNamedComponents)
        } else {
            Either.right(allNamedComponents)
        }
    }
}

fun <S> whenAllNamedOperationsAreIn(names: Set<Name>): Parser<Node<S>, Any, Boolean> {
    return allNamedComponentsRecursively<S>()
        .anyError()
        .map { namedComponents -> namedComponents.all { names.contains(it.name) } }
        .recoverError { it is NoNamedComponents }
}
