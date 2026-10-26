package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
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
 * The App & Shortcut Search page's quick panel rows: the switch is the same setting as Search Behavior's Quick App
 * Panel, not a second one, and the slider stays within what the panel can hold.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class AppSearchQuickPanelRowsTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val prefs
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
    }

    private fun tap(title: String) {
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        compose.onNodeWithText(title).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun theRowsSayWhatTheyDo() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { AppSearchScreen(prefs, {}, {}) } }
        compose.onNodeWithText("Quick App Panel").assertExists()
        compose.onNodeWithText("Fixed Applications Sourced from Application Search Menu. Quickly Launch Your Favorite Apps Without Searching.").assertExists()
        compose.onNodeWithText("Max Quick Panel Apps").assertExists()
        compose.onAllNodesWithText("Manage Shortcuts for 20 Apps.").assertCountEquals(0)
        compose.onAllNodesWithText("Max Shortcut Suggestions").assertCountEquals(0)
    }

    @Test
    fun theSwitchIsSearchBehaviorsQuickAppPanel() {
        prefs.edit().put(SettingsManager.QUICK_SEARCH_HORIZONTAL, true).commit()
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { AppSearchScreen(prefs, {}, {}) } }
        compose.onNodeWithText("Quick App Panel").assertIsOn()

        tap("Quick App Panel")

        assertEquals(false, prefs[SettingsManager.QUICK_SEARCH_HORIZONTAL])
        compose.onNodeWithText("Quick App Panel").assertIsOff()
    }

    @Test
    fun theSwitchStartsOffLikeThePanel() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { AppSearchScreen(prefs, {}, {}) } }
        compose.onNodeWithText("Quick App Panel").assertIsOff()
        compose.onNodeWithText("6").assertExists()
    }

    @Test
    fun theSliderStaysWithinOneToTwentyAndResetsToSix() {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { AppSearchScreen(prefs, {}, {}) } }
        val slider = compose.onNodeWithContentDescription("Max Quick Panel Apps")

        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(40f) }
        compose.waitForIdle()
        assertEquals(20, prefs[SettingsManager.SHORTCUT_RESULTS_COUNT])

        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.waitForIdle()
        assertEquals(1, prefs[SettingsManager.SHORTCUT_RESULTS_COUNT])

        compose.onNodeWithContentDescription("Reset Max Quick Panel Apps").performClick()
        compose.waitForIdle()
        assertEquals(6, prefs[SettingsManager.SHORTCUT_RESULTS_COUNT])
    }
}
