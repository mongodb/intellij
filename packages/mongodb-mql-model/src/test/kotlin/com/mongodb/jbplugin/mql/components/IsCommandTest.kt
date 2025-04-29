package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.components.IsCommand.CommandType
import com.mongodb.jbplugin.mql.components.IsCommand.CommandType.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class IsCommandTest {
    @ParameterizedTest
    @MethodSource("commandTypeAndIndexing")
    fun `commands that benefit of indexing`(cmdType: CommandType, usesIndexes: Boolean) {
        assertEquals(usesIndexes, cmdType.usesIndexes)
    }

    companion object {
        @JvmStatic
        fun commandTypeAndIndexing(): Array<Array<Any>> = arrayOf(
            arrayOf(AGGREGATE, true),
            arrayOf(COUNT_DOCUMENTS, true),
            arrayOf(DELETE_MANY, true),
            arrayOf(DELETE_ONE, true),
            arrayOf(DISTINCT, true),
            arrayOf(ESTIMATED_DOCUMENT_COUNT, true),
            arrayOf(FIND_MANY, true),
            arrayOf(FIND_ONE, true),
            arrayOf(FIND_ONE_AND_DELETE, true),
            arrayOf(FIND_ONE_AND_REPLACE, true),
            arrayOf(FIND_ONE_AND_UPDATE, true),
            arrayOf(INSERT_MANY, false),
            arrayOf(INSERT_ONE, false),
            arrayOf(REPLACE_ONE, false),
            arrayOf(UPDATE_MANY, true),
            arrayOf(UPDATE_ONE, true),
            arrayOf(UPSERT, true),
            arrayOf(RUN_COMMAND, false),
            arrayOf(UNKNOWN, false),
        )
    }
}
