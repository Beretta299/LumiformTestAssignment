package com.lumiform.formviewer.core.domain

import com.lumiform.formviewer.core.domain.model.FormItem
import com.lumiform.formviewer.core.domain.model.ImageQuestion
import com.lumiform.formviewer.core.domain.model.ResponseSet
import com.lumiform.formviewer.core.domain.model.SyncResult
import com.lumiform.formviewer.core.domain.model.SyncState
import com.lumiform.formviewer.core.domain.repository.FormRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeFormRepository : FormRepository {
    val form = MutableStateFlow<List<FormItem>>(emptyList())
    val syncState = MutableStateFlow(SyncState(lastSyncedAt = null, lastError = null))

    /** The stored definition of each response set, as `updateSelection` sees it. */
    val responseSets = mutableMapOf<Long, ResponseSet>()
    val selections = mutableMapOf<Long, Set<Long>>()
    var syncResult: SyncResult = SyncResult.Success
    var syncCount = 0
        private set

    override fun observeForm(): Flow<List<FormItem>> = form

    override fun observeSyncState(): Flow<SyncState> = syncState

    override suspend fun sync(): SyncResult {
        syncCount++
        return syncResult
    }

    override suspend fun updateSelection(
        responseSetId: Long,
        transform: (responseSet: ResponseSet, selected: Set<Long>) -> Set<Long>,
    ) {
        val responseSet = responseSets[responseSetId] ?: return
        selections[responseSetId] = transform(responseSet, selections[responseSetId].orEmpty())
    }

    override suspend fun getImageQuestion(id: Long): ImageQuestion? =
        form.value.filterIsInstance<ImageQuestion>().firstOrNull { it.id == id }
}
