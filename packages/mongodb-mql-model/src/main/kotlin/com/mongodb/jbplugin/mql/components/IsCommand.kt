package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.Component

class IsCommand(val type: CommandType) : Component {
    enum class CommandType(val canonical: String, val usesIndexes: Boolean, val isSupported: Boolean) {
        AGGREGATE("aggregate", usesIndexes = true, isSupported = true),
        COUNT_DOCUMENTS("countDocuments", usesIndexes = true, isSupported = true),
        DELETE_MANY("deleteMany", usesIndexes = true, isSupported = true),
        DELETE_ONE("deleteOne", usesIndexes = true, isSupported = true),
        DISTINCT("distinct", usesIndexes = true, isSupported = true),
        ESTIMATED_DOCUMENT_COUNT("estimatedDocumentCount", usesIndexes = true, isSupported = true),
        FIND_MANY("find", usesIndexes = true, isSupported = true),
        FIND_ONE("findOne", usesIndexes = true, isSupported = true),
        FIND_ONE_AND_DELETE("findOneAndDelete", usesIndexes = true, isSupported = true),
        FIND_ONE_AND_REPLACE("findOneAndDelete", usesIndexes = true, isSupported = true),
        FIND_ONE_AND_UPDATE("findOneAndUpdate", usesIndexes = true, isSupported = true),
        INSERT_MANY("insertMany", usesIndexes = false, isSupported = true),
        INSERT_ONE("insertOne", usesIndexes = false, isSupported = true),
        REPLACE_ONE("replaceOne", usesIndexes = false, isSupported = true),
        UPDATE_MANY("updateMany", usesIndexes = true, isSupported = true),
        UPDATE_ONE("updateOne", usesIndexes = true, isSupported = true),
        UPSERT("updateOne", usesIndexes = true, isSupported = true), // this is update with upsert
        RUN_COMMAND("runCommand", usesIndexes = false, isSupported = false), // potentially does, but let's not face it now
        UNKNOWN("<unknown>", usesIndexes = false, isSupported = false)
    }
}
