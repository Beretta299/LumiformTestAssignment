package com.lumiform.formviewer.core.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lumiform.formviewer.core.data.local.entity.AnswerSyncState
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
    fun upsert_storesTheAnswerAsPendingUpload() =
        runTest {
            answerDao.upsert(answer(1021, 102, selectedAt = 42))

            val stored = answerDao.getAnswers(102).single()
            assertThat(stored.responseId).isEqualTo(1021L)
            assertThat(stored.selectedAt).isEqualTo(42L)
            assertThat(stored.syncState).isEqualTo(AnswerSyncState.PENDING_UPSERT)
        }

    @Test
    fun getAnswers_hidesPendingDeletes() =
        runTest {
            answerDao.upsert(answer(1021, 102))
            answerDao.upsert(answer(1022, 102).copy(syncState = AnswerSyncState.PENDING_DELETE))

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
    fun answerForUnknownResponse_violatesForeignKey() =
        runTest {
            assertThrowsSuspend<SQLiteConstraintException> {
                answerDao.upsert(answer(9999, 101))
            }
        }
}
