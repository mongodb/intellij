package com.mongodb.jbplugin

import com.mongodb.jbplugin.mql.Node

interface QueryInspection<Settings> {
    suspend fun <Source> run(
        query: Node<Source>,
        holder: InspectionHolder<Source>,
        settings: Settings
    )
}
