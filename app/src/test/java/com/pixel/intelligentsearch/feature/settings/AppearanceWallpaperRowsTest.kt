package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Background Blur and Background Transparency act on the wallpaper behind the overlay, so they show only while Show
 * Wallpaper is on; Search Pill Opacity styles the pills and stays either way.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h640dp-xhdpi")
class AppearanceWallpaperRowsTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    private val prefs
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
    }

    private fun show() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { AppearanceScreen(prefs, {}, {}) } }
        compose.waitForIdle()
    }

    /** Scrolls to and taps the row titled [title], after the rows' tap debounce has passed. */
    private fun tap(title: String) {
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        compose.onNodeWithText(title).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun assertWallpaperRowsShown(shown: Boolean) {
        for (title in listOf("Background Blur", "Background Transparency")) {
            if (shown) compose.onNodeWithText(title).assertExists() else compose.onNodeWithText(title).assertDoesNotExist()
        }
        compose.onNodeWithText("Search Pill Opacity").assertExists()
    }

    @Test
    fun blurAndTransparencyShowOnlyWithTheWallpaper() {
        show()
        assertWallpaperRowsShown(true)

        tap("Show Wallpaper")
        assertEquals(false, prefs[SettingsManager.SHOW_WALLPAPER])
        assertWallpaperRowsShown(false)

        tap("Show Wallpaper")
        assertEquals(true, prefs[SettingsManager.SHOW_WALLPAPER])
        assertWallpaperRowsShown(true)
    }

    @Test
    fun withTheWallpaperOffTheyStartHidden() {
        prefs.edit().put(SettingsManager.SHOW_WALLPAPER, false).commit()
        show()
        assertWallpaperRowsShown(false)
    }
}
