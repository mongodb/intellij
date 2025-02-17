package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.mql.BsonAnyOf
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.toBsonType

actual fun recursivelyBuildSchema(value: Any?): BsonType {
    return when (value) {
        null -> BsonNull
        is Map<*, *> -> BsonObject(
            value.map { it.key.toString() to recursivelyBuildSchema(it.value) }
                .toMap()
        )

        is Collection<*> -> recursivelyBuildSchema(value.toTypedArray())
        is Array<*> ->
            BsonArray(
                value
                    .map {
                        it.toBsonType()
                    }.toSet()
                    .let {
                        if (it.size == 1) {
                            it.first()
                        } else {
                            BsonAnyOf(it)
                        }
                    },
            )
        else -> value.toBsonType()
    }
}
