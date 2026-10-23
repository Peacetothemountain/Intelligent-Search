package com.pixel.intelligentsearch.feature.widget

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** A shortcut set to open a website opens that site, however its address was typed. */
@RunWith(RobolectricTestRunner::class)
class CustomShortcutUrlTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = com.pixel.intelligentsearch.core.data.SettingsManager.storedPrefs(context)

    private fun weatherOpens(address: String) {
        prefs.edit().putString("Weather_custom_type", "url").putString("Weather_custom_value", address).commit()
    }

    @Test
    fun aSchemeTypedInCapitalsIsStillTheSitesScheme() {
        weatherOpens("HTTPS://example.com")
        val site = getShortcutIntent(context, "Weather").data
        assertEquals("https", site?.scheme)
        assertEquals("example.com", site?.host)
    }

    @Test
    fun anAddressWithoutASchemeOpensOverHttps() {
        weatherOpens("example.com/forecast")
        assertEquals("https://example.com/forecast", getShortcutIntent(context, "Weather").dataString)
        weatherOpens("http://example.com")
        assertEquals("http://example.com", getShortcutIntent(context, "Weather").dataString)
    }

    @Test
    fun talkBackNamesTheSite() {
        weatherOpens("HTTPS://example.com")
        prefs.edit().putString("widget_system_shortcut_1", "Weather").commit()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val widget = SearchWidgetProvider.buildResponsiveViews(context, appWidgetId = 1, isMaterialYou = false, prefs = prefs)
            .getValue(SearchWidgetProvider.STANDARD_WIDTH)
            .apply(activity, FrameLayout(activity))

        val described = ArrayList<View>()
        widget.findViewsWithText(described, "Open example.com", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
        assertTrue("No slot is described as \"Open example.com\"", described.isNotEmpty())
    }
}
