package com.lumiform.formviewer.ui.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumiform.formviewer.core.domain.model.ChoiceQuestion
import com.lumiform.formviewer.core.domain.model.SyncError
import com.lumiform.formviewer.core.domain.model.SyncResult
import com.lumiform.formviewer.core.domain.model.SyncState
import com.lumiform.formviewer.core.domain.usecase.ObserveConnectionSecurityUseCase
import com.lumiform.formviewer.core.domain.usecase.ObserveConnectivityUseCase
import com.lumiform.formviewer.core.domain.usecase.ObserveFormUseCase
import com.lumiform.formviewer.core.domain.usecase.ObserveShowItemIdsUseCase
import com.lumiform.formviewer.core.domain.usecase.ObserveSyncStateUseCase
import com.lumiform.formviewer.core.domain.usecase.SetShowItemIdsUseCase
import com.lumiform.formviewer.core.domain.usecase.SyncFormUseCase
import com.lumiform.formviewer.core.domain.usecase.ToggleResponseUseCase
import com.lumiform.formviewer.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The launch sync is left to the background work scheduled on app start; this screen syncs directly only on
 * pull-to-refresh and Retry, and on a first launch offline, where that work would wait for a network and leave the
 * screen loading instead of offering Retry.
 */
@HiltViewModel
class FormViewModel
    @Inject
    constructor(
        observeForm: ObserveFormUseCase,
        observeSyncState: ObserveSyncStateUseCase,
        observeConnectivity: ObserveConnectivityUseCase,
        observeConnectionSecurity: ObserveConnectionSecurityUseCase,
        observeShowItemIds: ObserveShowItemIdsUseCase,
        private val setShowItemIds: SetShowItemIdsUseCase,
        private val syncForm: SyncFormUseCase,
        private val toggleResponse: ToggleResponseUseCase,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val refreshState = MutableStateFlow<Refresh>(Refresh.Idle)

        // Shared, so the launch check below and the screen state read the database once.
        private val form =
            observeForm()
                .map { FlatForm(it.toRows(), it.summary()) }
                .flowOn(defaultDispatcher)
                .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

        val uiState: StateFlow<FormUiState> =
            combine(
                form,
                observeSyncState(),
                combine(observeConnectivity(), observeConnectionSecurity(), ::Connection),
                observeShowItemIds(),
                refreshState,
            ) { flatForm, syncState, connection, showItemIds, refresh ->
                val formState = formState(flatForm, syncState)
                FormUiState(
                    form = formState,
                    isRefreshing = refresh == Refresh.Running,
                    isOffline = !connection.isOnline,
                    isConnectionInsecure = connection.isCompromised,
                    refreshFailure = (refresh as? Refresh.Failed)?.error?.takeIf { formState is FormState.Content },
                    showItemIds = showItemIds,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FormUiState())

        init {
            viewModelScope.launch {
                if (form.first().rows.isEmpty() && !observeConnectivity().first()) refresh()
            }
        }

        fun refresh() {
            if (refreshState.value == Refresh.Running) return
            refreshState.value = Refresh.Running
            viewModelScope.launch {
                refreshState.value =
                    when (val result = syncForm()) {
                        SyncResult.Success -> Refresh.Idle
                        is SyncResult.Failure -> Refresh.Failed(result.error)
                    }
            }
        }

        fun onRefreshFailureShown() {
            refreshState.update { if (it is Refresh.Failed) Refresh.Idle else it }
        }

        fun onResponseClick(
            question: ChoiceQuestion,
            responseId: Long,
        ) {
            viewModelScope.launch { toggleResponse(question, responseId) }
        }

        fun onShowItemIdsChange(show: Boolean) {
            viewModelScope.launch { setShowItemIds(show) }
        }

        private data class Connection(
            val isOnline: Boolean,
            val isCompromised: Boolean,
        )

        private sealed interface Refresh {
            data object Idle : Refresh

            data object Running : Refresh

            data class Failed(
                val error: SyncError,
            ) : Refresh
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

private data class FlatForm(
    val rows: List<FormRow>,
    val summary: FormSummary,
)

/** A stored form always wins: a failed sync only matters while there is nothing to show. */
private fun formState(
    form: FlatForm,
    syncState: SyncState,
): FormState {
    val error = syncState.lastError
    return when {
        form.rows.isNotEmpty() -> FormState.Content(form.rows, form.summary, syncState.lastSyncedAt)
        error != null -> FormState.Error(error)
        syncState.lastSyncedAt != null -> FormState.Empty
        else -> FormState.Loading
    }
}
