package com.lumiform.formviewer.ui.image

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.math.max

/**
 * The image's transform: drawn scaled by [scale] around the container's centre, then moved by [offset]. Below 1× the
 * user is pinching the image away; [dismissDrag] is a vertical swipe on an unzoomed image. Pure, so the gesture rules
 * are unit-tested without a device.
 */
data class ZoomTransform(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
    val dismissDrag: Float = 0f,
) {
    val isZoomed: Boolean get() = scale > 1f + EPSILON

    companion object {
        const val MAX_SCALE = 5f
        const val DOUBLE_TAP_SCALE = 2.5f

        /** How far a pinch can shrink the image while the user decides whether to let go. */
        const val MIN_PINCH_SCALE = 0.5f

        /** Letting go of a pinch below this closes the viewer; above it the image springs back to 1×. */
        const val DISMISS_SCALE = 0.8f

        /** A swipe this fraction of the container's height closes the viewer. */
        const val DISMISS_DRAG_FRACTION = 0.18f

        private const val EPSILON = 0.01f
    }
}

/**
 * Applies one step of a pinch/pan gesture. [centroid] is relative to the container's top-left. A pinch zooms around
 * the fingers; a pan moves a zoomed image within its bounds; a one-finger vertical drag on an unzoomed image becomes
 * [ZoomTransform.dismissDrag].
 */
fun ZoomTransform.applyGesture(
    centroid: Offset,
    pan: Offset,
    zoom: Float,
    container: Size,
): ZoomTransform {
    val isPinch = abs(zoom - 1f) > 0.001f
    if (!isPinch && !isZoomed && scale >= 1f) {
        return copy(dismissDrag = dismissDrag + pan.y)
    }
    val newScale = (scale * zoom).coerceIn(ZoomTransform.MIN_PINCH_SCALE, ZoomTransform.MAX_SCALE)
    val zoomed = zoomAround(centroid - container.center, newScale)
    return zoomed.copy(offset = clampOffset(zoomed.offset + pan, newScale, container), dismissDrag = 0f)
}

/** Double tap: zooms to [ZoomTransform.DOUBLE_TAP_SCALE] around [tap], or back to 1× when already zoomed. */
fun ZoomTransform.toggleZoom(
    tap: Offset,
    container: Size,
): ZoomTransform {
    if (isZoomed) return ZoomTransform()
    val zoomed = zoomAround(tap - container.center, ZoomTransform.DOUBLE_TAP_SCALE)
    return zoomed.copy(offset = clampOffset(zoomed.offset, ZoomTransform.DOUBLE_TAP_SCALE, container))
}

sealed interface GestureEnd {
    data object Dismiss : GestureEnd

    data class SettleTo(
        val transform: ZoomTransform,
    ) : GestureEnd
}

/** What happens when the fingers lift: close the viewer, spring back to 1×, or stay where the user left it. */
fun ZoomTransform.onGestureEnd(container: Size): GestureEnd =
    when {
        scale < ZoomTransform.DISMISS_SCALE -> GestureEnd.Dismiss
        abs(dismissDrag) > container.height * ZoomTransform.DISMISS_DRAG_FRACTION -> GestureEnd.Dismiss
        scale < 1f || dismissDrag != 0f -> GestureEnd.SettleTo(ZoomTransform())
        else -> GestureEnd.SettleTo(this)
    }

/** 0 while the image is at rest, rising to 1 as a pinch-out or a swipe nears dismissal; fades the scrim and title. */
fun ZoomTransform.dismissProgress(container: Size): Float {
    val byScale = ((1f - scale) / (1f - ZoomTransform.DISMISS_SCALE)).coerceIn(0f, 1f)
    val threshold = container.height * ZoomTransform.DISMISS_DRAG_FRACTION
    val byDrag = if (threshold > 0f) (abs(dismissDrag) / threshold).coerceIn(0f, 1f) else 0f
    return max(byScale, byDrag)
}

/** Keeps the point under [focus] (relative to the centre) fixed while the scale changes. */
private fun ZoomTransform.zoomAround(
    focus: Offset,
    newScale: Float,
): ZoomTransform {
    val contentPoint = (focus - offset) / scale
    return copy(scale = newScale, offset = focus - contentPoint * newScale)
}

/** A zoomed image can be panned only until its edge meets the container's edge; at or below 1× it stays centred. */
internal fun clampOffset(
    offset: Offset,
    scale: Float,
    container: Size,
): Offset {
    if (scale <= 1f) return Offset.Zero
    val maxX = container.width * (scale - 1f) / 2f
    val maxY = container.height * (scale - 1f) / 2f
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

private val Size.center: Offset get() = Offset(width / 2f, height / 2f)
