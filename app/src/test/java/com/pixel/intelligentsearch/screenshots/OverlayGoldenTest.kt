package com.pixel.intelligentsearch.screenshots

import android.os.Looper
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.search.ContactActionTargets
import com.pixel.intelligentsearch.feature.search.FakeContactBook
import com.pixel.intelligentsearch.feature.search.SearchOverlayScreen
import com.pixel.intelligentsearch.feature.search.SearchViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden images of the search overlay in states that don't depend on which apps are installed: the first-run
 * tutorial, empty, a calculator answer, a web-address answer, a timer action, a currency conversion, a date answer
 * and a search for two made-up contacts with their Call, Message, Email and WhatsApp buttons (the contacts and the
 * apps behind the buttons are stand-ins the test installs).
 * See [SettingsGoldenTest] for recording.
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class OverlayGoldenTest(private val state: String, private val query: String, private val dark: Boolean) {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    @Before fun inject() {
        hilt.inject()
        disableHandRolledAnimations()
    }

    @Test
    fun overlay() {
        // Every state but "tutorial" is shown as after the first-run tutorial.
        if (state != "tutorial") {
            com.pixel.intelligentsearch.feature.settings.TutorialManager.completeTutorial(
                androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
                    .getSharedPreferences(com.pixel.intelligentsearch.core.data.SettingsManager.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            )
        }
        // Live web suggestions come from the network and arrive at random times; goldens must not depend on them.
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences(com.pixel.intelligentsearch.core.data.SettingsManager.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(com.pixel.intelligentsearch.core.data.SettingsManager.SEARCH_WEB_SUGGESTIONS.name, false).commit()
        // Buttons found by an earlier test must not stand in for this one's.
        ContactActionTargets.cached = null
        if (state == "contact") {
            val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
            FakeContactBook.install(app)
            FakeContactBook.installContactApps(app)
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                val viewModel: SearchViewModel = hiltViewModel()
                if (query.isNotEmpty()) LaunchedEffect(Unit) { viewModel.onQueryChanged(query) }
                SearchOverlayScreen(
                    onOpenSettings = {},
                    onLaunchApp = { _, _, _, _ -> },
                    viewModel = viewModel,
                    isKeyboardDisabled = true
                )
            }
        }
        compose.settle()
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/overlay/${state}_${if (dark) "dark" else "light"}.png",
            roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={2}")
        fun parameters(): List<Array<Any>> = listOf(
            "tutorial" to "",
            "empty" to "",
            "math" to "12*4+7",
            "url" to "example.com",
            "timer" to "timer 5m",
            // Nothing is ever fetched in tests, so the card shows the built-in rates and says so.
            "currency" to "100 usd to eur",
            // Two dates with years, so the answer doesn't depend on the day the test runs.
            "date" to "days between 2026-03-01 and 2026-06-30",
            // One Robin as the Best Match with chips, the other as a row with round buttons.
            "contact" to FakeContactBook.QUERY
        ).flatMap { (state, query) -> listOf(arrayOf<Any>(state, query, false), arrayOf<Any>(state, query, true)) }
    }
}
