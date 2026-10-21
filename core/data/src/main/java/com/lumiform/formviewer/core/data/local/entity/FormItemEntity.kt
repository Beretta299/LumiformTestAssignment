package com.lumiform.formviewer.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One node of the form tree (page, section or question) stored as an adjacency list.
 *
 * [type] is kept as the raw string from the payload so items of types this app version doesn't know
 * survive in the database and render properly once the app is updated.
 */
@Entity(
    tableName = "form_item",
    foreignKeys = [
        ForeignKey(
            entity = FormItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("parent_id"), Index("sync_generation")],
)
data class FormItemEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "parent_id") val parentId: Long?,
    /**
     * Index of this item in its parent's array in the payload.
     * Open question: it's not yet clear whether the app must keep the server's order or will only rely on
     * user-chosen sorting later. Kept anyway, since SQL tables have no inherent row order and the server
     * order would otherwise be lost.
     */
    @ColumnInfo(name = "sort_index") val sortIndex: Int,
    val type: String,
    val title: String?,
    val content: String?,
    @ColumnInfo(name = "image_src") val imageSrc: String?,
    @ColumnInfo(name = "sync_generation") val syncGeneration: Long = 0,
)
