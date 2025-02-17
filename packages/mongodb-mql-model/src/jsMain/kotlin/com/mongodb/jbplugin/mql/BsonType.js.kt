package com.mongodb.jbplugin.mql

import kotlin.reflect.KClass

actual inline fun <reified T> T.toBsonType(): BsonType {
    return this?.runtimeBsonType(T::class) ?: BsonNull
}

fun Any.runtimeBsonType(klass: KClass<*>): BsonType {
    return when (klass) {
        Int::class -> BsonInt32
        Long::class -> BsonInt64
        Float::class -> BsonDouble
        Double::class -> BsonDouble
        String::class -> BsonAnyOf(BsonNull, BsonString)
        Array::class -> BsonArray(schema = BsonAny)
        Boolean::class -> BsonBoolean
        Map::class -> BsonObject(
            (this as Map<*, *>).map { (k, v) -> k.toString() to v.toBsonType() } as Map<String, BsonType>
        )
        else -> when (jsTypeOf(this)) {
            "string" -> BsonAnyOf(BsonNull, BsonString)
            "number" -> if (isInteger(this)) {
                BsonInt32
            } else {
                BsonDouble
            }
            else -> BsonAny
        }
    }
}

private fun jsTypeOf(value: Any): String {
    return js("typeof value")
}

private fun isInteger(value: Any): Boolean {
    return js("Number.isInteger(value)")
}
