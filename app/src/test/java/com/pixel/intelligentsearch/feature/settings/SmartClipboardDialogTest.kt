package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Turning on Smart Clipboard Suggestions first says, in plain words, what the user gets and when it looks. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class SmartClipboardDialogTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val prefs get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
    }

    @After fun tearDown() { prefs.edit().clear().commit() }

    @Test
    fun theDialogSaysWhatTheUserGetsWithoutAndroidInternals() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { SearchBehaviorScreen(prefs) {} } }
        compose.waitForIdle()

        compose.onNode(isToggleable() and hasText("Smart Clipboard Suggestions")).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Turn On Smart Clipboard?").assertExists()
        compose.onNodeWithText(SMART_CLIPBOARD_DIALOG_TEXT).assertExists()
        // It used to explain itself with "a foreground lifecycle observer" and "Android 10+ restrictions".
        for (internal in listOf("lifecycle", "observer", "foreground", "Android", "restriction")) {
            assertFalse("\"$internal\" in: $SMART_CLIPBOARD_DIALOG_TEXT", SMART_CLIPBOARD_DIALOG_TEXT.contains(internal, ignoreCase = true))
        }
        assertFalse(prefs[SettingsManager.SMART_CLIPBOARD_SUGGESTIONS])

        compose.onNodeWithText("Turn On").performClick()
        compose.waitForIdle()
        assertTrue(prefs[SettingsManager.SMART_CLIPBOARD_SUGGESTIONS])
    }
}
