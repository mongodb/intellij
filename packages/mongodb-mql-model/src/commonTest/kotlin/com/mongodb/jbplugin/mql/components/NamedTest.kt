package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.BsonAny
import com.mongodb.jbplugin.mql.BsonType
import kotlin.test.Test
import kotlin.test.assertEquals

class NamedTest {

    @Test
    fun all_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.ALL, BsonAny)
    }

    @Test
    fun and_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.AND, BsonAny)
    }

    @Test
    fun bitsAllClear_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.BITS_ALL_CLEAR, BsonAny)
    }

    @Test
    fun bitsAllSet_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.BITS_ALL_SET, BsonAny)
    }

    @Test
    fun bitsAnyClear_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.BITS_ANY_CLEAR, BsonAny)
    }

    @Test
    fun bitsAnySet_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.BITS_ANY_SET, BsonAny)
    }

    @Test
    fun combine_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.COMBINE, BsonAny)
    }

    @Test
    fun elementMatch_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.ALL, BsonAny)
    }

    @Test
    fun eq_operation_should_be_equality() {
        assertThatOperationHasRoleForType(QueryRole.EQUALITY, Name.EQ, BsonAny)
    }

    @Test
    fun exists_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.EXISTS, BsonAny)
    }

    @Test
    fun geoIntersects_operation_should_be_range() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.GEO_INTERSECTS, BsonAny)
    }

    @Test
    fun geoWithin_operation_should_be_range() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.GEO_WITHIN, BsonAny)
    }

    @Test
    fun geoWithinBox_operation_should_be_range() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.GEO_WITHIN_BOX, BsonAny)
    }

    @Test
    fun geoWithinCenter_operation_should_be_range() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.GEO_WITHIN_CENTER, BsonAny)
    }

    @Test
    fun geoWithinCenterSphere_operation_should_be_range() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.GEO_WITHIN_CENTER_SPHERE, BsonAny)
    }

    @Test
    fun geoWithinPolygon_operation_should_be_range() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.GEO_WITHIN_POLYGON, BsonAny)
    }

    @Test
    fun gt_operation_should_be_range() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.GT, BsonAny)
    }

    @Test
    fun gte_operation_should_be_range() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.GTE, BsonAny)
    }

    @Test
    fun in_operation_should_be_range() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.IN, BsonAny)
    }

    @Test
    fun inc_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.INC, BsonAny)
    }

    @Test
    fun lt_operation_should_be_range() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.LT, BsonAny)
    }

    @Test
    fun lte_operation_should_be_range() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.LTE, BsonAny)
    }

    @Test
    fun ne_operation_should_be_range() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.NE, BsonAny)
    }

    @Test
    fun near_operation_should_be_range() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.NEAR, BsonAny)
    }

    @Test
    fun nearSphere_operation_should_be_range() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.NEAR_SPHERE, BsonAny)
    }

    @Test
    fun nin_operation_should_be_range() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.NIN, BsonAny)
    }

    @Test
    fun nor_operation_should_be_union() {
        assertThatOperationHasRoleForType(QueryRole.UNION, Name.NOR, BsonAny)
    }

    @Test
    fun not_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.NOT, BsonAny)
    }

    @Test
    fun or_operation_should_be_union() {
        assertThatOperationHasRoleForType(QueryRole.UNION, Name.OR, BsonAny)
    }

    @Test
    fun regex_operation_should_be_range() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.REGEX, BsonAny)
    }

    @Test
    fun set_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.SET, BsonAny)
    }

    @Test
    fun setOnInsert_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.SET_ON_INSERT, BsonAny)
    }

    @Test
    fun size_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.SIZE, BsonAny)
    }

    @Test
    fun text_operation_should_be_range() {
        assertThatOperationHasRoleForType(QueryRole.RANGE, Name.TEXT, BsonAny)
    }

    @Test
    fun type_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.TYPE, BsonAny)
    }

    @Test
    fun unset_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.UNSET, BsonAny)
    }

    @Test
    fun match_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.MATCH, BsonAny)
    }

    @Test
    fun project_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.PROJECT, BsonAny)
    }

    @Test
    fun include_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.INCLUDE, BsonAny)
    }

    @Test
    fun exclude_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.EXCLUDE, BsonAny)
    }

    @Test
    fun group_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.GROUP, BsonAny)
    }

    @Test
    fun sum_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.SUM, BsonAny)
    }

    @Test
    fun avg_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.AVG, BsonAny)
    }

    @Test
    fun first_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.FIRST, BsonAny)
    }

    @Test
    fun last_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.LAST, BsonAny)
    }

    @Test
    fun top_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.TOP, BsonAny)
    }

    @Test
    fun topN_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.TOP_N, BsonAny)
    }

    @Test
    fun bottom_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.BOTTOM, BsonAny)
    }

    @Test
    fun bottomN_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.BOTTOM_N, BsonAny)
    }

    @Test
    fun max_operation_should_be_sort() {
        assertThatOperationHasRoleForType(QueryRole.SORT, Name.MAX, BsonAny)
    }

    @Test
    fun min_operation_should_be_sort() {
        assertThatOperationHasRoleForType(QueryRole.SORT, Name.MIN, BsonAny)
    }

    @Test
    fun push_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.PUSH, BsonAny)
    }

    @Test
    fun pull_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.PULL, BsonAny)
    }

    @Test
    fun pullAll_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.PULL_ALL, BsonAny)
    }

    @Test
    fun pop_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.POP, BsonAny)
    }

    @Test
    fun addToSet_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.ADD_TO_SET, BsonAny)
    }

    @Test
    fun sort_operation_should_be_sort() {
        assertThatOperationHasRoleForType(QueryRole.SORT, Name.SORT, BsonAny)
    }

    @Test
    fun ascending_operation_should_be_sort() {
        assertThatOperationHasRoleForType(QueryRole.SORT, Name.ASCENDING, BsonAny)
    }

    @Test
    fun descending_operation_should_be_sort() {
        assertThatOperationHasRoleForType(QueryRole.SORT, Name.DESCENDING, BsonAny)
    }

    @Test
    fun addFields_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.ADD_FIELDS, BsonAny)
    }

    @Test
    fun unwind_operation_should_be_irrelevant() {
        assertThatOperationHasRoleForType(QueryRole.IRRELEVANT, Name.UNWIND, BsonAny)
    }

    private fun assertThatOperationHasRoleForType(expectedRole: QueryRole, givenOp: Name, givenType: BsonType) {
        val actual = givenOp.queryRole(bsonType = givenType)
        assertEquals(expectedRole, actual)
    }
}
