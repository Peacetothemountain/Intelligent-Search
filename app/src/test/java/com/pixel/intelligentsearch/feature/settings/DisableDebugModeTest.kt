package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.occasion.Occasion
import com.pixel.intelligentsearch.feature.widget.WidgetDoodles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Turning Debug Mode off also ends a widget doodle preview, whose switch lives on the page that disappears. */
@RunWith(RobolectricTestRunner::class)
class DisableDebugModeTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Test
    fun endsTheDoodlePreviewAndLocksDebugMode() {
        prefs.edit()
            .putString(WidgetDoodles.PREVIEW_KEY, Occasion.entries.first().name)
            .putBoolean(SettingsManager.DEBUG_UNLOCKED.name, true)
            .commit()
        var redraws = 0

        disableDebugMode(context, prefs, redraw = { redraws++ })

        assertNull(WidgetDoodles.previewing(prefs))
        assertFalse(prefs.getBoolean(SettingsManager.DEBUG_UNLOCKED.name, true))
        assertEquals(1, redraws)
    }

    @Test
    fun leavesTheWidgetsAloneWithoutAPreview() {
        prefs.edit().putBoolean(SettingsManager.DEBUG_UNLOCKED.name, true).commit()
        var redraws = 0

        disableDebugMode(context, prefs, redraw = { redraws++ })

        assertFalse(prefs.getBoolean(SettingsManager.DEBUG_UNLOCKED.name, true))
        assertEquals(0, redraws)
    }
}
