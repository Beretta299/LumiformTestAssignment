package com.lumiform.formviewer.core.data.remote.security

import com.lumiform.formviewer.core.data.remote.FormFetchException
import com.lumiform.formviewer.core.data.remote.RetrofitFormRemoteDataSource
import com.lumiform.formviewer.core.data.remote.RetryPolicy
import com.lumiform.formviewer.core.data.remote.createFormApi
import com.lumiform.formviewer.core.data.remote.formFixture
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.time.Duration

class ConnectionSecurityInterceptorTest {
    private val rootCa = HeldCertificate.Builder().certificateAuthority(0).build()
    private val serverCertificate =
        HeldCertificate
            .Builder()
            .addSubjectAlternativeName(HOST)
            .signedBy(rootCa)
            .build()
    private val otherCa = HeldCertificate.Builder().certificateAuthority(0).build()
    private val server = MockWebServer()
    private val interceptor = ConnectionSecurityInterceptor()

    @BeforeEach
    fun setUp() {
        val serverCertificates =
            HandshakeCertificates
                .Builder()
                .heldCertificate(serverCertificate, rootCa.certificate)
                .build()
        server.useHttps(serverCertificates.sslSocketFactory())
        server.start(InetAddress.getByName(HOST), 0)
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun client(pinnedCa: HeldCertificate): OkHttpClient {
        val trusted = HandshakeCertificates.Builder().addTrustedCertificate(rootCa.certificate).build()
        return OkHttpClient
            .Builder()
            .sslSocketFactory(trusted.sslSocketFactory(), trusted.trustManager)
            .certificatePinner(
                CertificatePinner.Builder().add(HOST, CertificatePinner.pin(pinnedCa.certificate)).build(),
            )
            // One route only, so the pin failure is the exception that surfaces.
            .dns { listOf(InetAddress.getByName(HOST)) }
            .addInterceptor(interceptor)
            .build()
    }

    private fun OkHttpClient.get() = newCall(Request.Builder().url(server.url("/")).build()).execute().close()

    @Test
    fun initially_isNotCompromised() {
        assertFalse(interceptor.isCompromised.value)
    }

    @Test
    fun pinMismatch_failsTheCall_andMarksCompromised() {
        assertThrows<SSLPeerUnverifiedException> { client(pinnedCa = otherCa).get() }

        assertTrue(interceptor.isCompromised.value)
    }

    @Test
    fun secureResponseAfterRejection_clearsCompromised() {
        server.enqueue(MockResponse.Builder().code(200).build())
        assertThrows<SSLPeerUnverifiedException> { client(pinnedCa = otherCa).get() }

        client(pinnedCa = rootCa).get()

        assertFalse(interceptor.isCompromised.value)
    }

    @Test
    fun pinMismatch_failsFetchAsInsecure_withoutRetrying() =
        runTest {
            val retryPolicy = RetryPolicy(maxAttempts = 3, initialDelay = Duration.ZERO, jitter = 0.0)
            val dataSource =
                RetrofitFormRemoteDataSource(createFormApi(server.url("/"), client(pinnedCa = otherCa)), retryPolicy)
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body(formFixture())
                    .build(),
            )

            assertThrows<FormFetchException.Insecure> { dataSource.fetchForm() }

            assertEquals(0, server.requestCount)
            assertTrue(interceptor.isCompromised.value)
        }

    @Test
    fun isCertificateRejection_onlyMatchesCertificateFailures() {
        val untrusted = SSLHandshakeException("handshake failed").apply { initCause(CertificateException("untrusted")) }

        assertTrue(SSLPeerUnverifiedException("Certificate pinning failure").isCertificateRejection())
        assertTrue(untrusted.isCertificateRejection())
        assertTrue(ConnectException("other route refused").apply { addSuppressed(untrusted) }.isCertificateRejection())
        assertFalse(SSLHandshakeException("Connection reset during handshake").isCertificateRejection())
        assertFalse(IOException("offline").isCertificateRejection())
    }

    private companion object {
        const val HOST = "localhost"
    }
}
