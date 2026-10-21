package com.lumiform.formviewer.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A response the user selected. Kept apart from the form definition so a sync can update the
 * definition without touching answers; removing a response cascades to its answer.
 *
 * [responseSetId] is denormalised on purpose: it records which question the answer was given to, so
 * an answer whose response later moves to another question can be detected and dropped.
 */
@Entity(
    tableName = "answer",
    foreignKeys = [
        ForeignKey(
            entity = ResponseEntity::class,
            parentColumns = ["id"],
            childColumns = ["response_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("response_set_id")],
)
data class AnswerEntity(
    @PrimaryKey @ColumnInfo(name = "response_id") val responseId: Long,
    @ColumnInfo(name = "response_set_id") val responseSetId: Long,
    @ColumnInfo(name = "selected_at") val selectedAt: Long,
    @ColumnInfo(name = "sync_state") val syncState: AnswerSyncState = AnswerSyncState.PENDING_UPSERT,
    @ColumnInfo(name = "server_updated_at") val serverUpdatedAt: Long? = null,
)

/**
 * Outbox state of an answer, ready for a future upload endpoint.
 *
 * Stored by [storedValue], not by name, so constants can be renamed without breaking saved rows.
 * Never change or reuse a stored value.
 */
enum class AnswerSyncState(
    val storedValue: String,
) {
    SYNCED("synced"),
    PENDING_UPSERT("pending_upsert"),
    PENDING_DELETE("pending_delete"),
    ;

    companion object {
        fun fromStoredValue(value: String): AnswerSyncState =
            entries.firstOrNull { it.storedValue == value }
                ?: error("Unknown answer sync state '$value' in database")
    }
}
