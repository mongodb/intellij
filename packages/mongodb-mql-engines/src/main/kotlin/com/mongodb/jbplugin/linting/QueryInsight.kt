package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.linting.Inspection.CollectionDoesNotExist
import com.mongodb.jbplugin.linting.Inspection.DatabaseDoesNotExist
import com.mongodb.jbplugin.linting.Inspection.FieldDoesNotExist
import com.mongodb.jbplugin.linting.Inspection.NoCollectionSpecified
import com.mongodb.jbplugin.linting.Inspection.NoDatabaseInferred
import com.mongodb.jbplugin.linting.Inspection.NotUsingIndex
import com.mongodb.jbplugin.linting.Inspection.NotUsingIndexEffectively
import com.mongodb.jbplugin.linting.Inspection.TypeMismatch
import com.mongodb.jbplugin.linting.InspectionAction.ChooseConnection
import com.mongodb.jbplugin.linting.InspectionAction.CreateIndexSuggestionScript
import com.mongodb.jbplugin.linting.InspectionAction.RunQuery
import com.mongodb.jbplugin.linting.InspectionCategory.CORRECTNESS
import com.mongodb.jbplugin.linting.InspectionCategory.ENVIRONMENT_MISMATCH
import com.mongodb.jbplugin.linting.InspectionCategory.PERFORMANCE
import com.mongodb.jbplugin.mql.Node

enum class InspectionCategory(val displayName: String) {
    PERFORMANCE("inspection.category.performance"),
    CORRECTNESS("inspection.category.correctness"),
    ENVIRONMENT_MISMATCH("inspection.category.environment")
}

sealed interface InspectionAction {
    data object NoAction : InspectionAction
    data object RunQuery : InspectionAction
    data object CreateIndexSuggestionScript : InspectionAction
    data object ChooseConnection : InspectionAction
}

val ALL_MDB_INSPECTIONS = listOf(
    NotUsingIndex,
    NotUsingIndexEffectively,
    FieldDoesNotExist,
    TypeMismatch,
    DatabaseDoesNotExist,
    CollectionDoesNotExist,
    NoDatabaseInferred,
    NoCollectionSpecified,
)

sealed interface Inspection {
    val primaryAction: InspectionAction
    val secondaryActions: Array<InspectionAction>
    val category: InspectionCategory

    // Performance Warnings
    data object NotUsingIndex : Inspection {
        override val primaryAction = CreateIndexSuggestionScript
        override val secondaryActions = emptyArray<InspectionAction>()
        override val category = PERFORMANCE
    }

    data object NotUsingIndexEffectively : Inspection {
        override val primaryAction = CreateIndexSuggestionScript
        override val secondaryActions = emptyArray<InspectionAction>()
        override val category = PERFORMANCE
    }

    data object NotUsingProject : Inspection {
        override val primaryAction = ChooseConnection
        override val secondaryActions = emptyArray<InspectionAction>()
        override val category = PERFORMANCE
    }

    data object FieldDoesNotExist : Inspection {
        override val primaryAction = RunQuery
        override val secondaryActions = emptyArray<InspectionAction>()
        override val category = CORRECTNESS
    }

    data object TypeMismatch : Inspection {
        override val primaryAction = RunQuery
        override val secondaryActions = emptyArray<InspectionAction>()
        override val category = CORRECTNESS
    }

    data object DatabaseDoesNotExist : Inspection {
        override val primaryAction = ChooseConnection
        override val secondaryActions = emptyArray<InspectionAction>()
        override val category = ENVIRONMENT_MISMATCH
    }

    data object CollectionDoesNotExist : Inspection {
        override val primaryAction = ChooseConnection
        override val secondaryActions = emptyArray<InspectionAction>()
        override val category = ENVIRONMENT_MISMATCH
    }

    data object NoDatabaseInferred : Inspection {
        override val primaryAction = ChooseConnection
        override val secondaryActions = emptyArray<InspectionAction>()
        override val category = ENVIRONMENT_MISMATCH
    }

