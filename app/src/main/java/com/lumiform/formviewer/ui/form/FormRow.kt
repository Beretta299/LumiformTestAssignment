package com.lumiform.formviewer.ui.form

import com.lumiform.formviewer.core.domain.model.ChoiceQuestion
import com.lumiform.formviewer.core.domain.model.FormContainer
import com.lumiform.formviewer.core.domain.model.FormItem
import com.lumiform.formviewer.core.domain.model.Page
import com.lumiform.formviewer.core.domain.model.Question
import com.lumiform.formviewer.core.domain.model.Score
import com.lumiform.formviewer.core.domain.model.Section
import com.lumiform.formviewer.core.domain.model.Unsupported

/**
 * One LazyColumn item of the flattened form tree. [depth] counts the sections (and unknown containers) above a row;
 * pages don't add depth, since every page starts flush. Consecutive rows with the same [group] form one block that
 * shares a timeline rail: a page's direct child and everything under it. Page headers belong to no group. Sections
 * and questions carry an outline [number] ("2", "2.1") restarting on every page.
 */
sealed interface FormRow {
    val id: Long
    val depth: Int
    val group: Long?
}

/** [number] is the page's position among the top-level pages, or null for a page nested where it doesn't belong. */
data class PageRow(
    override val id: Long,
    val title: String?,
    val number: Int? = null,
    val pageCount: Int = 0,
) : FormRow {
    override val depth = 0
    override val group = null
}

data class SectionRow(
    override val id: Long,
    val title: String?,
    val number: String,
    override val depth: Int,
    override val group: Long,
) : FormRow

data class QuestionRow(
    val question: Question,
    val number: String,
    override val depth: Int,
    override val group: Long,
) : FormRow {
    override val id = question.id
}

/** [isTopLevel] marks an unknown item where a page was expected, which gets a page-level card. */
data class UnsupportedRow(
    override val id: Long,
    override val depth: Int,
    override val group: Long,
    val isTopLevel: Boolean,
) : FormRow

/** One entry per choice question, in form order: whether it has a selection. [score] sums the selected points. */
data class FormSummary(
    val answered: List<Boolean>,
    val score: Int,
) {
    val answeredCount: Int get() = answered.count { it }
    val total: Int get() = answered.size
    val fraction: Float get() = if (total == 0) 0f else answeredCount.toFloat() / total
}

fun List<FormItem>.toRows(): List<FormRow> {
    val pageNumbers = filterIsInstance<Page>().withIndex().associate { (index, page) -> page.id to index + 1 }
    return flatMap { it.toRows(depth = 0, group = null, number = "", isTopLevel = true) }
        .map { row ->
            if (row is PageRow) row.copy(number = pageNumbers[row.id], pageCount = pageNumbers.size) else row
        }
}

/** Choice questions are the form's only answerable items. */
fun List<FormItem>.summary(): FormSummary {
    val choices = flatMap { it.selfAndDescendants() }.filterIsInstance<ChoiceQuestion>()
    val selectedPoints =
        choices
            .flatMap { choice -> choice.responseSet.responses.filter { it.id in choice.selectedResponseIds } }
            .sumOf { (it.score as? Score.Points)?.value ?: 0 }
    return FormSummary(answered = choices.map { it.selectedResponseIds.isNotEmpty() }, score = selectedPoints)
}

/**
 * A null [group] means a page (or a top-level unknown item) is the parent, so the item starts its own group. [number]
 * is this item's outline number; a page's children start again at "1".
 */
private fun FormItem.toRows(
    depth: Int,
    group: Long?,
    number: String,
    isTopLevel: Boolean,
): List<FormRow> {
    val own = group ?: id
    return when (this) {
        is Page -> {
            listOf(PageRow(id, title)) + childRows(depth, group = null, prefix = "")
        }

        is Section -> {
            listOf(SectionRow(id, title, number, depth, own)) + childRows(depth + 1, own, prefix = number)
        }

        is Unsupported -> {
            if (isTopLevel) {
                listOf(UnsupportedRow(id, depth, own, isTopLevel = true)) + childRows(depth, group = null, prefix = "")
            } else {
                listOf(UnsupportedRow(id, depth, own, isTopLevel = false)) + childRows(depth + 1, own, prefix = number)
            }
        }

        is Question -> {
            listOf(QuestionRow(this, number, depth, own))
        }
    }
}

private fun FormContainer.childRows(
    depth: Int,
    group: Long?,
    prefix: String,
): List<FormRow> =
    children.withIndex().flatMap { (index, child) ->
        val number = if (prefix.isEmpty()) "${index + 1}" else "$prefix.${index + 1}"
        child.toRows(depth, group, number, isTopLevel = false)
    }

private fun FormItem.selfAndDescendants(): List<FormItem> =
    listOf(this) + (this as? FormContainer)?.children?.flatMap { it.selfAndDescendants() }.orEmpty()
