package com.pixel.intelligentsearch.feature.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where the floating button snaps and stays, and where the edge swipe strip goes: pure pixel arithmetic. */
class FloatingGeometryTest {

    // A 1080 x 2400 phone with a 120px status bar and a 60px gesture handle.
    private val area = SafeArea(left = 0, top = 120, right = 1080, bottom = 2340)
    private val size = 154
    private val margin = 22

    @Test
    fun aReleasedButtonSnapsToTheNearerEdge() {
        assertEquals(ScreenEdge.LEFT, FloatingButtonGeometry.nearestEdge(x = 300, sizePx = size, area = area))
        assertEquals(ScreenEdge.RIGHT, FloatingButtonGeometry.nearestEdge(x = 500, sizePx = size, area = area))
        val (edge, bounds) = FloatingButtonGeometry.snapped(x = 700, y = 900, sizePx = size, area = area, marginPx = margin)
        assertEquals(ScreenEdge.RIGHT, edge)
        assertEquals(1080 - margin - size, bounds.x)
        assertEquals(900, bounds.y)
        val (left, leftBounds) = FloatingButtonGeometry.snapped(x = 10, y = 900, sizePx = size, area = area, marginPx = margin)
        assertEquals(ScreenEdge.LEFT, left)
        assertEquals(margin, leftBounds.x)
    }

    @Test
    fun itStaysOutOfTheStatusAndNavigationBars() {
        assertEquals(120, FloatingButtonGeometry.clampY(0, size, area))
        assertEquals(2340 - size, FloatingButtonGeometry.clampY(5000, size, area))
        assertEquals(1000, FloatingButtonGeometry.clampY(1000, size, area))
        val (_, snapped) = FloatingButtonGeometry.snapped(x = 900, y = 2390, sizePx = size, area = area, marginPx = margin)
        assertEquals(2340 - size, snapped.y)
    }

    @Test
    fun itStaysOnScreenWhileDragged() {
        assertEquals(0, FloatingButtonGeometry.clampX(-80, size, area))
        assertEquals(1080 - size, FloatingButtonGeometry.clampX(2000, size, area))
        // A side navigation bar (landscape, 3-button) is kept clear too.
        val landscape = SafeArea(left = 0, top = 100, right = 2232, bottom = 1080)
        assertEquals(2232 - size, FloatingButtonGeometry.clampX(2300, size, landscape))
        assertEquals(2232 - margin - size, FloatingButtonGeometry.restingX(ScreenEdge.RIGHT, size, landscape, margin))
    }

    @Test
    fun thePositionIsRememberedAsAShareOfItsTravelSoItSurvivesRotation() {
        val y = 1200
        val stored = FloatingButtonGeometry.positionOf(y, size, area)
        assertEquals(y.toDouble(), FloatingButtonGeometry.yFor(stored, size, area).toDouble(), 2.0)
        assertEquals(0, FloatingButtonGeometry.positionOf(area.top, size, area))
        assertEquals(FloatingButtonGeometry.POSITION_SCALE, FloatingButtonGeometry.positionOf(area.bottom - size, size, area))
        // The same share lands at the same relative height in landscape, inside its bars.
        val landscape = SafeArea(left = 0, top = 100, right = 2232, bottom = 1080)
        val half = FloatingButtonGeometry.yFor(500, size, landscape)
        assertEquals((100 + (980 - size) / 2).toDouble(), half.toDouble(), 1.0)
        // Out-of-range stored values (an old or crafted backup) are held to the area.
        assertEquals(area.top, FloatingButtonGeometry.yFor(-50, size, area))
        assertEquals(area.bottom - size, FloatingButtonGeometry.yFor(4000, size, area))
    }

    @Test
    fun aScreenTooSmallForTheButtonKeepsItAtTheTop() {
        val tiny = SafeArea(0, 50, 100, 120)
        assertEquals(50, FloatingButtonGeometry.clampY(400, size, tiny))
        assertEquals(0, FloatingButtonGeometry.positionOf(400, size, tiny))
        assertEquals(0, FloatingButtonGeometry.clampX(30, size, tiny))
    }

    @Test
    fun theStripRunsAlongTheChosenEdgeInsideTheBars() {
        val right = edgeStripBounds(ScreenEdge.RIGHT, area, positionPercent = 50, heightPercent = 25, widthPx = 42)
        assertEquals(1080 - 42, right.x)
        assertEquals(42, right.width)
        assertEquals(555, right.height) // 25% of 2220
        assertEquals(120 + 1110 - 555 / 2, right.y)
        val left = edgeStripBounds(ScreenEdge.LEFT, area, positionPercent = 50, heightPercent = 25, widthPx = 42)
        assertEquals(0, left.x)
        // Pushed to the very top or bottom, it still stays between the bars.
        val top = edgeStripBounds(ScreenEdge.RIGHT, area, positionPercent = 0, heightPercent = 40, widthPx = 42)
        assertEquals(120, top.y)
        val bottom = edgeStripBounds(ScreenEdge.RIGHT, area, positionPercent = 100, heightPercent = 40, widthPx = 42)
        assertEquals(2340, bottom.y + bottom.height)
    }

    @Test
    fun withGestureNavigationTheStripIsCappedToWhatAndroidKeepsFromBack() {
        val capped = edgeStripBounds(ScreenEdge.LEFT, area, positionPercent = 50, heightPercent = 60, widthPx = 42, maxHeightPx = 525)
        assertEquals(525, capped.height)
        assertEquals(120 + 1110 - 525 / 2, capped.y)
    }

    @Test
    fun gestureNavigationIsToldApartFromThreeButtons() {
        // Gesture navigation: side back zones, nothing tappable at the bottom.
        assertTrue(isGestureNavigation(gestureLeft = 63, gestureRight = 63, tappableLeft = 0, tappableRight = 0, tappableBottom = 0))
        // 3-button, portrait: tappable buttons at the bottom, no side gestures.
        assertFalse(isGestureNavigation(gestureLeft = 0, gestureRight = 0, tappableLeft = 0, tappableRight = 0, tappableBottom = 126))
        // 3-button, landscape: the bar on the side is a gesture inset there, but it's tappable.
        assertFalse(isGestureNavigation(gestureLeft = 0, gestureRight = 126, tappableLeft = 0, tappableRight = 126, tappableBottom = 0))
    }

    @Test
    fun anUnknownStoredEdgeReadsAsTheRightEdge() {
        assertEquals(ScreenEdge.LEFT, ScreenEdge.from("left"))
        assertEquals(ScreenEdge.RIGHT, ScreenEdge.from("right"))
        assertEquals(ScreenEdge.RIGHT, ScreenEdge.from("sideways"))
    }
}
