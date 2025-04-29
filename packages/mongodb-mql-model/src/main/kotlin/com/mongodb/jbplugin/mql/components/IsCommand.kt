package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.Component

class IsCommand(val type: CommandType) : Component {
    enum class CommandType(val canonical: String, val usesIndexes: Boolean) {
        AGGREGATE("aggregate", usesIndexes = true),
        COUNT_DOCUMENTS("countDocuments", usesIndexes = true),
        DELETE_MANY("deleteMany", usesIndexes = true),
        DELETE_ONE("deleteOne", usesIndexes = true),
        DISTINCT("distinct", usesIndexes = true),
        ESTIMATED_DOCUMENT_COUNT("estimatedDocumentCount", usesIndexes = true),
        FIND_MANY("find", usesIndexes = true),
        FIND_ONE("findOne", usesIndexes = true),
        FIND_ONE_AND_DELETE("findOneAndDelete", usesIndexes = true),
        FIND_ONE_AND_REPLACE("findOneAndDelete", usesIndexes = true),
        FIND_ONE_AND_UPDATE("findOneAndUpdate", usesIndexes = true),
        INSERT_MANY("insertMany", usesIndexes = false),
        INSERT_ONE("insertOne", usesIndexes = false),
        REPLACE_ONE("replaceOne", usesIndexes = false),
        UPDATE_MANY("updateMany", usesIndexes = true),
        UPDATE_ONE("updateOne", usesIndexes = true),
        UPSERT("updateOne", usesIndexes = true), // this is update with upsert
        RUN_COMMAND("runCommand", usesIndexes = false), // potentially does, but let's not face it now
        UNKNOWN("<unknown>", usesIndexes = false)
    }
}
