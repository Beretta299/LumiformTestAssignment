package com.lumiform.formviewer.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.lumiform.formviewer.core.data.local.InMemoryDatabaseRule
import com.lumiform.formviewer.core.data.local.RoomFormLocalDataSource
import com.lumiform.formviewer.core.data.remote.FormFetchException
import com.lumiform.formviewer.core.data.remote.FormRemoteDataSource
import com.lumiform.formviewer.core.data.remote.dto.ChoiceQuestionDto
import com.lumiform.formviewer.core.data.remote.dto.FormItemDto
import com.lumiform.formviewer.core.data.remote.dto.PageDto
import com.lumiform.formviewer.core.data.remote.dto.ResponseDto
import com.lumiform.formviewer.core.data.remote.dto.SectionDto
import com.lumiform.formviewer.core.data.remote.formFixtureDomain
import com.lumiform.formviewer.core.data.remote.formFixtureDtos
import com.lumiform.formviewer.core.domain.model.ChoiceQuestion
import com.lumiform.formviewer.core.domain.model.FormContainer
import com.lumiform.formviewer.core.domain.model.FormItem
import com.lumiform.formviewer.core.domain.model.ImageQuestion
import com.lumiform.formviewer.core.domain.model.SyncError
import com.lumiform.formviewer.core.domain.model.SyncResult
import com.lumiform.formviewer.core.domain.model.SyncState
import com.lumiform.formviewer.core.domain.usecase.ToggleResponseUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
internal class OfflineFirstFormRepositoryTest {
    @get:Rule
    val databaseRule = InMemoryDatabaseRule()

    private val remote = FakeFormRemoteDataSource()
    private val clock = FakeClock(Instant.fromEpochMilliseconds(1_000))

    private fun repository() = OfflineFirstFormRepository(RoomFormLocalDataSource(databaseRule.database), remote, clock)

    private val repository by lazy { repository() }

    @Test
    fun beforeAnySync_formIsEmptyAndNeverSynced() =
        runTest {
            assertThat(repository.observeForm().first()).isEmpty()
            assertThat(repository.observeSyncState().first()).isEqualTo(SyncState(null, null))
        }

