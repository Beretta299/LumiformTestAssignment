package com.lumiform.formviewer.core.data.remote

import com.lumiform.formviewer.core.data.remote.dto.FormItemDto
import com.lumiform.formviewer.core.domain.model.ChoiceQuestion
import com.lumiform.formviewer.core.domain.model.FormItem
import com.lumiform.formviewer.core.domain.model.ImageQuestion
import com.lumiform.formviewer.core.domain.model.Page
import com.lumiform.formviewer.core.domain.model.Response
import com.lumiform.formviewer.core.domain.model.ResponseSet
import com.lumiform.formviewer.core.domain.model.Score
import com.lumiform.formviewer.core.domain.model.Section
import com.lumiform.formviewer.core.domain.model.TextQuestion

/** The payload served by the real endpoint, copied verbatim. */
internal fun formFixture(): String =
    checkNotNull(object {}.javaClass.getResource("/form.json")) { "form.json fixture missing" }.readText()

internal fun formFixtureDtos(): List<FormItemDto> = FormJson.decodeFromString(formFixture())

/** [formFixture] as the domain tree, with [selections] keyed by response set id. */
internal fun formFixtureDomain(selections: Map<Long, Set<Long>> = emptyMap()): List<FormItem> =
    listOf(
        Page(
            1,
            "Main Page",
            listOf(
                Section(
                    2,
                    "Introduction",
                    listOf(
                        TextQuestion(3, "Welcome to the main page!"),
                        ImageQuestion(4, "$IMAGE_BASE/android/android.png", "Welcome Image"),
                    ),
                ),
                Section(
                    5,
                    "Chapter 1",
                    listOf(
                        TextQuestion(6, "This is the first chapter."),
                        Section(
                            7,
                            "Subsection 1.1",
                            listOf(
                                TextQuestion(8, "This is a subsection under Chapter 1."),
                                ImageQuestion(9, "$IMAGE_BASE/ios/ios.png", "Chapter 1 Image"),
                            ),
                        ),
                    ),
                ),
            ),
        ),
        Page(
            10,
            "Second Page",
            listOf(
                Section(
                    11,
                    "Chapter 2",
                    listOf(
                        TextQuestion(12, "This is the second chapter."),
                        ChoiceQuestion(
                            13,
                            "What is the main topic of Chapter 2?",
                            ResponseSet(
                                101,
                                multipleSelection = false,
                                listOf(
                                    Response(1011, "Safety procedures", Score.Points(1)),
                                    Response(1012, "Equipment checks", Score.Points(2)),
                                    Response(1013, "Not applicable", Score.NotScored),
                                ),
                            ),
                            selections[101].orEmpty(),
                        ),
                        ChoiceQuestion(
                            14,
                            "Which areas were inspected?",
                            ResponseSet(
                                102,
                                multipleSelection = true,
                                listOf(
                                    Response(1021, "Entrance", Score.NotScored),
                                    Response(1022, "Storage", Score.NotScored),
                                    Response(1023, "Loading bay", Score.NotScored),
                                ),
                            ),
                            selections[102].orEmpty(),
                        ),
                    ),
                ),
            ),
        ),
    )

private const val IMAGE_BASE = "https://raw.githubusercontent.com/github/explore/main/topics"
