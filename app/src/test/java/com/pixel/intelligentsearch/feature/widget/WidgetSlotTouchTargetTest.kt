package com.pixel.intelligentsearch.feature.widget

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The mic and shortcut buttons are big enough to tap (48dp tall, and 40dp across, the gap between their icons), and
 * making them so moved no icon.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class WidgetSlotTouchTargetTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs by lazy { context.getSharedPreferences("widget_slots_test", Context.MODE_PRIVATE) }
    private val slots = listOf(R.id.widget_voice_search, R.id.widget_lens_search, R.id.widget_shortcut_2, R.id.widget_shortcut_3)

    /** The widget at its widest, laid out in a 340 x 64dp cell, with all four slots filled. */
    private fun layOut(material: Boolean): View {
        val p = if (material) "widget_material" else "widget_system"
        prefs.edit().putString("${p}_shortcut_2", "Weather").putString("${p}_shortcut_3", "News").commit()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val cell = FrameLayout(activity)
        val widget = SearchWidgetProvider.buildResponsiveViews(context, appWidgetId = 1, isMaterialYou = material, prefs = prefs)
            .getValue(SearchWidgetProvider.EXPANDED_WIDTH)
            .apply(activity, cell)
        cell.addView(widget, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val width = dp(340)
        val height = dp(64)
        cell.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        cell.layout(0, 0, width, height)
        return cell
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun leftIn(root: View, view: View): Int {
        var left = 0
        var v: View = view
        while (v !== root) {
            left += v.left
            v = v.parent as View
        }
        return left
    }

    /** Where the icon's centre is across the widget, in dp. */
    private fun iconCentre(root: View, slot: View): Float {
        val inner = slot.width - slot.paddingLeft - slot.paddingRight
        return (leftIn(root, slot) + slot.paddingLeft + inner / 2f) / context.resources.displayMetrics.density
    }

    private fun check(material: Boolean, centres: List<Float>) {
        val root = layOut(material)
        val shown = slots.map { root.findViewById<View>(it) }.filter { it.visibility == View.VISIBLE }
        assertEquals(4, shown.size)
        for (slot in shown) {
            val name = context.resources.getResourceEntryName(slot.id)
            assertTrue("$name is ${slot.height}px tall", slot.height >= dp(48))
            assertTrue("$name is ${slot.width}px wide", slot.width >= dp(40))
        }
        assertEquals(centres, shown.map { iconCentre(root, it) })
    }

    @Test
    fun materialSlotsAreTappable() = check(material = true, centres = listOf(128f, 168f, 208f, 248f))

    @Test
    fun systemSlotsAreTappable() = check(material = false, centres = listOf(192f, 232f, 272f, 312f))
}
