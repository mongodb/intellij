package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.Component

data class HasRunCommand<S>(
    val database: HasValueReference<S>,
    val commandName: HasValueReference<S>,
    val additionalArguments: List<Pair<HasFieldReference<S>, HasValueReference<S>>> = emptyList()
) : Component
