package com.lumiform.formviewer.ui.form

import com.lumiform.formviewer.core.domain.model.ChoiceQuestion
import com.lumiform.formviewer.core.domain.model.Page
import com.lumiform.formviewer.core.domain.model.Response
import com.lumiform.formviewer.core.domain.model.ResponseSet
import com.lumiform.formviewer.core.domain.model.Score
import com.lumiform.formviewer.core.domain.model.SyncError
import com.lumiform.formviewer.core.domain.model.SyncResult
import com.lumiform.formviewer.core.domain.model.SyncState
import com.lumiform.formviewer.core.domain.model.TextQuestion
import com.lumiform.formviewer.core.domain.usecase.ObserveConnectionSecurityUseCase
import com.lumiform.formviewer.core.domain.usecase.ObserveConnectivityUseCase
import com.lumiform.formviewer.core.domain.usecase.ObserveFormUseCase
import com.lumiform.formviewer.core.domain.usecase.ObserveShowItemIdsUseCase
import com.lumiform.formviewer.core.domain.usecase.ObserveSyncStateUseCase
import com.lumiform.formviewer.core.domain.usecase.SetShowItemIdsUseCase
import com.lumiform.formviewer.core.domain.usecase.SyncFormUseCase
import com.lumiform.formviewer.core.domain.usecase.ToggleResponseUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FormViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeFormRepository()
    private val connectivity = FakeConnectivityRepository()
    private val security = FakeConnectionSecurityRepository()
    private val settings = FakeSettingsRepository()

    private val syncedAt = Instant.fromEpochMilliseconds(1_000)
    private val page = Page(id = 1, title = "Page", children = listOf(TextQuestion(id = 2, content = "Hello")))

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Creates the ViewModel and keeps its state subscribed, as the screen would. */
    private fun TestScope.viewModel(): FormViewModel {
        val viewModel =
            FormViewModel(
                observeForm = ObserveFormUseCase(repository),
                observeSyncState = ObserveSyncStateUseCase(repository),
                observeConnectivity = ObserveConnectivityUseCase(connectivity),
                observeConnectionSecurity = ObserveConnectionSecurityUseCase(security),
                observeShowItemIds = ObserveShowItemIdsUseCase(settings),
                setShowItemIds = SetShowItemIdsUseCase(settings),
                syncForm = SyncFormUseCase(repository),
                toggleResponse = ToggleResponseUseCase(repository),
                defaultDispatcher = dispatcher,
            )
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun showItemIds_followsTheStoredSetting_andTheToggleSavesIt() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            assertFalse(viewModel.uiState.value.showItemIds)

            viewModel.onShowItemIdsChange(true)

            assertTrue(settings.showItemIds.value)
            assertTrue(viewModel.uiState.value.showItemIds)
        }

    @Test
    fun form_isLoading_untilAnythingIsStored() =
        runTest(dispatcher) {
            assertEquals(FormState.Loading, viewModel().uiState.value.form)
        }

    @Test
    fun form_showsStoredRows() =
        runTest(dispatcher) {
            repository.form.value = listOf(page)
            repository.syncState.value = SyncState(syncedAt, lastError = null)

            assertEquals(
                FormState.Content(listOf(page).toRows(), FormSummary(answered = emptyList(), score = 0), syncedAt),
                viewModel().uiState.value.form,
            )
        }

    @Test
    fun form_keepsStoredRows_whenTheLastSyncFailed() =
        runTest(dispatcher) {
            repository.form.value = listOf(page)
            repository.syncState.value = SyncState(syncedAt, SyncError.Server)

            assertTrue(viewModel().uiState.value.form is FormState.Content)
        }

    @Test
    fun form_isError_whenNothingIsStoredAndTheLastSyncFailed() =
        runTest(dispatcher) {
            repository.syncState.value = SyncState(lastSyncedAt = null, SyncError.Server)

            assertEquals(FormState.Error(SyncError.Server), viewModel().uiState.value.form)
        }

    @Test
    fun form_isEmpty_whenASyncSucceededWithoutItems() =
        runTest(dispatcher) {
            repository.syncState.value = SyncState(syncedAt, lastError = null)

            assertEquals(FormState.Empty, viewModel().uiState.value.form)
        }

    @Test
    fun launch_leavesTheSyncToBackgroundWork_whenOnline() =
        runTest(dispatcher) {
            viewModel()

            assertEquals(0, repository.syncCount)
        }

    @Test
    fun launch_leavesTheSyncToBackgroundWork_whenAFormIsStored() =
        runTest(dispatcher) {
            repository.form.value = listOf(page)
            connectivity.isOnline.value = false

            viewModel()

            assertEquals(0, repository.syncCount)
        }

    @Test
    fun launch_syncs_whenOfflineWithNothingStored() =
        runTest(dispatcher) {
            connectivity.isOnline.value = false

            viewModel()

            assertEquals(1, repository.syncCount)
        }

    @Test
    fun refresh_isRefreshingWhileTheSyncRuns_andIgnoresRepeatedCalls() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            repository.syncGate = gate
            val viewModel = viewModel()

            viewModel.refresh()
            viewModel.refresh()
            assertTrue(viewModel.uiState.value.isRefreshing)

            gate.complete(Unit)
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertEquals(1, repository.syncCount)
        }

    @Test
    fun refresh_reportsAFailureOnce_whenAFormIsShown() =
        runTest(dispatcher) {
            repository.form.value = listOf(page)
            repository.syncResult = SyncResult.Failure(SyncError.Offline)
            val viewModel = viewModel()

            viewModel.refresh()
            assertEquals(SyncError.Offline, viewModel.uiState.value.refreshFailure)

            viewModel.onRefreshFailureShown()
            assertNull(viewModel.uiState.value.refreshFailure)
        }

    @Test
    fun refresh_leavesAFailureToTheErrorState_whenNothingIsShown() =
        runTest(dispatcher) {
            repository.syncResult = SyncResult.Failure(SyncError.Offline)
            val viewModel = viewModel()

            viewModel.refresh()

            assertNull(viewModel.uiState.value.refreshFailure)
        }

    @Test
    fun state_followsConnectivityAndConnectionSecurity() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            assertFalse(viewModel.uiState.value.isOffline)
            assertFalse(viewModel.uiState.value.isConnectionInsecure)

            connectivity.isOnline.value = false
            security.isCompromised.value = true

            assertTrue(viewModel.uiState.value.isOffline)
            assertTrue(viewModel.uiState.value.isConnectionInsecure)
        }

    @Test
    fun onResponseClick_persistsTheSelectionRule() =
        runTest(dispatcher) {
            val responseSet =
                ResponseSet(
                    id = 10,
                    multipleSelection = false,
                    responses = listOf(Response(11, "Yes", Score.Points(1)), Response(12, "No", Score.NotScored)),
                )
            repository.responseSets[responseSet.id] = responseSet
            val question =
                ChoiceQuestion(id = 3, content = "Ok?", responseSet = responseSet, selectedResponseIds = emptySet())
            val viewModel = viewModel()

            viewModel.onResponseClick(question, 11)
            assertEquals(setOf(11L), repository.selections[10])

            viewModel.onResponseClick(question, 12)
            assertEquals(setOf(12L), repository.selections[10])
        }
}
