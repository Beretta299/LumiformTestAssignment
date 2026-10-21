package com.lumiform.formviewer.core.data.remote.security

import com.lumiform.formviewer.core.domain.repository.ConnectionSecurityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.security.cert.CertificateException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Tracks whether a server certificate was rejected (untrusted or not pinned), i.e. traffic is likely intercepted.
 * Lets the UI block the app until a later request gets through securely again.
 */
@Singleton
internal class ConnectionSecurityInterceptor
    @Inject
    constructor() :
    Interceptor,
        ConnectionSecurityRepository {
        private val compromised = MutableStateFlow(false)
        override val isCompromised: StateFlow<Boolean> = compromised.asStateFlow()

        override fun intercept(chain: Interceptor.Chain): Response {
            val response =
                try {
                    chain.proceed(chain.request())
                } catch (e: IOException) {
                    if (e.isCertificateRejection()) compromised.value = true
                    throw e
                }
            // Cached responses prove nothing. Assumes every host of this client is pinned; with an unpinned host,
            // clear only for pinned ones.
            if (response.networkResponse?.handshake != null) compromised.value = false
            return response
        }
    }

/**
 * A rejected certificate is deterministic, unlike a handshake cut off by a bad connection. It may be a
 * suppressed exception when OkHttp tried several routes (e.g. IPv6 and IPv4) and reports another one.
 */
internal fun Throwable.isCertificateRejection(): Boolean =
    generateSequence(this) { it.cause }.any { error ->
        error is SSLPeerUnverifiedException ||
            (
                error is SSLHandshakeException &&
                    generateSequence(error.cause) { it.cause }.any { it is CertificateException }
            )
    } ||
        suppressed.any { it.isCertificateRejection() }
