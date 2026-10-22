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
    /** Position among its siblings in the payload; SQL rows have no order of their own. */
    @ColumnInfo(name = "sort_index") val sortIndex: Int,
    val type: String,
    val title: String?,
    val content: String?,
    @ColumnInfo(name = "image_src") val imageSrc: String?,
    @ColumnInfo(name = "sync_generation") val syncGeneration: Long = 0,
)
