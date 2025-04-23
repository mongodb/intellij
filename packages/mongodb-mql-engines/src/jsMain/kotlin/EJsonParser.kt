import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonDate
import com.mongodb.jbplugin.mql.BsonDecimal128
import com.mongodb.jbplugin.mql.BsonDouble
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonInt64
import com.mongodb.jbplugin.mql.BsonObjectId
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.components.HasValueReference
import kotlin.js.Date

private fun jsTypeOf(v: dynamic): String = js("typeof v") as String

fun parseEjson(value: dynamic): HasValueReference<dynamic> = HasValueReference(
    when {
        jsTypeOf(value) == "string" -> HasValueReference.Runtime(value, BsonString)
        jsTypeOf(value) == "boolean" -> HasValueReference.Runtime(value, BsonBoolean)
        jsTypeOf(value) == "number" -> HasValueReference.Runtime(value, BsonDouble)
        jsTypeOf(value) == "bigint" -> HasValueReference.Runtime(value, BsonInt64)
        value is Date -> HasValueReference.Runtime(value, BsonDate)
        js("Array.isArray(value)") as Boolean -> HasValueReference.Runtime(value, BsonArray(BsonAny))
        value != null && jsTypeOf(value) == "object" -> run {
            val keys = js("Object.keys(value)") as Array<String>
            if (keys.size == 1 && keys[0].startsWith("$")) {
                when (keys[0]) {
                    "\$numberLong" -> HasValueReference.Runtime(value, BsonInt64)
                    "\$numberDecimal" -> HasValueReference.Runtime(value, BsonDecimal128)
                    "\$numberDouble" -> HasValueReference.Runtime(value, BsonDouble)
                    "\$numberInt" -> HasValueReference.Runtime(value, BsonInt32)
                    "\$oid" -> HasValueReference.Runtime(value, BsonObjectId)
                    "\$date" -> HasValueReference.Runtime(value, BsonDate)
                    else -> HasValueReference.Runtime(value, BsonAny)
                }
            } else {
                HasValueReference.Runtime(value, BsonAny)
            }
        }
        else -> HasValueReference.Runtime(value, BsonAny)
    }
)
