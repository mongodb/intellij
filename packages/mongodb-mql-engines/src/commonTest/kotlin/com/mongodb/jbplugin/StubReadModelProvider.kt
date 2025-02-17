package com.mongodb.jbplugin

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.Slice

class StubReadModelProvider<D>(
    private val responses: Map<Slice<*>, () -> Any?> = emptyMap(),
    private val default: () -> Any? = { null },
) : MongoDbReadModelProvider<D> {
    override fun <T : Any> slice(
        dataSource: D,
        slice: Slice<T>
    ): T {
        return (responses[slice] ?: default).invoke() as T
    }
}
