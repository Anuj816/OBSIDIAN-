package com.anuj.obsidian.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun CoroutineScope.safeLaunch(
    tag: String = "Coroutine",
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job {
    return this.launch(context, start) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) {
                throw e
            }
            com.anuj.obsidian.ObsidianCrashLogger.logException(e, "SafeLaunch caught exception in $tag")
        }
    }
}
