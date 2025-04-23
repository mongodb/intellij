import com.mongodb.jbplugin.mql.*
import com.mongodb.jbplugin.mql.components.HasValueReference
import kotlin.js.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EJsonParserTest {
    @Test fun parseStringReturnsBsonString() {
        val input: dynamic = "hello"
        val result = parseEjson(input)
        assertTrue(result.reference is HasValueReference.Runtime<*>)
        val ref = result.reference as HasValueReference.Runtime<*>
        assertEquals(input, ref.source)
        assertEquals(BsonString, ref.type)
    }

    @Test fun parseBooleanReturnsBsonBoolean() {
        val inputTrue: dynamic = true
        val inputFalse: dynamic = false
        val r1 = parseEjson(inputTrue)
        val r2 = parseEjson(inputFalse)
        listOf(r1, r2).forEach {
            assertTrue(it.reference is HasValueReference.Runtime<*>)
            val ref = it.reference as HasValueReference.Runtime<*>
            assertEquals(BsonBoolean, ref.type)
        }
        assertEquals(inputTrue, (r1.reference as HasValueReference.Runtime<*>).source)
        assertEquals(inputFalse, (r2.reference as HasValueReference.Runtime<*>).source)
    }

    @Test fun parseNumberReturnsBsonDouble() {
        val input: dynamic = 3.14
        val result = parseEjson(input)
        val ref = result.reference as HasValueReference.Runtime<*>
        assertEquals(BsonDouble, ref.type)
        assertEquals(input, ref.source)
    }

    @Test fun parseBigintReturnsBsonInt64() {
        val input: dynamic = js("BigInt(9007199254740991)")
        val result = parseEjson(input)
        val ref = result.reference as HasValueReference.Runtime<*>
        assertEquals(BsonInt64, ref.type)
        assertEquals(input, ref.source)
    }

    @Test fun parseJsDateReturnsBsonDate() {
        val input: dynamic = Date(1620000000000)
        val result = parseEjson(input)
        val ref = result.reference as HasValueReference.Runtime<*>
        assertEquals(BsonDate, ref.type)
        assertEquals(input, ref.source)
    }

    @Test fun parseArrayReturnsBsonArrayOfBsonAny() {
        val input: dynamic = js("[1, 'two', true]")
        val result = parseEjson(input)
        val ref = result.reference as HasValueReference.Runtime<*>
        assertEquals(BsonArray(BsonAny), ref.type)
        assertEquals(input, ref.source)
    }

    @Test fun parseEjsonNumberLongReturnsBsonInt64() {
        val input: dynamic = js("{ '${'$'}numberLong': '1234567890' }")
        val result = parseEjson(input)
        val ref = result.reference as HasValueReference.Runtime<*>
        assertEquals(BsonInt64, ref.type)
        assertEquals(input, ref.source)
    }

    @Test fun parseEjsonNumberDecimalReturnsBsonDecimal128() {
        val input: dynamic = js("{ '${'$'}numberDecimal': '1234.5678' }")
        val result = parseEjson(input)
        val ref = result.reference as HasValueReference.Runtime<*>
        assertEquals(BsonDecimal128, ref.type)
        assertEquals(input, ref.source)
    }

    @Test fun parseEjsonNumberDoubleReturnsBsonDouble() {
        val input: dynamic = js("{ '${'$'}numberDouble': '9.81' }")
        val result = parseEjson(input)
        val ref = result.reference as HasValueReference.Runtime<*>
        assertEquals(BsonDouble, ref.type)
        assertEquals(input, ref.source)
    }

    @Test fun parseEjsonNumberIntReturnsBsonInt32() {
        val input: dynamic = js("{ '${'$'}numberInt': '42' }")
        val result = parseEjson(input)
        val ref = result.reference as HasValueReference.Runtime<*>
        assertEquals(BsonInt32, ref.type)
        assertEquals(input, ref.source)
    }

    @Test fun parseEjsonOidReturnsBsonObjectId() {
        val input: dynamic = js("{ '${'$'}oid': '507f1f77bcf86cd799439011' }")
        val result = parseEjson(input)
        val ref = result.reference as HasValueReference.Runtime<*>
        assertEquals(BsonObjectId, ref.type)
        assertEquals(input, ref.source)
    }

    @Test fun parseNestedEjsonDateReturnsBsonDate() {
        val input: dynamic = js("{ '${'$'}date': { '${'$'}numberLong': 1620000000000 } }")
        val result = parseEjson(input)
        val ref = result.reference as HasValueReference.Runtime<*>
        assertEquals(BsonDate, ref.type)
        assertEquals(input, ref.source)
    }

    @Test fun parsePlainObjectReturnsBsonAny() {
        val input: dynamic = js("{ foo: 'bar', num: 99 }")
        val result = parseEjson(input)
        val ref = result.reference as HasValueReference.Runtime<*>
        assertEquals(BsonAny, ref.type)
        assertEquals(input, ref.source)
    }
}
