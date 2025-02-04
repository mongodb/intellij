package com.mongodb.jbplugin.dialects.mongosh

import com.mongodb.jbplugin.dialects.mongosh.backend.DefaultContext
import com.mongodb.jbplugin.dialects.mongosh.backend.MongoshBackend
import com.mongodb.jbplugin.mql.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.bson.types.ObjectId
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDateTime

class MongoshBackendTest {
    @Test
    fun `generates a valid find query`() = runTest {
        assertGeneratedJs(
            """
            db.getSiblingDB("myDb").getCollection("myColl").find({"field": 1})
            """.trimIndent()
        ) {
            emitDbAccess()
            emitDatabaseAccess(registerConstant("myDb"))
            emitCollectionAccess(registerConstant("myColl"))
            emitFunctionName("find")
            emitFunctionCall(long = false, {
                emitObjectStart()
                emitObjectKey(registerConstant("field"))
                emitContextValue(registerConstant(1))
                emitObjectEnd()
            })
        }
    }

    @Test
    fun `generates a valid query with runtime parameters`() = runTest {
        assertGeneratedJs(
            """
            var myColl = ""
            var myDb = ""
            var myValue = ""

            db.getSiblingDB(myDb).getCollection(myColl).find({"field": myValue})
            """.trimIndent()
        ) {
            emitDbAccess()
            emitDatabaseAccess(registerVariable("myDb", BsonString, null))
            emitCollectionAccess(registerVariable("myColl", BsonString, null))
            emitFunctionName("find")
            emitFunctionCall(long = false, {
                emitObjectStart()
                emitObjectKey(registerConstant("field"))
                emitContextValue(registerVariable("myValue", BsonString, null))
                emitObjectEnd()
            })
        }
    }

    @Test
    fun `generates a valid update query`() = runTest {
        assertGeneratedJs(
            """
            var myColl = ""
            var myDb = ""
            var myValue = ""

            db.getSiblingDB(myDb).getCollection(myColl).update({"field": myValue}, {"myUpdate": 1})
            """.trimIndent()
        ) {
            emitDbAccess()
            emitDatabaseAccess(registerVariable("myDb", BsonString, null))
            emitCollectionAccess(registerVariable("myColl", BsonString, null))
            emitFunctionName("update")
            emitFunctionCall(long = false, {
                emitObjectStart()
                emitObjectKey(registerConstant("field"))
                emitContextValue(registerVariable("myValue", BsonString, null))
                emitObjectEnd()
            }, {
                emitObjectStart()
                emitObjectKey(registerConstant("myUpdate"))
                emitContextValue(registerConstant(1))
                emitObjectEnd()
            })
        }
    }

    @ParameterizedTest
    @MethodSource("bsonValues")
    fun `generates a valid bson object given a value`(testCase: Pair<Any, String>) = runBlocking {
        val (value, expected) = testCase
        assertGeneratedJs(
            expected
        ) {
            emitContextValue(registerConstant(value))
        }
    }

    @ParameterizedTest
    @MethodSource("bsonTypes")
    fun `generates a valid default object given the type of the value`(
        testCase: Pair<BsonType, String>
    ) = runTest {
        val (type, expected) = testCase
        assertGeneratedJs(
            """
                var arg = $expected

                arg
            """.trimIndent()
        ) {
            emitContextValue(registerVariable("arg", type, null))
        }
    }

    companion object {
        @JvmStatic
        fun bsonValues(): Array<Pair<Any, String>> = arrayOf(
            1 to "1",
            1.5 to "1.5",
            "myString" to "\"myString\"",
            LocalDateTime.of(2000, 5, 10, 5, 0) to "ISODate(\"2000-05-10T05:00:00\")",
            BigInteger("5234") to "Decimal128(\"5234\")",
            BigDecimal("5234.5234") to "Decimal128(\"5234.5234\")",
            true to "true",
            ObjectId("66e02569aa5b362fa36f2416") to "ObjectId(\"66e02569aa5b362fa36f2416\")",
            listOf(1, 2.2, LocalDateTime.of(2000, 5, 10, 5, 0)) to
                "[1, 2.2, ISODate(\"2000-05-10T05:00:00\")]",
            mapOf("a" to "1", "b" to 2) to "{\"a\": \"1\", \"b\": 2}",
            SomeObject(1, "2") to "{}", // we won't serialize unknown objects
        )

        @JvmStatic
        fun bsonTypes(): Array<Pair<BsonType, String>> = arrayOf(
            BsonAny to "\"any\"",
            BsonAnyOf(BsonNull, BsonString) to "\"\"",
            BsonAnyOf(BsonNull, BsonString, BsonInt64) to "\"\"",
            BsonArray(BsonAny) to "[]",
            BsonBoolean to "false",
            BsonDate to "ISODate(\"2009-02-11T18:00:00\")",
            BsonDecimal128 to "Decimal128(\"0\")",
            BsonDouble to "0.0",
            BsonInt32 to "0",
            BsonInt64 to "0",
            BsonNull to "null",
            BsonObject(emptyMap()) to "{}",
            BsonObjectId to "ObjectId(\"000000000000000000000000\")",
            BsonString to "\"\"",
        )

        private data class SomeObject(val exampleInt: Int, val exampleString: String)
    }
}

private suspend fun assertGeneratedJs(
    @Language(
        "js"
    ) js: String,
    script: suspend MongoshBackend.() -> MongoshBackend
) {
    val generated = script(MongoshBackend(DefaultContext())).computeOutput()
    assertEquals(js, generated)
}
