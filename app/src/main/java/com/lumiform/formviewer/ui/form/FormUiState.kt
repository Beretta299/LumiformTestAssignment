package com.lumiform.formviewer.ui.form

import com.lumiform.formviewer.core.domain.model.SyncError
import kotlin.time.Instant

data class FormUiState(
    val form: FormState = FormState.Loading,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val isConnectionInsecure: Boolean = false,
    /** A pull-to-refresh or Retry that failed while the form is on screen; shown once, then cleared. */
    val refreshFailure: SyncError? = null,
)

sealed interface FormState {
    /** Nothing stored yet and no sync has failed: the first sync is still on its way. */
    data object Loading : FormState

    /** A sync succeeded but the form has no items. */
    data object Empty : FormState

    /** Nothing stored and the last sync failed. */
    data class Error(
        val error: SyncError,
    ) : FormState

    data class Content(
        val rows: List<FormRow>,
        val summary: FormSummary,
        val lastSyncedAt: Instant?,
    ) : FormState
}
