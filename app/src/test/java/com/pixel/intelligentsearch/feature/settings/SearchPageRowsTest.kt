package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The App & Shortcut and Contact Search pages offer only rows that do something. Recently used shortcuts can't be
 * read by an app that isn't the home screen, and the contact picker row only toasted the picked contact's id.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class SearchPageRowsTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val prefs
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
    }

    @Test
    fun appSearchHasNoAppAnimationsOrRecentShortcutsSwitch() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { AppSearchScreen(prefs, {}, {}) } }
        compose.waitForIdle()
        // Apps always grow out of their icon now: the App Animations switch is gone.
        compose.onAllNodesWithText("App Animations").assertCountEquals(0)
        compose.onAllNodesWithText("Include Recent Shortcuts").assertCountEquals(0)
    }

    @Test
    fun contactSearchHasNoContactMenu() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { ContactSearchScreen(prefs) {} } }
        compose.waitForIdle()
        compose.onNodeWithText("Direct Call").assertExists()
        compose.onAllNodesWithText("Contact Menu").assertCountEquals(0)
        compose.onAllNodesWithText("Opens Contact Picker.").assertCountEquals(0)
    }

    @Test
    fun theRecentShortcutsKeyIsNoLongerASetting() {
        assertNull(SettingsManager.keyFor("shortcut.recent"))
    }
}
