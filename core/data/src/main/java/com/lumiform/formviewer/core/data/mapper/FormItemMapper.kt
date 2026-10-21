package com.lumiform.formviewer.core.data.mapper

import com.lumiform.formviewer.core.data.local.FormItemType
import com.lumiform.formviewer.core.data.local.StoredFormItem
import com.lumiform.formviewer.core.data.local.StoredResponseSet
import com.lumiform.formviewer.core.data.local.entity.AnswerSyncState
import com.lumiform.formviewer.core.data.local.entity.FormItemEntity
import com.lumiform.formviewer.core.data.local.entity.ResponseEntity
import com.lumiform.formviewer.core.domain.model.ChoiceQuestion
import com.lumiform.formviewer.core.domain.model.FormItem
import com.lumiform.formviewer.core.domain.model.ImageQuestion
import com.lumiform.formviewer.core.domain.model.Page
import com.lumiform.formviewer.core.domain.model.Response
import com.lumiform.formviewer.core.domain.model.ResponseSet
import com.lumiform.formviewer.core.domain.model.Score
import com.lumiform.formviewer.core.domain.model.Section
import com.lumiform.formviewer.core.domain.model.TextQuestion
import com.lumiform.formviewer.core.domain.model.Unsupported

/** Rebuilds the form tree from its rows, ordering siblings by their position in the payload. */
internal fun List<StoredFormItem>.toDomainForm(): List<FormItem> {
    val childrenByParent = groupBy { it.item.parentId }

    fun childrenOf(parentId: Long?): List<FormItem> =
        childrenByParent[parentId]
            .orEmpty()
            .sortedBy { it.item.sortIndex }
            .map { stored ->
                val item = stored.item
                when (item.type) {
                    FormItemType.PAGE -> Page(item.id, item.title, childrenOf(item.id))

                    FormItemType.SECTION -> Section(item.id, item.title, childrenOf(item.id))

                    FormItemType.TEXT -> TextQuestion(item.id, item.content)

                    FormItemType.IMAGE -> item.toImageQuestionUnchecked()

                    // Without its response set a choice can't be answered, so it falls back to unsupported.
                    FormItemType.CHOICE -> stored.responseSet?.let { item.toChoiceQuestion(it) }

                    else -> null
                } ?: Unsupported(item.id, item.type, childrenOf(item.id))
            }

    return childrenOf(parentId = null)
}

internal fun FormItemEntity.toImageQuestion(): ImageQuestion? =
    takeIf { it.type == FormItemType.IMAGE }?.toImageQuestionUnchecked()

private fun FormItemEntity.toImageQuestionUnchecked() = ImageQuestion(id, src = imageSrc, title = title)

private fun FormItemEntity.toChoiceQuestion(stored: StoredResponseSet) =
    ChoiceQuestion(
        id = id,
        content = content,
        responseSet = stored.toDomain(),
        selectedResponseIds =
            stored.answers
                .filter { it.syncState != AnswerSyncState.PENDING_DELETE }
                .mapTo(mutableSetOf()) { it.responseId },
    )

internal fun StoredResponseSet.toDomain() =
    ResponseSet(
        id = responseSet.id,
        multipleSelection = responseSet.multipleSelection,
        responses = responses.sortedBy { it.sortIndex }.map { it.toDomain() },
    )

private fun ResponseEntity.toDomain() = Response(id, label, score?.let(Score::Points) ?: Score.NotScored)
