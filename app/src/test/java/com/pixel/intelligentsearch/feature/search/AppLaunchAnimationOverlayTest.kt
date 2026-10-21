package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.os.Bundle
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Singleton

/** Tapping an app in the real overlay hands its launch the App Animations setting's animation, or none when it is off. */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class AppLaunchAnimationOverlayTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object FakeSourcesModule {
        @Provides
        @Singleton
        fun provideSources(): DeviceSearchSources = FakeDeviceSearchSources()
    }

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
    private val launches = mutableListOf<Pair<String, Bundle?>>()
    private lateinit var tapped: androidx.compose.ui.geometry.Rect

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        TutorialManager.completeTutorial(prefs)
        prefs.edit().put(SettingsManager.SEARCH_WEB_SUGGESTIONS, false).commit()
    }

    private fun tapCamera(appAnimations: Boolean): Bundle? {
        prefs.edit().put(SettingsManager.APP_ANIMATIONS, appAnimations).commit()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                val viewModel: SearchViewModel = hiltViewModel()
                androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.onQueryChanged("Camera") }
                SearchOverlayScreen(
                    onOpenSettings = {},
                    onLaunchApp = { packageName, _, _, options -> launches += packageName to options },
                    viewModel = viewModel,
                    isKeyboardDisabled = true
                )
            }
        }
        compose.settle()
        val camera = compose.onAllNodesWithText("Camera").onFirst()
        tapped = camera.fetchSemanticsNode().boundsInWindow
        camera.performClick()
        compose.settle()
        assertEquals(listOf("com.example.camera"), launches.map { it.first })
        return launches.single().second
    }

    @Test
    fun withAppAnimationsOnTheAppScalesUpFromTheTap() {
        val options = tapCamera(appAnimations = true)
        assertNotNull("the launch carries its animation", options)
        assertEquals(AppLaunchAnimationTest.ANIM_SCALE_UP, options!!.getInt(AppLaunchAnimationTest.ANIM_TYPE))
        // It grows out of the tapped app, not the corner of the screen.
        val x = options.getInt(AppLaunchAnimationTest.START_X).toFloat()
        val y = options.getInt(AppLaunchAnimationTest.START_Y).toFloat()
        assertTrue("($x, $y) lies in $tapped", x in tapped.left - 1..tapped.right + 1 && y in tapped.top - 1..tapped.bottom + 1)
    }

    @Test
    fun withAppAnimationsOffTheSystemDefaultPlays() {
        assertNull(tapCamera(appAnimations = false))
    }
}
