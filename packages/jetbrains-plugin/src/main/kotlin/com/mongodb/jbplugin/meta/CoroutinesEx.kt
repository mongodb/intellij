package com.mongodb.jbplugin.meta

import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

suspend fun <T> withinReadAction(cb: suspend () -> T): T = withContext(Dispatchers.IO) {
    ApplicationManager.getApplication().runReadAction<T> {
        runBlocking {
            cb()
        }
    }
}

fun <T> withinReadActionBlocking(cb: () -> T): T {
    return ApplicationManager.getApplication().runReadAction<T> {
        cb()
    }
}
