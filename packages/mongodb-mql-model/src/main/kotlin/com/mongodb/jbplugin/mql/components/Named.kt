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
enum class Name(val canonical: String) : HasQueryRole {
    ALL("all") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    AND("and") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    BITS_ALL_CLEAR("bitsAllClear") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    BITS_ALL_SET("bitsAllSet") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    BITS_ANY_CLEAR("bitsAnyClear") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    BITS_ANY_SET("bitsAnySet") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    COMBINE("combine") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    ELEM_MATCH("elementMatch") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    EQ("eq") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.EQUALITY
    },
    EXISTS("exists") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    GEO_INTERSECTS("geoIntersects") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    GEO_WITHIN("geoWithin") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    GEO_WITHIN_BOX("geoWithinBox") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    GEO_WITHIN_CENTER("geoWithinCenter") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    GEO_WITHIN_CENTER_SPHERE("geoWithinCenterSphere") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    GEO_WITHIN_POLYGON("geoWithinPolygon") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    GT("gt") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    GTE("gte") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    IN("in") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    INC("inc") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    LT("lt") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    LTE("lte") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    NE("ne") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    NEAR("near") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    NEAR_SPHERE("nearSphere") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    NIN("nin") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    NOR("nor") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.UNION
    },
    NOT("not") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    OR("or") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.UNION
    },
    REGEX("regex") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    SET("set") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    SET_ON_INSERT("setOnInsert") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    SIZE("size") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    TEXT("text") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.RANGE
    },
    TYPE("type") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    UNSET("unset") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    MATCH("match") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    PROJECT("project") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    INCLUDE("include") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    EXCLUDE("exclude") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    GROUP("group") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    SUM("sum") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    AVG("avg") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    FIRST("first") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    LAST("last") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    TOP("top") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    TOP_N("topN") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    BOTTOM("bottom") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    BOTTOM_N("bottomN") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    MAX("max") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.SORT
    },
    MIN("min") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.SORT
    },
    PUSH("push") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    PULL("pull") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    PULL_ALL("pullAll") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    POP("pop") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    ADD_TO_SET("addToSet") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    SORT("sort") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.SORT
    },
    ASCENDING("ascending") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.SORT
    },
    DESCENDING("descending") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.SORT
    },
    ADD_FIELDS("addFields") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    UNWIND("unwind") {
        override fun queryRole(bsonType: BsonType): QueryRole = QueryRole.IRRELEVANT
    },
    UNKNOWN("<unknown operator>") {
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
