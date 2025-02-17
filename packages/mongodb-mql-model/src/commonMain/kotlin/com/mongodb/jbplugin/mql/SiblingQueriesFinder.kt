package com.mongodb.jbplugin.mql

interface SiblingQueriesFinder<S> {
    fun allSiblingsOf(query: Node<S>): Array<Node<S>>
}
