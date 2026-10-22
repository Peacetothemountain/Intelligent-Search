package com.pixel.intelligentsearch.feature.widget

import android.app.Activity
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the widget's views show and answer to, applied the way a launcher applies them. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class SearchWidgetViewsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs by lazy { context.getSharedPreferences("widget_views_test", Context.MODE_PRIVATE) }
    private val activity by lazy { Robolectric.buildActivity(Activity::class.java).setup().get() }

    private fun render(material: Boolean, size: SizeF = SearchWidgetProvider.STANDARD_WIDTH): View =
        SearchWidgetProvider.buildResponsiveViews(context, appWidgetId = 1, isMaterialYou = material, prefs = prefs)
            .getValue(size)
            .apply(activity, FrameLayout(activity))

    private fun gLogo(material: Boolean): ImageView = render(material).findViewById(R.id.widget_g_logo)

    /** Keep System G is a choice of the Custom G style only, as the editor's preview shows; other styles ignore it. */
    @Test
    fun keepSystemGLeavesTheOtherGStylesAlone() {
        prefs.edit()
            .putString("widget_material_material_g_icon", "Material G Icon")
            .putBoolean("widget_material_custom_keep_system_g", true)
            .putString("widget_system_material_g_icon", "Accented G Icon")
            .putBoolean("widget_system_custom_keep_system_g", true)
            .commit()

        assertTrue("The Material G is drawn in the theme's colours", gLogo(material = true).drawable is BitmapDrawable)
        assertNotNull("The Accented G is tinted with the accent", gLogo(material = false).imageTintList)
    }

    @Test
    fun keepSystemGKeepsTheCustomGInGooglesColours() {
        for (p in listOf("widget_material", "widget_system")) {
            prefs.edit()
                .putString("${p}_material_g_icon", "Custom G Icon")
                .putBoolean("${p}_custom_keep_system_g", true)
                .commit()
        }

        for (material in listOf(true, false)) {
            assertNull("material=$material", gLogo(material).imageTintList)
        }
    }

    /** A view that answers taps but is hidden at every width is dead weight in every update the launcher gets. */
    @Test
    fun everyTappableViewShowsAtSomeWidth() {
        val sizes = listOf(
            SearchWidgetProvider.ULTRA_COMPACT_WIDTH,
            SearchWidgetProvider.COMPACT_WIDTH,
            SearchWidgetProvider.STANDARD_WIDTH,
            SearchWidgetProvider.EXPANDED_WIDTH
        )
        for (material in listOf(false, true)) {
            val shownSomewhere = mutableMapOf<Int, Boolean>()
            for (size in sizes) {
                forEachView(render(material, size), parentShown = true) { view, shown ->
                    if (view.hasOnClickListeners()) shownSomewhere.merge(view.id, shown, Boolean::or)
                }
            }
            val neverShown = shownSomewhere.filterValues { !it }.keys.map { context.resources.getResourceEntryName(it) }
            assertTrue("Tappable but never shown (material=$material): $neverShown", neverShown.isEmpty())
        }
    }

    private fun forEachView(view: View, parentShown: Boolean, action: (View, Boolean) -> Unit) {
        val shown = parentShown && view.visibility == View.VISIBLE
        action(view, shown)
        if (view is ViewGroup) for (i in 0 until view.childCount) forEachView(view.getChildAt(i), shown, action)
    }
}
