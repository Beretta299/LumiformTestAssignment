package com.lumiform.formviewer.core.data.local

import com.lumiform.formviewer.core.data.local.entity.AnswerEntity
import com.lumiform.formviewer.core.data.local.entity.FormItemEntity
import com.lumiform.formviewer.core.data.local.entity.ResponseEntity
import com.lumiform.formviewer.core.data.local.entity.ResponseSetEntity

internal fun item(
    id: Long,
    parentId: Long?,
    sortIndex: Int,
    type: String,
    title: String? = null,
    content: String? = null,
    imageSrc: String? = null,
) = FormItemEntity(id, parentId, sortIndex, type, title, content, imageSrc)

internal fun answer(
    responseId: Long,
    responseSetId: Long,
    selectedAt: Long = 0,
) = AnswerEntity(responseId = responseId, responseSetId = responseSetId, selectedAt = selectedAt)

/** Mirrors the challenge's sample payload. */
internal fun sampleSnapshot() =
    FormSnapshot(
        items =
            listOf(
                item(1, null, 0, "page", title = "Main Page"),
                item(2, 1, 0, "section", title = "Introduction"),
                item(3, 2, 0, "text", content = "Welcome to the main page!"),
                item(4, 2, 1, "image", title = "Welcome Image", imageSrc = "https://example.com/android.png"),
                item(5, 1, 1, "section", title = "Chapter 1"),
                item(6, 5, 0, "text", content = "This is the first chapter."),
                item(7, 5, 1, "section", title = "Subsection 1.1"),
                item(8, 7, 0, "text", content = "This is a subsection under Chapter 1."),
                item(9, 7, 1, "image", title = "Chapter 1 Image", imageSrc = "https://example.com/ios.png"),
                item(10, null, 1, "page", title = "Second Page"),
                item(11, 10, 0, "section", title = "Chapter 2"),
                item(12, 11, 0, "text", content = "This is the second chapter."),
                item(13, 11, 1, "choice", content = "What is the main topic of Chapter 2?"),
                item(14, 11, 2, "choice", content = "Which areas were inspected?"),
            ),
        responseSets =
            listOf(
                ResponseSetEntity(id = 101, itemId = 13, multipleSelection = false),
                ResponseSetEntity(id = 102, itemId = 14, multipleSelection = true),
            ),
        responses =
            listOf(
                ResponseEntity(1011, 101, 0, "Safety procedures", 1),
                ResponseEntity(1012, 101, 1, "Equipment checks", 2),
                ResponseEntity(1013, 101, 2, "Not applicable", null),
                ResponseEntity(1021, 102, 0, "Entrance", null),
                ResponseEntity(1022, 102, 1, "Storage", null),
                ResponseEntity(1023, 102, 2, "Loading bay", null),
            ),
    )
