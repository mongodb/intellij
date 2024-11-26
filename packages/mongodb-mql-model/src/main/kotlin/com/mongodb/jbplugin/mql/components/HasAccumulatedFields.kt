package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.HasChildren
import com.mongodb.jbplugin.mql.Node

data class HasAccumulatedFields<S>(
    override val children: List<Node<S>>
) : HasChildren<S>
