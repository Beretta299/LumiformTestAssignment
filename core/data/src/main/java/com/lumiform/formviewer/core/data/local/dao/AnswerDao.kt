package com.lumiform.formviewer.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lumiform.formviewer.core.data.local.entity.AnswerEntity
import com.lumiform.formviewer.core.data.local.entity.AnswerSyncState

@Dao
abstract class AnswerDao {
    /** Answers the user currently has; ones only waiting to be deleted on the server are hidden. */
    suspend fun getAnswers(responseSetId: Long): List<AnswerEntity> =
        getAnswersExcept(responseSetId, AnswerSyncState.PENDING_DELETE)

    @Upsert
    abstract suspend fun upsert(answer: AnswerEntity)

    @Query("DELETE FROM answer WHERE response_id = :responseId")
    abstract suspend fun delete(responseId: Long)

    @Query("SELECT * FROM answer WHERE response_set_id = :responseSetId AND sync_state != :excluded")
    protected abstract suspend fun getAnswersExcept(
        responseSetId: Long,
        excluded: AnswerSyncState,
    ): List<AnswerEntity>
}
