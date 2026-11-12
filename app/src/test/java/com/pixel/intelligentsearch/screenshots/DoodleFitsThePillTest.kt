package com.pixel.intelligentsearch.screenshots

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
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
 *
 * Where the home screen reads right to left the G sits at the pill's right end instead, 4dp from it, so the same
 * holds mirrored: the half circle is centred at (28, 24) and nothing may be drawn right of it. Each doodle is drawn
 * here by its own layout, as the widget draws it, so one that is mirrored there is mirrored here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class DoodleFitsThePillTest {

    @Test
    fun everyDoodleStaysInsideThePillThroughItsWholeLoop() {
        val spills = spills(rightToLeft = false)
        assertTrue("Doodles spill past the pill's rounded left end:\n" + spills.joinToString("\n"), spills.isEmpty())
    }

    @Test
    @Config(qualifiers = "+ar-ldrtl")
    fun everyDoodleStaysInsideThePillWhereTheBarReadsRightToLeft() {
        val spills = spills(rightToLeft = true, except = RIGHT_TO_LEFT_EXCEPTIONS)
        assertTrue("Doodles spill past the pill's rounded right end:\n" + spills.joinToString("\n"), spills.isEmpty())
    }

    private fun spills(rightToLeft: Boolean, except: Set<Occasion> = emptySet()): List<String> {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val holder = FrameLayout(activity)
        holder.layoutDirection = if (rightToLeft) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        val spills = mutableListOf<String>()
        for (occasion in Occasion.entries - except) {
            val bar = LayoutInflater.from(activity).inflate(occasion.layout, holder, false) as ProgressBar
            holder.removeAllViews()
            holder.addView(bar, FrameLayout.LayoutParams(SIZE, SIZE))
            val exactly = View.MeasureSpec.makeMeasureSpec(SIZE, View.MeasureSpec.EXACTLY)
            holder.measure(exactly, exactly)
            holder.layout(0, 0, SIZE, SIZE)
            val timeline = DoodleArtGoldenTest.Timeline(bar.indeterminateDrawable as AnimatedVectorDrawable)
            // The still frame, then every 150 ms over 12 s, longer than any doodle's loop.
            val moments = listOf(-1L) + (0L..12_000L step 150L)
            for (time in moments) {
                if (time >= 0) timeline.seek(time)
                val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
                holder.draw(Canvas(bitmap))
                val spill = firstSpill(bitmap, rightToLeft)
                if (spill != null) {
                    spills += "${occasion.name} at ${if (time < 0) "the still frame" else "$time ms"}: drawn at $spill"
                    break
                }
            }
        }
        return spills
    }

    /** The first visible pixel outside the pill, in doodle units, or null when there is none. */
    private fun firstSpill(bitmap: Bitmap, rightToLeft: Boolean): String? {
        val unit = SIZE / 48f
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val ux = (x + 0.5f) / unit
            val uy = (y + 0.5f) / unit
            // How far in from the pill's rounded end this is, whichever end that is.
            val fromEnd = if (rightToLeft) 48f - ux else ux
            // Inside the straight part of the pill, or inside its rounded end (with half a unit for anti-aliasing).
            if (fromEnd >= CENTRE_X || hypot(fromEnd - CENTRE_X, uy - CENTRE_Y) <= RADIUS + 0.5f) continue
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

        /**
         * Music Day keeps its own way round right to left (its notes would read backwards mirrored, and its staff is
         * as wide as the G, so there is nowhere to move it): one note's bounce passes 0.7 of a unit outside the right
         * end's curve for a moment.
         */
        val RIGHT_TO_LEFT_EXCEPTIONS = setOf(Occasion.MUSIC_DAY)
    }
}
