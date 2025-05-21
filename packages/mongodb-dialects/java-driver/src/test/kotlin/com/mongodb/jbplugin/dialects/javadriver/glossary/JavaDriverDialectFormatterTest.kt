package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.BsonAnyOf
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonDate
import com.mongodb.jbplugin.mql.BsonDecimal128
import com.mongodb.jbplugin.mql.BsonDouble
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonInt64
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.BsonObjectId
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.BsonUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class JavaDriverDialectFormatterTest {
    @ParameterizedTest
    @MethodSource("bsonTypesToJava")
    fun `should map BSON types to the java representation`(
        bsonType: BsonType,
        javaType: String,
    ) {
        assertEquals(
            javaType,
            JavaDriverDialectFormatter.formatType(bsonType),
        )
    }

    companion object {
        @JvmStatic
        fun bsonTypesToJava(): Array<Array<Any>> =
            arrayOf(
                arrayOf(BsonDouble, "double"),
                arrayOf(BsonString, "String"),
                arrayOf(BsonObject(emptyMap()), "Object"),
                arrayOf(BsonArray(BsonDouble), "List<Double>"),
                arrayOf(BsonObjectId, "ObjectId"),
                arrayOf(BsonBoolean, "boolean"),
                arrayOf(BsonDate, "Date"),
                arrayOf(BsonNull, "null"),
                arrayOf(BsonInt32, "int"),
                arrayOf(BsonInt64, "long"),
                arrayOf(BsonDecimal128, "BigDecimal"),
                arrayOf(BsonUUID, "UUID"),
                arrayOf(BsonAny, "any"),
                arrayOf(BsonAnyOf(BsonNull, BsonDouble), "Double"), // java boxed value
                arrayOf(BsonAnyOf(BsonInt32, BsonDouble), "double | int"),
                arrayOf(BsonAnyOf(), "any"),
            )
    }
}
