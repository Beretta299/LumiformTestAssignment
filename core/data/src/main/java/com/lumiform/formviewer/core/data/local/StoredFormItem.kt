package com.lumiform.formviewer.core.data.local

import androidx.room.Embedded
import androidx.room.Relation
import com.lumiform.formviewer.core.data.local.entity.AnswerEntity
import com.lumiform.formviewer.core.data.local.entity.FormItemEntity
import com.lumiform.formviewer.core.data.local.entity.ResponseEntity
import com.lumiform.formviewer.core.data.local.entity.ResponseSetEntity

/** A form item with its response set, read in one transaction so a sync can never be seen half-applied. */
data class StoredFormItem(
    @Embedded val item: FormItemEntity,
    @Relation(entity = ResponseSetEntity::class, parentColumn = "id", entityColumn = "item_id")
    val responseSet: StoredResponseSet?,
)

/** [answers] include rows only waiting for a server delete; readers must skip them. */
data class StoredResponseSet(
    @Embedded val responseSet: ResponseSetEntity,
    @Relation(parentColumn = "id", entityColumn = "response_set_id")
    val responses: List<ResponseEntity>,
    @Relation(parentColumn = "id", entityColumn = "response_set_id")
    val answers: List<AnswerEntity>,
)
