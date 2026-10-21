package com.lumiform.formviewer.core.domain.model

import kotlin.time.Instant

/** [lastSyncedAt] is null until the first successful sync; [lastError] is null after a successful one. */
data class SyncState(
    val lastSyncedAt: Instant?,
    val lastError: SyncError?,
)

enum class SyncError {
    /** The server couldn't be reached. */
    Offline,

    /** The server answered with an error status. */
    Server,

    /** The server's response isn't a valid form. */
    InvalidData,

    /** The server's certificate was rejected, so the connection may be intercepted. */
    Insecure,
}

sealed interface SyncResult {
    data object Success : SyncResult

    data class Failure(
        val error: SyncError,
    ) : SyncResult
}
