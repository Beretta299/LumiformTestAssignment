package com.lumiform.formviewer.ui.form

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumiform.formviewer.R
import com.lumiform.formviewer.core.domain.model.ChoiceQuestion
import com.lumiform.formviewer.core.domain.model.SyncError
import com.lumiform.formviewer.ui.theme.FormTheme
import kotlinx.coroutines.delay

private const val ENTRANCE_ROWS = 14
private const val ENTRANCE_STAGGER_MILLIS = 45L
private const val ENTRANCE_WINDOW_MILLIS = 300L

@Composable
fun FormRoute(
    onImageClick: (questionId: Long) -> Unit,
    viewModel: FormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FormScreen(
        state = state,
        layout = currentFormLayout(),
        onRefresh = viewModel::refresh,
        onRefreshFailureShown = viewModel::onRefreshFailureShown,
        onResponseClick = viewModel::onResponseClick,
        onUpdateApp = rememberOpenStoreListing(),
        onImageClick = onImageClick,
        onShowItemIdsChange = viewModel::onShowItemIdsChange,
    )
}

/** Opens this app's store listing, or null when no store app can, so callers can hide the action. */
@Composable
private fun rememberOpenStoreListing(): (() -> Unit)? {
    val context = LocalContext.current
    return remember(context) {
        val intent = Intent(Intent.ACTION_VIEW, "market://details?id=${context.packageName}".toUri())
        if (intent.resolveActivity(context.packageManager) != null) ({ context.startActivity(intent) }) else null
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FormScreen(
    state: FormUiState,
    layout: FormLayout,
    onRefresh: () -> Unit,
    onRefreshFailureShown: () -> Unit,
    onResponseClick: (ChoiceQuestion, responseId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onUpdateApp: (() -> Unit)? = null,
    onImageClick: (questionId: Long) -> Unit = {},
    onShowItemIdsChange: (Boolean) -> Unit = {},
    screenState: FormScreenState = rememberFormScreenState(state.isOffline),
) {
    val colors = FormTheme.colors
    val snackbarHostState = screenState.snackbarHostState
    val refreshFailureMessage = state.refreshFailure?.let { stringResource(it.refreshFailureMessage) }
    LaunchedEffect(refreshFailureMessage) {
        if (refreshFailureMessage != null) {
            snackbarHostState.showSnackbar(refreshFailureMessage)
            onRefreshFailureShown()
        }
    }
    LaunchedEffect(state.isOffline) { screenState.onConnectivityChanged(state.isOffline) }
    val content = state.form as? FormState.Content
    val pullToRefreshState = screenState.pullToRefreshState
    val rows = content?.rows.orEmpty()
    val currentPage by remember(screenState, rows) { derivedStateOf { screenState.currentPage(rows) } }
    val status = screenState.syncStatus(state.isOffline, state.isRefreshing, content?.lastSyncedAt)

    Box(modifier.background(colors.paper)) {
        Scaffold(containerColor = colors.paper, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                FormHeader(
                    collapse = screenState.headerState,
                    summary = content?.summary,
                    currentPage = currentPage,
                    status = status,
                    onRefresh = onRefresh,
                    onSettingsClick = { screenState.settingsOpen = true },
                )
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    state = pullToRefreshState,
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullToRefreshState,
                            isRefreshing = state.isRefreshing,
                            color = colors.signal,
                            containerColor = colors.surface,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (val form = state.form) {
                        FormState.Loading -> {
                            LoadingState()
                        }

                        FormState.Empty -> {
                            MessageState(R.string.form_empty_title, R.string.form_empty_message)
                        }

                        is FormState.Error -> {
                            MessageState(
                                form.error.title,
                                form.error.message,
                                onRetry = onRefresh,
                                isRetrying = state.isRefreshing,
                            )
                        }

                        is FormState.Content -> {
                            // Between the list and pull-to-refresh, so a pull at the top expands the header first.
                            FormContent(
                                content = form,
                                screenState = screenState,
                                layout = layout,
                                onResponseClick = onResponseClick,
                                onUpdateApp = onUpdateApp,
                                onImageClick = onImageClick,
                                showItemIds = state.showItemIds,
                                modifier = Modifier.nestedScroll(screenState.headerState.nestedScrollConnection),
                            )
                        }
                    }
                }
            }
        }
        if (screenState.settingsOpen) {
            DisplaySettingsSheet(
                showItemIds = state.showItemIds,
                onShowItemIdsChange = onShowItemIdsChange,
                onDismiss = { screenState.settingsOpen = false },
            )
        }
        // Covers the whole screen: nothing loaded over a possibly intercepted connection is shown.
        if (state.isConnectionInsecure) {
            Box(Modifier.fillMaxSize().background(colors.hero)) {
                MessageState(
                    title = R.string.form_error_insecure_title,
                    message = R.string.form_error_insecure_message,
                    onRetry = onRefresh,
                    isRetrying = state.isRefreshing,
                    textColor = colors.onHero,
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
        }
    }
}

@Composable
private fun FormContent(
    content: FormState.Content,
    screenState: FormScreenState,
    layout: FormLayout,
    onResponseClick: (ChoiceQuestion, responseId: Long) -> Unit,
    onUpdateApp: (() -> Unit)?,
    onImageClick: (questionId: Long) -> Unit,
    showItemIds: Boolean,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        delay(ENTRANCE_WINDOW_MILLIS)
        screenState.entrancePlayed = true
    }
    val rows = content.rows
    LazyColumn(
        state = screenState.listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 32.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        rows.forEachIndexed { index, row ->
            val rail = railPosition(rows.getOrNull(index - 1), row, rows.getOrNull(index + 1))
            val playEntrance = !screenState.entrancePlayed && index < ENTRANCE_ROWS
            item(key = row.id, contentType = row.contentType) {
                FormRowItem(
                    row = row,
                    rail = rail,
                    layout = layout,
                    onResponseClick = onResponseClick,
                    onUpdateApp = onUpdateApp,
                    onImageClick = onImageClick,
                    showItemIds = showItemIds,
                    modifier = Modifier.animateItem().formContentWidth().entrance(index, playEntrance),
                )
            }
        }
    }
}

/** Rises and fades in after a delay that grows with [index]; [play] is read once, when the row first appears. */
@Composable
private fun Modifier.entrance(
    index: Int,
    play: Boolean,
): Modifier {
    val progress = remember { Animatable(if (play) 0f else 1f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(index * ENTRANCE_STAGGER_MILLIS)
            progress.animateTo(1f, spec)
        }
    }
    return graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 28.dp.toPx()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingState() {
    val description = stringResource(R.string.form_loading)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator(
            color = FormTheme.colors.signal,
            modifier =
                Modifier.size(72.dp).semantics {
                    contentDescription =
                        description
                },
        )
    }
}

/** Scrollable, so pull-to-refresh also works on an empty or failed screen. */
@Composable
private fun MessageState(
    @StringRes title: Int,
    @StringRes message: Int,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    isRetrying: Boolean = false,
    textColor: Color = FormTheme.colors.text,
) {
    val colors = FormTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
    ) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.headlineSmall,
            color = textColor,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(message),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Button(
                onClick = onRetry,
                enabled = !isRetrying,
                colors = ButtonDefaults.buttonColors(containerColor = colors.signal, contentColor = colors.onSignal),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.form_retry))
            }
        }
    }
}

private val FormRow.contentType: Any
    get() = if (this is QuestionRow) question::class else this::class

private val SyncError.title: Int
    @StringRes get() =
        when (this) {
            SyncError.Offline -> R.string.form_error_offline_title
            SyncError.Server -> R.string.form_error_server_title
            SyncError.InvalidData -> R.string.form_error_invalid_data_title
            SyncError.Insecure -> R.string.form_error_insecure_title
        }

private val SyncError.message: Int
    @StringRes get() =
        when (this) {
            SyncError.Offline -> R.string.form_error_offline_message
            SyncError.Server -> R.string.form_error_server_message
            SyncError.InvalidData -> R.string.form_error_invalid_data_message
            SyncError.Insecure -> R.string.form_error_insecure_message
        }

private val SyncError.refreshFailureMessage: Int
    @StringRes get() =
        when (this) {
            SyncError.Offline -> R.string.form_refresh_failed_offline
            SyncError.Server -> R.string.form_refresh_failed_server
            SyncError.InvalidData -> R.string.form_refresh_failed_invalid_data
            SyncError.Insecure -> R.string.form_refresh_failed_insecure
        }
