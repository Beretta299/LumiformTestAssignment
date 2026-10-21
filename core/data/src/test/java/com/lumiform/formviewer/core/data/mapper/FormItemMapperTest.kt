package com.lumiform.formviewer.core.data.mapper

import com.google.common.truth.Truth.assertThat
import com.lumiform.formviewer.core.data.local.FormSnapshot
import com.lumiform.formviewer.core.data.local.answer
import com.lumiform.formviewer.core.data.local.entity.AnswerSyncState
import com.lumiform.formviewer.core.data.local.entity.ResponseEntity
import com.lumiform.formviewer.core.data.local.entity.ResponseSetEntity
import com.lumiform.formviewer.core.data.local.item
import com.lumiform.formviewer.core.data.local.sampleSnapshot
import com.lumiform.formviewer.core.data.local.toStored
import com.lumiform.formviewer.core.data.remote.formFixtureDomain
import com.lumiform.formviewer.core.data.remote.formFixtureDtos
import com.lumiform.formviewer.core.domain.model.ChoiceQuestion
import com.lumiform.formviewer.core.domain.model.FormContainer
import com.lumiform.formviewer.core.domain.model.FormItem
import com.lumiform.formviewer.core.domain.model.ImageQuestion
import com.lumiform.formviewer.core.domain.model.Page
import com.lumiform.formviewer.core.domain.model.Response
import com.lumiform.formviewer.core.domain.model.Score
import com.lumiform.formviewer.core.domain.model.TextQuestion
import com.lumiform.formviewer.core.domain.model.Unsupported
import org.junit.jupiter.api.Test

class FormItemMapperTest {
    @Test
    fun fixture_roundTripsFromPayloadToDomainTree() {
        val form = formFixtureDtos().toSnapshot().toStored().toDomainForm()

        assertThat(form).isEqualTo(formFixtureDomain())
    }

    @Test
    fun siblings_areOrderedByPosition_notByRowOrder() {
        val form =
            formFixtureDtos()
                .toSnapshot()
                .toStored()
                .reversed()
                .toDomainForm()

        assertThat(form).isEqualTo(formFixtureDomain())
    }

    @Test
    fun answers_becomeSelections_exceptPendingDeletes() {
        val answers =
            listOf(
                answer(1012, 101),
                answer(1021, 102),
                answer(1022, 102).copy(syncState = AnswerSyncState.PENDING_DELETE),
            )

        val form = formFixtureDtos().toSnapshot().toStored(answers).toDomainForm()

        assertThat(form).isEqualTo(formFixtureDomain(mapOf(101L to setOf(1012L), 102L to setOf(1021L))))
    }

    @Test
    fun score_nullBecomesNotScored() {
        val choice =
            sampleSnapshot()
                .toStored()
                .toDomainForm()
                .flatten()
                .filterIsInstance<ChoiceQuestion>()
                .first { it.id == 13L }

        assertThat(choice.responseSet.responses.map { it.score })
            .containsExactly(Score.Points(1), Score.Points(2), Score.NotScored)
            .inOrder()
    }

    @Test
    fun unknownType_becomesUnsupported_withItsChildren() {
        val snapshot =
            FormSnapshot(
                items =
                    listOf(
                        item(1, null, 0, "page", title = "Page"),
                        item(2, 1, 0, "date"),
                        item(3, 2, 0, "text", content = "Nested"),
                        item(4, null, 1, "wizard"),
                    ),
                responseSets = emptyList(),
                responses = emptyList(),
            )

        assertThat(snapshot.toStored().toDomainForm())
            .containsExactly(
                Page(1, "Page", listOf(Unsupported(2, "date", listOf(TextQuestion(3, "Nested"))))),
                Unsupported(4, "wizard", emptyList()),
            ).inOrder()
    }

    @Test
    fun choiceWithoutResponseSet_becomesUnsupported() {
        val snapshot = FormSnapshot(listOf(item(1, null, 0, "choice", content = "?")), emptyList(), emptyList())

        assertThat(snapshot.toStored().toDomainForm()).containsExactly(Unsupported(1, "choice", emptyList()))
    }

    @Test
    fun responses_areOrderedByPosition() {
        val snapshot =
            FormSnapshot(
                items = listOf(item(1, null, 0, "choice", content = "?")),
                responseSets = listOf(ResponseSetEntity(id = 10, itemId = 1, multipleSelection = false)),
                responses = listOf(ResponseEntity(12, 10, 1, "Second", null), ResponseEntity(11, 10, 0, "First", 3)),
            )

        val choice = snapshot.toStored().toDomainForm().single() as ChoiceQuestion

        assertThat(choice.responseSet.responses)
            .containsExactly(Response(11, "First", Score.Points(3)), Response(12, "Second", Score.NotScored))
            .inOrder()
    }

    @Test
    fun toImageQuestion_mapsOnlyImageItems() {
        val image = item(4, 2, 1, "image", title = "Welcome", imageSrc = "https://example.com/a.png")

        assertThat(image.toImageQuestion()).isEqualTo(ImageQuestion(4, "https://example.com/a.png", "Welcome"))
        assertThat(item(3, 2, 0, "text", content = "Hi").toImageQuestion()).isNull()
    }

    private fun List<FormItem>.flatten(): List<FormItem> =
        flatMap { item -> listOf(item) + (item as? FormContainer)?.children?.flatten().orEmpty() }
}
