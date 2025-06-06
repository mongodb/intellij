package com.mongodb.jbplugin.dialects.mongosh.backend

import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.BsonAnyOf
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonDate
import com.mongodb.jbplugin.mql.BsonDecimal128
import com.mongodb.jbplugin.mql.BsonDouble
import com.mongodb.jbplugin.mql.BsonEnum
import com.mongodb.jbplugin.mql.BsonInt32
import com.mongodb.jbplugin.mql.BsonInt64
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.BsonObjectId
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.BsonUUID
import com.mongodb.jbplugin.mql.ComputedBsonType
import com.mongodb.jbplugin.mql.QueryContext
import com.mongodb.jbplugin.mql.QueryContext.AsIs
import org.bson.types.ObjectId
import org.owasp.encoder.Encode
import java.math.BigDecimal
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.*

/**
 * @param context
 */
class MongoshBackend(
    private val context: Context = DefaultContext(),
    val prettyPrint: Boolean = false,
    val automaticallyRun: Boolean = false,
    private val paddingSpaces: Int = 2
) : Context by context {
    private val output: StringBuilder = StringBuilder()

    private var line: Int = 0
    private var column: Int = 0
    private var paddingScopes: Stack<Int> = Stack<Int>().apply {
        push(0)
    }

    fun applyQueryExpansions(context: QueryContext): MongoshBackend {
        for (variable in context.expansions) {
            registerVariable(variable.key, variable.value.type, variable.value.defaultValue)
        }

        return this
    }

    fun emitDbAccess(): MongoshBackend {
        emitAsIs("db")
        return emitPropertyAccess()
    }

    suspend fun emitDatabaseAccess(dbName: ContextValue): MongoshBackend {
        val nextPadding = column - 1 // align to the dot

        emitAsIs("getSiblingDB")
        emitFunctionCall(long = false, {
            emitContextValue(dbName)
        })

        if (prettyPrint) {
            paddingScopes.push(nextPadding)
            emitNewLine()
        }

        return emitPropertyAccess()
    }

    suspend fun emitCollectionAccess(collName: ContextValue): MongoshBackend {
        emitAsIs("getCollection")
        emitFunctionCall(long = false, {
            emitContextValue(collName)
        })

        if (prettyPrint) {
            emitNewLine()
        }

        return this
    }

    fun emitObjectStart(long: Boolean = false): MongoshBackend {
        val nextPadding = paddingScopes.peek() + paddingSpaces
        if (long && prettyPrint) {
            paddingScopes.push(nextPadding)
            emitAsIs("{")
            emitNewLine()
        } else {
            emitAsIs("{")
        }
        return this
    }

    fun emitObjectEnd(long: Boolean = false): MongoshBackend {
        if (long && prettyPrint) {
            paddingScopes.pop()
            emitNewLine()
        }

        emitAsIs("}")
        return this
    }

    fun emitArrayStart(long: Boolean = false): MongoshBackend {
        val nextPadding = paddingScopes.peek() + paddingSpaces
        if (long && prettyPrint) {
            paddingScopes.push(nextPadding)
            emitAsIs("[")
            emitNewLine()
        } else {
            emitAsIs("[")
        }
        return this
    }

    fun emitArrayEnd(long: Boolean = false): MongoshBackend {
        if (long && prettyPrint) {
            paddingScopes.pop()
            emitNewLine()
        }
        emitAsIs("]")
        return this
    }

    fun emitObjectKey(key: ContextValue): MongoshBackend {
        when (key) {
            is ContextValue.Variable -> emitAsIs("[${key.name}]")
            is ContextValue.Constant -> emitPrimitive(key.value)
        }
        emitAsIs(": ")
        return this
    }

    fun emitObjectValueEnd(long: Boolean = false): MongoshBackend {
        emitAsIs(", ")
        if (long && prettyPrint) {
            emitNewLine()
        }
        return this
    }

    fun computeOutput(): String {
        val preludeBackend =
            MongoshBackend(context, prettyPrint, automaticallyRun = false, paddingSpaces)

        preludeBackend.variableList().sortedBy { it.name }.forEach {
            preludeBackend.emitVariableDeclaration(it.name, it.type, it.value)
        }

        val prelude = preludeBackend.output.toString()

        // if we are forced to run this query, wrap it into a function and call itself
        if (automaticallyRun) {
            return "(function () { ${prelude.replace('\n', ';')} return $output; })()"
        }

        return (prelude + "\n" + output.toString()).trim()
    }

    fun emitFunctionName(name: String): MongoshBackend = emitAsIs(name)

    suspend fun emitFunctionCall(long: Boolean = false, vararg body: suspend MongoshBackend.() -> MongoshBackend?): MongoshBackend {
        emitAsIs("(")
        if (body.isNotEmpty()) {
            if (long && prettyPrint) {
                val nextDelta = column - (paddingSpaces / 2)
                paddingScopes.push(nextDelta)
                emitNewLine()
            }

            val args = body.mapNotNull {
                MongoshBackend(
                    context,
                    prettyPrint,
                    automaticallyRun = false,
                    paddingSpaces
                ).it()
            }.joinToString(
                separator = when (prettyPrint) {
                    true -> ",${newLineWithPaddingString()}"
                    false -> ", "
                }
            ) { it.output.toString() }

            output.append(args) // it's already encoded by the child backends
        }

        if (long && prettyPrint) {
            paddingScopes.pop()
            emitNewLine()
        }

        emitAsIs(")")
        return this
    }

    fun didNotEmit() = null

    fun emitPropertyAccess(): MongoshBackend {
        emitAsIs(".")
        return this
    }

    fun emitComment(comment: String): MongoshBackend {
        emitAsIs("/* $comment */")
        return this
    }

    fun emitContextValue(value: ContextValue): MongoshBackend {
        when (value) {
            is ContextValue.Constant -> emitPrimitive(value.value)
            is ContextValue.Variable -> emitAsIs(value.name)
        }

        return this
    }

    fun emitNewLine(): MongoshBackend {
        emitAsIs(newLineWithPaddingString(), encode = false)
        return this
    }

    private fun newLineWithPaddingString(): String {
        line += 1
        column = 1

        return "\n" + " ".repeat(paddingScopes.lastOrNull() ?: 0)
    }

    fun emitAsIs(string: String, encode: Boolean = true): MongoshBackend {
        val stringToOutput = if (encode) Encode.forJavaScript(string) else string

        output.append(stringToOutput)
        column += stringToOutput.length
        return this
    }

    private fun emitVariableDeclaration(name: String, type: BsonType, value: Any?): MongoshBackend {
        emitAsIs("var ")
        emitAsIs(name)
        emitAsIs(" = ")
        if (value == null || (value as? AsIs)?.isEmpty == true) {
            emitPrimitive(defaultValueOfBsonType(type))
        } else {
            emitPrimitive(value)
        }
        emitNewLine()
        return this
    }

    private fun emitPrimitive(value: Any?): MongoshBackend {
        emitAsIs(serializePrimitive(value), encode = false)
        return this
    }
}

