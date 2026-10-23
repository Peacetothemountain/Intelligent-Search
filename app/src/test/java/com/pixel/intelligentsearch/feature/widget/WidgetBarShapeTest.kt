package com.pixel.intelligentsearch.feature.widget

import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.widget.MAX_CORNER_RADIUS
import com.pixel.intelligentsearch.core.data.widget.MAX_OUTLINE_THICKNESS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bar's outline and corners: the defaults are the shapes the widget always had, Corner Radius is exactly the
 * search bar's own corners (the pill's and the circle's) up to fully round at any outline, the outline follows them,
 * every value the sliders (or a backup) can hold gives corners no rounder than half of what they round, and each
 * radius has a background drawn with it.
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

    /**
     * The bug: the slider set the outer edge and the pill got what was left inside the outline, so on the Material
     * design (8dp rim) 16dp gave the pill and circle 8dp corners, and a thicker outline made a fully round bar boxy.
     */
    @Test
    fun theCornerRadiusIsExactlyTheSearchBarsCornersWhateverTheOutline() {
        for (material in listOf(true, false)) {
            val half = if (material) 24 else 28
            for (outline in 0..MAX_OUTLINE_THICKNESS step WidgetBarShape.STEP) {
                for (corner in 0..half step WidgetBarShape.STEP) {
                    val shape = WidgetBarShape.of(material, outline, corner)
                    val label = "material=$material outline=$outline corner=$corner: $shape"
                    assertEquals(label, corner, shape.pillRadius)
                    assertEquals(label, corner.coerceAtMost(WidgetBarShape.CIRCLE_SIZE / 2), shape.circleRadius)
                    assertEquals(label, outline, shape.outline)
                    // The outline follows the pill: its outer corners are the pill's plus the outline, square stays square.
                    assertEquals(label, if (corner == 0) 0 else corner + outline, shape.outerRadius)
                }
                // Fully round stays fully round as the outline grows: the pill, the circle and the whole bar.
                val round = WidgetBarShape.of(material, outline, half)
                assertEquals(round.pillHeight / 2, round.pillRadius)
                assertEquals(round.height / 2, round.outerRadius)
                assertTrue(round.circleIsRound)
            }
        }
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
                    assertTrue(label, shape.outerRadius <= WidgetBarShape.MAX_BACKGROUND_RADIUS)
                    // The pill stays a 48dp touch target or more.
                    assertTrue(label, shape.pillHeight >= 48)
                    assertEquals(label, 0, shape.outline % WidgetBarShape.STEP)
                    assertEquals(label, 0, shape.outerRadius % WidgetBarShape.STEP)
                    assertEquals(label, 0, shape.pillRadius % WidgetBarShape.STEP)
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
    fun squareCornersMakeASquareCircleButtonAndOutlineToo() {
        val square = WidgetBarShape.of(isMaterialDesign = true, outlineThickness = 8, cornerRadius = 0)
        assertEquals(0, square.pillRadius)
        assertEquals(0, square.circleRadius)
        assertEquals(0, square.outerRadius)
        assertTrue(!square.circleIsRound)
    }

    @Test
    fun theCornerSliderEndsWhereThePillIsFullyRound() {
        assertEquals(24, WidgetBarShape.maxCornerRadius(isMaterialDesign = true))
        assertEquals(28, WidgetBarShape.maxCornerRadius(isMaterialDesign = false))
        // A stored value past it (the Material default, 32) is fully round.
        assertEquals(24, WidgetBarShape.of(true, 0, SettingsManager.WIDGET_MATERIAL_CORNER_RADIUS.default).pillRadius)
    }

    @Test
    fun everyRadiusHasABackgroundDrawnWithIt() {
        assertEquals(R.drawable.bg_widget_r0, WidgetBarShape.background(0))
        assertEquals(R.drawable.bg_widget_r24, WidgetBarShape.background(24))
        assertEquals(R.drawable.bg_widget_r24, WidgetBarShape.background(25))
        assertEquals(R.drawable.bg_widget_r40, WidgetBarShape.background(WidgetBarShape.MAX_BACKGROUND_RADIUS))
        assertEquals(R.drawable.bg_widget_r40, WidgetBarShape.background(99))
        assertEquals(R.drawable.bg_widget_r0, WidgetBarShape.background(-3))
    }

    /** Each value maps to the background drawn with exactly that radius, for both designs' pills, circle and outline. */
    @Test
    fun eachValueMapsToTheBackgroundOfThatRadius() {
        val byRadius = (0..WidgetBarShape.MAX_BACKGROUND_RADIUS step WidgetBarShape.STEP).associateWith { radius ->
            R.drawable::class.java.getField("bg_widget_r$radius").getInt(null)
        }
        for (material in listOf(true, false)) {
            for (outline in 0..MAX_OUTLINE_THICKNESS step WidgetBarShape.STEP) {
                for (corner in 0..WidgetBarShape.maxCornerRadius(material) step WidgetBarShape.STEP) {
                    val shape = WidgetBarShape.of(material, outline, corner)
                    val label = "material=$material outline=$outline corner=$corner"
                    assertEquals(label, byRadius.getValue(shape.pillRadius), WidgetBarShape.background(shape.pillRadius))
                    assertEquals(label, byRadius.getValue(shape.outerRadius), WidgetBarShape.background(shape.outerRadius))
                    assertEquals(label, byRadius.getValue(shape.circleRadius), WidgetBarShape.background(shape.circleRadius))
                }
            }
        }
    }
}
