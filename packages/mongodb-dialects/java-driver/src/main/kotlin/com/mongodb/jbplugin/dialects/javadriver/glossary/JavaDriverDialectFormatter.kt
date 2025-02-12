package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.dialects.OutputQuery
import com.mongodb.jbplugin.indexing.IndexAnalyzer
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
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.QueryContext

object JavaDriverDialectFormatter : DialectFormatter {
    override suspend fun <S> formatQuery(query: Node<S>, queryContext: QueryContext) =
        OutputQuery.None

    override fun <S> indexCommand(
        query: Node<S>,
        index: IndexAnalyzer.SuggestedIndex<S>
    ): String {
        throw UnsupportedOperationException()
    }

    override fun formatType(type: BsonType): String = when (type) {
        is BsonDouble -> "double"
        is BsonString -> "String"
        is BsonObject -> "Object"
        is BsonArray -> "List<${formatTypeNullable(type.schema)}>"
        is BsonObjectId -> "ObjectId"
        is BsonBoolean -> "boolean"
        is BsonDate -> "Date"
        is BsonNull -> "null"
        is BsonInt32 -> "int"
        is BsonInt64 -> "long"
        is BsonDecimal128 -> "BigDecimal"
        is BsonUUID -> "UUID"
        is BsonAnyOf ->
            if (type.types.contains(BsonNull)) {
                formatTypeNullable(BsonAnyOf(type.types - BsonNull))
            } else {
                type.types
                    .map { formatType(it) }
                    .sorted()
                    .joinToString(" | ")
            }
        is BsonEnum -> type.name ?: (type.members.take(3).sorted().joinToString(" | ") + "...")
        else -> "any"
    }

    private fun formatTypeNullable(type: BsonType): String =
        when (type) {
            is BsonDouble -> "Double"
            is BsonString -> "String"
            is BsonObject -> "Object"
            is BsonArray -> "List<${formatTypeNullable(type.schema)}>"
            is BsonObjectId -> "ObjectId"
            is BsonBoolean -> "Boolean"
            is BsonDate -> "Date"
            is BsonNull -> "null"
            is BsonInt32 -> "Integer"
            is BsonInt64 -> "Long"
            is BsonDecimal128 -> "BigDecimal"
            is BsonAny -> "any"
            is BsonAnyOf ->
                type.types
                    .map { formatTypeNullable(it) }
                    .sorted()
                    .joinToString(" | ")

            else -> "any"
        }
}
