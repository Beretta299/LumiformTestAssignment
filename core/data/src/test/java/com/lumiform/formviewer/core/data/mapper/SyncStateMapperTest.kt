package com.lumiform.formviewer.core.data.mapper

import com.lumiform.formviewer.core.data.local.entity.SyncMetadataEntity
import com.lumiform.formviewer.core.data.remote.FormFetchException
import com.lumiform.formviewer.core.domain.model.SyncError
import com.lumiform.formviewer.core.domain.model.SyncState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.IOException
import kotlin.time.Instant

class SyncStateMapperTest {
    @Test
    fun noMetadata_isNeverSyncedWithoutError() {
        assertEquals(SyncState(lastSyncedAt = null, lastError = null), null.toSyncState())
    }

    @Test
    fun metadata_mapsTimeAndError() {
        val metadata = SyncMetadataEntity("form", lastSyncedAt = 1_000, lastError = "offline")

        assertEquals(SyncState(Instant.fromEpochMilliseconds(1_000), SyncError.Offline), metadata.toSyncState())
    }

    @ParameterizedTest
    @EnumSource(SyncError::class)
    fun storedError_roundTrips(error: SyncError) {
        val metadata = SyncMetadataEntity("form", lastSyncedAt = null, lastError = error.storedValue)

        assertEquals(error, metadata.toSyncState().lastError)
    }

    @Test
    fun unknownStoredError_isDropped() {
        assertEquals(null, SyncMetadataEntity("form", null, "from_the_future").toSyncState().lastError)
    }

    @Test
    fun fetchExceptions_mapToSyncErrors() {
        val cause = IOException()

        assertEquals(SyncError.Offline, FormFetchException.Connectivity(cause).toSyncError())
        assertEquals(SyncError.Server, FormFetchException.Http(503).toSyncError())
        assertEquals(SyncError.InvalidData, FormFetchException.MalformedResponse().toSyncError())
        assertEquals(SyncError.Insecure, FormFetchException.Insecure(cause).toSyncError())
    }
}
