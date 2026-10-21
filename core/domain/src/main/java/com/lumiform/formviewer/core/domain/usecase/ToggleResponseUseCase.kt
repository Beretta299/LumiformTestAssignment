package com.lumiform.formviewer.core.domain.usecase

import com.lumiform.formviewer.core.domain.model.ChoiceQuestion
import com.lumiform.formviewer.core.domain.repository.FormRepository
import javax.inject.Inject

/** Applies a tap on [responseId] of [question] and persists the resulting selection. */
class ToggleResponseUseCase
    @Inject
    constructor(
        private val repository: FormRepository,
    ) {
        suspend operator fun invoke(
            question: ChoiceQuestion,
            responseId: Long,
        ) = repository.updateSelection(question.responseSet.id) { responseSet, selected ->
            // The form may have been re-synced since it was shown; a tap on a response it no longer has is ignored.
            if (responseSet.responses.none { it.id == responseId }) {
                selected
            } else {
                toggleSelection(selected, responseId, responseSet.multipleSelection)
            }
        }
    }

/**
 * Multi-select toggles the tapped response. Single-select replaces the selection with it, and re-tapping the
 * selected response clears it, since "nothing selected" is a valid answer.
 */
internal fun toggleSelection(
    selected: Set<Long>,
    responseId: Long,
    multipleSelection: Boolean,
): Set<Long> =
    when {
        multipleSelection -> if (responseId in selected) selected - responseId else selected + responseId
        responseId in selected -> emptySet()
        else -> setOf(responseId)
    }
