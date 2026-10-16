package com.pixel.intelligentsearch.screenshots

import android.os.Looper
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
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
 * tutorial, empty, a calculator answer, a web-address answer and a timer action. See [SettingsGoldenTest] for recording.
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
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                val viewModel: SearchViewModel = hiltViewModel()
                if (query.isNotEmpty()) LaunchedEffect(Unit) { viewModel.onQueryChanged(query) }
                SearchOverlayScreen(
                    onOpenSettings = {},
                    onLaunchApp = { _, _, _ -> },
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
            "timer" to "timer 5m"
        ).flatMap { (state, query) -> listOf(arrayOf<Any>(state, query, false), arrayOf<Any>(state, query, true)) }
    }
}
