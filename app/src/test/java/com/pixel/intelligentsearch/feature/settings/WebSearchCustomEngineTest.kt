package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
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
 * Choosing Custom as the Primary Search App starts with an empty address for the user to fill in: no engine is picked
 * for them (it used to save DuckDuckGo's address), so searches go to the phone's own web search until there is one.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class WebSearchCustomEngineTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>()

    private val prefs: SharedPreferences = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
    }

    /** The page as the app shows it: with the settings view model, which the old auto-fill wrote through. */
    private fun show() {
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                CompositionLocalProvider(LocalSettingsViewModel provides hiltViewModel<SettingsViewModel>()) {
                    WebSearchScreen(prefs, onBack = {})
                }
            }
        }
        compose.waitForIdle()
    }

    /** Scrolls to and taps [text], after the rows' tap debounce has passed. */
    private fun tap(text: String) {
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        compose.onNodeWithText(text).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun choosingCustomLeavesTheAddressBlank() {
        show()
        tap("Primary Search App")
        tap("Custom")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Custom", prefs[SettingsManager.SEARCH_ENGINE])
        assertEquals("", prefs[SettingsManager.CUSTOM_SEARCH_ENGINE_URL])
        val urlField = compose.onNode(hasSetTextAction() and hasText("Custom Search URL (use %s for query)"))
            .fetchSemanticsNode()
        assertEquals("", urlField.config[SemanticsProperties.EditableText].text)
    }
}
