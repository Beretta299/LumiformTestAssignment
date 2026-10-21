package com.lumiform.formviewer.core.domain.repository

import com.lumiform.formviewer.core.domain.model.FormItem
import com.lumiform.formviewer.core.domain.model.ImageQuestion
import com.lumiform.formviewer.core.domain.model.ResponseSet
import com.lumiform.formviewer.core.domain.model.SyncResult
import com.lumiform.formviewer.core.domain.model.SyncState
import kotlinx.coroutines.flow.Flow

interface FormRepository {
    /** The stored form with the user's selections; emits again whenever either changes. */
    fun observeForm(): Flow<List<FormItem>>

    fun observeSyncState(): Flow<SyncState>

    /** Replaces the stored form with the server's, keeping selections that still apply. Never throws for I/O. */
    suspend fun sync(): SyncResult

    /**
     * Atomically replaces the selected responses of [responseSetId] with [transform] applied to the stored response
     * set and selection, so a tap never acts on a stale screen. Does nothing if the set no longer exists; ids outside
     * the set are dropped.
     */
    suspend fun updateSelection(
        responseSetId: Long,
        transform: (responseSet: ResponseSet, selected: Set<Long>) -> Set<Long>,
    )

    suspend fun getImageQuestion(id: Long): ImageQuestion?
}
