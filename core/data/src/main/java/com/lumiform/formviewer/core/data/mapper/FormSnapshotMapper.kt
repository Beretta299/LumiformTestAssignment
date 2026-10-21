package com.lumiform.formviewer.core.data.mapper

import com.lumiform.formviewer.core.data.local.FormItemType
import com.lumiform.formviewer.core.data.local.FormSnapshot
import com.lumiform.formviewer.core.data.local.entity.FormItemEntity
import com.lumiform.formviewer.core.data.local.entity.ResponseEntity
import com.lumiform.formviewer.core.data.local.entity.ResponseSetEntity
import com.lumiform.formviewer.core.data.remote.dto.ChoiceQuestionDto
import com.lumiform.formviewer.core.data.remote.dto.FormContainerDto
import com.lumiform.formviewer.core.data.remote.dto.FormItemDto
import com.lumiform.formviewer.core.data.remote.dto.ImageQuestionDto
import com.lumiform.formviewer.core.data.remote.dto.PageDto
import com.lumiform.formviewer.core.data.remote.dto.SectionDto
import com.lumiform.formviewer.core.data.remote.dto.TextQuestionDto
import com.lumiform.formviewer.core.data.remote.dto.UnsupportedItemDto

/** Flattens the payload's tree into rows, recording each item's parent and position among its siblings. */
internal fun List<FormItemDto>.toSnapshot(): FormSnapshot {
    val placed = placeUnder(parentId = null).toList()
    val choices = placed.map { it.dto }.filterIsInstance<ChoiceQuestionDto>()
    return FormSnapshot(
        items = placed.map { it.toEntity() },
        responseSets =
            choices.map { ResponseSetEntity(it.responseSet.id, itemId = it.id, it.responseSet.multipleSelection) },
        responses =
            choices.flatMap { choice ->
                choice.responseSet.responses.mapIndexed { index, response ->
                    ResponseEntity(response.id, choice.responseSet.id, index, response.label, response.score)
                }
            },
    )
}

private data class PlacedItem(
    val dto: FormItemDto,
    val parentId: Long?,
    val sortIndex: Int,
)

private fun List<FormItemDto>.placeUnder(parentId: Long?): Sequence<PlacedItem> =
    asSequence().withIndex().flatMap { (index, dto) ->
        val children = (dto as? FormContainerDto)?.items?.placeUnder(dto.id).orEmpty()
        sequenceOf(PlacedItem(dto, parentId, index)) + children
    }

private fun PlacedItem.toEntity(): FormItemEntity {
    fun entity(
        type: String,
        title: String? = null,
        content: String? = null,
        imageSrc: String? = null,
    ) = FormItemEntity(dto.id, parentId, sortIndex, type, title, content, imageSrc)

    return when (dto) {
        is PageDto -> entity(FormItemType.PAGE, title = dto.title)
        is SectionDto -> entity(FormItemType.SECTION, title = dto.title)
        is TextQuestionDto -> entity(FormItemType.TEXT, content = dto.content)
        is ImageQuestionDto -> entity(FormItemType.IMAGE, title = dto.title, imageSrc = dto.src)
        is ChoiceQuestionDto -> entity(FormItemType.CHOICE, content = dto.content)
        is UnsupportedItemDto -> entity(dto.type)
    }
}
