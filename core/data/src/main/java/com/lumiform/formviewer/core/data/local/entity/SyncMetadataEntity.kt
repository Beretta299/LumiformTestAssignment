package com.lumiform.formviewer.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long?,
    @ColumnInfo(name = "last_error") val lastError: String?,
)
