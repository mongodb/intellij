package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.Component

data class HasFieldReference<S>(
    val reference: FieldReference<S>,
) : Component {

    sealed interface FieldReference<S>

    /**
     * Encodes a possible FieldReference that cannot be classified as one of the remaining
     * FieldReference implementations because of us not having enough metadata about it.
     */
    data object Unknown : FieldReference<Any>

    /**
     * Encodes a FieldReference that is statically typed in the user code and one that is
     * expected to reference a field from the target namespace of the query / aggregation.
     */
    data class FromSchema<S>(
        val source: S,
        val fieldName: String,
        val displayName: String = fieldName,
    ) : FieldReference<S> {
        override fun toString(): String {
            return "FromSchema(fieldName=$fieldName,displayName=$displayName)"
        }
    }

    /**
     * Encodes a FieldReference that is part of a schema, but it's not defined
     * in code. For example, the _id field that is created on { $group: "$expr" }.
     */
    data class Inferred<S>(
        val source: S,
        val fieldName: String,
        val displayName: String = fieldName,
    ) : FieldReference<S> {
        override fun toString(): String {
            return "Inferred(fieldName=$fieldName, displayName=$displayName)"
        }
    }

    /**
     * Encodes a FieldReference that does not exist in the original schema and the value of which
     * is computed using some expression.
     */
    data class Computed<S>(
        val source: S,
        val fieldName: String,
        val displayName: String = fieldName,
    ) : FieldReference<S> {
        override fun toString(): String {
            return "Computed(fieldName=$fieldName, displayName=$displayName)"
        }
    }
}
