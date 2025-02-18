/**
 * Represents all supported Bson types. We are not using the ones defined in the driver as we need more information,
 * like nullability and composability (for example, a value that can be either int or bool).
 */

package com.mongodb.jbplugin.mql

import kotlinx.collections.immutable.persistentMapOf

/**
 * Represents any of the valid BSON types.
 */
sealed interface BsonType {
    val cardinality: Long
        get() = Long.MAX_VALUE

    /**
     * Checks whether the underlying type is assignable to the provided type
     * Example usage:
     * ```kt
     * val fieldType = BsonAnyOf(BsonInt32, BsonNull)
     * val valueType = BsonInt32
     * valueType.isAssignableTo(fieldType) // true
     *
     * // or
     * val valueType = BsonObject(mapOf("name" to BsonString))
     * val fieldType = BsonObject(mapOf("name" to BsonString, "version" to BsonString))
     * valueType.isAssignableTo(fieldType) // true because all the keys in the value type are also in field type
     *
     * // or
     * val valueType = BsonAnyOf(BsonInt32, BsonNull)
     * val fieldType = BsonInt32
     * valueType.isAssignableTo(fieldType) // false because the value can be BsonNull but field expects to be BsonInt32
     * ```
     *
     * @param otherType
     */
    fun isAssignableTo(otherType: BsonType): Boolean = when (this) {
        otherType -> true
        is BsonAny -> true
        else -> when (otherType) {
            is BsonAny -> true
            is BsonAnyOf -> otherType.types.subtract(setOf(BsonNull)).all {
                this.isAssignableTo(it)
            }
            is BsonArray -> this.isAssignableTo(otherType.schema)
            else -> false
        }
    }
}

/**
 * BSON String
 */
data object BsonString : BsonType

/**
 * Boolean
 */
data object BsonBoolean : BsonType {
    override val cardinality = 2L
}

/**
 * Date
 */
data object BsonDate : BsonType

/**
 * ObjectId
 */
data object BsonObjectId : BsonType

/**
 * 32-bit integer
 */

data object BsonInt32 : BsonType {
    override fun isAssignableTo(otherType: BsonType): Boolean = when (otherType) {
        is BsonInt64 -> true
        else -> super.isAssignableTo(otherType)
    }
}

/**
 * 64-bit integer
 */
data object BsonInt64 : BsonType {
    override val cardinality = Long.MAX_VALUE
}

/**
 * A double (64 bit floating point)
 */
data object BsonDouble : BsonType {
    override fun isAssignableTo(otherType: BsonType): Boolean = when (otherType) {
        is BsonDecimal128 -> true
        else -> super.isAssignableTo(otherType)
    }
}

/**
 * Decimal128 (128 bit floating point)
 */
data object BsonDecimal128 : BsonType {
    override val cardinality = Long.MAX_VALUE
}

/**
 * null / non existing field
 */

data object BsonNull : BsonType {
    override val cardinality = 1L

    override fun isAssignableTo(otherType: BsonType): Boolean = when (otherType) {
        is BsonNull -> true
        is BsonAny -> true
        is BsonAnyOf -> otherType.types.contains(BsonNull)
        else -> false
    }
}

/**
 * This is not a BSON type per se, but need a value for an unknown
 * bson type.
 */
data object BsonAny : BsonType

/**
 * This is not a BSON type per se, but a schema is dynamic and for a single
 * field we can have multiple types of values, so we will map this scenario
 * with the AnyOf type.
 *
 * @property types
 */
data class BsonAnyOf(
    val types: Set<BsonType>,
) : BsonType {
    override val cardinality = types.maxOf { it.cardinality }

    constructor(vararg types: BsonType) : this(types.toSet())

    override fun isAssignableTo(otherType: BsonType): Boolean = when (otherType) {
        is BsonAny -> true
        is BsonNull -> types.size == 1 && types.contains(BsonNull)
        else -> this.types.subtract(setOf(BsonNull)).all { it.isAssignableTo(otherType) }
    }
}

/**
 * Represents a map of key -> type.
 *
 * @property schema
 */
