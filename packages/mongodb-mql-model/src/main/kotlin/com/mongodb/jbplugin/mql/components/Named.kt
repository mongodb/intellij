/**
 * The named component represents metadata of the name of the operation that a specific
 * node represents.
 */

package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.Component

/**
 * Specifies the role of the operator given the query, based on how it should be used when indexing.
 * The QueryRole can varify depending on the operator and the type it's applied to.
 */
enum class QueryRole {
    EQUALITY,
    SORT,
    RANGE,
    UNION,
    IRRELEVANT
}

interface HasQueryRole {
    fun queryRole(bsonType: BsonType): QueryRole
}

/**
 * A canonical representation of operations. All operations relevant in the driver
 * are listed here.
 *
 * @property canonical
 */
enum class Name(val canonical: String, val isSupported: Boolean) : HasQueryRole {
    ALL("all", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    AND("and", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    BITS_ALL_CLEAR("bitsAllClear", isSupported = false) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    BITS_ALL_SET("bitsAllSet", isSupported = false) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    BITS_ANY_CLEAR("bitsAnyClear", isSupported = false) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    BITS_ANY_SET("bitsAnySet", isSupported = false) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    COMBINE("combine", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    ELEM_MATCH("elemMatch", isSupported = false) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    EMPTY("empty", isSupported = false) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.EQUALITY
    },
    EQ("eq", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.EQUALITY
    },
    EXISTS("exists", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    EXPR("expr", isSupported = false) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    GEO_INTERSECTS("geoIntersects", isSupported = false) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    GEO_WITHIN("geoWithin", isSupported = false) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    GEO_WITHIN_BOX("geoWithinBox", isSupported = false) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    GEO_WITHIN_CENTER("geoWithinCenter", isSupported = false) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    GEO_WITHIN_CENTER_SPHERE("geoWithinCenterSphere", isSupported = false) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    GEO_WITHIN_POLYGON("geoWithinPolygon", isSupported = false) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    GT("gt", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    GTE("gte", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    IN("in", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    INC("inc", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },

    // We purposely have it commented out to test for the negative case when there is a new Filter,
    // and we did not update this table. In such a case, the parser should just identify the filter
    // as UNKNOWN. If we decide to add support for this operation, then feel free to uncomment this
    // and also update / remove the test case.
    // JSON_SCHEMA("jsonSchema", isSupported = false) {
    //     override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    // },
    LT("lt", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    LTE("lte", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    MOD("mod", isSupported = false) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    NE("ne", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    NEAR("near", isSupported = false) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    NEAR_SPHERE("nearSphere", isSupported = false) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    NIN("nin", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    NOR("nor", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.UNION
    },
    NOT("not", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    OR("or", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.UNION
    },
    REGEX("regex", isSupported = false) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    SET("set", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    SET_ON_INSERT("setOnInsert", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    SIZE("size", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    TEXT("text", isSupported = false) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    TYPE("type", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    WHERE("where", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    UNSET("unset", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    MATCH("match", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    PROJECT("project", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    INCLUDE("include", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    EXCLUDE("exclude", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    GROUP("group", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    SUM("sum", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    AVG("avg", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    FIRST("first", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    LAST("last", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    TOP("top", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    TOP_N("topN", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    BOTTOM("bottom", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    BOTTOM_N("bottomN", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    MAX("max", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.SORT
    },
    MIN("min", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.SORT
    },
    PUSH("push", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    PULL("pull", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    PULL_ALL("pullAll", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    POP("pop", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    ADD_TO_SET("addToSet", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    SORT("sort", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.SORT
    },
    ASCENDING("ascending", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.SORT
    },
    DESCENDING("descending", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.SORT
    },
    ADD_FIELDS("addFields", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    UNWIND("unwind", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    LIMIT("limit", isSupported = true) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    UNKNOWN("<unknown operator>", isSupported = false) {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    };

    override fun toString(): String = canonical

    companion object {
        fun from(canonical: String): Name =
            entries.firstOrNull { it.canonical == canonical } ?: UNKNOWN
    }
}

/**
 * Represents an operation that has a name. Usually most operations will have a name.
 *
 * @property name
 */
data class Named(
    val name: Name,
) : Component, HasQueryRole by name
