package com.lumiform.formviewer.ui.form

import com.lumiform.formviewer.core.domain.model.FormItem
import com.lumiform.formviewer.core.domain.model.ImageQuestion
import com.lumiform.formviewer.core.domain.model.ResponseSet
import com.lumiform.formviewer.core.domain.model.SyncResult
import com.lumiform.formviewer.core.domain.model.SyncState
import com.lumiform.formviewer.core.domain.repository.ConnectionSecurityRepository
import com.lumiform.formviewer.core.domain.repository.ConnectivityRepository
import com.lumiform.formviewer.core.domain.repository.FormRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeFormRepository : FormRepository {
    val form = MutableStateFlow<List<FormItem>>(emptyList())
    val syncState = MutableStateFlow(SyncState(lastSyncedAt = null, lastError = null))
    val responseSets = mutableMapOf<Long, ResponseSet>()
    val selections = mutableMapOf<Long, Set<Long>>()
    var syncResult: SyncResult = SyncResult.Success

    /** When set, `sync` suspends until it completes, so a test can observe a sync in flight. */
    var syncGate: CompletableDeferred<Unit>? = null
    var syncCount = 0
        private set

    override fun observeForm(): Flow<List<FormItem>> = form

    override fun observeSyncState(): Flow<SyncState> = syncState

    override suspend fun sync(): SyncResult {
        syncCount++
        syncGate?.await()
        return syncResult
    }

    override suspend fun updateSelection(
        responseSetId: Long,
        transform: (responseSet: ResponseSet, selected: Set<Long>) -> Set<Long>,
    ) {
        val responseSet = responseSets[responseSetId] ?: return
        selections[responseSetId] = transform(responseSet, selections[responseSetId].orEmpty())
    }

    override suspend fun getImageQuestion(id: Long): ImageQuestion? = null
}

internal class FakeConnectivityRepository : ConnectivityRepository {
    override val isOnline = MutableStateFlow(true)
}

internal class FakeConnectionSecurityRepository : ConnectionSecurityRepository {
    override val isCompromised = MutableStateFlow(false)
}
