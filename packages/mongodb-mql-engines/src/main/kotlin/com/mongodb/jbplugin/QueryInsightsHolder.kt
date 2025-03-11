package com.mongodb.jbplugin

import com.mongodb.jbplugin.mql.Node

data class QueryInsight<S>(
    val query: Node<S>,
    val source: S,
    val description: String,
    val descriptionArguments: List<String>,
    val inspection: Inspection,
)

sealed interface Inspection {
    val displayName: String
    val shortName: String
    val action: Action

    data object NotUsingIndex : Inspection {
        override val displayName: String = "Query missing index"
        override val shortName: String = "IndexCheckingLinter"
        override val action: Action = CreateIndex
    }

    data object IneffectiveIndex : Inspection {
        override val displayName: String = "Ineffective index"
        override val shortName: String = "IndexCheckingLinter"
        override val action: Action = CreateIndex
    }

    data object NonExistentField : Inspection {
        override val displayName: String = "Field doesn't exist"
        override val shortName: String = "FieldCheckingLinter"
        override val action: Action = RunQuery
    }

    data object FieldValueTypeMismatch : Inspection {
        override val displayName: String = "Type of provided value does not match the type of field"
        override val shortName: String = "FieldCheckingLinter"
        override val action: Action = RunQuery
    }

    data object NonExistentDatabase : Inspection {
        override val displayName: String = "Database doesn't exist"
        override val shortName: String = "NamespaceCheckingLinter"
        override val action: Action = ChooseConnection
    }

    data object UnIdentifiedDatabase : Inspection {
        override val displayName: String = "No Database inferred"
        override val shortName: String = "NamespaceCheckingLinter"
        override val action: Action = ChooseConnection
    }

    data object NonExistentCollection : Inspection {
        override val displayName: String = "Database doesn't exist"
        override val shortName: String = "NamespaceCheckingLinter"
        override val action: Action = ChooseConnection
    }

    data object UnIdentifiedCollection : Inspection {
        override val displayName: String = "No Collection inferred"
        override val shortName: String = "NamespaceCheckingLinter"
        override val action: Action = ChooseConnection
    }

    data object UnIdentifiedNamespace : Inspection {
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

interface QueryInsightsHolder<S> {
    suspend fun register(queryInsight: QueryInsight<S>)
}
