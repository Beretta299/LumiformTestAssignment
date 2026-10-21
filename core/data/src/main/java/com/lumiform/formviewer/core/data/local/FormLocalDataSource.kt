package com.lumiform.formviewer.core.data.local

import androidx.room.withTransaction
import com.lumiform.formviewer.core.data.local.entity.AnswerEntity
import com.lumiform.formviewer.core.data.local.entity.FormItemEntity
import com.lumiform.formviewer.core.data.local.entity.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

internal interface FormLocalDataSource {
    fun observeForm(): Flow<List<StoredFormItem>>

    fun observeSyncMetadata(): Flow<SyncMetadataEntity?>

    suspend fun getItem(id: Long): FormItemEntity?

    /** Merges [snapshot] and records the successful sync in one transaction. */
    suspend fun saveSyncedForm(
        snapshot: FormSnapshot,
        syncedAtMillis: Long,
    )

    /** Records a failed sync, keeping the time of the last successful one. */
    suspend fun saveSyncError(error: String)

    /** See `FormRepository.updateSelection`; [transform] gets the stored set and its current selection. */
    suspend fun updateSelection(
        responseSetId: Long,
        selectedAtMillis: Long,
        transform: (StoredResponseSet, Set<Long>) -> Set<Long>,
    )
}

internal class RoomFormLocalDataSource
    @Inject
    constructor(
        private val database: FormDatabase,
    ) : FormLocalDataSource {
        private val formDao = database.formDao()
        private val answerDao = database.answerDao()
        private val syncMetadataDao = database.syncMetadataDao()

        override fun observeForm(): Flow<List<StoredFormItem>> = formDao.observeForm()

        override fun observeSyncMetadata(): Flow<SyncMetadataEntity?> = syncMetadataDao.observe(SYNC_KEY)

        override suspend fun getItem(id: Long): FormItemEntity? = formDao.getItem(id)

        override suspend fun saveSyncedForm(
            snapshot: FormSnapshot,
            syncedAtMillis: Long,
        ) = database.withTransaction {
            formDao.mergeForm(snapshot)
            syncMetadataDao.upsert(SyncMetadataEntity(SYNC_KEY, lastSyncedAt = syncedAtMillis, lastError = null))
        }

        override suspend fun saveSyncError(error: String) =
            database.withTransaction {
                val lastSyncedAt = syncMetadataDao.get(SYNC_KEY)?.lastSyncedAt
                syncMetadataDao.upsert(SyncMetadataEntity(SYNC_KEY, lastSyncedAt = lastSyncedAt, lastError = error))
            }

        // Kept answers are left untouched, so their selection time and upload state survive.
        override suspend fun updateSelection(
            responseSetId: Long,
            selectedAtMillis: Long,
            transform: (StoredResponseSet, Set<Long>) -> Set<Long>,
        ) = database.withTransaction {
            val stored = formDao.getResponseSet(responseSetId) ?: return@withTransaction
            val current = answerDao.getAnswers(responseSetId).mapTo(mutableSetOf()) { it.responseId }
            val updated = transform(stored, current) intersect stored.responses.mapTo(mutableSetOf()) { it.id }
            (current - updated).forEach { answerDao.delete(it) }
            (updated - current).forEach { answerDao.upsert(AnswerEntity(it, responseSetId, selectedAtMillis)) }
        }

        companion object {
            const val SYNC_KEY = "form"
        }
    }
