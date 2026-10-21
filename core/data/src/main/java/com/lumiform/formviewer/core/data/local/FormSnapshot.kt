package com.lumiform.formviewer.core.data.local

import com.lumiform.formviewer.core.data.local.entity.FormItemEntity
import com.lumiform.formviewer.core.data.local.entity.ResponseEntity
import com.lumiform.formviewer.core.data.local.entity.ResponseSetEntity

/**
 * The whole form as received from the server, converted to rows: the input of `FormDao.mergeForm`.
 *
 * It is always complete, never a partial update: anything stored locally but missing here is deleted on merge.
 */
data class FormSnapshot(
    val items: List<FormItemEntity>,
    val responseSets: List<ResponseSetEntity>,
    val responses: List<ResponseEntity>,
)
