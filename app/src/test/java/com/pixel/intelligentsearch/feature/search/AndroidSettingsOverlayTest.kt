package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
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

/** The search overlay shows the Android Settings section for a query, and tapping a page opens it. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class AndroidSettingsOverlayTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        com.pixel.intelligentsearch.feature.settings.TutorialManager.completeTutorial(prefs)
        // Live web suggestions come from the network; this test must not depend on them.
        prefs.edit().putBoolean(SettingsManager.SEARCH_WEB_SUGGESTIONS.name, false).commit()
        // The Battery page opens, as the Settings app's would on a phone.
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.settings"
                name = "com.android.settings.SubSettings"
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent(Intent.ACTION_POWER_USAGE_SUMMARY), info)
    }

    private fun showOverlay(query: String) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                val viewModel: SearchViewModel = hiltViewModel()
                LaunchedEffect(Unit) { viewModel.onQueryChanged(query) }
                SearchOverlayScreen(
                    onOpenSettings = {},
                    onLaunchApp = { _, _, _, _ -> },
                    viewModel = viewModel,
                    isKeyboardDisabled = true
                )
            }
        }
        compose.settle()
    }

    @Test
    fun aMatchingSettingsPageIsShownAndOpensWhenTapped() {
        showOverlay("battery")

        compose.onNodeWithContentDescription("Battery settings").performClick()
        compose.settle(steps = 2)

        val started = shadowOf(compose.activity).nextStartedActivity
            ?: shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
        assertEquals(Intent.ACTION_POWER_USAGE_SUMMARY, started?.action)
    }

    @Test
    fun turningTheSourceOffHidesTheSection() {
        context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(SettingsManager.SEARCH_ANDROID_SETTINGS.name, false).commit()
        showOverlay("battery")

        compose.onNodeWithContentDescription("Battery settings").assertDoesNotExist()
    }
}
