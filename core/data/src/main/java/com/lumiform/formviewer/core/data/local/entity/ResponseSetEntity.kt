package com.lumiform.formviewer.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "response_set",
    foreignKeys = [
        ForeignKey(
            entity = FormItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("item_id", unique = true), Index("sync_generation")],
)
data class ResponseSetEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "item_id") val itemId: Long,
    @ColumnInfo(name = "multiple_selection") val multipleSelection: Boolean,
    @ColumnInfo(name = "sync_generation") val syncGeneration: Long = 0,
)