private fun serializePrimitive(value: Any?): String = when (value) {
    is BigInteger -> "Decimal128(\"$value\")"
    is BigDecimal -> "Decimal128(\"$value\")"
    is Byte, is Short, is Int, is Long, is Float, is Double, is Number -> value.toString()
    is Boolean -> value.toString()
    is ObjectId -> "ObjectId(\"${Encode.forJavaScript(value.toHexString())}\")"
    is String -> '"' + Encode.forJavaScript(value) + '"'
    is Date -> "ISODate(\"${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(value)}\")"
    is Instant, is LocalDate, is LocalDateTime, is TemporalAccessor ->
        "ISODate(\"${DateTimeFormatter.ISO_DATE_TIME.format(value as TemporalAccessor)}\")"
    is UUID -> "UUID(\"$value\")"
    is Collection<*> -> value.joinToString(separator = ", ", prefix = "[", postfix = "]") {
        serializePrimitive(it)
    }

    is Map<*, *> -> value.entries.joinToString(separator = ", ", prefix = "{", postfix = "}") {
        "\"${it.key}\": ${serializePrimitive(it.value)}"
    }
    is AsIs -> value.value
    null -> "null"
    else -> "{}"
}

private fun defaultValueOfBsonType(type: BsonType): Any? = when (type) {
    BsonAny -> "any"
    is BsonAnyOf -> defaultValueOfBsonType(type.types.firstOrNull { it !is BsonNull } ?: BsonAny)
    is BsonArray -> emptyList<Any>()
    BsonBoolean -> false
    BsonDate -> LocalDateTime.of(2009, 2, 11, 18, 0)
    BsonDecimal128 -> BigInteger.ZERO
    BsonDouble -> 0.0
    BsonInt32 -> 0
    BsonInt64 -> 0
    BsonUUID -> UUID.randomUUID()
    BsonNull -> null
    is BsonObject -> emptyMap<Any, Any>()
    BsonObjectId -> ObjectId("000000000000000000000000")
    BsonString -> ""
    is ComputedBsonType<*> -> defaultValueOfBsonType(type.baseType)
    is BsonEnum -> type.members.firstOrNull() ?: ""
}
