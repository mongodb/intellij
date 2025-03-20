package com.mongodb.jbplugin.linting

interface QueryInsightsHolder<S, I : Inspection> {
    suspend fun register(insight: QueryInsight<S, I>)
}
