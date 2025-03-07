package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Component
import com.mongodb.jbplugin.mql.Namespace

/**
 * @param S
 * @property reference
 */
data class HasCollectionReference<S>(
    val reference: CollectionReference<S>,
) : Component {
    data object Unknown : CollectionReference<Any> {
        inline fun <reified T> cast() = Unknown as T
    }

    /**
     * Makes a copy of HasCollectionReference after changing the underlying reference to Known
     * only if the underlying reference is  not Unknown
     *
     * @param database
     */
    fun copy(database: String): HasCollectionReference<S> = when (reference) {
        is Known -> copy(
            reference = Known(
                databaseSource = reference.databaseSource,
                collectionSource = reference.collectionSource,
                namespace = Namespace(database, reference.namespace.collection),
                schema = reference.schema,
            )
        )

        is OnlyCollection -> copy(
            reference = Known(
                databaseSource = null,
                collectionSource = reference.collectionSource,
                namespace = Namespace(database, reference.collection),
                schema = null,
            )
        )

        is Unknown -> this
    }

    fun copy(collectionSchema: CollectionSchema) = when (reference) {
        is Known -> copy(
            reference = Known(
                databaseSource = reference.databaseSource,
                collectionSource = reference.collectionSource,
                namespace = reference.namespace,
                schema = collectionSchema,
            )
        )

        else -> this
    }

    /**
     * @param S
     */
    sealed interface CollectionReference<S>

    /**
     * @param S
     * @property databaseSource - Source where database was parsed from
     * @property collectionSource - Source where collection was parsed from
     * @property namespace - Namespace derived from parsed database and collection
     * @property schema - Generally injected after a query is parsed which is why the default is
     * always null
     */
    data class Known<S>(
        val databaseSource: S?,
        val collectionSource: S,
        val namespace: Namespace,
        val schema: CollectionSchema? = null
    ) : CollectionReference<S> {
        override fun toString(): String {
            return "Known(namespace=$namespace,schema=$schema)"
        }
    }

    /**
     * @param S
     * @property collection
     * @property collectionSource
     */
    data class OnlyCollection<S>(
        val collectionSource: S,
        val collection: String,
    ) : CollectionReference<S> {
        override fun toString(): String {
            return "Only(collection='$collection')"
        }
    }
}
