package com.pixel.intelligentsearch.feature.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The home screen's own search bar, which the launcher hosts once this app is the search engine, draws the design
 * saved in Widget Customization. Pixel Launcher binds it with no options (no searchbox host category), so it has to
 * pick a provider that is told apart from the picker's System and Material widgets by its class alone.
 */
@RunWith(RobolectricTestRunner::class)
class HomeSearchBarDesignTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = AppWidgetManager.getInstance(context)

    /** Binds [provider] as Pixel Launcher's search bar host does: no options. */
    private fun bindLikePixelLauncher(id: Int, provider: Class<*>) {
        val component = ComponentName(context, provider)
        shadowOf(manager).addInstalledProvider(AppWidgetProviderInfo().apply { this.provider = component })
        shadowOf(manager).bindAppWidgetId(id, component)
    }

    private fun drawsMaterial(id: Int, savedStyle: String) =
        SearchWidgetProvider.resolveIsMaterialYou(manager, id, forcedIsMaterial = null, widgetThemeStyle = savedStyle)

    @Test
    fun theHomeScreenSearchBarFollowsTheSavedDesign() {
        bindLikePixelLauncher(21, SearchBoxWidgetProvider::class.java)
        assertTrue(drawsMaterial(21, "Material You (Minimal)"))
        assertFalse(drawsMaterial(21, "System Default"))
    }

    @Test
    fun thePickersSystemWidgetStaysSystem() {
        bindLikePixelLauncher(22, SearchWidgetProvider::class.java)
        assertFalse(drawsMaterial(22, "Material You (Minimal)"))
    }

    /** Pixel Launcher hosts the first searchbox widget not hidden from the picker, else the first hidden one. */
    @Test
    fun theHomeScreenSearchBarIsTheOnlySearchboxWidget() {
        fun flags(xml: Int, attribute: String): Int {
            val parser = context.resources.getXml(xml)
            while (parser.name != "appwidget-provider") {
                check(parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) { "No appwidget-provider in $xml" }
            }
            return parser.getAttributeIntValue(ANDROID_NS, attribute, 0)
        }
        val searchbox = AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX
        assertEquals(searchbox, flags(R.xml.search_widget_searchbox_info, "widgetCategory") and searchbox)
        val hidden = AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER
        assertEquals(hidden, flags(R.xml.search_widget_searchbox_info, "widgetFeatures") and hidden)
        assertEquals(0, flags(R.xml.search_widget_system_info, "widgetCategory") and searchbox)
        assertEquals(0, flags(R.xml.search_widget_material_info, "widgetCategory") and searchbox)
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
