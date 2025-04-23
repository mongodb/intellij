package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonDate
import com.mongodb.jbplugin.mql.BsonDecimal128
import com.mongodb.jbplugin.mql.BsonDouble
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonInt64
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.BsonObjectId
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.BsonType
import kotlin.js.Date

private fun jsTypeOf(v: dynamic): String = js("typeof v") as String

actual fun recursivelyBuildSchema(value: Any?): BsonType = when {
    jsTypeOf(value) == "string" -> BsonString
    jsTypeOf(value) == "boolean" -> BsonBoolean
    jsTypeOf(value) == "number" -> BsonDouble
    jsTypeOf(value) == "bigint" -> BsonInt64
    value is Date -> BsonDate
    js("Array.isArray(value)") as Boolean -> BsonArray(BsonAny)
    value != null && jsTypeOf(value) == "object" -> run {
        val keys = js("Object.keys(value)") as Array<String>
        if (keys.size == 1 && keys[0].startsWith("$")) {
            when (keys[0]) {
                "\$numberLong" -> BsonInt64
                "\$numberDecimal" -> BsonDecimal128
                "\$numberDouble" -> BsonDouble
                "\$numberInt" -> BsonInt32
                "\$oid" -> BsonObjectId
                "\$date" -> BsonDate
                else -> BsonAny
            }
        } else {
            val map = keys.associateWith { key ->
                recursivelyBuildSchema((value.asDynamic())[key])
            }

            BsonObject(map)
        }
    }
    else -> BsonAny
}
