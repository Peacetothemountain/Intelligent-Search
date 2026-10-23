package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Search Behavior's Keyboard card: Start Assistant with Voice leads it, above Show Keyboard When Search Opens. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class KeyboardSettingsCardTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val prefs get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
    }

    @After fun tearDown() { prefs.edit().clear().commit() }

    /** Unclipped, so a row scrolled out of sight still reports where it is. */
    private fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y

    @Test
    fun startAssistantWithVoiceIsTheCardsFirstRowAndKeepsItsSetting() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { Column { KeyboardSettingsCard() } } }
        compose.waitForIdle()

        val voice = top("Start Assistant with Voice")
        assertTrue("voice at $voice", voice < top("Show Keyboard When Search Opens"))

        compose.onNode(isToggleable() and hasText("Start Assistant with Voice")).performClick()
        compose.waitForIdle()
        assertTrue(prefs[SettingsManager.ASSISTANT_STARTS_WITH_VOICE])
    }

    @Test
    fun searchBehaviorListsItOnceAtTheTopOfTheKeyboardCard() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { SearchBehaviorScreen(prefs) {} } }
        compose.waitForIdle()

        assertEquals(1, compose.onAllNodes(hasText("Start Assistant with Voice")).fetchSemanticsNodes().size)
        // No longer among the overlay's switches, which end with Smart Clipboard Suggestions.
        assertTrue(top("Smart Clipboard Suggestions") < top("Start Assistant with Voice"))
        assertTrue(top("Start Assistant with Voice") < top("Show Keyboard When Search Opens"))
    }
}
