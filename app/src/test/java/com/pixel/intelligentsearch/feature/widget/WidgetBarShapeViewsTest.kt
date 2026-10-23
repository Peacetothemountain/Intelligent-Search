package com.pixel.intelligentsearch.feature.widget

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Outline Thickness and Corner Radius as the placed widget draws them, for both designs: the pill (and the Material
 * design's circle) take exactly the slider's radius, the outline is as thick as set and follows the pill around, and
 * a redraw over the views a launcher already shows never keeps the previous design's corners.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class WidgetBarShapeViewsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs by lazy { context.getSharedPreferences("widget_bar_shape_views_test", Context.MODE_PRIVATE) }
    private val activity by lazy { Robolectric.buildActivity(Activity::class.java).setup().get() }

    private fun prefix(material: Boolean) = if (material) "widget_material" else "widget_system"

    private fun views(material: Boolean, outline: Int, corner: Int): RemoteViews {
        prefs.edit()
            .clear()
            .putInt("${prefix(material)}_outline_thickness", outline)
            .putInt("${prefix(material)}_corner_radius", corner)
            .commit()
        return SearchWidgetProvider.buildResponsiveViews(context, appWidgetId = 1, isMaterialYou = material, prefs = prefs)
            .getValue(SearchWidgetProvider.EXPANDED_WIDTH)
    }

    private fun View.drawn(id: Int): Int = shadowOf(findViewById<ImageView>(id).drawable).createdFromResId

    private fun radiusOf(drawable: Int): Int = context.resources.getResourceEntryName(drawable).removePrefix("bg_widget_r").toInt()

    private fun View.dp(px: Int): Int = Math.round(px / context.resources.displayMetrics.density)

    @Test
    fun theSlidersValuesAreWhatTheWidgetDraws() {
        for (material in listOf(true, false)) {
            val pillHeight = if (material) 48 else 56
            for ((outline, corner) in listOf(0 to 0, 0 to 12, 4 to 12, 8 to 16, 12 to 20, 2 to pillHeight / 2, 12 to pillHeight / 2)) {
                val widget = views(material, outline, corner).apply(activity, FrameLayout(activity))
                val label = "material=$material outline=$outline corner=$corner"

                assertEquals("$label pill corners", corner, radiusOf(widget.drawn(R.id.widget_pill_background)))
                assertEquals("$label bar height", pillHeight + 2 * outline, widget.dp(widget.findViewById<View>(android.R.id.background).layoutParams.height))
                val outer = widget.findViewById<ImageView>(R.id.widget_outer_background)
                assertEquals("$label outline shown", outline > 0, outer.visibility == View.VISIBLE)
                if (outline > 0) {
                    assertEquals("$label outline corners", if (corner == 0) 0 else corner + outline, radiusOf(widget.drawn(R.id.widget_outer_background)))
                }
                if (material) {
                    // The rim is the bar row's padding; the circle has the pill's corners (24dp is round on 48dp).
                    val row = widget.findViewById<View>(R.id.widget_bar_row)
                    assertEquals("$label rim", outline, widget.dp(row.paddingTop))
                    assertEquals("$label rim", outline, widget.dp(row.paddingLeft))
                    assertEquals("$label circle corners", corner.coerceAtMost(24), radiusOf(widget.drawn(R.id.widget_sound_background)))
                } else {
                    val pill = widget.findViewById<View>(R.id.widget_pill_container).layoutParams as ViewGroup.MarginLayoutParams
                    assertEquals("$label outline", outline, widget.dp(pill.topMargin))
                    assertEquals("$label outline", outline, widget.dp(pill.leftMargin))
                }
            }
        }
    }

    /** A launcher redraws a placed widget by applying the new views onto the old ones; nothing of the old shape stays. */
    @Test
    fun aRedrawOverTheShownWidgetTakesTheNewShape() {
        for (material in listOf(true, false)) {
            val parent = FrameLayout(activity)
            val widget = views(material, outline = 4, corner = 10).apply(activity, parent)
            views(material, outline = 0, corner = if (material) 24 else 28).reapply(activity, widget)
            val label = "material=$material"

            assertEquals(label, if (material) 24 else 28, radiusOf(widget.drawn(R.id.widget_pill_background)))
            assertEquals(label, View.GONE, widget.findViewById<View>(R.id.widget_outer_background).visibility)
            if (material) assertEquals("$label circle back to round", 24, radiusOf(widget.drawn(R.id.widget_sound_background)))
        }
    }
}
