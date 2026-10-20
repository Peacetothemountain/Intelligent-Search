package com.pixel.intelligentsearch.feature.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.search.SelectedTextSearchActivity
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

/** The Launch Portal switch turns "Intelligent Search" in other apps' selection menu and share sheet off and on. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SearchFromSelectedTextSettingTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
    private val entryState get() = context.packageManager.getComponentEnabledSetting(ComponentName(context, SelectedTextSearchActivity::class.java))

    @Before
    fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
    }

    @Test
    fun switchDisablesAndReenablesTheEntry() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { LaunchPortalScreen(prefs) {} } }
        assertEquals(true, prefs[SettingsManager.SEARCH_FROM_SELECTED_TEXT])
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, entryState)

        compose.onNodeWithText("Search from Selected Text").performClick()
        compose.waitForIdle()
        assertEquals(false, prefs[SettingsManager.SEARCH_FROM_SELECTED_TEXT])
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, entryState)

        // Taps closer together than the row's debounce count once; let the debounce pass.
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        compose.onNodeWithText("Search from Selected Text").performClick()
        compose.waitForIdle()
        assertEquals(true, prefs[SettingsManager.SEARCH_FROM_SELECTED_TEXT])
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, entryState)
    }
}
