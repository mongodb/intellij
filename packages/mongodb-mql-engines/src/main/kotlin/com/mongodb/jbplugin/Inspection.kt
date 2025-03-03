package com.mongodb.jbplugin

import com.mongodb.jbplugin.mql.Node

interface QueryInspection<Settings> {
    suspend fun <Context, Source> run(
        query: Node<Source>,
        holder: InspectionHolder<Context, Source>,
        settings: Settings
    )
}
