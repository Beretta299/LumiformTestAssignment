package com.lumiform.formviewer.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lumiform.formviewer.core.data.local.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE `key` = :key")
    fun observe(key: String): Flow<SyncMetadataEntity?>

    @Query("SELECT * FROM sync_metadata WHERE `key` = :key")
    suspend fun get(key: String): SyncMetadataEntity?

    @Upsert
    suspend fun upsert(metadata: SyncMetadataEntity)
}
