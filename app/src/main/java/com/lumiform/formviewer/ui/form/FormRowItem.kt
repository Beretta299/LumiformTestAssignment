package com.lumiform.formviewer.ui.form

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lumiform.formviewer.R
import com.lumiform.formviewer.core.domain.model.ChoiceQuestion
import com.lumiform.formviewer.core.domain.model.ImageQuestion
import com.lumiform.formviewer.core.domain.model.TextQuestion
import com.lumiform.formviewer.ui.theme.FormTheme
import com.lumiform.formviewer.ui.theme.HierarchyTypography

private val RailGutter = 30.dp
private val RailX = 7.dp
private val NodeY = 11.dp
private val NodeRadius = 7.dp
private val IndentStep = 16.dp
private val RowGap = 22.dp
private val GroupGap = 14.dp
private val ImageShape = RoundedCornerShape(22.dp)
private val CardShape = RoundedCornerShape(18.dp)

/**
 * Where a row sits on its group's timeline rail. A group's root section is its heading and stays off the rail, so
 * the rail starts at the row below it.
 */
data class RailPosition(
    val onRail: Boolean,
    val first: Boolean,
    val last: Boolean,
)

fun railPosition(
    previous: FormRow?,
    row: FormRow,
    next: FormRow?,
): RailPosition {
    fun FormRow.isHeading() = this is PageRow || (this is SectionRow && depth == 0)
    val first = previous == null || previous.group != row.group || previous.isHeading()
    val last = next == null || next.group != row.group
    return RailPosition(onRail = !row.isHeading(), first = first, last = last)
}

@Composable
fun FormRowItem(
    row: FormRow,
    rail: RailPosition,
    layout: FormLayout,
    onResponseClick: (ChoiceQuestion, responseId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onUpdateApp: (() -> Unit)? = null,
) {
    val padded = modifier.padding(horizontal = layout.horizontalPadding)
    when (row) {
        is PageRow -> {
            PageHeader(row, padded)
        }

        is SectionRow -> {
            if (rail.onRail) {
                RailRow(rail, RailNode.None, layout, row.depth, padded) { SectionTitle(row) }
            } else {
                SectionTitle(row, padded.padding(top = 8.dp, bottom = if (rail.last) GroupGap else 16.dp))
            }
        }

        is QuestionRow -> {
            val question = row.question
            val node =
                when (question) {
                    is ChoiceQuestion -> if (question.selectedResponseIds.isEmpty()) RailNode.Open else RailNode.Done
                    else -> RailNode.Dot
                }
            RailRow(rail, node, layout, row.depth, padded) { QuestionContent(row, layout, onResponseClick) }
        }

        is UnsupportedRow -> {
            RailRow(
                rail,
                RailNode.Dot,
                layout,
                row.depth,
                padded,
            ) { UnsupportedItem(onUpdateApp.takeIf { row.isTopLevel }) }
        }
    }
}

@Composable
private fun PageHeader(
    row: PageRow,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(top = 28.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        row.number?.let {
            Text(
                stringResource(R.string.form_page_number, it, row.pageCount),
                style = HierarchyTypography.number,
                color = FormTheme.colors.signal,
            )
        }
        Text(
            row.title ?: stringResource(R.string.form_untitled_page),
            style = HierarchyTypography.page,
            color = FormTheme.colors.text,
        )
    }
}

@Composable
private fun SectionTitle(
    row: SectionRow,
    modifier: Modifier = Modifier,
) {
    val style = HierarchyTypography.section(row.depth)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(row.number, style = style, color = FormTheme.colors.signal)
        Text(row.title ?: stringResource(R.string.form_untitled_section), style = style, color = FormTheme.colors.text)
    }
}

@Composable
private fun QuestionContent(
    row: QuestionRow,
    layout: FormLayout,
    onResponseClick: (ChoiceQuestion, responseId: Long) -> Unit,
) {
    val colors = FormTheme.colors
    val question = row.question
    val label =
        when (question) {
            is TextQuestion -> question.content
            is ImageQuestion -> question.title
            is ChoiceQuestion -> question.content
        }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                row.number,
                style = HierarchyTypography.number,
                color = colors.muted,
                modifier = Modifier.padding(top = 3.dp),
            )
            label?.let { Text(it, style = HierarchyTypography.question, color = colors.text) }
        }
        when (question) {
            is TextQuestion -> Unit
            is ImageQuestion -> Thumbnail(question, layout)
            is ChoiceQuestion -> ChoiceQuestionItem(question, onResponseClick)
        }
    }
}

/** Full width, so it sits centred; Coil sizes the request to this box, so the image is decoded at reduced size. */
@Composable
private fun Thumbnail(
    question: ImageQuestion,
    layout: FormLayout,
) {
    val trackColor = FormTheme.colors.track
    val placeholder = remember(trackColor) { ColorPainter(trackColor) }
    AsyncImage(
        model = question.src,
        contentDescription = null,
        placeholder = placeholder,
        error = placeholder,
        fallback = placeholder,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().height(layout.thumbnailHeight).clip(ImageShape),
    )
}

/** A top-level item stands in for a whole page, so it also offers [onUpdateApp] (null hides the action). */
@Composable
private fun UnsupportedItem(onUpdateApp: (() -> Unit)?) {
    val colors = FormTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(1.5.dp, colors.outline, CardShape)
            .background(colors.surface)
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = colors.signal)
            Text(
                stringResource(R.string.form_unsupported_item),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text,
            )
        }
        if (onUpdateApp != null) {
            TextButton(onClick = onUpdateApp, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.form_unsupported_update))
            }
        }
    }
}

private enum class RailNode { None, Dot, Open, Done }

/**
 * Draws this row's slice of the rail: the line through the row (stopping at the node on the first and last rows) and
 * the node, which fills in with a spring once a choice question is answered. Nested rows indent beside the rail.
 */
@Composable
private fun RailRow(
    rail: RailPosition,
    node: RailNode,
    layout: FormLayout,
    depth: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = FormTheme.colors
    val motion = MaterialTheme.motionScheme
    val done = node == RailNode.Done
    val fill by animateColorAsState(if (done) colors.success else colors.paper, motion.defaultEffectsSpec())
    val ring by animateColorAsState(if (done) colors.success else colors.outline, motion.defaultEffectsSpec())
    val pop by animateFloatAsState(if (done) 1.15f else 1f, motion.fastSpatialSpec())
    val indent = IndentStep * (depth - 1).coerceIn(0, layout.maxIndentLevels)
    Row(
        modifier
            .drawBehind {
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - RailX.toPx() else RailX.toPx()
                val nodeY = NodeY.toPx()
                val top = if (rail.first) nodeY else 0f
                val bottom = if (rail.last) nodeY else size.height
                if (bottom > top) drawLine(colors.outline, Offset(x, top), Offset(x, bottom), strokeWidth = 2.dp.toPx())
                val center = Offset(x, nodeY)
                when (node) {
                    RailNode.None -> {}

                    RailNode.Dot -> {
                        drawCircle(colors.muted, radius = 3.dp.toPx(), center = center)
                    }

                    RailNode.Open, RailNode.Done -> {
                        val radius = NodeRadius.toPx() * pop
                        drawCircle(fill, radius = radius, center = center)
                        drawCircle(ring, radius = radius - 1.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
                    }
                }
            }.padding(start = RailGutter + indent, bottom = if (rail.last) RowGap + GroupGap else RowGap),
    ) {
        content()
    }
}

internal fun Modifier.formContentWidth(): Modifier = widthIn(max = FormLayout.MaxContentWidth).fillMaxWidth()