    @Test
    fun successfulSync_storesFormAndRecordsTime() =
        runTest {
            repository.observeForm().test {
                assertThat(awaitItem()).isEmpty()

                assertThat(repository.sync()).isEqualTo(SyncResult.Success)

                assertThat(awaitItem()).isEqualTo(formFixtureDomain())
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(repository.observeSyncState().first())
                .isEqualTo(SyncState(lastSyncedAt = clock.now, lastError = null))
        }

    @Test
    fun cachedForm_isServedWhileOffline() =
        runTest {
            repository.sync()
            remote.failWith(FormFetchException.Connectivity(IOException()))

            val restarted = repository()
            assertThat(restarted.sync()).isEqualTo(SyncResult.Failure(SyncError.Offline))
            assertThat(restarted.observeForm().first()).isEqualTo(formFixtureDomain())
        }

    @Test
    fun failedSync_keepsCacheAndLastSyncTime_andRecordsEachErrorKind() =
        runTest {
            repository.sync()
            val syncedAt = clock.now
            clock.now = Instant.fromEpochMilliseconds(2_000)
            val failures =
                mapOf(
                    FormFetchException.Connectivity(IOException()) to SyncError.Offline,
                    FormFetchException.Http(503) to SyncError.Server,
                    FormFetchException.MalformedResponse() to SyncError.InvalidData,
                    FormFetchException.Insecure(IOException()) to SyncError.Insecure,
                )

            failures.forEach { (exception, error) ->
                remote.failWith(exception)

                assertThat(repository.sync()).isEqualTo(SyncResult.Failure(error))
                assertThat(repository.observeSyncState().first()).isEqualTo(SyncState(syncedAt, error))
                assertThat(repository.observeForm().first()).isEqualTo(formFixtureDomain())
            }
        }

    @Test
    fun failedFirstSync_recordsErrorWithoutSyncTime() =
        runTest {
            remote.failWith(FormFetchException.Insecure(IOException()))

            assertThat(repository.sync()).isEqualTo(SyncResult.Failure(SyncError.Insecure))
            assertThat(repository.observeSyncState().first()).isEqualTo(SyncState(null, SyncError.Insecure))
            assertThat(repository.observeForm().first()).isEmpty()
        }

    @Test
    fun successfulSyncAfterFailure_clearsError() =
        runTest {
            remote.failWith(FormFetchException.Http(500))
            repository.sync()
            remote.succeedWith(formFixtureDtos())

            repository.sync()

            assertThat(repository.observeSyncState().first()).isEqualTo(SyncState(clock.now, null))
        }

    @Test
    fun selections_areObservedAndSurviveResyncAndRestart() =
        runTest {
            repository.sync()

            repository.updateSelection(101) { _, _ -> setOf(1012) }
            repository.updateSelection(102) { _, selected -> selected + 1021 + 1023 }
            repository.sync()

            val expected = formFixtureDomain(mapOf(101L to setOf(1012L), 102L to setOf(1021L, 1023L)))
            assertThat(repository.observeForm().first()).isEqualTo(expected)
            assertThat(repository().observeForm().first()).isEqualTo(expected)
        }

    @Test
    fun updateSelection_actsOnStoredSelection_andKeepsUnchangedAnswers() =
        runTest {
            repository.sync()
            repository.updateSelection(102) { _, selected -> selected + 1021 }
            clock.now = Instant.fromEpochMilliseconds(2_000)

            repository.updateSelection(102) { _, current ->
                assertThat(current).containsExactly(1021L)
                current + 1022
            }

            val answers =
                databaseRule.database
                    .answerDao()
                    .getAnswers(102)
                    .associate { it.responseId to it.selectedAt }
            assertThat(answers).containsExactly(1021L, 1_000L, 1022L, 2_000L)
        }

    @Test
    fun updateSelection_toEmpty_clearsAnswers() =
        runTest {
            repository.sync()
            repository.updateSelection(101) { _, _ -> setOf(1011) }

            repository.updateSelection(101) { _, _ -> emptySet() }

            assertThat(repository.observeForm().first()).isEqualTo(formFixtureDomain())
        }

    @Test
    fun tapOnResponseRemovedBySync_isIgnored() =
        runTest {
            repository.sync()
            repository.updateSelection(101) { _, _ -> setOf(1011) }
            val shown = shownChoice(13)
            remote.succeedWith(formFixtureDtos().mapChoices { it.withResponses { responses -> responses.take(1) } })
            repository.sync()

            toggleResponse(shown, 1012)

            assertThat(selectedIds(101)).containsExactly(1011L)
        }

    @Test
    fun tapAfterSwitchToSingleSelect_leavesAtMostOneAnswer() =
        runTest {
            repository.sync()
            val shown = shownChoice(14)
            remote.succeedWith(formFixtureDtos().mapChoices { it.withMultipleSelection(false) })
            repository.sync()

            toggleResponse(shown, 1021)
            toggleResponse(shown, 1022)

            assertThat(selectedIds(102)).containsExactly(1022L)
        }

    @Test
    fun tapOnQuestionRemovedBySync_isIgnored() =
        runTest {
            repository.sync()
            val shown = shownChoice(14)
            remote.succeedWith(formFixtureDtos().mapChoices { if (it.id == 14L) null else it })
            repository.sync()

            toggleResponse(shown, 1021)

            assertThat(databaseRule.database.answerDao().getAnswers(102)).isEmpty()
        }

    @Test
    fun updateSelection_dropsIdsOutsideTheSet() =
        runTest {
            repository.sync()

            repository.updateSelection(101) { _, _ -> setOf(1011, 1021) }

            assertThat(selectedIds(101)).containsExactly(1011L)
        }

    private val toggleResponse get() = ToggleResponseUseCase(repository)

    private suspend fun shownChoice(id: Long): ChoiceQuestion =
        repository
            .observeForm()
            .first()
            .flatten()
            .filterIsInstance<ChoiceQuestion>()
            .single { it.id == id }

    private suspend fun selectedIds(responseSetId: Long) =
        databaseRule.database
            .answerDao()
            .getAnswers(responseSetId)
            .map { it.responseId }

    @Test
    fun getImageQuestion_returnsOnlyImages() =
        runTest {
            repository.sync()

            assertThat(repository.getImageQuestion(4)).isEqualTo(
                ImageQuestion(
                    4,
                    "https://raw.githubusercontent.com/github/explore/main/topics/android/android.png",
                    "Welcome Image",
                ),
            )
            assertThat(repository.getImageQuestion(3)).isNull()
            assertThat(repository.getImageQuestion(999)).isNull()
        }
}

private fun List<FormItem>.flatten(): List<FormItem> =
    flatMap { item -> listOf(item) + (item as? FormContainer)?.children?.flatten().orEmpty() }

/** Applies [transform] to every choice in the payload; returning null removes the choice. */
private fun List<FormItemDto>.mapChoices(transform: (ChoiceQuestionDto) -> ChoiceQuestionDto?): List<FormItemDto> =
    mapNotNull { item ->
        when (item) {
            is ChoiceQuestionDto -> transform(item)
            is PageDto -> item.copy(items = item.items.mapChoices(transform))
            is SectionDto -> item.copy(items = item.items.mapChoices(transform))
            else -> item
        }
    }

private fun ChoiceQuestionDto.withResponses(transform: (List<ResponseDto>) -> List<ResponseDto>) =
    copy(responseSet = responseSet.copy(responses = transform(responseSet.responses)))

private fun ChoiceQuestionDto.withMultipleSelection(multipleSelection: Boolean) =
    copy(responseSet = responseSet.copy(multipleSelection = multipleSelection))

private class FakeFormRemoteDataSource : FormRemoteDataSource {
    private var fetch: () -> List<FormItemDto> = ::formFixtureDtos

    fun succeedWith(form: List<FormItemDto>) {
        fetch = { form }
    }

    fun failWith(exception: FormFetchException) {
        fetch = { throw exception }
    }

    override suspend fun fetchForm(): List<FormItemDto> = fetch()
}

private class FakeClock(
    var now: Instant,
) : Clock {
    override fun now(): Instant = now
}
