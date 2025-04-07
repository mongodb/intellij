package com.mongodb.jbplugin.meta

import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CoroutinesExTest {
    @Test
    fun `should cancel a job before starting a new one`() = runTest {
        var runFully = false

        launch {
            val job = SingleExecutionJob(this)
            job.launch {
                for (i in 0..100) { // simulate a long-running process
                    delay(1)
                    ensureActive()
                }
                runFully = true
            }

            job.launch { }
        }

        testScheduler.advanceUntilIdle() // this will cancel the job and schedule onCancel
        assertFalse(runFully)
    }
}