    data object NoCollectionSpecified : Inspection {
        override val primaryAction = ChooseConnection
        override val secondaryActions = emptyArray<InspectionAction>()
        override val category = ENVIRONMENT_MISMATCH
    }
}

data class QueryInsight<S, I : Inspection>(
    val query: Node<S>,
    val source: S,
    val description: String,
    val descriptionArguments: List<String>,
    val inspection: I,
    val message: String? = null,
) {
    companion object {
        fun <S> notUsingIndex(query: Node<S>): QueryInsight<S, NotUsingIndex> {
            return QueryInsight(
                query = query,
                source = query.source,
                description = "insight.not-using-index",
                descriptionArguments = emptyList(),
                inspection = NotUsingIndex
            )
        }

        fun <S> notUsingIndexEffectively(
            query: Node<S>
        ): QueryInsight<S, NotUsingIndexEffectively> {
            return QueryInsight(
                query = query,
                source = query.source,
                description = "insight.not-using-index-effectively",
                descriptionArguments = emptyList(),
                inspection = NotUsingIndexEffectively
            )
        }

        fun <S> nonExistingField(
            query: Node<S>,
            source: S,
            field: String
        ): QueryInsight<S, FieldDoesNotExist> {
            return QueryInsight(
                query = query,
                source = source,
                description = "insight.field-does-not-exist",
                descriptionArguments = listOf(field),
                inspection = FieldDoesNotExist
            )
        }

        fun <S> typeMismatch(
            query: Node<S>,
            source: S,
            field: String,
            fieldType: String,
            valueType: String
        ): QueryInsight<S, TypeMismatch> {
            return QueryInsight(
                query = query,
                source = source,
                description = "insight.type-mismatch",
                descriptionArguments = listOf(field, fieldType, valueType),
                inspection = TypeMismatch
            )
        }

        fun <S> nonExistentDatabase(
            query: Node<S>,
            source: S,
            database: String
        ): QueryInsight<S, DatabaseDoesNotExist> {
            return QueryInsight(
                query = query,
                source = source,
                description = "insight.database-does-not-exist",
                descriptionArguments = listOf(database),
                inspection = DatabaseDoesNotExist
            )
        }

        fun <S> nonExistentCollection(
            query: Node<S>,
            source: S,
            collection: String,
            database: String,
        ): QueryInsight<S, CollectionDoesNotExist> {
            return QueryInsight(
                query = query,
                source = source,
                description = "insight.collection-does-not-exist",
                descriptionArguments = listOf(collection, database),
                inspection = CollectionDoesNotExist
            )
        }

        fun <S> noDatabaseInferred(
            query: Node<S>,
        ): QueryInsight<S, NoDatabaseInferred> {
            return QueryInsight(
                query = query,
                // Ideally we should be attaching the insight to whichever database reference we
                // can find but our current NamespaceExtractor strips out that information.
                source = query.source,
                description = "insight.no-database-inferred",
                descriptionArguments = listOf(),
                inspection = NoDatabaseInferred
            )
        }

        fun <S> noCollectionSpecified(
            query: Node<S>,
        ): QueryInsight<S, NoCollectionSpecified> {
            return QueryInsight(
                query = query,
                // Ideally we should be attaching the insight to whichever collection reference we
                // can find but our current NamespaceExtractor strips out that information.
                source = query.source,
                description = "insight.no-collection-specified",
                descriptionArguments = listOf(),
                inspection = NoCollectionSpecified
            )
        }

        fun <S> notUsingProject(
            query: Node<S>,
            message: String,
        ): QueryInsight<S, Inspection.NotUsingProject> {
            return QueryInsight(
                query = query,
                source = query.source,
                description = "insight.not-using-project",
                descriptionArguments = emptyList(),
                message = message,
                inspection = Inspection.NotUsingProject
            )
        }
    }
}
