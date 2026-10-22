package com.lumiform.formviewer.ui.image

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ZoomMathTest {
    private val container = Size(1000f, 2000f)
    private val center = Offset(500f, 1000f)

    private fun ZoomTransform.pinch(
        zoom: Float,
        at: Offset = center,
    ) = applyGesture(at, Offset.Zero, zoom, container)

    private fun ZoomTransform.drag(pan: Offset) = applyGesture(center, pan, 1f, container)

    @Test
    fun doubleTap_zoomsAroundTheTap_andKeepsThatPointUnderTheFinger() {
        val tap = Offset(700f, 1000f)

        val zoomed = ZoomTransform().toggleZoom(tap, container)

        assertEquals(ZoomTransform.DOUBLE_TAP_SCALE, zoomed.scale)
        // The content point under the tap (200 px right of centre) is drawn at 200 * 2.5 + offset = 200.
        assertEquals(-300f, zoomed.offset.x, 0.01f)
        assertEquals(0f, zoomed.offset.y, 0.01f)
    }

    @Test
    fun doubleTap_onAZoomedImage_resetsIt() {
        val zoomed = ZoomTransform().toggleZoom(center, container)

        assertEquals(ZoomTransform(), zoomed.toggleZoom(center, container))
    }

    @Test
    fun doubleTap_nearAnEdge_keepsTheImageInsideTheContainer() {
        val zoomed = ZoomTransform().toggleZoom(Offset(0f, 0f), container)

        assertEquals(container.width * 1.5f / 2f, zoomed.offset.x, 0.01f)
        assertEquals(container.height * 1.5f / 2f, zoomed.offset.y, 0.01f)
    }

    @Test
    fun pinch_isCappedAtTheMaximumScale() {
        assertEquals(ZoomTransform.MAX_SCALE, ZoomTransform().pinch(3f).pinch(3f).scale)
    }

    @Test
    fun pan_onAZoomedImage_movesItWithinItsBounds_andNeverDismisses() {
        val zoomed = ZoomTransform(scale = 2f)

        val panned = zoomed.drag(Offset(10_000f, 10_000f))

        assertEquals(Offset(500f, 1000f), panned.offset)
        assertEquals(0f, panned.dismissDrag)
        assertEquals(GestureEnd.SettleTo(panned), panned.onGestureEnd(container))
    }

    @Test
    fun pinchingOut_belowTheThreshold_dismisses() {
        val pinched = ZoomTransform().pinch(0.7f)

        assertEquals(GestureEnd.Dismiss, pinched.onGestureEnd(container))
        assertEquals(1f, pinched.dismissProgress(container))
    }

    @Test
    fun pinchingOut_aLittle_springsBackToOneX() {
        val pinched = ZoomTransform().pinch(0.9f)

        assertEquals(GestureEnd.SettleTo(ZoomTransform()), pinched.onGestureEnd(container))
        assertEquals(0.5f, pinched.dismissProgress(container), 0.01f)
    }

    @Test
    fun pinchingOut_isFlooredSoTheImageNeverVanishes() {
        assertEquals(ZoomTransform.MIN_PINCH_SCALE, ZoomTransform().pinch(0.1f).scale)
    }

    @Test
    fun swipe_onAnUnzoomedImage_pastTheThreshold_dismisses() {
        val swiped = ZoomTransform().drag(Offset(0f, 400f))

        assertEquals(400f, swiped.dismissDrag)
        assertEquals(GestureEnd.Dismiss, swiped.onGestureEnd(container))
    }

    @Test
    fun swipe_upwardsAlsoDismisses() {
        assertEquals(GestureEnd.Dismiss, ZoomTransform().drag(Offset(0f, -400f)).onGestureEnd(container))
    }

    @Test
    fun shortSwipe_springsBack() {
        val swiped = ZoomTransform().drag(Offset(0f, 100f))

        assertEquals(GestureEnd.SettleTo(ZoomTransform()), swiped.onGestureEnd(container))
        assertTrue(swiped.dismissProgress(container) in 0.2f..0.3f)
    }

    @Test
    fun isZoomed_ignoresRoundingAroundOneX() {
        assertFalse(ZoomTransform(scale = 1.005f).isZoomed)
        assertTrue(ZoomTransform(scale = 1.1f).isZoomed)
    }
}
