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
import kotlin.jvm.javaClass

/**
 * Returns the inferred BSON type of the current Java class, considering it's nullability.
 *
 * @param value
 */
fun <T> toBsonType(klass: Class<T>?, value: T? = null): BsonType {
    return when (klass) {
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
            if (klass.isEnum) {
                val variants = klass.enumConstants.map { it.toString() }.toSet()
                BsonEnum(variants)
            } else if (Collection::class.java.isAssignableFrom(klass) ||
                Array::class.java.isAssignableFrom(klass)
            ) {
                return BsonAnyOf(BsonNull, BsonArray(BsonAny)) // types are lost at runtime
            } else if (Map::class.java.isAssignableFrom(klass)) {
                if (value == null) {
                    return BsonAnyOf(BsonNull, BsonAny)
                } else {
                    val fields = Map::class.java.cast(value).entries.associate {
                        it.key.toString() to toBsonType(it.value?.javaClass, it.value)
                    }
                    return BsonAnyOf(BsonNull, BsonObject(fields))
                }
            } else if (Class::class.java.isAssignableFrom(klass)) {
                return BsonAnyOf(BsonNull, BsonObject(emptyMap()))
            } else {
                val fields =
                    klass.declaredFields.associate {
                        it.name to toBsonType(it.type)
                    }

                return BsonAnyOf(BsonNull, BsonObject(fields))
            }
    }
}

fun primitiveOrWrapper(example: Class<*>): Class<*> {
    val type = runCatching { example.getField("TYPE").get(null) as? Class<*> }.getOrNull()
    return type ?: example
}

actual inline fun <reified T : Any?> T.toBsonType(): BsonType {
    if (this == null) {
        return BsonNull
    }

    return toBsonType(this.javaClass, this)
}
