/**
 * Provides functions to simplify assertions.
 */

package com.mongodb.jbplugin.fixtures

import java.time.Duration

/**
 * Waits for a given condition to be success, or throws an assertion error on timeout.
 *
 */
fun <T> waitFor(timeout: Duration, interval: Duration, condition: () -> Pair<Boolean, T>): T {
    if (timeout <= Duration.ZERO) {
        throw AssertionError("Test timed out.")
    }

    val (success, result) = condition()
    if (success) {
        return result
    } else {
        Thread.sleep(interval.toMillis())
        return waitFor(timeout - interval, interval, condition)
    }
}

/**
 *  Convenience method that uses a single boolean value instead of a pair.
 *
 *  If the condition throws an exception or returns false it's considered a failure.
 */
fun waitFor(timeout: Duration, interval: Duration, condition: () -> Boolean) {
    waitFor<Boolean>(timeout, interval) {
        val (success, result) = runCatching {
            val value = runCatching { condition() }.getOrDefault(false)
            value to value
        }.getOrDefault(false to false)

        success to result
    }
}

/**
 * Waits until the block function finishes successfully up to 1 second (or the provided timeout).
 *
 * Example usages:
 *
 * ```kt
 * eventually {
 *    verify(mock).myFunction()
 * }
 * // with custom timeout
 * eventually(timeout = Duration.ofSeconds(5)) {
 *    verify(mock).myFunction()
 * }
 * ```
 *
 * @param timeout
 * @param fn
 * @param recovery
 */
fun eventually(
    timeout: Duration = Duration.ofSeconds(10),
    recovery: () -> Unit = {},
    fn: (Int) -> Unit,
) {
    var attempt = 1
    waitFor(timeout, Duration.ofMillis(50)) {
        val result = runCatching {
            fn(attempt++)
        }

        result.exceptionOrNull()?.printStackTrace(System.err)

        if (result.isFailure) {
            recovery()
        }

        result.isSuccess
    }
}
