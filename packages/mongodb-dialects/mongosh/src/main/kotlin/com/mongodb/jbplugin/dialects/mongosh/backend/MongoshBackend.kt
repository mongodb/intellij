package com.mongodb.jbplugin.dialects.mongosh.backend

import com.mongodb.jbplugin.mql.*
import com.mongodb.jbplugin.mql.QueryContext.AsIs
import org.bson.types.ObjectId
import org.owasp.encoder.Encode
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.*

private const val MONGODB_FIRST_RELEASE = "2009-02-11T18:00:00.000Z"

/**
 * @param context
 */
class MongoshBackend(
    private val context: Context = DefaultContext(),
    val prettyPrint: Boolean = false,
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

    fun emitDatabaseAccess(dbName: ContextValue): MongoshBackend {
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

    fun emitCollectionAccess(collName: ContextValue): MongoshBackend {
        emitAsIs("getCollection")
        emitFunctionCall(long = false, {
            emitContextValue(collName)
        })

        if (prettyPrint) {
            emitNewLine()
        }

        return emitPropertyAccess()
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
        val preludeBackend = MongoshBackend(context, prettyPrint, paddingSpaces)
        preludeBackend.variableList().sortedBy { it.name }.forEach {
            preludeBackend.emitVariableDeclaration(it.name, it.type, it.value)
        }

        val prelude = preludeBackend.output.toString()
        return (prelude + "\n" + output.toString()).trim()
    }

    fun emitFunctionName(name: String): MongoshBackend = emitAsIs(name)

    fun emitFunctionCall(long: Boolean = false, vararg body: MongoshBackend.() -> MongoshBackend): MongoshBackend {
        emitAsIs("(")
        if (body.isNotEmpty()) {
            if (long && prettyPrint) {
                val nextDelta = column - (paddingSpaces / 2)
                paddingScopes.push(nextDelta)
                emitNewLine()
            }

            body[0].invoke(this)
            body.slice(1 until body.size).forEach {
                if (long && prettyPrint) {
                    emitNewLine()
                }
                emitAsIs(", ")
                it(this)
            }
        }

        if (long && prettyPrint) {
            paddingScopes.pop()
            emitNewLine()
        }

        emitAsIs(")")
        return this
    }

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
        output.append("\n")
        emitAsIs(" ".repeat(paddingScopes.lastOrNull() ?: 0))

        line += 1
        column = 1

        return this
    }

    /**
     * Emits a literal string value into the script.
     *
     * This function is <b>unsafe</b>, <b>it does not encode input values, so it's sensitive
     * to code injection</b>. Only use this for well-known, literal constant values that we have
     * control of.
     *
     * @see emitContextValue for dynamic values provided by the user.
     */
    fun emitStringLiteral(value: String): MongoshBackend {
        return emitAsIs("\"$value\"", encode = false)
    }

    private fun emitAsIs(string: String, encode: Boolean = true): MongoshBackend {
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
    is Date, is Instant, is LocalDate, is LocalDateTime, is TemporalAccessor ->
        "ISODate(\"${DateTimeFormatter.ISO_DATE_TIME.format(value as TemporalAccessor)}\")"
    is UUID -> "UUID(\"$value\")"
    is Collection<*> -> value.joinToString(separator = ", ", prefix = "[", postfix = "]") {
        serializePrimitive(it)
    }

    is Map<*, *> -> value.entries.joinToString(separator = ", ", prefix = "{", postfix = "}") {
        "\"${it.key}\": ${serializePrimitive(it.value)}"
    }
    is QueryContext.AsIs -> value.value.toString()
    null -> "null"
    else -> "{}"
}

private fun defaultValueOfBsonType(type: BsonType): Any? = when (type) {
    BsonAny -> "any"
    is BsonAnyOf -> defaultValueOfBsonType(type.types.firstOrNull { it !is BsonNull } ?: BsonAny)
    is BsonArray -> emptyList<Any>()
    BsonBoolean -> false
    BsonDate -> Date.from(Instant.parse(MONGODB_FIRST_RELEASE))
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
}
