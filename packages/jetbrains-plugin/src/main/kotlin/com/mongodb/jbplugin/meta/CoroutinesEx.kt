package com.mongodb.jbplugin.meta

import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.rd.util.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class SingleExecutionJob(
    private val coroutineScope: CoroutineScope,
    private val job: AtomicReference<Job?> = AtomicReference(null)
) {
    suspend fun launch(
        onCancel: suspend () -> Unit = {},
        task: suspend CoroutineScope.() -> Unit
    ) {
        coroutineScope.launch {
            val currentJob = job.getAndSet(null)
            if (currentJob != null) {
                currentJob.cancelAndJoin()
                coroutineScope.launch {
                    onCancel()
                }
            }

            val newJob = coroutineScope.launch { task() }

            if (!job.compareAndSet(null, newJob)) {
                newJob.cancelAndJoin() // someone was faster, so cancel this job and let them continue
            }
        }.join()
    }
}

fun CoroutineScope.singleExecutionJob(name: String, context: CoroutineContext = Dispatchers.IO): SingleExecutionJob {
    return SingleExecutionJob(childScope(name, context))
}

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
