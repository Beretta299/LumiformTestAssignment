package com.lumiform.formviewer.ui.form

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lumiform.formviewer.R
import com.lumiform.formviewer.core.domain.model.ChoiceQuestion
import com.lumiform.formviewer.core.domain.model.Response
import com.lumiform.formviewer.core.domain.model.Score
import com.lumiform.formviewer.ui.theme.FormTheme

private const val PRESSED_SCALE = 0.96f
private const val SHORT_LABEL_LENGTH = 10
private val TrackShape = RoundedCornerShape(22.dp)
private val OptionCorner = 16.dp
private val TileShape = RoundedCornerShape(18.dp)
private val ChipShape = RoundedCornerShape(10.dp)

/**
 * Single select is a track with one thumb that springs to the chosen answer, laid out side by side when the answers
 * are short (Yes / No / N/A). Multi select is a grid of tiles that each pop a check. Selection isn't held here: a tap
 * goes to the use case, and the new selection comes back from the database.
 */
@Composable
fun ChoiceQuestionItem(
    question: ChoiceQuestion,
    onResponseClick: (ChoiceQuestion, responseId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val responses = question.responseSet.responses
    val haptics = LocalHapticFeedback.current
    val onClick = { response: Response ->
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onResponseClick(question, response.id)
    }
    if (question.responseSet.multipleSelection) {
        CheckTiles(responses, question.selectedResponseIds, onClick, modifier)
    } else {
        val horizontal = responses.size in 2..4 && responses.all { it.label.length <= SHORT_LABEL_LENGTH }
        SlidingSelector(responses, question.selectedResponseIds, horizontal, onClick, modifier)
    }
}

@Composable
private fun SlidingSelector(
    responses: List<Response>,
    selectedIds: Set<Long>,
    horizontal: Boolean,
    onClick: (Response) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FormTheme.colors
    val bounds = remember { mutableStateMapOf<Long, Rect>() }
    val target = responses.firstOrNull { it.id in selectedIds }?.let { bounds[it.id] }
    val thumb = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    var thumbShown by remember { mutableStateOf(false) }
    val thumbSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    LaunchedEffect(target) {
        when {
            target == null -> {
                thumbShown = false
            }

            // The first selection appears in place; only a change of answer slides.
            !thumbShown -> {
                thumb.snapTo(target)
                thumbShown = true
            }

            else -> {
                thumb.animateTo(target, thumbSpec)
            }
        }
    }
    val thumbAlpha by animateFloatAsState(if (thumbShown) 1f else 0f, MaterialTheme.motionScheme.fastEffectsSpec())
    val options =
        Modifier
            .selectableGroup()
            .drawBehind {
                if (thumbAlpha > 0f) {
                    drawRoundRect(
                        color = colors.selection,
                        topLeft = thumb.value.topLeft,
                        size = thumb.value.size,
                        cornerRadius = CornerRadius(OptionCorner.toPx()),
                        alpha = thumbAlpha,
                    )
                }
            }
    Box(
        modifier
            .fillMaxWidth()
            .clip(TrackShape)
            .background(colors.track)
            .padding(5.dp),
    ) {
        val option = @Composable { response: Response, optionModifier: Modifier ->
            SelectorOption(
                response = response,
                selected = response.id in selectedIds,
                onClick = { onClick(response) },
                modifier = optionModifier.onPlaced { bounds[response.id] = it.boundsInParent() },
            )
        }
        if (horizontal) {
            Row(options) { responses.forEach { option(it, Modifier.weight(1f)) } }
        } else {
            Column(options, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                responses.forEach { option(it, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun SelectorOption(
    response: Response,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FormTheme.colors
    val content by animateColorAsState(
        if (selected) colors.onSelection else colors.text,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier =
            modifier
                .pressScale(interaction)
                .clip(RoundedCornerShape(OptionCorner))
                .selectable(selected, interaction, ripple(), role = Role.RadioButton, onClick = onClick)
                .heightIn(min = 48.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            response.label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            modifier = Modifier.weight(1f, fill = false),
        )
        PointsChip(response.score, selected)
    }
}

@Composable
private fun CheckTiles(
    responses: List<Response>,
    selectedIds: Set<Long>,
    onClick: (Response) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        responses.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { response ->
                    CheckTile(response, response.id in selectedIds, { onClick(response) }, Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CheckTile(
    response: Response,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FormTheme.colors
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        if (selected) colors.signalSoft else colors.surface,
        motion.defaultEffectsSpec(),
    )
    val border by animateColorAsState(if (selected) colors.selection else colors.outline, motion.defaultEffectsSpec())
    val checkScale by animateFloatAsState(if (selected) 1f else 0f, motion.fastSpatialSpec())
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .pressScale(interaction)
                .clip(TileShape)
                .background(container)
                .border(1.5.dp, border, TileShape)
                .selectable(selected, interaction, ripple(), role = Role.Checkbox, onClick = onClick)
                .heightIn(min = 72.dp)
                .padding(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.align(Alignment.BottomStart).padding(top = 20.dp),
        ) {
            Text(response.label, style = MaterialTheme.typography.labelLarge, color = colors.text)
            PointsChip(response.score, selected)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = checkScale
                        scaleY = checkScale
                    }.clip(CircleShape)
                    .background(colors.signal),
        ) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = colors.onSignal,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** "+2" next to an answer; it lights up in the signal colour once that answer counts. */
@Composable
private fun PointsChip(
    score: Score,
    selected: Boolean,
) {
    if (score !is Score.Points) return
    val colors = FormTheme.colors
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        if (selected) colors.signal else Color.Transparent,
        motion.defaultEffectsSpec(),
    )
    val content by animateColorAsState(if (selected) colors.onSignal else colors.muted, motion.defaultEffectsSpec())
    val description = stringResource(R.string.form_score, score.value)
    Text(
        text = stringResource(R.string.form_points, score.value),
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier =
            Modifier
                .clip(ChipShape)
                .background(container)
                .border(1.dp, if (selected) Color.Transparent else colors.outline, ChipShape)
                .padding(horizontal = 7.dp, vertical = 3.dp)
                .semantics { contentDescription = description },
    )
}

/** A quick squish on press, sprung back by the expressive motion scheme. */
@Composable
private fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, MaterialTheme.motionScheme.fastSpatialSpec())
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
