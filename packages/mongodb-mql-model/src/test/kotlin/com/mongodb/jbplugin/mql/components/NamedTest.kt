package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.BsonType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NamedTest {

    @Test
    fun `all operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.ALL, BsonAny)
    }

    @Test
    fun `and operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.AND, BsonAny)
    }

    @Test
    fun `bitsAllClear operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.BITS_ALL_CLEAR, BsonAny)
    }

    @Test
    fun `bitsAllSet operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.BITS_ALL_SET, BsonAny)
    }

    @Test
    fun `bitsAnyClear operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.BITS_ANY_CLEAR, BsonAny)
    }

    @Test
    fun `bitsAnySet operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.BITS_ANY_SET, BsonAny)
    }

    @Test
    fun `combine operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.COMBINE, BsonAny)
    }

    @Test
    fun `elementMatch operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.ALL, BsonAny)
    }

    @Test
    fun `eq operation should be equality`() {
        assertThatOperationHasRoleForType(QueryRole.EQUALITY, Name.EQ, BsonAny)
    }

    @Test
    fun `exists operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.EXISTS, BsonAny)
    }

    @Test
    fun `geoIntersects operation should be range`() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.GEO_INTERSECTS, BsonAny)
    }

    @Test
    fun `geoWithin operation should be range`() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.GEO_WITHIN, BsonAny)
    }

    @Test
    fun `geoWithinBox operation should be range`() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.GEO_WITHIN_BOX, BsonAny)
    }

    @Test
    fun `geoWithinCenter operation should be range`() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.GEO_WITHIN_CENTER, BsonAny)
    }

    @Test
    fun `geoWithinCenterSphere operation should be range`() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.GEO_WITHIN_CENTER_SPHERE, BsonAny)
    }

    @Test
    fun `geoWithinPolygon operation should be range`() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.GEO_WITHIN_POLYGON, BsonAny)
    }

    @Test
    fun `gt operation should be range`() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.GT, BsonAny)
    }

    @Test
    fun `gte operation should be range`() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.GTE, BsonAny)
    }

    @Test
    fun `in operation should be range`() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.IN, BsonAny)
    }

    @Test
    fun `inc operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.INC, BsonAny)
    }

    @Test
    fun `lt operation should be range`() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.LT, BsonAny)
    }

    @Test
    fun `lte operation should be range`() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.LTE, BsonAny)
    }

    @Test
    fun `ne operation should be range`() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.NE, BsonAny)
    }

    @Test
    fun `near operation should be range`() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.NEAR, BsonAny)
    }

    @Test
    fun `nearSphere operation should be range`() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.NEAR_SPHERE, BsonAny)
    }

    @Test
    fun `nin operation should be range`() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.NIN, BsonAny)
    }

    @Test
    fun `nor operation should be union`() {
        assertThatOperationHasRoleForType(QueryRole.UNION, Name.NOR, BsonAny)
    }

    @Test
    fun `not operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.NOT, BsonAny)
    }

    @Test
    fun `or operation should be union`() {
        assertThatOperationHasRoleForType(QueryRole.UNION, Name.OR, BsonAny)
    }

    @Test
    fun `regex operation should be range`() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.REGEX, BsonAny)
    }

    @Test
    fun `set operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.SET, BsonAny)
    }

    @Test
    fun `setOnInsert operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.SET_ON_INSERT, BsonAny)
    }

    @Test
    fun `size operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.SIZE, BsonAny)
    }

    @Test
    fun `text operation should be range`() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.TEXT, BsonAny)
    }

    @Test
    fun `type operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.TYPE, BsonAny)
    }

    @Test
    fun `unset operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.UNSET, BsonAny)
    }

    @Test
    fun `match operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.MATCH, BsonAny)
    }

    @Test
    fun `project operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.PROJECT, BsonAny)
    }

    @Test
    fun `include operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.INCLUDE, BsonAny)
    }

    @Test
    fun `exclude operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.EXCLUDE, BsonAny)
    }

    @Test
    fun `group operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.GROUP, BsonAny)
    }

    @Test
    fun `sum operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.SUM, BsonAny)
    }

    @Test
    fun `avg operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.AVG, BsonAny)
    }

    @Test
    fun `first operation should be sort`() {
        assertThatOperationHasRoleForType(QueryRole.SORT, Name.FIRST, BsonAny)
    }

    @Test
    fun `last operation should be sort`() {
        assertThatOperationHasRoleForType(QueryRole.SORT, Name.LAST, BsonAny)
    }

    @Test
    fun `top operation should be sort`() {
        assertThatOperationHasRoleForType(QueryRole.SORT, Name.TOP, BsonAny)
    }

    @Test
    fun `topN operation should be sort`() {
        assertThatOperationHasRoleForType(QueryRole.SORT, Name.TOP_N, BsonAny)
    }

    @Test
    fun `bottom operation should be sort`() {
        assertThatOperationHasRoleForType(QueryRole.SORT, Name.BOTTOM, BsonAny)
    }

    @Test
    fun `bottomN operation should be sort`() {
        assertThatOperationHasRoleForType(QueryRole.SORT, Name.BOTTOM_N, BsonAny)
    }

    @Test
    fun `max operation should be sort`() {
        assertThatOperationHasRoleForType(QueryRole.SORT, Name.MAX, BsonAny)
    }

    @Test
    fun `min operation should be sort`() {
        assertThatOperationHasRoleForType(QueryRole.SORT, Name.MIN, BsonAny)
    }

    @Test
    fun `push operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.PUSH, BsonAny)
    }

    @Test
    fun `pull operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.PULL, BsonAny)
    }

    @Test
    fun `pullAll operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.PULL_ALL, BsonAny)
    }

    @Test
    fun `pop operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.POP, BsonAny)
    }

    @Test
    fun `addToSet operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.ADD_TO_SET, BsonAny)
    }

    @Test
    fun `sort operation should be sort`() {
        assertThatOperationHasRoleForType(QueryRole.SORT, Name.SORT, BsonAny)
    }

    @Test
    fun `ascending operation should be sort`() {
        assertThatOperationHasRoleForType(QueryRole.SORT, Name.ASCENDING, BsonAny)
    }

    @Test
    fun `descending operation should be sort`() {
        assertThatOperationHasRoleForType(QueryRole.SORT, Name.DESCENDING, BsonAny)
    }

    @Test
    fun `addFields operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.ADD_FIELDS, BsonAny)
    }

    @Test
    fun `unwind operation should be irrelevant`() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.UNWIND, BsonAny)
    }

    private fun assertThatOperationHasRoleForType(expectedRole: QueryRole, givenOp: Name, givenType: BsonType) {
        val actual = givenOp.queryRole(bsonType = givenType)
        assertEquals(expectedRole, actual)
    }
}
