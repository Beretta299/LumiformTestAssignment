package com.lumiform.formviewer.ui.form

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.lumiform.formviewer.R
import com.lumiform.formviewer.ui.theme.FormTheme
import kotlinx.coroutines.delay
import kotlin.time.Instant

private val ExpandedHeight = 212.dp
private val CollapsedHeight = 64.dp
private const val MAX_SEGMENTS = 16
private const val EXPANDED_FADE_END = 0.625f
private const val COLLAPSED_FADE_START = 0.4f
private const val CROSSOVER = 0.5f
private const val MINUTE_MILLIS = 60_000L
private const val PULSE_MILLIS = 1_400

/**
 * Collapses the header before the list scrolls and expands it again only once the list is back at the top, like
 * Material's exit-until-collapsed app bar. It sits between the list and pull-to-refresh, so pulling at the top
 * expands the header first and only then refreshes.
 */
@Stable
class CollapsingHeaderState(
    private val range: Float,
    initialOffset: Float = 0f,
) {
    private var offset by mutableFloatStateOf(initialOffset.coerceIn(0f, range))

    /** 0 when expanded, 1 when collapsed. */
    val fraction: Float get() = if (range == 0f) 0f else offset / range

    val nestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y >= 0f) return Offset.Zero
                val collapsed = (offset - available.y).coerceAtMost(range)
                val consumed = collapsed - offset
                offset = collapsed
                return Offset(0f, -consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y <= 0f) return Offset.Zero
                val expanded = (offset - available.y).coerceAtLeast(0f)
                val used = offset - expanded
                offset = expanded
                return Offset(0f, used)
            }
        }

    companion object {
        /** Saves the collapsed fraction, so a rotation keeps the header in step with the restored scroll position. */
        fun saver(range: Float): Saver<CollapsingHeaderState, Float> =
            Saver(save = { it.fraction }, restore = { CollapsingHeaderState(range, it * range) })
    }
}

@Composable
fun rememberCollapsingHeaderState(): CollapsingHeaderState {
    val range = with(LocalDensity.current) { (ExpandedHeight - CollapsedHeight).toPx() }
    return rememberSaveable(range, saver = CollapsingHeaderState.saver(range)) { CollapsingHeaderState(range) }
}

/** What the header says about syncing; [FormScreenState.syncStatus] picks one. */
sealed interface SyncStatus {
    data object Syncing : SyncStatus

    data class Offline(
        val lastSyncedAt: Instant?,
    ) : SyncStatus

    data object BackOnline : SyncStatus

    data class Synced(
        val lastSyncedAt: Instant?,
    ) : SyncStatus
}

/**
 * An ink card: answered count, a rolling score and one progress segment per question. It morphs into a slim bar
 * with the current page's title as the list scrolls. Without a form ([summary] null) it stays slim.
 */
@Composable
fun FormHeader(
    collapse: CollapsingHeaderState,
    summary: FormSummary?,
    currentPage: PageRow?,
    status: SyncStatus,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FormTheme.colors
    // Read only in layout and draw lambdas, so scrolling relayouts and redraws the header without recomposing it.
    val hasForm = summary != null
    val fraction = { if (hasForm) collapse.fraction else 1f }
    // The two layouts crossfade; each is composed only while it shows, and only the one mostly on screen is
    // reachable by touch and accessibility. These flip at thresholds, so they recompose a handful of times per scroll.
    val showExpanded by remember(collapse, hasForm) { derivedStateOf { hasForm && fraction() < EXPANDED_FADE_END } }
    val showCollapsed by remember(collapse, hasForm) { derivedStateOf { fraction() > COLLAPSED_FADE_START } }
    val collapsedInFront by remember(collapse, hasForm) { derivedStateOf { fraction() >= CROSSOVER } }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .layout { measurable, constraints ->
                    val height = lerp(ExpandedHeight, CollapsedHeight, fraction()).roundToPx()
                    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                    layout(placeable.width, height) { placeable.place(0, 0) }
                }.graphicsLayer {
                    shape = RoundedCornerShape(lerp(28.dp, 22.dp, fraction()))
                    clip = true
                }.background(colors.hero),
    ) {
        if (summary != null && showExpanded) {
            ExpandedHeader(
                summary = summary,
                currentPage = currentPage,
                status = status,
                onRefresh = onRefresh,
                modifier =
                    Modifier
                        .graphicsLayer {
                            alpha = (1f - fraction() / EXPANDED_FADE_END).coerceIn(0f, 1f)
                            translationY = -fraction() * 24.dp.toPx()
                        }.hiddenFromAccessibility(collapsedInFront),
            )
        }
        if (showCollapsed) {
            CollapsedHeader(
                summary = summary,
                title = currentPage?.title ?: stringResource(R.string.app_name),
                status = status,
                onRefresh = onRefresh,
                modifier =
                    Modifier
                        .graphicsLayer {
                            alpha = ((fraction() - COLLAPSED_FADE_START) / (1f - COLLAPSED_FADE_START)).coerceIn(0f, 1f)
                            translationY = (1f - fraction()) * 16.dp.toPx()
                        }.hiddenFromAccessibility(!collapsedInFront),
            )
        }
    }
}

private fun Modifier.hiddenFromAccessibility(hidden: Boolean): Modifier = if (hidden) clearAndSetSemantics {} else this

