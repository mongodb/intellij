package com.mongodb.jbplugin.mql

import org.bson.types.Decimal128
import org.bson.types.ObjectId
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Date
import java.util.UUID
import kotlin.collections.get

/**
 * Returns the inferred BSON type of the current Java class, considering it's nullability.
 *
 * @param value
 */
fun <T> Class<T>?.toBsonType(value: T? = null): BsonType {
    return when (this) {
        null -> BsonNull
        Float::class.javaPrimitiveType -> BsonDouble
        Float::class.javaObjectType -> BsonAnyOf(BsonNull, BsonDouble)
        Double::class.javaPrimitiveType -> BsonDouble
        Double::class.javaObjectType -> BsonAnyOf(BsonNull, BsonDouble)
        Boolean::class.javaPrimitiveType -> BsonBoolean
        Boolean::class.javaObjectType -> BsonAnyOf(BsonNull, BsonBoolean)
        Short::class.javaPrimitiveType -> BsonInt32
        Short::class.javaObjectType -> BsonAnyOf(BsonNull, BsonInt32)
        Int::class.javaPrimitiveType -> BsonInt32
        Int::class.javaObjectType -> BsonAnyOf(BsonNull, BsonInt32)
        Long::class.javaPrimitiveType -> BsonInt64
        Long::class.javaObjectType -> BsonAnyOf(BsonNull, BsonInt64)
        CharSequence::class.java, String::class.java -> BsonAnyOf(BsonNull, BsonString)
        Date::class.java, Instant::class.java, LocalDate::class.java, LocalDateTime::class.java ->
            BsonAnyOf(BsonNull, BsonDate)
        UUID::class.java -> BsonAnyOf(BsonNull, BsonUUID)
        ObjectId::class.java -> BsonAnyOf(BsonNull, BsonObjectId)
        BigInteger::class.java -> BsonAnyOf(BsonNull, BsonInt64)
        BigDecimal::class.java -> BsonAnyOf(BsonNull, BsonDecimal128)
        Decimal128::class.java -> BsonAnyOf(BsonNull, BsonDecimal128)
        else ->
            if (isEnum) {
                val variants = this.enumConstants.map { it.toString() }.toSet()
                BsonEnum(variants)
            } else if (Collection::class.java.isAssignableFrom(this) ||
                Array::class.java.isAssignableFrom(this)
            ) {
                return BsonAnyOf(BsonNull, BsonArray(BsonAny)) // types are lost at runtime
            } else if (Map::class.java.isAssignableFrom(this)) {
                value?.let {
                    val fields =
                        Map::class.java.cast(value).entries.associate {
                            it.key.toString() to it.value?.javaClass.toBsonType(it.value)
                        }
                    return BsonAnyOf(BsonNull, BsonObject(fields))
                } ?: return BsonAnyOf(BsonNull, BsonAny)
            } else {
                val fields =
                    this.declaredFields.associate {
                        it.name to it.type.toBsonType()
                    }

                return BsonAnyOf(BsonNull, BsonObject(fields))
            }
    }
}

fun primitiveOrWrapper(example: Class<*>): Class<*> {
    val type = runCatching { example.getField("TYPE").get(null) as? Class<*> }.getOrNull()
    return type ?: example
}
