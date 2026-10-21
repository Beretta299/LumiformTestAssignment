package com.lumiform.formviewer.core.data.remote.dto

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.serializer

/**
 * One node of the form tree. The `type` field picks the subclass by its [SerialName], so a new item type only
 * needs a new subclass; unknown types become [UnsupportedItemDto].
 *
 * Display text is nullable, but fields the tree and the answers depend on are required, so a payload missing
 * them is rejected whole instead of replacing the cached form with a partial one.
 */
@Serializable
internal sealed interface FormItemDto {
    val id: Long
}

internal sealed interface FormContainerDto : FormItemDto {
    val items: List<FormItemDto>
}

@Serializable
@SerialName("page")
internal data class PageDto(
    override val id: Long,
    val title: String? = null,
    override val items: List<FormItemDto> = emptyList(),
) : FormContainerDto

@Serializable
@SerialName("section")
internal data class SectionDto(
    override val id: Long,
    val title: String? = null,
    override val items: List<FormItemDto> = emptyList(),
) : FormContainerDto

@Serializable
@SerialName("text")
internal data class TextQuestionDto(
    override val id: Long,
    val content: String? = null,
) : FormItemDto

@Serializable
@SerialName("image")
internal data class ImageQuestionDto(
    override val id: Long,
    val src: String? = null,
    val title: String? = null,
) : FormItemDto

@Serializable
@SerialName("choice")
internal data class ChoiceQuestionDto(
    override val id: Long,
    val content: String? = null,
    @SerialName("response_set") val responseSet: ResponseSetDto,
) : FormItemDto

/**
 * An item of a type this app version doesn't know. The raw [type] is kept for rendering after an app update,
 * and [items] are still parsed so known questions nested under it aren't lost.
 */
internal data class UnsupportedItemDto(
    override val id: Long,
    val type: String = "",
    override val items: List<FormItemDto> = emptyList(),
) : FormContainerDto

/** Reads an unknown item; the polymorphic decoder has already consumed `type`, so it's passed in. */
internal class UnsupportedItemDeserializer(
    private val type: String?,
) : DeserializationStrategy<FormItemDto> {
    private val body = serializer<UnsupportedItemBody>()
    override val descriptor = body.descriptor

    override fun deserialize(decoder: Decoder): FormItemDto {
        val item = body.deserialize(decoder)
        return UnsupportedItemDto(item.id, type.orEmpty(), item.items)
    }
}

@Serializable
private class UnsupportedItemBody(
    val id: Long,
    val items: List<FormItemDto> = emptyList(),
)

@Serializable
internal data class ResponseSetDto(
    val id: Long,
    @SerialName("multiple_selection") val multipleSelection: Boolean,
    val responses: List<ResponseDto> = emptyList(),
)

@Serializable
internal data class ResponseDto(
    val id: Long,
    val label: String,
    val score: Int? = null,
)