@Composable
private fun ExpandedHeader(
    summary: FormSummary,
    currentPage: PageRow?,
    status: SyncStatus,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FormTheme.colors
    Column(modifier.fillMaxSize().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge, color = colors.onHero)
            Spacer(Modifier.weight(1f))
            StatusLabel(status)
            RefreshButton(status == SyncStatus.Syncing, onRefresh)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(end = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.form_answered_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.heroMuted,
                )
                Text(
                    "${summary.answeredCount}/${summary.total}",
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.onHero,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.form_score_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.heroMuted,
                )
                RollingNumber(summary.score, colors.signal)
            }
        }
        Spacer(Modifier.height(14.dp))
        ProgressSegments(summary, Modifier.fillMaxWidth().padding(end = 12.dp), height = 6.dp)
        currentPage?.let { page ->
            Text(
                text =
                    listOfNotNull(
                        page.title,
                        page.number?.let { stringResource(R.string.form_page_number, it, page.pageCount) },
                    ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = colors.heroMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun CollapsedHeader(
    summary: FormSummary?,
    title: String,
    status: SyncStatus,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FormTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize().padding(start = 20.dp, end = 8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onHero,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (summary != null) {
            ProgressSegments(summary, Modifier.width(72.dp), height = 5.dp)
            Text(summary.score.toString(), style = MaterialTheme.typography.titleMedium, color = colors.signal)
        } else {
            StatusLabel(status)
        }
        RefreshButton(status == SyncStatus.Syncing, onRefresh)
    }
}

/** Rolls up when the score grows and down when it shrinks. */
@Composable
private fun RollingNumber(
    value: Int,
    color: Color,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val direction = if (targetState >= initialState) 1 else -1
            (slideInVertically(motion.defaultSpatialSpec()) { it * direction } + fadeIn(motion.fastEffectsSpec()))
                .togetherWith(
                    slideOutVertically(motion.defaultSpatialSpec()) { -it * direction } +
                        fadeOut(motion.fastEffectsSpec()),
                ).using(SizeTransform(clip = true))
        },
        label = "score",
    ) { score ->
        Text(score.toString(), style = MaterialTheme.typography.displaySmall, color = color)
    }
}

/** One segment per choice question that fills when answered; a single bar once there are too many to tell apart. */
@Composable
private fun ProgressSegments(
    summary: FormSummary,
    modifier: Modifier,
    height: Dp,
) {
    val colors = FormTheme.colors
    val segments =
        if (summary.total in
            1..MAX_SEGMENTS
        ) {
            summary.answered.map { if (it) 1f else 0f }
        } else {
            listOf(summary.fraction)
        }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        segments.forEach { target ->
            val fill by animateFloatAsState(target, MaterialTheme.motionScheme.slowSpatialSpec())
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .drawBehind {
                        val radius = CornerRadius(size.height / 2)
                        drawRoundRect(colors.heroTrack, cornerRadius = radius)
                        drawRoundRect(
                            colors.signal,
                            size = Size(size.width * fill.coerceIn(0f, 1f), size.height),
                            cornerRadius = radius,
                        )
                    },
            )
        }
    }
}

@Composable
private fun StatusLabel(status: SyncStatus) {
    val colors = FormTheme.colors
    val now by rememberCurrentTimeMillis()
    val (text, dotColor) =
        when (status) {
            SyncStatus.Syncing -> {
                stringResource(R.string.form_syncing) to null
            }

            is SyncStatus.Offline -> {
                offlineText(status.lastSyncedAt, now) to colors.warning
            }

            SyncStatus.BackOnline -> {
                stringResource(R.string.form_back_online) to colors.success
            }

            is SyncStatus.Synced -> {
                (
                    status.lastSyncedAt?.let { stringResource(R.string.form_synced_at, it.relativeTo(now)) }
                        ?: stringResource(R.string.form_never_synced)
                ) to
                    null
            }
        }
    AnimatedContent(targetState = text to dotColor, label = "status") { (label, dot) ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            dot?.let { PulsingDot(it) }
            Text(label, style = MaterialTheme.typography.labelMedium, color = dot ?: colors.heroMuted, maxLines = 1)
        }
    }
}

@Composable
private fun offlineText(
    lastSyncedAt: Instant?,
    now: Long,
): String =
    lastSyncedAt?.let { stringResource(R.string.form_offline_banner, it.relativeTo(now)) }
        ?: stringResource(R.string.form_offline_banner_undated)

@Composable
private fun PulsingDot(color: Color) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(PULSE_MILLIS)), label = "ring")
    Box(
        Modifier
            .size(7.dp)
            .drawBehind {
                drawCircle(color.copy(alpha = 0.5f * (1f - ring)), radius = size.minDimension / 2 * (1f + ring * 1.4f))
            }.clip(CircleShape)
            .background(color),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RefreshButton(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val colors = FormTheme.colors
    val description = stringResource(R.string.form_refresh)
    IconButton(onClick = onRefresh, modifier = Modifier.semantics { contentDescription = description }) {
        if (isRefreshing) {
            LoadingIndicator(color = colors.signal, modifier = Modifier.size(28.dp))
        } else {
            Icon(painterResource(R.drawable.ic_refresh), contentDescription = null, tint = colors.onHero)
        }
    }
}

/** The current time, refreshed every minute so "5 minutes ago" keeps counting while the screen is open. */
@Composable
private fun rememberCurrentTimeMillis(): State<Long> =
    produceState(System.currentTimeMillis()) {
        while (true) {
            delay(MINUTE_MILLIS)
            value = System.currentTimeMillis()
        }
    }

private fun Instant.relativeTo(nowMillis: Long): CharSequence =
    DateUtils.getRelativeTimeSpanString(toEpochMilliseconds(), nowMillis, DateUtils.MINUTE_IN_MILLIS)
