package com.pixel.intelligentsearch.screenshots

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.settings.AppSearchScreen
import com.pixel.intelligentsearch.feature.settings.AppearanceScreen
import com.pixel.intelligentsearch.feature.settings.BackupRestoreScreen
import com.pixel.intelligentsearch.feature.settings.ContactSearchScreen
import com.pixel.intelligentsearch.feature.settings.CustomIconsScreen
import com.pixel.intelligentsearch.feature.settings.FileSearchScreen
import com.pixel.intelligentsearch.feature.settings.LaunchPortalScreen
import com.pixel.intelligentsearch.feature.settings.MainSettingsScreen
import com.pixel.intelligentsearch.feature.settings.ManageHiddenAppsScreen
import com.pixel.intelligentsearch.feature.settings.SearchBehaviorScreen
import com.pixel.intelligentsearch.feature.settings.SearchPillsScreen
import com.pixel.intelligentsearch.feature.settings.SearchSourcesScreen
import com.pixel.intelligentsearch.feature.settings.WebSearchScreen
import com.pixel.intelligentsearch.feature.settings.WidgetSettingsScreen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden images of every settings page as it looks before the rebuild. Record with
 * `./gradlew testDebugUnitTest --tests "*GoldenTest*" -Proborazzi.record=true`; every other run compares the
 * rendered page against the committed image, so a rebuilt page must look the same.
 *
 * The window is tall so each page is captured over its whole scroll length; the clock is frozen at a fixed
 * point so animated elements are captured at the same frame every time.
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class SettingsGoldenTest(private val page: String, private val dark: Boolean) {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() {
        hilt.inject()
        disableHandRolledAnimations()
    }

    private val prefs: SharedPreferences
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Composable
    private fun Page(name: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val back = {}
        when (name) {
            "main" -> MainSettingsScreen(prefs, {}, back, context)
            "appearance" -> AppearanceScreen(prefs, {}, back)
            "custom_icons" -> CustomIconsScreen(prefs, back)
            "search_sources" -> SearchSourcesScreen(prefs, {}, back)
            "search_behavior" -> SearchBehaviorScreen(prefs, {}, back)
            "launch_portal" -> LaunchPortalScreen(prefs, back)
            "app_search" -> AppSearchScreen(prefs, {}, back)
            "search_pills" -> SearchPillsScreen(prefs, back)
            "web_search" -> WebSearchScreen(prefs, back)
            "contact_search" -> ContactSearchScreen(prefs, back)
            "file_search" -> FileSearchScreen(prefs, back)
            "widget" -> WidgetSettingsScreen(prefs, back)
            "hidden_apps" -> ManageHiddenAppsScreen(prefs, back)
            "backup" -> BackupRestoreScreen(prefs, back)
            "setup" -> com.pixel.intelligentsearch.feature.settings.SetupChecklistScreen(prefs, {}, back)
            else -> error("unknown page $name")
        }
    }

    @Test
    fun page() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                // Settings pages are transparent; the app draws its background behind them.
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                ) { Page(page) }
            }
        }
        compose.settle()
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/settings/${page}_${if (dark) "dark" else "light"}.png",
            roborazziOptions = GOLDEN_OPTIONS
        )
    }

    companion object {
        val PAGES = listOf(
            "main", "appearance", "custom_icons", "search_sources", "search_behavior", "launch_portal", "app_search",
            "search_pills", "web_search", "contact_search", "file_search", "widget", "hidden_apps", "backup", "setup"
        )

        /** Up to 0.1% of pixels may differ (anti-aliasing); a real layout or colour change exceeds that. */
        val GOLDEN_OPTIONS by lazy {
            RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f))
        }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} dark={1}")
        fun parameters(): List<Array<Any>> = PAGES.flatMap { listOf(arrayOf<Any>(it, false), arrayOf<Any>(it, true)) }
    }
}
