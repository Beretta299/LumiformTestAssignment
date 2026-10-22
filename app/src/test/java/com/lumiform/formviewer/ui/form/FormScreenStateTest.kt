package com.lumiform.formviewer.ui.form

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.mutableStateOf
import com.lumiform.formviewer.core.domain.model.TextQuestion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
class FormScreenStateTest {
    private val syncedAt = Instant.fromEpochMilliseconds(1_000)

    private fun screenState(
        firstVisibleItem: Int = 0,
        wasOffline: Boolean = false,
    ) = FormScreenState(
        listState = LazyListState(firstVisibleItemIndex = firstVisibleItem),
        headerState = CollapsingHeaderState(range = 100f),
        pullToRefreshState = PullToRefreshState(),
        snackbarHostState = SnackbarHostState(),
        entrancePlayed = mutableStateOf(false),
        settingsOpen = mutableStateOf(false),
        wasOffline = wasOffline,
    )

    @Test
    fun currentPage_isTheLastPageHeaderAtOrAboveTheFirstVisibleRow() {
        val first = PageRow(1, "One")
        val second = PageRow(3, "Two")
        val rows =
            listOf(
                first,
                QuestionRow(TextQuestion(2, "a"), "1", 0, 2),
                second,
                QuestionRow(TextQuestion(4, "b"), "1", 0, 4),
            )

        assertEquals(first, screenState(firstVisibleItem = 1).currentPage(rows))
        assertEquals(second, screenState(firstVisibleItem = 3).currentPage(rows))
    }

    @Test
    fun syncStatus_prefersSyncing_thenOffline_thenTheLastSync() {
        val state = screenState()

        assertEquals(SyncStatus.Syncing, state.syncStatus(isOffline = true, isRefreshing = true, syncedAt))
        assertEquals(SyncStatus.Offline(syncedAt), state.syncStatus(isOffline = true, isRefreshing = false, syncedAt))
        assertEquals(SyncStatus.Synced(syncedAt), state.syncStatus(isOffline = false, isRefreshing = false, syncedAt))
    }

    @Test
    fun onConnectivityChanged_showsBackOnlineForAMoment_afterReconnecting() =
        runTest {
            val state = screenState(wasOffline = true)

            launch { state.onConnectivityChanged(isOffline = false) }
            runCurrent()
            assertEquals(SyncStatus.BackOnline, state.syncStatus(isOffline = false, isRefreshing = false, syncedAt))

            advanceTimeBy(3_000)
            assertEquals(
                SyncStatus.Synced(syncedAt),
                state.syncStatus(isOffline = false, isRefreshing = false, syncedAt),
            )
        }

    @Test
    fun onConnectivityChanged_staysQuiet_whenItWasNeverOffline() =
        runTest {
            val state = screenState(wasOffline = false)

            state.onConnectivityChanged(isOffline = false)

            assertEquals(
                SyncStatus.Synced(syncedAt),
                state.syncStatus(isOffline = false, isRefreshing = false, syncedAt),
            )
        }
}
