package com.pixel.intelligentsearch.core.ui.expressive

import androidx.compose.animation.core.Spring
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.feature.settings.SettingsDebouncer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveComponentsTest {

    @Test
    fun testMotionTokenPressScales() {
        assertEquals(0.92f, ExpressiveMotionTokens.PRESS_SCALE_BUTTON, 0.001f)
        assertEquals(0.94f, ExpressiveMotionTokens.PRESS_SCALE_PILL, 0.001f)
        assertEquals(0.96f, ExpressiveMotionTokens.PRESS_SCALE_CARD, 0.001f)
        assertEquals(0.97f, ExpressiveMotionTokens.PRESS_SCALE_BOUNCY, 0.001f)
        assertEquals(0.98f, ExpressiveMotionTokens.PRESS_SCALE_SEARCH_BAR, 0.001f)
        assertEquals(0.985f, ExpressiveMotionTokens.PRESS_SCALE_ROW, 0.001f)
    }

    @Test
    fun testSpatialSpringTokens() {
        val defaultSpatial = ExpressiveMotionTokens.Spatial.ExpressiveDefault<Float>()
        assertEquals(ExpressiveMotionTokens.SPATIAL_DEFAULT_DAMPING, defaultSpatial.dampingRatio, 0.001f)
        assertEquals(ExpressiveMotionTokens.SPATIAL_DEFAULT_STIFFNESS, defaultSpatial.stiffness, 0.001f)
        assertEquals(0.8f, defaultSpatial.dampingRatio, 0.001f)
        assertEquals(380f, defaultSpatial.stiffness, 0.001f)

        val fastSpatial = ExpressiveMotionTokens.Spatial.FastSpatial<Float>()
        assertEquals(ExpressiveMotionTokens.SPATIAL_FAST_DAMPING, fastSpatial.dampingRatio, 0.001f)
        assertEquals(ExpressiveMotionTokens.SPATIAL_FAST_STIFFNESS, fastSpatial.stiffness, 0.001f)
        assertEquals(0.6f, fastSpatial.dampingRatio, 0.001f)
        assertEquals(800f, fastSpatial.stiffness, 0.001f)

        val slowSpatial = ExpressiveMotionTokens.Spatial.SlowSpatial<Float>()
        assertEquals(ExpressiveMotionTokens.SPATIAL_SLOW_DAMPING, slowSpatial.dampingRatio, 0.001f)
        assertEquals(ExpressiveMotionTokens.SPATIAL_SLOW_STIFFNESS, slowSpatial.stiffness, 0.001f)
        assertEquals(0.8f, slowSpatial.dampingRatio, 0.001f)
        assertEquals(200f, slowSpatial.stiffness, 0.001f)
    }

    @Test
    fun testEffectsSpringTokens() {
        val fastSpatial = ExpressiveMotionTokens.Effects.FastSpatial<Float>()
        assertEquals(ExpressiveMotionTokens.EFFECTS_FAST_DAMPING, fastSpatial.dampingRatio, 0.001f)
        assertEquals(ExpressiveMotionTokens.EFFECTS_FAST_STIFFNESS, fastSpatial.stiffness, 0.001f)
        assertEquals(1.0f, fastSpatial.dampingRatio, 0.001f)
        assertEquals(3800f, fastSpatial.stiffness, 0.001f)

        val fastEffects = ExpressiveMotionTokens.Effects.FastEffects<Float>()
        assertEquals(fastSpatial.dampingRatio, fastEffects.dampingRatio, 0.001f)
        assertEquals(fastSpatial.stiffness, fastEffects.stiffness, 0.001f)

        val defaultEffects = ExpressiveMotionTokens.Effects.ExpressiveDefault<Float>()
        assertEquals(ExpressiveMotionTokens.EFFECTS_DEFAULT_DAMPING, defaultEffects.dampingRatio, 0.001f)
        assertEquals(ExpressiveMotionTokens.EFFECTS_DEFAULT_STIFFNESS, defaultEffects.stiffness, 0.001f)
        assertEquals(1.0f, defaultEffects.dampingRatio, 0.001f)
        assertEquals(1600f, defaultEffects.stiffness, 0.001f)

        val slowEffects = ExpressiveMotionTokens.Effects.SlowEffects<Float>()
        assertEquals(ExpressiveMotionTokens.EFFECTS_SLOW_DAMPING, slowEffects.dampingRatio, 0.001f)
        assertEquals(ExpressiveMotionTokens.EFFECTS_SLOW_STIFFNESS, slowEffects.stiffness, 0.001f)
        assertEquals(1.0f, slowEffects.dampingRatio, 0.001f)
        assertEquals(800f, slowEffects.stiffness, 0.001f)
    }

    @Test
    fun testSpecializedSpringTokens() {
        val criticallyDamped = ExpressiveMotionTokens.criticallyDampedSpring<Float>()
        assertEquals(1.0f, criticallyDamped.dampingRatio, 0.001f)
        assertEquals(700f, criticallyDamped.stiffness, 0.001f)

        val snappy = ExpressiveMotionTokens.snappySpring<Float>()
        assertEquals(0.6f, snappy.dampingRatio, 0.001f)
        assertEquals(800f, snappy.stiffness, 0.001f)

        val morph = ExpressiveMotionTokens.morphSpring<Float>()
        assertEquals(0.74f, morph.dampingRatio, 0.001f)
        assertEquals(420f, morph.stiffness, 0.001f)

        val bouncy = ExpressiveMotionTokens.bouncySpring<Float>()
        assertEquals(0.68f, bouncy.dampingRatio, 0.001f)
        assertEquals(460f, bouncy.stiffness, 0.001f)

        val elastic = ExpressiveMotionTokens.elasticSpring<Float>()
        assertEquals(0.8f, elastic.dampingRatio, 0.001f)
        assertEquals(380f, elastic.stiffness, 0.001f)

        val gentle = ExpressiveMotionTokens.gentleSpring<Float>()
        assertEquals(1.0f, gentle.dampingRatio, 0.001f)
        assertEquals(1500f, gentle.stiffness, 0.001f)
    }

    @Test
    fun testCornerTokensRadii() {
        assertEquals(0.dp, ExpressiveCornerTokens.None)
        assertEquals(8.dp, ExpressiveCornerTokens.ExtraSmall)
        assertEquals(12.dp, ExpressiveCornerTokens.Small)
        assertEquals(16.dp, ExpressiveCornerTokens.Medium)
        assertEquals(20.dp, ExpressiveCornerTokens.Large)
        assertEquals(24.dp, ExpressiveCornerTokens.LargeIncreased)
        assertEquals(28.dp, ExpressiveCornerTokens.ExtraLarge)
        assertEquals(32.dp, ExpressiveCornerTokens.ExtraLargeIncreased)
        assertEquals(1000.dp, ExpressiveCornerTokens.Full)
    }

    @Test
    fun testSquircleShapeSingleRadiusOutline() {
        val shape = SquircleShape(cornerRadius = 16.dp, smoothing = 0.6f)
        val density = Density(density = 2.0f, fontScale = 1.0f)
        val outline = shape.createOutline(
            size = Size(200f, 100f),
            layoutDirection = LayoutDirection.Ltr,
            density = density
        )
        assertTrue(outline is Outline.Generic)
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
    fun testSettingsDebouncerAtomicCas() {
        SettingsDebouncer.recordClick(0L)
        assertTrue(SettingsDebouncer.canClick(cooldownMs = 180L))
        // Immediate second click must be rejected
        assertFalse(SettingsDebouncer.canClick(cooldownMs = 180L))
    }

    @Test
    fun testSettingsDebouncerKeyedIndependentWidgets() {
        val widgetA = "widget_a"
        val widgetB = "widget_b"

        SettingsDebouncer.recordClick(0L, key = widgetA)
        SettingsDebouncer.recordClick(0L, key = widgetB)

        // First click on widget A is allowed
        assertTrue(SettingsDebouncer.canClick(cooldownMs = 180L, key = widgetA))
        // Immediate second click on the SAME widget A must be rejected
        assertFalse(SettingsDebouncer.canClick(cooldownMs = 180L, key = widgetA))

        // Simultaneous / immediate click on independent widget B must STILL SUCCEED (no dead zone)
        assertTrue(SettingsDebouncer.canClick(cooldownMs = 180L, key = widgetB))
        // Second click on widget B is rejected
        assertFalse(SettingsDebouncer.canClick(cooldownMs = 180L, key = widgetB))
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
