package com.lumiform.formviewer.core.domain.usecase

import app.cash.turbine.test
import com.lumiform.formviewer.core.domain.FakeFormRepository
import com.lumiform.formviewer.core.domain.model.ChoiceQuestion
import com.lumiform.formviewer.core.domain.model.ImageQuestion
import com.lumiform.formviewer.core.domain.model.Page
import com.lumiform.formviewer.core.domain.model.Response
import com.lumiform.formviewer.core.domain.model.ResponseSet
import com.lumiform.formviewer.core.domain.model.Score
import com.lumiform.formviewer.core.domain.model.SyncError
import com.lumiform.formviewer.core.domain.model.SyncResult
import com.lumiform.formviewer.core.domain.model.SyncState
import com.lumiform.formviewer.core.domain.repository.ConnectionSecurityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class UseCasesTest {
    private val repository = FakeFormRepository()

    private fun responseSet(
        multipleSelection: Boolean,
        responseIds: List<Long> = listOf(1011, 1012),
    ) = ResponseSet(
        id = 101,
        multipleSelection = multipleSelection,
        responses = responseIds.map { Response(it, "Response $it", Score.NotScored) },
    )

    /** Stores the response set and returns the question as the screen shows it. */
    private fun choice(multipleSelection: Boolean): ChoiceQuestion {
        val responseSet = responseSet(multipleSelection)
        repository.responseSets[responseSet.id] = responseSet
        return ChoiceQuestion(id = 13, content = "Pick", responseSet = responseSet, selectedResponseIds = emptySet())
    }

    @Test
    fun observeForm_emitsRepositoryForm() =
        runTest {
            ObserveFormUseCase(repository)().test {
                assertEquals(emptyList<Page>(), awaitItem())
                val page = Page(id = 1, title = "Main", children = emptyList())
                repository.form.value = listOf(page)
                assertEquals(listOf(page), awaitItem())
            }
        }

    @Test
    fun observeSyncState_emitsRepositoryState() =
        runTest {
            ObserveSyncStateUseCase(repository)().test {
                assertEquals(SyncState(null, null), awaitItem())
                val failed = SyncState(Instant.fromEpochMilliseconds(5), SyncError.Offline)
                repository.syncState.value = failed
                assertEquals(failed, awaitItem())
            }
        }

    @Test
    fun syncForm_returnsRepositoryResult() =
        runTest {
            repository.syncResult = SyncResult.Failure(SyncError.Server)

            assertEquals(SyncResult.Failure(SyncError.Server), SyncFormUseCase(repository)())
            assertEquals(1, repository.syncCount)
        }

    @Test
    fun toggleResponse_singleSelect_replacesThenClears() =
        runTest {
            val toggle = ToggleResponseUseCase(repository)
            val question = choice(multipleSelection = false)

            toggle(question, 1011)
            toggle(question, 1012)
            assertEquals(setOf(1012L), repository.selections[101])

            toggle(question, 1012)
            assertEquals(emptySet<Long>(), repository.selections[101])
        }

    @Test
    fun toggleResponse_multiSelect_togglesEach() =
        runTest {
            val toggle = ToggleResponseUseCase(repository)
            val question = choice(multipleSelection = true)

            toggle(question, 1011)
            toggle(question, 1012)
            assertEquals(setOf(1011L, 1012L), repository.selections[101])

            toggle(question, 1011)
            assertEquals(setOf(1012L), repository.selections[101])
        }

    @Test
    fun toggleResponse_removedFromStoredSet_keepsSelection() =
        runTest {
            val shown = choice(multipleSelection = false)
            repository.selections[101] = setOf(1011)
            repository.responseSets[101] = responseSet(multipleSelection = false, responseIds = listOf(1011))

            ToggleResponseUseCase(repository)(shown, 1012)

            assertEquals(setOf(1011L), repository.selections[101])
        }

    @Test
    fun toggleResponse_followsStoredSelectionMode_notTheShownOne() =
        runTest {
            val shown = choice(multipleSelection = true)
            repository.selections[101] = setOf(1011)
            repository.responseSets[101] = responseSet(multipleSelection = false)

            ToggleResponseUseCase(repository)(shown, 1012)

            assertEquals(setOf(1012L), repository.selections[101])
        }

    @Test
    fun getImageQuestion_returnsMatchingQuestion() =
        runTest {
            val image = ImageQuestion(id = 4, src = "https://example.com/a.png", title = "A")
            repository.form.value = listOf(image)

            assertEquals(image, GetImageQuestionUseCase(repository)(4))
            assertNull(GetImageQuestionUseCase(repository)(5))
        }

    @Test
    fun observeConnectionSecurity_emitsRepositoryValue() =
        runTest {
            val compromised = MutableStateFlow(false)
            val security =
                object : ConnectionSecurityRepository {
                    override val isCompromised = compromised
                }

            ObserveConnectionSecurityUseCase(security)().test {
                assertEquals(false, awaitItem())
                compromised.value = true
                assertEquals(true, awaitItem())
            }
        }
}
