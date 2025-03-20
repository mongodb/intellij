package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.mql.Node

interface QueryInspection<Settings, I : Inspection> {
    suspend fun <Source> run(
        query: Node<Source>,
        holder: QueryInsightsHolder<Source, I>,
        settings: Settings
    )
}
