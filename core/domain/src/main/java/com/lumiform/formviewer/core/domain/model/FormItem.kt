package com.lumiform.formviewer.core.domain.model

/** One node of the form tree. Display text is nullable because the payload doesn't guarantee it. */
sealed interface FormItem {
    val id: Long
}

sealed interface FormContainer : FormItem {
    val children: List<FormItem>
}

data class Page(
    override val id: Long,
    val title: String?,
    override val children: List<FormItem>,
) : FormContainer

data class Section(
    override val id: Long,
    val title: String?,
    override val children: List<FormItem>,
) : FormContainer

sealed interface Question : FormItem

data class TextQuestion(
    override val id: Long,
    val content: String?,
) : Question

data class ImageQuestion(
    override val id: Long,
    val src: String?,
    val title: String?,
) : Question

data class ChoiceQuestion(
    override val id: Long,
    val content: String?,
    val responseSet: ResponseSet,
    val selectedResponseIds: Set<Long>,
) : Question

/**
 * An item of a type this app version doesn't know, shown as an "update the app" card. Its [children] are kept so
 * known questions nested under it still render.
 */
data class Unsupported(
    override val id: Long,
    val rawType: String,
    override val children: List<FormItem>,
) : FormContainer
