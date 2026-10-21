package com.lumiform.formviewer.core.data.remote

/** Why the form couldn't be fetched, without exposing Retrofit, OkHttp or kotlinx.serialization types. */
internal sealed class FormFetchException(
    message: String,
    cause: Throwable?,
) : Exception(message, cause) {
    abstract val isTransient: Boolean

    class Connectivity(
        cause: Throwable,
    ) : FormFetchException("Couldn't reach the server", cause) {
        override val isTransient = true
    }

    /** The server's certificate was rejected (untrusted or not pinned): the connection may be intercepted. */
    class Insecure(
        cause: Throwable,
    ) : FormFetchException("Server certificate was rejected", cause) {
        override val isTransient = false
    }

    class Http(
        val code: Int,
    ) : FormFetchException("Server responded with HTTP $code", null) {
        override val isTransient = code == 408 || code == 429 || code in 500..599
    }

    class MalformedResponse(
        cause: Throwable? = null,
    ) : FormFetchException("Response is not a valid form", cause) {
        override val isTransient = false
    }
}
