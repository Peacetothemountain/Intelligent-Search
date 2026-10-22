package com.pixel.intelligentsearch.feature.widget

import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.widget.MAX_CORNER_RADIUS
import com.pixel.intelligentsearch.core.data.widget.MAX_OUTLINE_THICKNESS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bar's outline and corners: the defaults are the shapes the widget always had, every value the sliders (or a
 * backup) can hold gives corners no rounder than half of what they round, so nothing is ever cut off, and each radius
 * has a background to draw it with.
 */
class WidgetBarShapeTest {

    @Test
    fun theDefaultsAreTheShapesTheWidgetAlwaysHad() {
        val material = WidgetBarShape.of(
            isMaterialDesign = true,
            outlineThickness = SettingsManager.WIDGET_MATERIAL_OUTLINE_THICKNESS.default,
            cornerRadius = SettingsManager.WIDGET_MATERIAL_CORNER_RADIUS.default
        )
        assertEquals(WidgetBarShape(outline = 8, pillHeight = 48, outerRadius = 32, pillRadius = 24, circleRadius = 24), material)
        assertEquals(64, material.height)
        assertTrue(material.circleIsRound)

        val system = WidgetBarShape.of(
            isMaterialDesign = false,
            outlineThickness = SettingsManager.WIDGET_SYSTEM_OUTLINE_THICKNESS.default,
            cornerRadius = SettingsManager.WIDGET_SYSTEM_CORNER_RADIUS.default
        )
        assertEquals(WidgetBarShape(outline = 0, pillHeight = 56, outerRadius = 28, pillRadius = 28, circleRadius = 24), system)
        assertEquals(56, system.height)
    }

    @Test
    fun noCornerIsEverRounderThanItsShapeAtAnySetting() {
        for (material in listOf(true, false)) {
            for (outline in -4..MAX_OUTLINE_THICKNESS + 10) {
                for (corner in -4..MAX_CORNER_RADIUS + 20) {
                    val shape = WidgetBarShape.of(material, outline, corner)
                    val label = "material=$material outline=$outline corner=$corner: $shape"
                    assertTrue(label, shape.outline in 0..MAX_OUTLINE_THICKNESS)
                    assertTrue(label, shape.outerRadius in 0..shape.height / 2)
                    assertTrue(label, shape.pillRadius in 0..shape.pillHeight / 2)
                    assertTrue(label, shape.circleRadius in 0..WidgetBarShape.CIRCLE_SIZE / 2)
                    // The outline's inner edge follows its outer edge, and the pill stays a 48dp touch target or more.
                    assertEquals(label, (shape.outerRadius - shape.outline).coerceAtLeast(0), shape.pillRadius)
                    assertTrue(label, shape.pillHeight >= 48)
                    assertEquals(label, 0, shape.outline % WidgetBarShape.STEP)
                    assertEquals(label, 0, shape.outerRadius % WidgetBarShape.STEP)
                }
            }
        }
    }

    @Test
    fun outlinesAndCornersComeInSteps() {
        // An odd value (only an older backup could hold one) is drawn at the step below.
        assertEquals(WidgetBarShape.of(true, 6, 20), WidgetBarShape.of(true, 7, 21))
        assertEquals(2, WidgetBarShape.of(false, 3, 28).outline)
    }

    @Test
    fun squareCornersMakeASquareCircleButtonToo() {
        val square = WidgetBarShape.of(isMaterialDesign = true, outlineThickness = 8, cornerRadius = 0)
        assertEquals(0, square.pillRadius)
        assertEquals(0, square.circleRadius)
        assertTrue(!square.circleIsRound)
    }

    @Test
    fun theCornerSliderEndsWhereTheBarIsFullyRound() {
        assertEquals(32, WidgetBarShape.maxCornerRadius(isMaterialDesign = true, outlineThickness = 8))
        assertEquals(24, WidgetBarShape.maxCornerRadius(isMaterialDesign = true, outlineThickness = 0))
        assertEquals(40, WidgetBarShape.maxCornerRadius(isMaterialDesign = false, outlineThickness = 12))
    }

    @Test
    fun everyRadiusHasABackgroundDrawnWithIt() {
        assertEquals(R.drawable.bg_widget_r0, WidgetBarShape.background(0))
        assertEquals(R.drawable.bg_widget_r24, WidgetBarShape.background(24))
        assertEquals(R.drawable.bg_widget_r24, WidgetBarShape.background(25))
        assertEquals(R.drawable.bg_widget_r40, WidgetBarShape.background(MAX_CORNER_RADIUS))
        assertEquals(R.drawable.bg_widget_r40, WidgetBarShape.background(99))
        assertEquals(R.drawable.bg_widget_r0, WidgetBarShape.background(-3))
    }
}