data class BsonObject(
    val schema: Map<String, BsonType>,
) : BsonType {
    override fun isAssignableTo(otherType: BsonType): Boolean = when (otherType) {
        is BsonAny -> true
        is BsonAnyOf -> otherType.types.any { this.isAssignableTo(it) }
        is BsonObject -> this.isAssignableToBsonObjectType(otherType)
        else -> false
    }

    private fun isAssignableToBsonObjectType(otherType: BsonObject): Boolean {
        return this.schema.all { (key, bsonType) ->
            otherType.schema[key]?.let { bsonType.isAssignableTo(it) } ?: false
        }
    }
}

/**
 * Represents a UUID. This is not a BSON type per se (it's a binary with subtype UUID), but it's
 * widely used making it convenient to have it a first-level type.
 */
data object BsonUUID : BsonType

/**
 * Represents the possible types that can be included in an array.
 *
 * @property schema
 */
data class BsonArray(
    val schema: BsonType,
) : BsonType {
    override fun isAssignableTo(otherType: BsonType): Boolean = when (otherType) {
        is BsonAny -> true
        is BsonAnyOf -> otherType.types.any { this.isAssignableTo(it) }
        is BsonArray -> this.schema.isAssignableTo(otherType.schema)
        else -> this.schema.isAssignableTo(otherType)
    }
}

/**
 * Represents an enumeration of values. This is not a BSON type per se, but it's useful to understand
 * the impact of index compression, as an enum has lower cardinality than a String.
 */
data class BsonEnum(val members: Set<String>, val name: String? = null) : BsonType {
    override val cardinality = members.size.toLong()

    override fun isAssignableTo(otherType: BsonType): Boolean = when (otherType) {
        is BsonEnum -> otherType.members.containsAll(members)
        is BsonAny, is BsonString -> true
        is BsonAnyOf -> otherType.types.any { this.isAssignableTo(it) }
        else -> super.isAssignableTo(otherType)
    }
}

data class ComputedBsonType<S>(val baseType: BsonType, val expression: Node<S>) :
    BsonType by baseType // for now it will behave as baseType

fun mergeSchemaTogether(
    first: BsonType,
    second: BsonType,
): BsonType {
    if (first is BsonObject && second is BsonObject) {
        val mergedMap =
            first.schema.entries
                .union(second.schema.entries)
                .fold(persistentMapOf<String, BsonType>()) { acc, entry ->
                    val currentValue = acc[entry.key]
                    if (currentValue != null) {
                        acc.put(entry.key, mergeSchemaTogether(currentValue, entry.value))
                    } else {
                        acc.put(entry.key, entry.value)
                    }
                }

        return BsonObject(mergedMap)
    }

    if (first is BsonArray && second is BsonArray) {
        return BsonArray(mergeSchemaTogether(first.schema, second.schema))
    }

    if (first is BsonAnyOf && second is BsonAnyOf) {
        return BsonAnyOf(first.types + second.types)
    }

    if (first is BsonAnyOf) {
        return BsonAnyOf(first.types + second)
    }

    if (second is BsonAnyOf) {
        return BsonAnyOf(second.types + first)
    }

    if (first == second) {
        return first
    }

    return BsonAnyOf(setOf(first, second))
}

fun flattenAnyOfReferences(schema: BsonType): BsonType =
    when (schema) {
        is BsonArray -> BsonArray(flattenAnyOfReferences(schema.schema))
        is BsonObject ->
            BsonObject(
                schema.schema.entries.associate {
                    Pair(
                        it.key,
                        flattenAnyOfReferences(it.value),
                    )
                },
            )

        is BsonAnyOf -> {
            val flattenAnyOf =
                schema.types.flatMap {
                    val flattenType = flattenAnyOfReferences(it)
                    if (flattenType is BsonAnyOf) {
                        flattenType.types
                    } else {
                        listOf(flattenType)
                    }
                }

            BsonAnyOf(flattenAnyOf.toSet())
        }

        else -> schema
    }

fun BsonType.toNonNullableType(): BsonType {
    return when (this) {
        is BsonAnyOf -> types.first { it != BsonNull }.toNonNullableType()
        BsonNull -> BsonAny
        else -> this
    }
}

expect inline fun <reified T : Any?> T.toBsonType(): BsonType
