package com.pixel.intelligentsearch.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Widget Action Icon picker's selection animations: each icon starts and ends at rest, and every part stays
 * inside the room the picker leaves it, so the icon never crosses its tile's edge.
 */
class ActionIconMotionTest {

    private val icons = listOf("None", "Search", "Assistant", "Now Playing")

    /**
     * Each part's outermost points in the icon's 24-unit space, from its path data (strokes and caps included): the
     * stars' and sparkle's four tips (the rest of each lies inside them), and the other parts' bounding corners.
     */
    private fun partExtremes(icon: String, pose: ActionIconPose): List<Pair<IconPartPose, List<Pair<Float, Float>>>> = when (icon) {
        "Search" -> listOf(
            pose.body to corners(2.75f, 2.75f, 21.5f, 21.5f),
            pose.accentA to tips(18f, 6.5f, 6f)
        )
        "Assistant" -> listOf(
            pose.body to tips(9f, 12f, 8f),
            pose.accentA to tips(19f, 5f, 4f),
            pose.accentB to tips(19f, 19f, 4f)
        )
        "Now Playing" -> listOf(
            pose.body to corners(1.6f, 5.4f, 11.6f, 21.5f),
            pose.accentA to corners(12.9f, 7.9f, 16.1f, 17.1f),
            pose.accentB to corners(17.4f, 9.9f, 20.6f, 14.1f)
        )
        else -> listOf(pose.body to corners(5f, 5f, 19f, 19f))
    }

    private fun corners(l: Float, t: Float, r: Float, b: Float) = listOf(l to t, r to t, l to b, r to b)

    private fun tips(cx: Float, cy: Float, reach: Float) =
        listOf(cx to cy - reach, cx + reach to cy, cx to cy + reach, cx - reach to cy)

    /** Where (x, y) lands under [part]: scaled and turned about its pivot, then moved, as the icon draws it. */
    private fun transform(part: IconPartPose, x: Float, y: Float): Pair<Float, Float> {
        val sx = part.pivotX + (x - part.pivotX) * part.scaleX
        val sy = part.pivotY + (y - part.pivotY) * part.scaleY
        val r = part.rotation * PI / 180.0
        val rx = part.pivotX + (sx - part.pivotX) * cos(r) - (sy - part.pivotY) * sin(r)
        val ry = part.pivotY + (sx - part.pivotX) * sin(r) + (sy - part.pivotY) * cos(r)
        return (rx + part.dx).toFloat() to (ry + part.dy).toFloat()
    }

    @Test
    fun everyIconStartsAndEndsAtRest() {
        val pose = ActionIconPose()
        for (icon in icons) for (t in listOf(-0.5f, 0f, 1f, 1.5f)) {
            ActionIconMotion.pose(icon, t, pose)
            assertTrue("$icon at $t", pose.isIdentity)
        }
    }

    @Test
    fun theLastFrameIsAlreadyBackAtRestSoTheEndDoesNotJump() {
        val pose = ActionIconPose()
        for (icon in icons) {
            ActionIconMotion.pose(icon, 0.999f, pose)
            for ((part, _) in partExtremes(icon, pose)) {
                // The X and the stars end a quarter turn round, which looks the same as no turn.
                val turn = ((part.rotation % 90f) + 90f) % 90f
                assertTrue("$icon turn ${part.rotation}", turn < 1f || turn > 89f)
                assertEquals("$icon scaleX", 1f, part.scaleX, 0.02f)
                assertEquals("$icon scaleY", 1f, part.scaleY, 0.02f)
                assertEquals("$icon dx", 0f, part.dx, 0.05f)
                assertEquals("$icon dy", 0f, part.dy, 0.05f)
            }
        }
    }

    @Test
    fun everyIconActuallyMoves() {
        val pose = ActionIconPose()
        for (icon in icons) {
            val moved = (1 until 100).any { ActionIconMotion.pose(icon, it / 100f, pose); !pose.isIdentity }
            assertTrue(icon, moved)
        }
    }

    @Test
    fun everyPartStaysInsideTheRoomThePickerLeavesIt() {
        // The picker clips the 22dp icon 4dp outside its box, which is 24 * 4 / 22 units; allow a little under that.
        val room = 3.5f
        val pose = ActionIconPose()
        for (icon in icons) for (step in 0..400) {
            val t = step / 400f
            ActionIconMotion.pose(icon, t, pose)
            for ((part, points) in partExtremes(icon, pose)) {
                if (part.scaleX == part.scaleY) {
                    assertTrue("$icon scale ${part.scaleX} at $t", part.scaleX <= ActionIconMotion.MAX_SCALE + 1e-4f)
                }
                assertTrue("$icon scale ${part.scaleX}/${part.scaleY} at $t", part.scaleX > 0.4f && part.scaleY > 0.4f)
                for ((x, y) in points) {
                    val (px, py) = transform(part, x, y)
                    assertTrue("$icon x $px at $t", px >= -room && px <= 24f + room)
                    assertTrue("$icon y $py at $t", py >= -room && py <= 24f + room)
                }
            }
        }
    }

    @Test
    fun theSpringStepOvershootsALittleAndSettlesExactly() {
        assertEquals(0f, ActionIconMotion.springStep(0f), 1e-6f)
        assertEquals(1f, ActionIconMotion.springStep(1f), 1e-6f)
        val peak = (0..1000).maxOf { ActionIconMotion.springStep(it / 1000f) }
        assertTrue("peak $peak", peak > 1.05f && peak < 1.15f)
        assertTrue(abs(ActionIconMotion.springStep(0.999f) - 1f) < 1e-3f)
    }
}
