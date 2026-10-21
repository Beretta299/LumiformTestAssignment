package com.lumiform.formviewer.core.data.mapper

import com.lumiform.formviewer.core.data.local.entity.SyncMetadataEntity
import com.lumiform.formviewer.core.data.remote.FormFetchException
import com.lumiform.formviewer.core.domain.model.SyncError
import com.lumiform.formviewer.core.domain.model.SyncState
import kotlin.time.Instant

internal fun SyncMetadataEntity?.toSyncState() =
    SyncState(
        lastSyncedAt = this?.lastSyncedAt?.let(Instant::fromEpochMilliseconds),
        lastError = this?.lastError?.let(::syncErrorOf),
    )

internal fun FormFetchException.toSyncError(): SyncError =
    when (this) {
        is FormFetchException.Connectivity -> SyncError.Offline
        is FormFetchException.Http -> SyncError.Server
        is FormFetchException.MalformedResponse -> SyncError.InvalidData
        is FormFetchException.Insecure -> SyncError.Insecure
    }

/** Stored instead of the enum name so constants can be renamed without breaking saved rows. */
internal val SyncError.storedValue: String
    get() =
        when (this) {
            SyncError.Offline -> "offline"
            SyncError.Server -> "server"
            SyncError.InvalidData -> "invalid_data"
            SyncError.Insecure -> "insecure"
        }

// An unknown value (e.g. written by a newer app version) only loses the error banner, so it isn't fatal.
private fun syncErrorOf(storedValue: String): SyncError? =
    SyncError.entries.firstOrNull { it.storedValue == storedValue }
