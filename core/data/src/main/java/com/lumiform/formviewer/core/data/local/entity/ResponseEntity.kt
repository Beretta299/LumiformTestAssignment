package com.lumiform.formviewer.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "response",
    foreignKeys = [
        ForeignKey(
            entity = ResponseSetEntity::class,
            parentColumns = ["id"],
            childColumns = ["response_set_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("response_set_id"), Index("sync_generation")],
)
data class ResponseEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "response_set_id") val responseSetId: Long,
    /**
     * Index of this response in its parent's array in the payload.
     * Open question: it's not yet clear whether the app must keep the server's order or will only rely on
     * user-chosen sorting later. Kept anyway, since SQL tables have no inherent row order and the server
     * order would otherwise be lost.
     */
    @ColumnInfo(name = "sort_index") val sortIndex: Int,
    val label: String,
    val score: Int?,
    @ColumnInfo(name = "sync_generation") val syncGeneration: Long = 0,
)
