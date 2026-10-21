package com.lumiform.formviewer.sync

import com.lumiform.formviewer.core.domain.model.FormItem
import com.lumiform.formviewer.core.domain.model.ImageQuestion
import com.lumiform.formviewer.core.domain.model.ResponseSet
import com.lumiform.formviewer.core.domain.model.SyncResult
import com.lumiform.formviewer.core.domain.model.SyncState
import com.lumiform.formviewer.core.domain.repository.FormRepository
import com.lumiform.formviewer.core.domain.repository.ImageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeFormRepository : FormRepository {
    val form = MutableStateFlow<List<FormItem>>(emptyList())
    var syncResult: SyncResult = SyncResult.Success

    override fun observeForm(): Flow<List<FormItem>> = form

    override fun observeSyncState(): Flow<SyncState> = MutableStateFlow(SyncState(null, null))

    override suspend fun sync(): SyncResult = syncResult

    override suspend fun updateSelection(
        responseSetId: Long,
        transform: (responseSet: ResponseSet, selected: Set<Long>) -> Set<Long>,
    ) = Unit

    override suspend fun getImageQuestion(id: Long): ImageQuestion? = null
}

internal class RecordingImageRepository : ImageRepository {
    val requested = mutableListOf<Set<String>>()
    var succeeds = true

    override suspend fun prefetch(urls: Set<String>): Boolean {
        requested += urls
        return succeeds
    }
}
