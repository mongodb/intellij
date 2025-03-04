package com.mongodb.jbplugin

import com.mongodb.jbplugin.mql.Node

sealed interface Inspection<S> {
    val query: Node<S>
    val description: String
    val descriptionArguments: List<String>
    val action: Action
    val source: S

    data class PerformanceWarning<S>(
        override val query: Node<S>,
        override val description: String,
        override val descriptionArguments: List<String>,
        override val action: Action,
        override val source: S,
    ) : Inspection<S>

    data class CorrectnessWarning<S>(
        override val query: Node<S>,
        override val description: String,
        override val descriptionArguments: List<String>,
        override val action: Action,
        override val source: S,
    ) : Inspection<S>

    data class OtherWarning<S>(
        override val query: Node<S>,
        override val description: String,
        override val descriptionArguments: List<String>,
        override val action: Action,
        override val source: S,
    ) : Inspection<S>

    sealed interface Action
    data object NoAction : Action
    data object ChooseConnection : Action
    data object NavigateToQuery : Action
    data object CreateIndex : Action
}

interface InspectionHolder<S> {
    suspend fun register(inspection: Inspection<S>)
}
