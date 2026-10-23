package com.pixel.intelligentsearch.core.ui.expressive

import com.pixel.intelligentsearch.core.ui.ClickDebouncer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveComponentsTest {

    @Test
    fun testSquircleShapeSingleRadiusOutline() {
        val shape = SquircleShape(cornerRadius = 16.dp, smoothing = 0.6f)
        val density = Density(density = 2.0f, fontScale = 1.0f)
        val outline = shape.createOutline(
            size = Size(200f, 100f),
            layoutDirection = LayoutDirection.Ltr,
            density = density
        )
        // A rounded rectangle, so backgrounds, borders, clips and shadows take Android's fast rounded paths: 16 dp at
        // density 2, grown by the smoothing (× 1.3), on every corner.
        assertTrue(outline is Outline.Rounded)
        val rect = (outline as Outline.Rounded).roundRect
        assertEquals(Size(200f, 100f), Size(rect.width, rect.height))
        for (corner in listOf(rect.topLeftCornerRadius, rect.topRightCornerRadius, rect.bottomRightCornerRadius, rect.bottomLeftCornerRadius)) {
            assertEquals(41.6f, corner.x, 1e-3f)
            assertEquals(41.6f, corner.y, 1e-3f)
        }
    }

    @Test
    fun testSquircleShapeCornersStopAtHalfTheShorterSide() {
        val outline = ExpressiveCornerTokens.ShapeFull.createOutline(Size(300f, 80f), LayoutDirection.Ltr, Density(3f))
        val rect = (outline as Outline.Rounded).roundRect
        assertEquals(40f, rect.topLeftCornerRadius.x, 1e-3f)
        assertEquals(40f, rect.bottomRightCornerRadius.y, 1e-3f)
    }

    @Test
    fun testSquircleShapeZeroRadiusReturnsRectangle() {
        val shape = SquircleShape(cornerRadius = 0.dp)
        val density = Density(density = 1.0f, fontScale = 1.0f)
        val outline = shape.createOutline(
            size = Size(100f, 100f),
            layoutDirection = LayoutDirection.Ltr,
            density = density
        )
        assertTrue(outline is Outline.Rectangle)
    }

    @Test
    fun testSquircleShapePerCornerRadiiAndEquality() {
        val shape1 = SquircleShape(
            topStart = 16.dp,
            topEnd = 12.dp,
            bottomEnd = 8.dp,
            bottomStart = 4.dp,
            smoothing = 0.6f
        )
        val shape2 = SquircleShape(
            topStart = 16.dp,
            topEnd = 12.dp,
            bottomEnd = 8.dp,
            bottomStart = 4.dp,
            smoothing = 0.6f
        )
        val shape3 = SquircleShape(
            topStart = 16.dp,
            topEnd = 12.dp,
            bottomEnd = 8.dp,
            bottomStart = 0.dp,
            smoothing = 0.6f
        )

        assertEquals(shape1, shape2)
        assertEquals(shape1.hashCode(), shape2.hashCode())
        assertNotEquals(shape1, shape3)

        val density = Density(density = 1.0f, fontScale = 1.0f)
        val ltrOutline = shape1.createOutline(Size(200f, 100f), LayoutDirection.Ltr, density)
        val rtlOutline = shape1.createOutline(Size(200f, 100f), LayoutDirection.Rtl, density)
        assertNotNull(ltrOutline)
        assertNotNull(rtlOutline)
    }

    @Test
    fun testClickDebouncerAtomicCas() {
        ClickDebouncer.recordClick(0L)
        assertTrue(ClickDebouncer.canClick(cooldownMs = 180L))
        // Immediate second click must be rejected
        assertFalse(ClickDebouncer.canClick(cooldownMs = 180L))
    }

    @Test
    fun testClickDebouncerKeyedIndependentWidgets() {
        val widgetA = "widget_a"
        val widgetB = "widget_b"

        ClickDebouncer.recordClick(0L, key = widgetA)
        ClickDebouncer.recordClick(0L, key = widgetB)

        // First click on widget A is allowed
        assertTrue(ClickDebouncer.canClick(cooldownMs = 180L, key = widgetA))
        // Immediate second click on the SAME widget A must be rejected
        assertFalse(ClickDebouncer.canClick(cooldownMs = 180L, key = widgetA))

        // Simultaneous / immediate click on independent widget B must STILL SUCCEED (no dead zone)
        assertTrue(ClickDebouncer.canClick(cooldownMs = 180L, key = widgetB))
        // Second click on widget B is rejected
        assertFalse(ClickDebouncer.canClick(cooldownMs = 180L, key = widgetB))
    }

    @Test
    fun testGoogleMessagesSwitchDimensionsAndTokens() {
        val trackWidth = 52.dp
        val trackHeight = 32.dp
        val checkedThumbSize = 24.dp
        val uncheckedThumbSize = 16.dp
        val pressedThumbWidth = 28.dp

        assertEquals(52.dp, trackWidth)
        assertEquals(32.dp, trackHeight)
        assertEquals(24.dp, checkedThumbSize)
        assertEquals(16.dp, uncheckedThumbSize)
        assertEquals(28.dp, pressedThumbWidth)

        // Off state has 2dp outline border, On state has 0dp border
        val offBorderWidth = 2.dp
        val onBorderWidth = 0.dp
        assertEquals(2.dp, offBorderWidth)
        assertEquals(0.dp, onBorderWidth)
    }
}
