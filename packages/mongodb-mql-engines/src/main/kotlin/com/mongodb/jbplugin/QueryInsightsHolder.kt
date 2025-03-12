package com.mongodb.jbplugin

import com.mongodb.jbplugin.mql.Node

data class QueryInsight<S, I : Inspection>(
    val query: Node<S>,
    val source: S,
    val description: String,
    val descriptionArguments: List<String>,
    val inspection: I,
)

sealed interface Inspection {
    val displayName: String
    val shortName: String
    val action: Action

    // This is just temporary so that we don't need to split the inspections individually right
    // away. In actual implementation this should be used, instead we should split the inspections.
    sealed interface FieldCheckInspection : Inspection
    sealed interface IndexCheckInspection : Inspection
    sealed interface NamespaceCheckInspection : Inspection

    data object NotUsingIndex : IndexCheckInspection {
        override val displayName: String = "Query missing index"
        override val shortName: String = "IndexCheckingLinter"
        override val action: Action = CreateIndex
    }

    data object IneffectiveIndex : IndexCheckInspection {
        override val displayName: String = "Ineffective index"
        override val shortName: String = "IndexCheckingLinter"
        override val action: Action = CreateIndex
    }

    data object NonExistentField : FieldCheckInspection {
        override val displayName: String = "Field doesn't exist"
        override val shortName: String = "FieldCheckingLinter"
        override val action: Action = RunQuery
    }

    data object FieldValueTypeMismatch : FieldCheckInspection {
        override val displayName: String = "Type of provided value does not match the type of field"
        override val shortName: String = "FieldCheckingLinter"
        override val action: Action = RunQuery
    }

    data object NonExistentDatabase : NamespaceCheckInspection {
        override val displayName: String = "Database doesn't exist"
        override val shortName: String = "NamespaceCheckingLinter"
        override val action: Action = ChooseConnection
    }

    data object UnIdentifiedDatabase : NamespaceCheckInspection {
        override val displayName: String = "No Database inferred"
        override val shortName: String = "NamespaceCheckingLinter"
        override val action: Action = ChooseConnection
    }

    data object NonExistentCollection : NamespaceCheckInspection {
        override val displayName: String = "Database doesn't exist"
        override val shortName: String = "NamespaceCheckingLinter"
        override val action: Action = ChooseConnection
    }

    data object UnIdentifiedCollection : NamespaceCheckInspection {
        override val displayName: String = "No Collection inferred"
        override val shortName: String = "NamespaceCheckingLinter"
        override val action: Action = ChooseConnection
    }

    data object UnIdentifiedNamespace : NamespaceCheckInspection {
        override val displayName: String = "No Namespace inferred"
        override val shortName: String = "NamespaceCheckingLinter"
        override val action: Action = ChooseConnection
    }

    sealed interface Action
    data object NoAction : Action
    data object RunQuery : Action
    data object ChooseConnection : Action
    data object NavigateToQuery : Action
    data object CreateIndex : Action
}

interface QueryInsightsHolder<S, I : Inspection> {
    suspend fun register(queryInsight: QueryInsight<S, I>)
}
