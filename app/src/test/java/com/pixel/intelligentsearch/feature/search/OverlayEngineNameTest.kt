package com.pixel.intelligentsearch.feature.search

import android.content.Context
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.DeviceSearchSources
import com.pixel.intelligentsearch.core.data.DeviceSearchSourcesModule
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.settings.TutorialManager
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Singleton

/**
 * The search engine's name under what was typed stays there when the user comes back to the search with the words
 * still in it: from Android's permission prompt (location) or a page granting access (Usage Access).
 */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class OverlayEngineNameTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object FakeSourcesModule {
        @Provides
        @Singleton
        fun provideSources(): DeviceSearchSources = FakeDeviceSearchSources()
    }

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    private val prefs get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        TutorialManager.completeTutorial(prefs)
        prefs.edit().put(SettingsManager.SEARCH_WEB_SUGGESTIONS, false).put(SettingsManager.SEARCH_ENGINE, "DuckDuckGo").commit()
    }

    private fun engineNames() = compose.onAllNodesWithText("DuckDuckGo").fetchSemanticsNodes().size

    @Test
    fun theEngineNameIsStillUnderTheWordsAfterComingBack() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                SearchOverlayScreen(onOpenSettings = {}, onLaunchApp = { _, _, _, _ -> }, isKeyboardDisabled = true)
            }
        }
        compose.settle()
        compose.onNode(hasSetTextAction()).performTextInput("screen time")
        compose.settle()
        assertEquals("the engine's name under the words", 1, engineNames())

        // Android's prompt over the search pauses it; it comes back with the words still typed.
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.settle()

        assertEquals("the engine's name under the words, back from the prompt", 1, engineNames())

        // Usage Access opens over it as a page of its own, out of sight; the overlay keeps the words for Back.
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.settle()

        assertEquals("the engine's name under the words, back from the page", 1, engineNames())
    }
}
