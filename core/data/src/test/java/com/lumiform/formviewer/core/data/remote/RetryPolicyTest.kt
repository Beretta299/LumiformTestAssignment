package com.lumiform.formviewer.core.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RetryPolicyTest {
    private val policy =
        RetryPolicy(
            maxAttempts = 3,
            initialDelay = 500.milliseconds,
            maxDelay = 5.seconds,
            factor = 2.0,
            jitter = 0.0,
        )

    @Test
    fun delayBefore_growsExponentially_andIsCappedAtMaxDelay() {
        val delays = (1..5).map { policy.delayBefore(retry = it) }

        assertEquals(listOf(500, 1000, 2000, 4000, 5000).map { it.milliseconds }, delays)
    }

    @Test
    fun delayBefore_withLargeRetryNumber_staysAtMaxDelay() {
        assertEquals(5.seconds, policy.delayBefore(retry = 10_000))
        assertEquals(Duration.ZERO, policy.copy(initialDelay = Duration.ZERO).delayBefore(retry = 10_000))
    }

    @Test
    fun delayBefore_staysWithinJitterBounds() {
        val jittered = policy.copy(jitter = 0.2)

        repeat(100) { seed ->
            val delay = jittered.delayBefore(retry = 1, random = Random(seed))
            assertTrue(delay in 400.milliseconds..600.milliseconds, "seed $seed gave $delay")
        }
    }

    @Test
    fun retry_retriesTransientFailures_untilSuccess_waitingBetweenAttempts() =
        runTest {
            var attempts = 0

            val result =
                policy.retry(shouldRetry = { it is IOException }) {
                    attempts++
                    if (attempts < 3) throw IOException("flaky")
                    "ok"
                }

            assertEquals("ok", result)
            assertEquals(3, attempts)
            assertEquals(1500L, currentTime)
        }

    @Test
    fun retry_givesUpAfterMaxAttempts_andThrowsTheLastError() =
        runTest {
            var attempts = 0

            val error =
                assertThrows<IOException> {
                    policy.retry(shouldRetry = { true }) {
                        attempts++
                        throw IOException("attempt $attempts")
                    }
                }

            assertEquals(3, attempts)
            assertEquals("attempt 3", error.message)
        }

    @Test
    fun retry_doesNotRetryErrorsThePredicateRejects() =
        runTest {
            var attempts = 0

            assertThrows<IllegalStateException> {
                policy.retry(shouldRetry = { it is IOException }) {
                    attempts++
                    error("permanent")
                }
            }

            assertEquals(1, attempts)
            assertEquals(0L, currentTime)
        }

    @Test
    fun retry_neverRetriesCancellation() =
        runTest {
            var attempts = 0

            assertThrows<CancellationException> {
                policy.retry(shouldRetry = { true }) {
                    attempts++
                    throw CancellationException("cancelled")
                }
            }

            assertEquals(1, attempts)
        }

    @Test
    fun invalidConfiguration_isRejected() {
        assertThrows<IllegalArgumentException> { RetryPolicy(maxAttempts = 0) }
        assertThrows<IllegalArgumentException> { RetryPolicy(jitter = 1.5) }
    }
}
