package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.view.View
import androidx.activity.BackEventCompat
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.hilt.navigation.compose.hiltViewModel
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Singleton

/** The back swipe on the search overlay: a cancelled swipe springs back without slowing the next one down. */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class OverlayBackSwipeTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object FakeSourcesModule {
        @Provides
        @Singleton
        fun provideSources(): DeviceSearchSources = FakeDeviceSearchSources()
    }

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    private val prefs
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        TutorialManager.completeTutorial(prefs)
        // Live web suggestions come from the network; these tests must not depend on them.
        prefs.edit().put(SettingsManager.SEARCH_WEB_SUGGESTIONS, false).commit()
    }

    private fun showOverlay() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                SearchOverlayScreen(
                    onOpenSettings = {},
                    onLaunchApp = { _, _, _, _ -> },
                    viewModel = hiltViewModel(),
                    isKeyboardDisabled = true
                )
            }
        }
        compose.settle()
    }

    @Test
    @Config(sdk = [35])
    fun aCancelledSwipeHandsTheRefreshRateBackToAndroid() {
        showOverlay()
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(240f, 400f, 0.6f, BackEventCompat.EDGE_LEFT))
        }
        compose.settle(steps = 2)
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.settle(steps = 10)

        val window = compose.activity.window
        // No preference and touch boost on, Android's defaults: not pinned near 60 Hz for whatever moves next.
        assertEquals(View.REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE, window.decorView.requestedFrameRate)
        assertTrue(window.attributes.frameRateBoostOnTouchEnabled)
        assertFalse(compose.activity.isFinishing)
    }

    @Test
    @Config(sdk = [35])
    fun aSwipeStartedWhileACancelledOneSpringsBackKeepsTheHighRefreshRate() {
        showOverlay()
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(240f, 400f, 0.6f, BackEventCompat.EDGE_LEFT))
        }
        compose.settle(steps = 2)
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        // The cancelled overlay is still springing back when the next swipe starts.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 400f, 0.3f, BackEventCompat.EDGE_LEFT))
        }
        compose.settle(steps = 3)

        val window = compose.activity.window
        assertEquals(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH, window.decorView.requestedFrameRate)
        assertTrue(window.attributes.frameRateBoostOnTouchEnabled)
        assertFalse(compose.activity.isFinishing)
    }
}
