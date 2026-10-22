package com.pixel.intelligentsearch.screenshots

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.SettingsManager
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
 * The overlay driven from a physical keyboard: on a phone, the second Settings page for "battery" highlighted with the
 * focus ring; on a tablet in landscape, the same highlight with its details in the pane beside the results.
 * See [SettingsGoldenTest] for recording.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class)
class KeyboardNavigationGoldenTest(private val dark: Boolean) {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val mode get() = if (dark) "dark" else "light"

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        com.pixel.intelligentsearch.feature.settings.TutorialManager.completeTutorial(prefs)
        // Live web suggestions come from the network and arrive at random times; goldens must not depend on them.
        prefs.edit().putBoolean(SettingsManager.SEARCH_WEB_SUGGESTIONS.name, false).commit()
        for (action in listOf(Intent.ACTION_POWER_USAGE_SUMMARY, Settings.ACTION_BATTERY_SAVER_SETTINGS, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) {
            val info = ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "com.android.settings"
                    name = "com.android.settings.SubSettings"
                }
            }
            shadowOf(context.packageManager).addResolveInfoForIntent(Intent(action), info)
        }
    }

    /** Shows the overlay for "battery" and moves the highlight to the second result. */
    private fun highlightSecondResult() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                val viewModel: SearchViewModel = hiltViewModel()
                LaunchedEffect(Unit) { viewModel.onQueryChanged("battery") }
                SearchOverlayScreen(onOpenSettings = {}, onLaunchApp = { _, _, _, _ -> }, viewModel = viewModel, isKeyboardDisabled = true)
            }
        }
        compose.settle()
        val field = compose.onNode(hasSetTextAction())
        field.requestFocus()
        field.performKeyInput { pressKey(Key.DirectionDown) }
        field.performKeyInput { pressKey(Key.DirectionDown) }
        compose.settle()
    }

    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    @Test
    fun highlightedRow() {
        highlightSecondResult()
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/overlay/keyboard_highlight_$mode.png",
            roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
        )
    }

    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    @Test
    fun detailPane() {
        highlightSecondResult()
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/overlay_tablet/detail_setting_$mode.png",
            roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }
}
