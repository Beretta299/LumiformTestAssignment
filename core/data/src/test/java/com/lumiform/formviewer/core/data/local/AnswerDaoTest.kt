package com.lumiform.formviewer.core.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.lumiform.formviewer.core.data.local.entity.AnswerSyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class AnswerDaoTest {
    @get:Rule
    val databaseRule = InMemoryDatabaseRule()

    private val answerDao get() = databaseRule.database.answerDao()

    @Before
    fun seedForm() =
        runBlocking {
            databaseRule.database.formDao().mergeForm(sampleSnapshot())
        }

    @Test
    fun upsert_isObservedAndDefaultsToPendingUpload() =
        runTest {
            answerDao.observeAnswers().test {
                assertThat(awaitItem()).isEmpty()

                answerDao.upsert(answer(1021, 102, selectedAt = 42))

                val stored = awaitItem().single()
                assertThat(stored.responseId).isEqualTo(1021L)
                assertThat(stored.selectedAt).isEqualTo(42L)
                assertThat(stored.syncState).isEqualTo(AnswerSyncState.PENDING_UPSERT)
            }
        }

    @Test
    fun observeAnswers_hidesPendingDeletes() =
        runTest {
            answerDao.upsert(answer(1021, 102))
            answerDao.upsert(answer(1022, 102).copy(syncState = AnswerSyncState.PENDING_DELETE))

            assertThat(answerDao.observeAnswers().first().map { it.responseId }).containsExactly(1021L)
            assertThat(answerDao.getAnswers(102).map { it.responseId }).containsExactly(1021L)
        }

    @Test
    fun delete_removesOnlyThatAnswer() =
        runTest {
            answerDao.upsert(answer(1021, 102))
            answerDao.upsert(answer(1022, 102))

            answerDao.delete(1021)

            assertThat(answerDao.getAnswers(102).map { it.responseId }).containsExactly(1022L)
        }

    @Test
    fun replaceAll_leavesOnlyGivenAnswersOfThatSet() =
        runTest {
            answerDao.upsert(answer(1011, 101))
            answerDao.upsert(answer(1021, 102))

            answerDao.replaceAll(101, listOf(answer(1012, 101)))

            assertThat(answerDao.getAnswers(101).map { it.responseId }).containsExactly(1012L)
            assertThat(answerDao.getAnswers(102).map { it.responseId }).containsExactly(1021L)
        }

    @Test
    fun replaceAll_withAnswerOfAnotherSet_isRejected() =
        runTest {
            answerDao.upsert(answer(1011, 101))

            assertThrowsSuspend<IllegalArgumentException> {
                answerDao.replaceAll(101, listOf(answer(1021, 102)))
            }
            assertThat(answerDao.getAnswers(101).map { it.responseId }).containsExactly(1011L)
        }

    @Test
    fun answerForUnknownResponse_violatesForeignKey() =
        runTest {
            assertThrowsSuspend<SQLiteConstraintException> {
                answerDao.upsert(answer(9999, 101))
            }
        }
}
