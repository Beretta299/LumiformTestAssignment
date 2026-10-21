package com.lumiform.formviewer.core.data.remote

import com.lumiform.formviewer.core.data.remote.dto.ChoiceQuestionDto
import com.lumiform.formviewer.core.data.remote.dto.FormItemDto
import com.lumiform.formviewer.core.data.remote.dto.ImageQuestionDto
import com.lumiform.formviewer.core.data.remote.dto.PageDto
import com.lumiform.formviewer.core.data.remote.dto.ResponseDto
import com.lumiform.formviewer.core.data.remote.dto.ResponseSetDto
import com.lumiform.formviewer.core.data.remote.dto.SectionDto
import com.lumiform.formviewer.core.data.remote.dto.TextQuestionDto
import com.lumiform.formviewer.core.data.remote.dto.UnsupportedItemDto
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class FormJsonParsingTest {
    private fun parse(json: String): List<FormItemDto> = FormJson.decodeFromString(json)

    @Test
    fun realPayload_parsesIntoTheFullTree() {
        val expected =
            listOf(
                PageDto(
                    id = 1,
                    title = "Main Page",
                    items =
                        listOf(
                            SectionDto(
                                id = 2,
                                title = "Introduction",
                                items =
                                    listOf(
                                        TextQuestionDto(id = 3, content = "Welcome to the main page!"),
                                        ImageQuestionDto(
                                            id = 4,
                                            src = ANDROID_IMAGE_URL,
                                            title = "Welcome Image",
                                        ),
                                    ),
                            ),
                            SectionDto(
                                id = 5,
                                title = "Chapter 1",
                                items =
                                    listOf(
                                        TextQuestionDto(id = 6, content = "This is the first chapter."),
                                        SectionDto(
                                            id = 7,
                                            title = "Subsection 1.1",
                                            items =
                                                listOf(
                                                    TextQuestionDto(
                                                        id = 8,
                                                        content = "This is a subsection under Chapter 1.",
                                                    ),
                                                    ImageQuestionDto(
                                                        id = 9,
                                                        src = IOS_IMAGE_URL,
                                                        title = "Chapter 1 Image",
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                        ),
                ),
                PageDto(
                    id = 10,
                    title = "Second Page",
                    items =
                        listOf(
                            SectionDto(
                                id = 11,
                                title = "Chapter 2",
                                items =
                                    listOf(
                                        TextQuestionDto(id = 12, content = "This is the second chapter."),
                                        ChoiceQuestionDto(
                                            id = 13,
                                            content = "What is the main topic of Chapter 2?",
                                            responseSet =
                                                ResponseSetDto(
                                                    id = 101,
                                                    multipleSelection = false,
                                                    responses =
                                                        listOf(
                                                            ResponseDto(
                                                                id = 1011,
                                                                label = "Safety procedures",
                                                                score = 1,
                                                            ),
                                                            ResponseDto(
                                                                id = 1012,
                                                                label = "Equipment checks",
                                                                score = 2,
                                                            ),
                                                            ResponseDto(
                                                                id = 1013,
                                                                label = "Not applicable",
                                                                score = null,
                                                            ),
                                                        ),
                                                ),
                                        ),
                                        ChoiceQuestionDto(
                                            id = 14,
                                            content = "Which areas were inspected?",
                                            responseSet =
                                                ResponseSetDto(
                                                    id = 102,
                                                    multipleSelection = true,
                                                    responses =
                                                        listOf(
                                                            ResponseDto(id = 1021, label = "Entrance"),
                                                            ResponseDto(id = 1022, label = "Storage"),
                                                            ResponseDto(id = 1023, label = "Loading bay"),
                                                        ),
                                                ),
                                        ),
                                    ),
                            ),
                        ),
                ),
            )

        assertEquals(expected, parse(formFixture()))
    }

    @Test
    fun unknownTopLevelType_becomesUnsupported() {
        val items = parse("""[{"id": 1, "type": "wizard", "title": "Step 1"}]""")

        assertEquals(listOf(UnsupportedItemDto(id = 1, type = "wizard")), items)
    }

    @Test
    fun unknownNestedType_becomesUnsupported_andKeepsItsKnownChildren() {
        val items =
            parse(
                """
                [{"id": 1, "type": "page", "items": [
                  {"id": 2, "type": "tabs", "items": [
                    {"id": 3, "type": "text", "content": "Still here"},
                    {"id": 4, "type": "date", "format": "yyyy-MM-dd"}
                  ]}
                ]}]
                """,
            )

        val expected =
            listOf(
                PageDto(
                    id = 1,
                    items =
                        listOf(
                            UnsupportedItemDto(
                                id = 2,
                                type = "tabs",
                                items =
                                    listOf(
                                        TextQuestionDto(id = 3, content = "Still here"),
                                        UnsupportedItemDto(id = 4, type = "date"),
                                    ),
                            ),
                        ),
                ),
            )
        assertEquals(expected, items)
    }

    @Test
    fun nonStringType_becomesUnsupported_withTheRawValue() {
        assertEquals(listOf(UnsupportedItemDto(id = 1, type = "7")), parse("""[{"id": 1, "type": 7}]"""))
    }

    @ParameterizedTest
    @ValueSource(strings = ["""{"id": 1}""", """{"id": 1, "type": null}"""])
    fun missingType_becomesUnsupported(item: String) {
        assertEquals(listOf(UnsupportedItemDto(id = 1)), parse("[$item]"))
    }

    @Test
    fun scoreAbsentOrNull_isParsedAsNotScored() {
        val items =
            parse(
                """
                [{"id": 1, "type": "choice", "content": "Q", "response_set": {
                  "id": 10, "multiple_selection": false,
                  "responses": [{"id": 1, "label": "a"}, {"id": 2, "label": "b", "score": null}]
                }}]
                """,
            )

        val responses = (items.single() as ChoiceQuestionDto).responseSet.responses
        assertEquals(listOf(null, null), responses.map { it.score })
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"id": 1, "type": "section"}""",
            """{"id": 1, "type": "section", "items": null}""",
        ],
    )
    fun containerWithoutItems_hasNoChildren(item: String) {
        assertEquals(listOf(SectionDto(id = 1)), parse("[$item]"))
    }

    @Test
    fun missingDisplayText_isNull() {
        val items =
            parse(
                """
                [
                  {"id": 1, "type": "text"},
                  {"id": 2, "type": "image"},
                  {"id": 3, "type": "page", "title": null}
                ]
                """,
            )

        assertEquals(listOf(TextQuestionDto(id = 1), ImageQuestionDto(id = 2), PageDto(id = 3)), items)
    }

    @Test
    fun unknownFields_areIgnored() {
        val items = parse("""[{"id": 1, "type": "text", "content": "Hi", "hint": "x", "meta": {"a": 1}}]""")

        assertEquals(listOf(TextQuestionDto(id = 1, content = "Hi")), items)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """[{"type": "text", "content": "no id"}]""",
            """[{"id": 1, "type": "choice", "content": "no response set"}]""",
            """[{"id": 1, "type": "choice", "response_set": {"id": 2, "responses": []}}]""",
            """[{"id": 1, "type": "choice", "response_set": {"id": 2, "multiple_selection": true, "responses": [{"id": 3}]}}]""",
            """[{"id": 1, "type": "page", "items": [42]}]""",
            """{"id": 1, "type": "page"}""",
            """[{"id": 1, "type": "page",""",
            """<html>Rate limited</html>""",
        ],
    )
    fun payloadMissingStructuralData_isRejected(json: String) {
        assertThrows<SerializationException> { parse(json) }
    }

    private companion object {
        const val ANDROID_IMAGE_URL = "https://raw.githubusercontent.com/github/explore/main/topics/android/android.png"
        const val IOS_IMAGE_URL = "https://raw.githubusercontent.com/github/explore/main/topics/ios/ios.png"
    }
}
