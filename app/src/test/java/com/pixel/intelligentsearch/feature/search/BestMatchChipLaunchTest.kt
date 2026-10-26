package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.AppAction
import com.pixel.intelligentsearch.core.data.AppItem
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import javax.inject.Singleton

/**
 * The Best Match card's quick actions ("Record Video" under Camera) open as the app itself does: out of the app's
 * icon, which the action is told as its source, with the app's icon on its splash screen.
 */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class BestMatchChipLaunchTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object CameraWithActionsModule {
        @Provides
        @Singleton
        fun provideSources(): DeviceSearchSources = FakeDeviceSearchSources(
            listOf(AppItem("Camera", "com.example.camera", ColorDrawable(0), actions = listOf(AppAction("Record Video", "android.media.action.VIDEO_CAPTURE"))))
        )
    }

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        TutorialManager.completeTutorial(prefs)
        prefs.edit().put(SettingsManager.SEARCH_WEB_SUGGESTIONS, false).commit()
    }

    private fun tapRecordVideo(): android.content.Intent? {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                val viewModel: SearchViewModel = hiltViewModel()
                androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.onQueryChanged("Camera") }
                SearchOverlayScreen(onOpenSettings = {}, onLaunchApp = { _, _, _, _ -> }, viewModel = viewModel, isKeyboardDisabled = true)
            }
        }
        compose.settle()
        compose.onNodeWithText("Record Video").performClick()
        compose.settle(steps = 3)
        val started = shadowOf(compose.activity).nextStartedActivityForResult
        assertEquals("android.media.action.VIDEO_CAPTURE", started?.intent?.action)
        val options = started.options
        assertNotNull("the action carries its launch options", options)
        assertEquals(AppLaunchAnimationTest.SPLASH_SCREEN_STYLE_ICON, options!!.getInt(AppLaunchAnimationTest.SPLASH_SCREEN_STYLE, -1))
        return started.intent
    }

    @Test
    fun aQuickActionOpensOutOfTheAppsIcon() {
        assertNotNull("the action is told the app icon it came from", tapRecordVideo()?.sourceBounds)
    }
}
