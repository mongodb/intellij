package com.mongodb.jbplugin.mql

/**
 * Represents a MongoDB Namespace (db/coll)
 *
 * @property database
 * @property collection
 */
class Namespace private constructor(
    val database: String,
    val collection: String,
) {
    val isValid = database.isNotBlank() && collection.isNotBlank()

    override fun toString(): String = "$database.$collection"

    override fun equals(other: Any?): Boolean = other is Namespace && hashCode() == other.hashCode()

    override fun hashCode(): Int = database.hashCode() * collection.hashCode()

    companion object {
        operator fun invoke(
            database: String,
            collection: String,
        ): Namespace =
            Namespace(
                database,
                collection,
            )
    }
}
