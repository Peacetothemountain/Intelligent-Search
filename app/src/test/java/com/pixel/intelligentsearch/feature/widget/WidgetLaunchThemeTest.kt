package com.pixel.intelligentsearch.feature.widget

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The home screen's search bar opens the overlay through [WidgetActivity], whose theme opens without the wallpaper.
 * Android counts a window showing the wallpaper as opaque, and Pixel Launcher grows an opaque app out of a widget by
 * covering it with a copy of the widget's background (the lavender bar at the top), a black fill and a dark scrim
 * (decompiled from Pixel Launcher's QuickstepTransitionManager and FloatingWidgetView); a translucent one it just grows
 * out of the widget. The overlay's other entries still open over the wallpaper.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetLaunchThemeTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun themeFlags(activity: Class<*>): Pair<Boolean, Boolean> {
        val info = context.packageManager.getActivityInfo(ComponentName(context, activity), PackageManager.GET_META_DATA)
        val theme = context.resources.newTheme().apply { applyStyle(info.themeResource, true) }
        val attrs = theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowIsTranslucent, android.R.attr.windowShowWallpaper))
        return try {
            attrs.getBoolean(0, false) to attrs.getBoolean(1, false)
        } finally {
            attrs.recycle()
        }
    }

    @Test
    fun theSearchBarsEntryOpensTranslucentWithoutTheWallpaper() {
        val (translucent, wallpaper) = themeFlags(WidgetActivity::class.java)
        assertTrue("translucent, so the launcher grows it out of the bar as a see-through window", translucent)
        assertFalse("no wallpaper at launch, or Android would count it as opaque", wallpaper)
    }

    @Test
    fun theOverlaysOtherEntriesStillOpenOverTheWallpaper() {
        val (_, wallpaper) = themeFlags(MainActivity::class.java)
        assertTrue(wallpaper)
    }
}
