package com.lumiform.formviewer.core.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lumiform.formviewer.core.data.local.entity.ResponseEntity
import com.lumiform.formviewer.core.data.local.entity.ResponseSetEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class FormDaoTest {
    @get:Rule
    val databaseRule = InMemoryDatabaseRule()

    private val formDao get() = databaseRule.database.formDao()
    private val answerDao get() = databaseRule.database.answerDao()

    private suspend fun items() = formDao.observeForm().first().map { it.item }

    private suspend fun responseSets() = formDao.observeForm().first().mapNotNull { it.responseSet?.responseSet }

    private suspend fun responses() = formDao.observeForm().first().flatMap { it.responseSet?.responses.orEmpty() }

    // Straight from the table, so an answer left behind by a broken merge can't hide behind a missing relation.
    private fun answeredResponseIds(): List<Long> =
        databaseRule.database.openHelper.readableDatabase.query("SELECT response_id FROM answer").use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getLong(0) else null }.toList()
        }

    @Test
    fun mergeIntoEmptyDatabase_storesWholeForm() =
        runTest {
            formDao.mergeForm(sampleSnapshot())

            assertThat(items().map { it.id }).containsExactlyElementsIn(1L..14L)
            assertThat(responseSets().map { it.id }).containsExactly(101L, 102L)
            assertThat(responses()).hasSize(6)
        }

    @Test
    fun observeForm_ordersTopLevelFirstThenByParentAndPosition() =
        runTest {
            formDao.mergeForm(sampleSnapshot())

            val items = items()
            assertThat(items.take(2).map { it.id }).containsExactly(1L, 10L).inOrder()
            assertThat(items.filter { it.parentId == 2L }.map { it.id }).containsExactly(3L, 4L).inOrder()
        }

    @Test
    fun merge_keepsNullableScoreAndRawType() =
        runTest {
            val snapshot = sampleSnapshot()
            formDao.mergeForm(snapshot.copy(items = snapshot.items + item(15, 11, 3, "date", content = "When?")))

            assertThat(responses().first { it.id == 1013L }.score).isNull()
            assertThat(formDao.getItem(15)?.type).isEqualTo("date")
        }

    @Test
    fun merge_updatesRowsInPlaceAndKeepsAnswers() =
        runTest {
            formDao.mergeForm(sampleSnapshot())
            answerDao.upsert(answer(1011, 101))

            val snapshot = sampleSnapshot()
            formDao.mergeForm(
                snapshot.copy(
                    items = snapshot.items.map { if (it.id == 2L) it.copy(title = "Intro (renamed)") else it },
                    responses =
                        snapshot.responses.map {
                            if (it.id ==
                                1011L
                            ) {
                                it.copy(label = "Safety", score = 5)
                            } else {
                                it
                            }
                        },
                ),
            )

            assertThat(formDao.getItem(2)?.title).isEqualTo("Intro (renamed)")
            assertThat(responses().first { it.id == 1011L }.label).isEqualTo("Safety")
            assertThat(answeredResponseIds()).containsExactly(1011L)
        }

    @Test
    fun merge_removesItemsMissingFromPayloadTogetherWithTheirSubtree() =
        runTest {
            formDao.mergeForm(sampleSnapshot())
            answerDao.upsert(answer(1021, 102))

            // Second page (10) and everything below it is gone from the payload.
            val snapshot = sampleSnapshot()
            formDao.mergeForm(
                FormSnapshot(
                    items = snapshot.items.filter { it.id < 10 },
                    responseSets = emptyList(),
                    responses = emptyList(),
                ),
            )

            assertThat(items().map { it.id }).containsExactlyElementsIn(1L..9L)
            assertThat(responseSets()).isEmpty()
            assertThat(responses()).isEmpty()
            assertThat(answeredResponseIds()).isEmpty()
        }

    @Test
    fun merge_movesItemToNewParent() =
        runTest {
            formDao.mergeForm(sampleSnapshot())

            val snapshot = sampleSnapshot()
            formDao.mergeForm(
                snapshot.copy(
                    items =
                        snapshot.items.map {
                            if (it.id ==
                                3L
                            ) {
                                it.copy(parentId = 5, sortIndex = 5)
                            } else {
                                it
                            }
                        },
                ),
            )

            assertThat(formDao.getItem(3)?.parentId).isEqualTo(5L)
        }

    @Test
    fun merge_removedResponse_dropsOnlyItsAnswer() =
        runTest {
            formDao.mergeForm(sampleSnapshot())
            answerDao.upsert(answer(1021, 102))
            answerDao.upsert(answer(1022, 102))

            val snapshot = sampleSnapshot()
            formDao.mergeForm(snapshot.copy(responses = snapshot.responses.filterNot { it.id == 1021L }))

            assertThat(answeredResponseIds()).containsExactly(1022L)
        }

    @Test
    fun merge_responseMovedToAnotherQuestion_dropsItsAnswer() =
        runTest {
            formDao.mergeForm(sampleSnapshot())
            answerDao.upsert(answer(1021, 102))
            answerDao.upsert(answer(1022, 102))

            val snapshot = sampleSnapshot()
            formDao.mergeForm(
                snapshot.copy(
                    responses =
                        snapshot.responses.map {
                            if (it.id ==
                                1021L
                            ) {
                                it.copy(responseSetId = 101)
                            } else {
                                it
                            }
                        },
                ),
            )

            assertThat(answeredResponseIds()).containsExactly(1022L)
        }

    @Test
    fun merge_multiToSingleSelect_clearsAllAnswersOfThatQuestion() =
        runTest {
            formDao.mergeForm(sampleSnapshot())
            answerDao.upsert(answer(1021, 102))
            answerDao.upsert(answer(1011, 101))

            val snapshot = sampleSnapshot()
            formDao.mergeForm(
                snapshot.copy(responseSets = snapshot.responseSets.map { it.copy(multipleSelection = false) }),
            )

            assertThat(answeredResponseIds()).containsExactly(1011L)
        }

    @Test
    fun merge_singleToMultiSelect_keepsAnswers() =
        runTest {
            formDao.mergeForm(sampleSnapshot())
            answerDao.upsert(answer(1011, 101))

            val snapshot = sampleSnapshot()
            formDao.mergeForm(
                snapshot.copy(responseSets = snapshot.responseSets.map { it.copy(multipleSelection = true) }),
            )

            assertThat(answeredResponseIds()).containsExactly(1011L)
        }

    @Test
    fun merge_choiceBecameText_removesItsResponsesAndAnswers() =
        runTest {
            formDao.mergeForm(sampleSnapshot())
            answerDao.upsert(answer(1011, 101))

            val snapshot = sampleSnapshot()
            formDao.mergeForm(
                snapshot.copy(
                    items = snapshot.items.map { if (it.id == 13L) it.copy(type = "text") else it },
                    responseSets = snapshot.responseSets.filterNot { it.id == 101L },
                    responses = snapshot.responses.filterNot { it.responseSetId == 101L },
                ),
            )

            assertThat(formDao.getItem(13)?.type).isEqualTo("text")
            assertThat(responseSets().map { it.id }).containsExactly(102L)
            assertThat(responses().map { it.responseSetId }.toSet()).containsExactly(102L)
            assertThat(answeredResponseIds()).isEmpty()
        }

    @Test
    fun merge_responseSetReplacedWithNewIdOnSameQuestion_storesNewSet() =
        runTest {
            formDao.mergeForm(sampleSnapshot())

            val snapshot = sampleSnapshot()
            formDao.mergeForm(
                snapshot.copy(
                    responseSets =
                        snapshot.responseSets.filterNot { it.id == 101L } +
                            ResponseSetEntity(
                                103,
                                13,
                                false,
                            ),
                    responses =
                        snapshot.responses.filterNot { it.responseSetId == 101L } +
                            ResponseEntity(1031, 103, 0, "Yes", 1),
                ),
            )

            assertThat(responseSets().map { it.id to it.itemId }).containsExactly(103L to 13L, 102L to 14L)
            assertThat(responses().filter { it.responseSetId == 103L }.map { it.id }).containsExactly(1031L)
        }

    @Test
    fun merge_emptyPayload_clearsForm() =
        runTest {
            formDao.mergeForm(sampleSnapshot())

            formDao.mergeForm(FormSnapshot(emptyList(), emptyList(), emptyList()))

            assertThat(items()).isEmpty()
            assertThat(responseSets()).isEmpty()
            assertThat(responses()).isEmpty()
        }

    @Test
    fun merge_childListedBeforeItsParent_isAccepted() =
        runTest {
            formDao.mergeForm(
                FormSnapshot(listOf(item(21, 20, 0, "text"), item(20, null, 0, "page")), emptyList(), emptyList()),
            )

            assertThat(items().map { it.id }).containsExactly(20L, 21L)
        }

    @Test
    fun merge_itemWithMissingParent_failsAndLeavesDatabaseUntouched() =
        runTest {
            formDao.mergeForm(sampleSnapshot())

            val orphan = FormSnapshot(listOf(item(21, 999, 0, "text")), emptyList(), emptyList())
            assertThrowsSuspend<SQLiteConstraintException> {
                formDao.mergeForm(orphan)
            }

            assertThat(items().map { it.id }).containsExactlyElementsIn(1L..14L)
        }
}
