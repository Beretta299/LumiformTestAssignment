package com.lumiform.formviewer.ui.form

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.time.Instant

private const val BACK_ONLINE_MILLIS = 2_500L

/**
 * The form screen's UI element state, hoisted out of the composables so callers (and tests) can own and drive it:
 * scroll position, header collapse, pull-to-refresh, snackbars, whether the entrance animation already played, and
 * the brief "back online" notice. Screen data stays in [FormUiState] from the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
class FormScreenState(
    val listState: LazyListState,
    val headerState: CollapsingHeaderState,
    val pullToRefreshState: PullToRefreshState,
    val snackbarHostState: SnackbarHostState,
    entrancePlayed: MutableState<Boolean>,
    settingsOpen: MutableState<Boolean>,
    wasOffline: Boolean,
) {
    /** Rows on screen when the form first appears rise in once; afterwards rows just appear. */
    var entrancePlayed by entrancePlayed

    /** The display settings sheet; kept across rotation. */
    var settingsOpen by settingsOpen

    private var wasOffline by mutableStateOf(wasOffline)
    private var showBackOnline by mutableStateOf(false)

    /** The page whose rows are at the top of the list. */
    fun currentPage(rows: List<FormRow>): PageRow? =
        rows.take(listState.firstVisibleItemIndex + 1).lastOrNull { it is PageRow } as? PageRow

    /** Call on every connectivity change; shows "back online" for a moment after the connection returns. */
    suspend fun onConnectivityChanged(isOffline: Boolean) {
        val cameBack = wasOffline && !isOffline
        wasOffline = isOffline
        showBackOnline = cameBack
        if (cameBack) {
            delay(BACK_ONLINE_MILLIS)
            showBackOnline = false
        }
    }

    fun syncStatus(
        isOffline: Boolean,
        isRefreshing: Boolean,
        lastSyncedAt: Instant?,
    ): SyncStatus =
        when {
            isRefreshing -> SyncStatus.Syncing
            isOffline -> SyncStatus.Offline(lastSyncedAt)
            showBackOnline -> SyncStatus.BackOnline
            else -> SyncStatus.Synced(lastSyncedAt)
        }
}

/** Scroll position, header collapse and the played entrance survive configuration changes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberFormScreenState(isOffline: Boolean = false): FormScreenState {
    val listState = rememberLazyListState()
    val headerState = rememberCollapsingHeaderState()
    val pullToRefreshState = rememberPullToRefreshState()
    val entrancePlayed = rememberSaveable { mutableStateOf(false) }
    val settingsOpen = rememberSaveable { mutableStateOf(false) }
    return remember(listState, headerState, pullToRefreshState) {
        FormScreenState(
            listState,
            headerState,
            pullToRefreshState,
            SnackbarHostState(),
            entrancePlayed,
            settingsOpen,
            isOffline,
        )
    }
}
