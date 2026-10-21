package com.lumiform.formviewer.core.data.remote

import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Exponential backoff with jitter for short retries over a flaky connection; longer outages are left to the
 * background sync. Using [delay] instead of an OkHttp interceptor keeps the wait cancellable and testable.
 */
internal data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelay: Duration = 500.milliseconds,
    val maxDelay: Duration = 5.seconds,
    val factor: Double = 2.0,
    val jitter: Double = 0.2,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
        require(jitter in 0.0..1.0) { "jitter must be within 0..1" }
    }

    fun delayBefore(
        retry: Int,
        random: Random = Random,
    ): Duration {
        // Capped before multiplying, or a large retry number overflows to Infinity (NaN for a zero delay).
        val maxMultiplier = if (initialDelay.isPositive()) maxDelay / initialDelay else 1.0
        val base = (initialDelay * factor.pow(retry - 1).coerceAtMost(maxMultiplier)).coerceAtMost(maxDelay)
        val spread = if (jitter == 0.0) 0.0 else random.nextDouble(-jitter, jitter)
        return base * (1 + spread)
    }

    suspend fun <T> retry(
        shouldRetry: (Throwable) -> Boolean,
        random: Random = Random,
        block: suspend () -> T,
    ): T {
        repeat(maxAttempts - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                if (e is CancellationException || !shouldRetry(e)) throw e
            }
            delay(delayBefore(retry = attempt + 1, random = random))
        }
        return block()
    }
}
