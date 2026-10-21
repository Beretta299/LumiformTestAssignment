package com.lumiform.formviewer.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.lumiform.formviewer.core.data.local.entity.AnswerEntity
import com.lumiform.formviewer.core.data.local.entity.AnswerSyncState
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AnswerDao {
    /** Answers the user currently has; ones only waiting to be deleted on the server are hidden. */
    fun observeAnswers(): Flow<List<AnswerEntity>> = observeAnswersExcept(AnswerSyncState.PENDING_DELETE)

    suspend fun getAnswers(responseSetId: Long): List<AnswerEntity> =
        getAnswersExcept(responseSetId, AnswerSyncState.PENDING_DELETE)

    @Upsert
    abstract suspend fun upsert(answer: AnswerEntity)

    @Query("DELETE FROM answer WHERE response_id = :responseId")
    abstract suspend fun delete(responseId: Long)

    @Query("DELETE FROM answer WHERE response_set_id = :responseSetId")
    abstract suspend fun deleteAll(responseSetId: Long)

    /** Atomically makes [answers] the only answers of a response set (e.g. a single-select change). */
    @Transaction
    open suspend fun replaceAll(
        responseSetId: Long,
        answers: List<AnswerEntity>,
    ) {
        require(
            answers.all { it.responseSetId == responseSetId },
        ) { "Answers must belong to response set $responseSetId" }
        deleteAll(responseSetId)
        answers.forEach { upsert(it) }
    }

    @Query("SELECT * FROM answer WHERE sync_state != :excluded")
    protected abstract fun observeAnswersExcept(excluded: AnswerSyncState): Flow<List<AnswerEntity>>

    @Query("SELECT * FROM answer WHERE response_set_id = :responseSetId AND sync_state != :excluded")
    protected abstract suspend fun getAnswersExcept(
        responseSetId: Long,
        excluded: AnswerSyncState,
    ): List<AnswerEntity>
}
