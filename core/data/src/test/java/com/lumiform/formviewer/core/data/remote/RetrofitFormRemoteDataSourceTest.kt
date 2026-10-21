package com.lumiform.formviewer.core.data.remote

import com.lumiform.formviewer.core.data.remote.dto.FormItemDto
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import retrofit2.Response
import java.io.File
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.time.Duration

class RetrofitFormRemoteDataSourceTest {
    private val server = MockWebServer()
    private val retryPolicy = RetryPolicy(maxAttempts = 3, initialDelay = Duration.ZERO, jitter = 0.0)

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun dataSource(client: OkHttpClient = OkHttpClient()) =
        RetrofitFormRemoteDataSource(createFormApi(server.url("/"), client), retryPolicy)

    private fun formResponse(): MockResponse =
        MockResponse
            .Builder()
            .code(200)
            .body(formFixture())
            .build()

    private fun errorResponse(code: Int): MockResponse = MockResponse.Builder().code(code).build()

    @Test
    fun fetchForm_requestsTheFormPath_andParsesTheBody() =
        runTest {
            server.enqueue(formResponse())

            val form = dataSource().fetchForm()

            assertEquals(FormJson.decodeFromString<List<FormItemDto>>(formFixture()), form)
            val request = server.takeRequest()
            assertEquals("/${FormApi.FORM_PATH}", request.target)
            assertEquals("max-age=0", request.headers["Cache-Control"])
        }

    @ParameterizedTest
    @ValueSource(ints = [408, 429, 500, 503])
    fun fetchForm_retriesTransientHttpErrors(code: Int) =
        runTest {
            server.enqueue(errorResponse(code))
            server.enqueue(formResponse())

            dataSource().fetchForm()

            assertEquals(2, server.requestCount)
        }

    @Test
    fun fetchForm_whenTransientErrorPersists_failsAfterMaxAttempts() =
        runTest {
            repeat(3) { server.enqueue(errorResponse(503)) }

            val error = assertThrows<FormFetchException.Http> { dataSource().fetchForm() }

            assertEquals(503, error.code)
            assertEquals(3, server.requestCount)
        }

    @ParameterizedTest
    @ValueSource(ints = [400, 403, 404])
    fun fetchForm_doesNotRetryClientErrors(code: Int) =
        runTest {
            server.enqueue(errorResponse(code))

            val error = assertThrows<FormFetchException.Http> { dataSource().fetchForm() }

            assertEquals(code, error.code)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun fetchForm_withMalformedBody_failsWithoutRetrying() =
        runTest {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body("<html>Rate limited</html>")
                    .build(),
            )

            assertThrows<FormFetchException.MalformedResponse> { dataSource().fetchForm() }

            assertEquals(1, server.requestCount)
        }

    @Test
    fun fetchForm_withEmptySuccessfulResponse_failsWithoutRetrying() =
        runTest {
            server.enqueue(MockResponse.Builder().code(204).build())

            assertThrows<FormFetchException.MalformedResponse> { dataSource().fetchForm() }

            assertEquals(1, server.requestCount)
        }

    @Test
    fun fetchForm_whenServerIsUnreachable_failsWithConnectivity() =
        runTest {
            val dataSource = dataSource()
            server.close()

            assertThrows<FormFetchException.Connectivity> { dataSource.fetchForm() }
        }

    @Test
    fun fetchForm_whenResponseIsTooSlow_retriesAndFailsWithConnectivity() =
        runTest {
            repeat(3) {
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(200)
                        .body(formFixture())
                        .headersDelay(1, TimeUnit.SECONDS)
                        .build(),
                )
            }
            val client = OkHttpClient.Builder().readTimeout(100, TimeUnit.MILLISECONDS).build()

            assertThrows<FormFetchException.Connectivity> { dataSource(client).fetchForm() }

            assertEquals(3, server.requestCount)
        }

    @Test
    fun fetchForm_revalidatesCachedForm_andUsesItOnNotModified(
        @TempDir cacheDir: File,
    ) = runTest {
        val client = OkHttpClient.Builder().cache(Cache(cacheDir, 1024 * 1024)).build()
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body(formFixture())
                .addHeader("ETag", "\"v1\"")
                .addHeader("Cache-Control", "max-age=300")
                .build(),
        )
        server.enqueue(
            MockResponse
                .Builder()
                .code(304)
                .addHeader("ETag", "\"v1\"")
                .build(),
        )
        val dataSource = dataSource(client)

        val first = dataSource.fetchForm()
        val second = dataSource.fetchForm()

        assertEquals(first, second)
        assertNull(server.takeRequest().headers["If-None-Match"])
        assertEquals("\"v1\"", server.takeRequest().headers["If-None-Match"])
    }

    @ParameterizedTest
    @ValueSource(strings = ["peerUnverified", "handshakeWithCertificateCause"])
    fun fetchForm_whenCertificateIsRejected_failsAsInsecureWithoutRetrying(kind: String) =
        runTest {
            val rejection =
                when (kind) {
                    "peerUnverified" -> {
                        SSLPeerUnverifiedException("Pin verification failed")
                    }

                    else -> {
                        SSLHandshakeException("handshake failed").apply {
                            initCause(CertificateException("Pin verification failed"))
                        }
                    }
                }
            val api = ThrowingFormApi(rejection)

            assertThrows<FormFetchException.Insecure> { RetrofitFormRemoteDataSource(api, retryPolicy).fetchForm() }

            assertEquals(1, api.calls)
        }

    @Test
    fun fetchForm_whenHandshakeIsInterrupted_retriesAsConnectivity() =
        runTest {
            val api = ThrowingFormApi(SSLHandshakeException("Connection reset during handshake"))

            assertThrows<FormFetchException.Connectivity> { RetrofitFormRemoteDataSource(api, retryPolicy).fetchForm() }

            assertEquals(3, api.calls)
        }

    private class ThrowingFormApi(
        private val error: Exception,
    ) : FormApi {
        var calls = 0

        override suspend fun getForm(): Response<List<FormItemDto>> {
            calls++
            throw error
        }
    }
}
