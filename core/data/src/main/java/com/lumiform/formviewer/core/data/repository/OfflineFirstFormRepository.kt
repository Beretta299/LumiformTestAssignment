package com.lumiform.formviewer.core.data.repository

import com.lumiform.formviewer.core.data.local.FormLocalDataSource
import com.lumiform.formviewer.core.data.mapper.storedValue
import com.lumiform.formviewer.core.data.mapper.toDomain
import com.lumiform.formviewer.core.data.mapper.toDomainForm
import com.lumiform.formviewer.core.data.mapper.toImageQuestion
import com.lumiform.formviewer.core.data.mapper.toSnapshot
import com.lumiform.formviewer.core.data.mapper.toSyncError
import com.lumiform.formviewer.core.data.mapper.toSyncState
import com.lumiform.formviewer.core.data.remote.FormFetchException
import com.lumiform.formviewer.core.data.remote.FormRemoteDataSource
import com.lumiform.formviewer.core.domain.model.FormItem
import com.lumiform.formviewer.core.domain.model.ImageQuestion
import com.lumiform.formviewer.core.domain.model.ResponseSet
import com.lumiform.formviewer.core.domain.model.SyncResult
import com.lumiform.formviewer.core.domain.model.SyncState
import com.lumiform.formviewer.core.domain.repository.FormRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.time.Clock

/** Room is the single source of truth: readers observe it, and a sync only ever writes to it. */
internal class OfflineFirstFormRepository
    @Inject
    constructor(
        private val local: FormLocalDataSource,
        private val remote: FormRemoteDataSource,
        private val clock: Clock,
    ) : FormRepository {
        override fun observeForm(): Flow<List<FormItem>> = local.observeForm().map { it.toDomainForm() }

        override fun observeSyncState(): Flow<SyncState> = local.observeSyncMetadata().map { it.toSyncState() }

        override suspend fun sync(): SyncResult =
            try {
                val snapshot = remote.fetchForm().toSnapshot()
                local.saveSyncedForm(snapshot, syncedAtMillis = clock.now().toEpochMilliseconds())
                SyncResult.Success
            } catch (e: FormFetchException) {
                val error = e.toSyncError()
                local.saveSyncError(error.storedValue)
                SyncResult.Failure(error)
            }

        override suspend fun updateSelection(
            responseSetId: Long,
            transform: (responseSet: ResponseSet, selected: Set<Long>) -> Set<Long>,
        ) = local.updateSelection(
            responseSetId,
            selectedAtMillis = clock.now().toEpochMilliseconds(),
        ) { stored, selected ->
            transform(stored.toDomain(), selected)
        }

        override suspend fun getImageQuestion(id: Long): ImageQuestion? = local.getItem(id)?.toImageQuestion()
    }
