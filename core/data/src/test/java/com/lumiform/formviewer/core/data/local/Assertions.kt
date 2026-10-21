package com.lumiform.formviewer.core.data.local

import org.junit.Assert.fail

/** Like `assertThrows`, but for suspending code, so it can be called directly inside `runTest`. */
internal suspend inline fun <reified T : Throwable> assertThrowsSuspend(block: suspend () -> Unit): T {
    try {
        block()
    } catch (expected: Throwable) {
        if (expected is T) return expected
        throw expected
    }
    fail("Expected ${T::class.simpleName} to be thrown")
    error("unreachable")
}
