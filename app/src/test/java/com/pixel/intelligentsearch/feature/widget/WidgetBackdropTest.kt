package com.pixel.intelligentsearch.feature.widget

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.feature.widget.WidgetColorSlidersTest.Bar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * The bar's background is drawn as one backdrop, the way a launcher draws the widget: the outline (the Material
 * design's rim), and the pill and circle over it, are one group faded once by Transparency, with the pill and circle
 * opaque within it, so where they sit over the rim they are painted once and never take on its colour (at Transparency
 * 50 the pill was 75% opaque, the rim showing through it, while the rim around it was 50%). A locked pill stays
 * solid and only the rim fades. The icons sit above the backdrop, fully opaque, and the backdrop's pill and circle lie
 * exactly under the buttons they back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class WidgetBackdropTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs by lazy { context.getSharedPreferences("widget_backdrop_test", Context.MODE_PRIVATE) }
    private val activity by lazy { Robolectric.buildActivity(Activity::class.java).setup().get() }

    private fun views(
        bar: Bar,
        transparency: Int,
        opacity: Int,
        outline: Int = 8,
        actionIcon: String = "Search",
        dark: Boolean = false
    ): RemoteViews {
        val p = if (bar.material) "widget_material" else "widget_system"
        prefs.edit().clear()
            .putString(SettingsManager.THEME.name, if (dark) "Material Dark" else "Material Light")
            .putBoolean("${p}_design_saved", true)
            .putString("${p}_subtheme", bar.subtheme)
            .putString("${p}_material_g_icon", "Accented G Icon")
            .putBoolean("${p}_custom_color_hct", true)
            .putInt("${p}_custom_hue", 200)
            .putInt("${p}_custom_saturation", 60)
            .putInt("${p}_custom_lightness", 70)
            .putInt("${p}_custom_color_opacity", opacity)
            .putInt("${p}_background_transparency", transparency)
            .putBoolean("${p}_lock_black", bar.lockBlack)
            .putString("${p}_action_icon", actionIcon)
            .putBoolean("${p}_show_doodles", false)
            .putBoolean("${p}_show_voice", true)
            .putString("${p}_shortcut_order", "mic,shortcut1,shortcut2,shortcut3")
            .putString("${p}_shortcut_1", "Weather")
            .putInt("${p}_outline_thickness", outline)
            .commit()
        return SearchWidgetProvider.buildResponsiveViews(context, appWidgetId = 1, isMaterialYou = bar.material, prefs = prefs)
            .getValue(SearchWidgetProvider.EXPANDED_WIDTH)
    }

    /** Lays [widget] out at a 4-cell phone widget's size, as a launcher does before drawing it. */
    private fun laidOut(widget: View): View {
        val density = context.resources.displayMetrics.density
        widget.measure(
            View.MeasureSpec.makeMeasureSpec((340 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((120 * density).toInt(), View.MeasureSpec.EXACTLY)
        )
        widget.layout(0, 0, widget.measuredWidth, widget.measuredHeight)
        return widget
    }

    /** Where [id] is drawn within [root]. */
    private fun View.boundsOf(id: Int): Rect {
        val view = findViewById<View>(id)
        var x = 0
        var y = 0
        var v: View = view
        while (v !== this) {
            x += v.left
            y += v.top
            v = v.parent as View
        }
        return Rect(x, y, x + view.width, y + view.height)
    }

    private fun View.isInside(ancestorId: Int): Boolean {
        var v: Any? = parent
        while (v is View) {
            if (v.id == ancestorId) return true
            v = v.parent
        }
        return false
    }

    private fun View.ownAlpha(id: Int) = findViewById<ImageView>(id).imageAlpha

    private fun assertAlpha(message: String, expected: Int, actual: Int) =
        assertTrue("$message: expected $expected, got $actual", abs(expected - actual) <= 1)

    @Test
    fun transparencyFadesTheWholeBackdropOnce() {
        for (bar in Bar.entries) for ((transparency, opacity) in listOf(0 to 100, 50 to 100, 28 to 60, 75 to 40, 100 to 100)) {
            val widget = views(bar, transparency, opacity).apply(activity, FrameLayout(activity))
            val label = "$bar transparency=$transparency opacity=$opacity"
            val faded = (100 - transparency) / 100f
            val customOpacity = opacity / 100f
            val locked = bar.material && bar.lockBlack

            // The custom colour's opacity fades the backdrop with Transparency when the whole backdrop is that colour.
            // With the Material pill locked black the backdrop stays solid and the rim alone takes both.
            val backdrop = when {
                locked -> 1f
                bar.isCustom -> faded * customOpacity
                else -> faded
            }
            val rim = if (locked) ((if (bar.isCustom) faded * customOpacity else faded) * 255).toInt() else 255
            assertEquals("$label backdrop", backdrop, widget.findViewById<View>(R.id.widget_backdrop).alpha, 1e-6f)
            assertEquals("$label pill is opaque within the backdrop", 255, widget.ownAlpha(R.id.widget_pill_background))
            assertAlpha("$label rim within the backdrop", rim, widget.ownAlpha(R.id.widget_outer_background))
            if (bar.material) {
                assertEquals("$label circle is opaque within the backdrop", 255, widget.ownAlpha(R.id.widget_sound_background))
            }

            // The icons are above the backdrop, untouched by it.
            val icons = listOf(R.id.widget_g_logo, R.id.widget_voice_search, R.id.widget_lens_search) +
                if (bar.material) listOf(R.id.widget_sound_icon) else emptyList()
            for (id in icons) {
                val icon = widget.findViewById<ImageView>(id)
                val name = context.resources.getResourceEntryName(id)
                assertEquals("$label $name is fully opaque", 255, icon.imageAlpha)
                assertTrue("$label $name is outside the backdrop", !icon.isInside(R.id.widget_backdrop))
                var v: Any? = icon
                while (v is View) {
                    assertEquals("$label $name is drawn at full opacity", 1f, v.alpha)
                    v = v.parent
                }
            }
        }
    }

    /**
     * Locked, the Material design's pill and circle are solid whatever the Transparency, and the rim alone fades: the
     * default Material bar (Transparency 28, locked) looks as it always has in the dark theme, solid #121212, and in
     * the light theme is the light theme's own pill, just as solid.
     */
    @Test
    fun theLockedPillStaysSolidWhileTheRimFades() {
        for (dark in listOf(true, false)) for (bar in listOf(Bar.MATERIAL_LOCKED, Bar.MATERIAL_CUSTOM_LOCKED)) {
            val label = "$bar ${if (dark) "dark" else "light"}"
            val widget = views(bar, transparency = 28, opacity = 100, dark = dark).apply(activity, FrameLayout(activity))
            val backdrop = widget.findViewById<View>(R.id.widget_backdrop).alpha
            assertEquals("$label backdrop", 1f, backdrop)
            for (id in listOf(R.id.widget_pill_background, R.id.widget_sound_background)) {
                val name = context.resources.getResourceEntryName(id)
                assertEquals("$label $name is solid", 255, widget.ownAlpha(id))
                assertEquals(
                    "$label $name is the theme's own pill",
                    WidgetColorSlidersTest.themePill(context, dark),
                    widget.findViewById<ImageView>(id).imageTintList!!.defaultColor and 0xFFFFFF
                )
            }
            assertAlpha("$label rim at Transparency 28", (0.72f * 255).toInt(), widget.ownAlpha(R.id.widget_outer_background))
        }
    }

    @Test
    fun theBackdropLiesExactlyUnderTheButtons() {
        for (bar in Bar.entries) for (outline in listOf(0, 4, 12)) for (action in listOf("Search", "None")) {
            if (!bar.material && action == "None") continue
            val widget = laidOut(views(bar, transparency = 30, opacity = 80, outline = outline, actionIcon = action).apply(activity, FrameLayout(activity)))
            val label = "$bar outline=$outline action=$action"
            assertEquals("$label pill", widget.boundsOf(R.id.widget_pill_container), widget.boundsOf(R.id.widget_pill_background))
            if (outline > 0) {
                assertEquals("$label outline fills the bar", widget.boundsOf(android.R.id.background), widget.boundsOf(R.id.widget_outer_background))
            }
            if (bar.material) {
                val circle = widget.findViewById<View>(R.id.widget_sound_background)
                val button = widget.findViewById<View>(R.id.widget_sound_search)
                assertEquals("$label circle shows with its button", button.visibility, circle.visibility)
                if (button.visibility == View.VISIBLE) {
                    assertEquals("$label circle", widget.boundsOf(R.id.widget_sound_search), widget.boundsOf(R.id.widget_sound_background))
                }
            }
        }
    }

    /** A launcher redraws a placed widget by applying the new views onto the old ones; nothing of the old backdrop stays. */
    @Test
    fun aRedrawOverTheShownWidgetTakesTheNewBackdrop() {
        val widget = views(Bar.MATERIAL_CUSTOM, transparency = 60, opacity = 50, outline = 4).apply(activity, FrameLayout(activity))
        views(Bar.MATERIAL, transparency = 0, opacity = 100, outline = 12, actionIcon = "None").reapply(activity, widget)
        laidOut(widget)

        assertEquals(1f, widget.findViewById<View>(R.id.widget_backdrop).alpha)
        assertEquals(View.GONE, widget.findViewById<View>(R.id.widget_sound_background).visibility)
        assertEquals(widget.boundsOf(R.id.widget_pill_container), widget.boundsOf(R.id.widget_pill_background))
    }
}
