package com.lumiform.formviewer.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.lumiform.formviewer.core.data.local.FormSnapshot
import com.lumiform.formviewer.core.data.local.entity.FormItemEntity
import com.lumiform.formviewer.core.data.local.entity.ResponseEntity
import com.lumiform.formviewer.core.data.local.entity.ResponseSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FormDao {
    @Query("SELECT * FROM form_item ORDER BY parent_id, sort_index")
    abstract fun observeItems(): Flow<List<FormItemEntity>>

    @Query("SELECT * FROM response_set")
    abstract fun observeResponseSets(): Flow<List<ResponseSetEntity>>

    @Query("SELECT * FROM response ORDER BY response_set_id, sort_index")
    abstract fun observeResponses(): Flow<List<ResponseEntity>>

    @Query("SELECT * FROM form_item WHERE id = :id")
    abstract suspend fun getItem(id: Long): FormItemEntity?

    /**
     * Merges a fresh form definition into the database without losing the user's answers.
     *
     * Rows are upserted in place (never REPLACE-d: a REPLACE deletes the old row first and the
     * cascade would wipe its answers). Rows not present in [snapshot] are swept afterwards using a
     * per-sync generation number, which avoids `NOT IN (...)` lists that hit SQLite's bound-argument
     * limit on large forms. Answers that no longer make sense are then reconciled:
     * - their response was removed → removed by the foreign key cascade;
     * - their response moved to another question → dropped;
     * - their question switched from multi- to single-select → all cleared.
     */
    @Transaction
    open suspend fun mergeForm(snapshot: FormSnapshot) {
        val generation = (maxGeneration() ?: 0) + 1
        val previouslyMultiSelect = multiSelectResponseSetIds().toSet()

        upsertItems(snapshot.items.parentsFirst().map { it.copy(syncGeneration = generation) })
        snapshot.responseSets.forEach { deleteOtherResponseSetsOfItem(itemId = it.itemId, keepId = it.id) }
        upsertResponseSets(snapshot.responseSets.map { it.copy(syncGeneration = generation) })
        upsertResponses(snapshot.responses.map { it.copy(syncGeneration = generation) })

        deleteStaleResponses(generation)
        deleteStaleResponseSets(generation)
        deleteStaleItems(generation)

        deleteAnswersOfMovedResponses()
        snapshot.responseSets
            .filter { !it.multipleSelection && it.id in previouslyMultiSelect }
            .forEach { deleteAnswersOfResponseSet(it.id) }
    }

    @Upsert
    protected abstract suspend fun upsertItems(items: List<FormItemEntity>)

    @Upsert
    protected abstract suspend fun upsertResponseSets(responseSets: List<ResponseSetEntity>)

    @Upsert
    protected abstract suspend fun upsertResponses(responses: List<ResponseEntity>)

    @Query("SELECT MAX(sync_generation) FROM form_item")
    protected abstract suspend fun maxGeneration(): Long?

    @Query("SELECT id FROM response_set WHERE multiple_selection = 1")
    protected abstract suspend fun multiSelectResponseSetIds(): List<Long>

    // An item has at most one response set; a replaced set must go before the upsert hits the unique index.
    @Query("DELETE FROM response_set WHERE item_id = :itemId AND id != :keepId")
    protected abstract suspend fun deleteOtherResponseSetsOfItem(
        itemId: Long,
        keepId: Long,
    )

    @Query("DELETE FROM response WHERE sync_generation < :generation")
    protected abstract suspend fun deleteStaleResponses(generation: Long)

    @Query("DELETE FROM response_set WHERE sync_generation < :generation")
    protected abstract suspend fun deleteStaleResponseSets(generation: Long)

    @Query("DELETE FROM form_item WHERE sync_generation < :generation")
    protected abstract suspend fun deleteStaleItems(generation: Long)

    @Query(
        """
        DELETE FROM answer WHERE NOT EXISTS (
            SELECT 1 FROM response
            WHERE response.id = answer.response_id AND response.response_set_id = answer.response_set_id
        )
        """,
    )
    protected abstract suspend fun deleteAnswersOfMovedResponses()

    @Query("DELETE FROM answer WHERE response_set_id = :responseSetId")
    protected abstract suspend fun deleteAnswersOfResponseSet(responseSetId: Long)
}

/**
 * Orders items so every parent is inserted before its children, as the immediate `parent_id` foreign key
 * requires. A deferred key would avoid this, but when a deferred check fails at COMMIT SQLite keeps the
 * transaction open while Android considers it finished, leaving the connection unusable.
 */
private fun List<FormItemEntity>.parentsFirst(): List<FormItemEntity> {
    val byId = associateBy { it.id }
    val depths = HashMap<Long, Int>()

    fun depthOf(item: FormItemEntity): Int =
        depths.getOrPut(item.id) {
            item.parentId?.let(byId::get)?.let { depthOf(it) + 1 } ?: 0
        }
    return sortedBy(::depthOf)
}
