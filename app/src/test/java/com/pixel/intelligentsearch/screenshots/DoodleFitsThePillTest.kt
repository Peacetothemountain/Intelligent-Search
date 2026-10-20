package com.pixel.intelligentsearch.screenshots

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.pixel.intelligentsearch.core.occasion.Occasion
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.hypot

/**
 * Every doodle stays inside the search bar at every moment of its loop. The doodle's 48-unit square sits over the G,
 * starting 4dp from the pill's left end, and on the Material design the pill is 48dp tall: its rounded left end is a
 * half circle of radius 24 centred at (20, 24) in the doodle's units. Anything drawn left of that curve would spill
 * past the bar's edge onto the home screen. (The System design's 56dp pill is rounder, so it fits whatever this does.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class DoodleFitsThePillTest {

    @Test
    fun everyDoodleStaysInsideThePillThroughItsWholeLoop() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val spills = mutableListOf<String>()
        for (occasion in Occasion.entries) {
            val drawable = DoodleArtGoldenTest.doodleDrawable(activity, occasion)
            val timeline = DoodleArtGoldenTest.Timeline(drawable)
            // The still frame, then every 150 ms over 12 s, longer than any doodle's loop.
            val moments = listOf(-1L) + (0L..12_000L step 150L)
            for (time in moments) {
                if (time >= 0) timeline.seek(time)
                val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, SIZE, SIZE)
                drawable.draw(Canvas(bitmap))
                val spill = firstSpill(bitmap)
                if (spill != null) {
                    spills += "${occasion.name} at ${if (time < 0) "the still frame" else "$time ms"}: drawn at $spill"
                    break
                }
            }
        }
        assertTrue("Doodles spill past the pill's rounded left end:\n" + spills.joinToString("\n"), spills.isEmpty())
    }

    /** The first visible pixel outside the pill, in doodle units, or null when there is none. */
    private fun firstSpill(bitmap: Bitmap): String? {
        val unit = SIZE / 48f
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val ux = (x + 0.5f) / unit
            val uy = (y + 0.5f) / unit
            // Inside the straight part of the pill, or inside its rounded end (with half a unit for anti-aliasing).
            if (ux >= CENTRE_X || hypot(ux - CENTRE_X, uy - CENTRE_Y) <= RADIUS + 0.5f) continue
            if (Color.alpha(bitmap.getPixel(x, y)) > VISIBLE_ALPHA) return "(%.1f, %.1f)".format(ux, uy)
        }
        return null
    }

    private companion object {
        const val SIZE = 192
        const val CENTRE_X = 20f
        const val CENTRE_Y = 24f
        const val RADIUS = 24f
        const val VISIBLE_ALPHA = 40
    }
}
