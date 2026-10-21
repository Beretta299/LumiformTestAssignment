package com.lumiform.formviewer.core.data.mapper

import com.google.common.truth.Truth.assertThat
import com.lumiform.formviewer.core.data.local.item
import com.lumiform.formviewer.core.data.local.sampleSnapshot
import com.lumiform.formviewer.core.data.remote.dto.PageDto
import com.lumiform.formviewer.core.data.remote.dto.TextQuestionDto
import com.lumiform.formviewer.core.data.remote.dto.UnsupportedItemDto
import com.lumiform.formviewer.core.data.remote.formFixtureDtos
import org.junit.jupiter.api.Test

class FormSnapshotMapperTest {
    @Test
    fun fixture_flattensIntoRowsWithParentAndPosition() {
        val snapshot = formFixtureDtos().toSnapshot()
        val expected = sampleSnapshot()

        assertThat(snapshot.items.map { it.copy(imageSrc = null) })
            .containsExactlyElementsIn(expected.items.map { it.copy(imageSrc = null) })
        assertThat(snapshot.items.first { it.id == 4L }.imageSrc).endsWith("/android/android.png")
        assertThat(snapshot.responseSets).containsExactlyElementsIn(expected.responseSets)
        assertThat(snapshot.responses).containsExactlyElementsIn(expected.responses)
    }

    @Test
    fun unsupportedItem_keepsRawTypeAndItsChildren() {
        val payload =
            listOf(
                PageDto(
                    1,
                    "Page",
                    listOf(
                        TextQuestionDto(2, "Before"),
                        UnsupportedItemDto(3, "date", listOf(TextQuestionDto(4, "Nested"))),
                    ),
                ),
            )

        assertThat(payload.toSnapshot().items).containsExactly(
            item(1, null, 0, "page", title = "Page"),
            item(2, 1, 0, "text", content = "Before"),
            item(3, 1, 1, "date"),
            item(4, 3, 0, "text", content = "Nested"),
        )
    }

    @Test
    fun emptyPayload_isEmptySnapshot() {
        val snapshot = emptyList<PageDto>().toSnapshot()

        assertThat(snapshot.items).isEmpty()
        assertThat(snapshot.responseSets).isEmpty()
        assertThat(snapshot.responses).isEmpty()
    }
}
