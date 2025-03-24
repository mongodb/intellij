package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.linting.Inspection.NotUsingIndex
import com.mongodb.jbplugin.linting.Inspection.NotUsingIndexEffectively
import com.mongodb.jbplugin.linting.InspectionAction.CreateIndexSuggestionScript
import com.mongodb.jbplugin.linting.InspectionCategory.PERFORMANCE
import com.mongodb.jbplugin.mql.Node

enum class InspectionCategory(val displayName: String) {
    PERFORMANCE("inspection.category.performance"),
}

sealed interface InspectionAction {
    data object NoAction : InspectionAction
    data object RunQuery : InspectionAction
    data object CreateIndexSuggestionScript : InspectionAction
    data object ChooseConnection : InspectionAction
}

sealed interface Inspection {
    val displayName: String
    val shortName: String
    val primaryAction: InspectionAction
    val secondaryActions: Array<InspectionAction>
    val category: InspectionCategory

    // Performance Warnings
    data object NotUsingIndex : Inspection {
        override val displayName = "inspection.not-using-index.display-name"
        override val shortName = "inspection.not-using-index.short-name"
        override val primaryAction = CreateIndexSuggestionScript
        override val secondaryActions = emptyArray<InspectionAction>()
        override val category = PERFORMANCE
    }

    data object NotUsingIndexEffectively : Inspection {
        override val displayName = "inspection.not-using-index-effectively.display-name"
        override val shortName = "inspection.not-using-index-effectively.short-name"
        override val primaryAction = CreateIndexSuggestionScript
        override val secondaryActions = emptyArray<InspectionAction>()
        override val category = PERFORMANCE
    }
}

data class QueryInsight<S, I : Inspection>(
    val query: Node<S>,
    val description: String,
    val descriptionArguments: List<String>,
    val inspection: I
) {
    companion object {
        fun <S> notUsingIndex(query: Node<S>): QueryInsight<S, NotUsingIndex> {
            return QueryInsight(query, "insight.not-using-index", emptyList(), NotUsingIndex)
        }

        fun <S> notUsingIndexEffectively(query: Node<S>): QueryInsight<S, NotUsingIndexEffectively> {
            return QueryInsight(query, "insight.not-using-index-effectively", emptyList(), NotUsingIndexEffectively)
        }
    }
}
