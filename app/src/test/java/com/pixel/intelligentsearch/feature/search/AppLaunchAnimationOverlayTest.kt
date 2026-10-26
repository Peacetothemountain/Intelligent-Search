package com.pixel.intelligentsearch.feature.search

import android.content.Context
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.DeviceSearchSources
import com.pixel.intelligentsearch.core.data.DeviceSearchSourcesModule
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.profile.RecordingLauncherApps
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Opening an app from the real overlay: it grows out of the icon that was tapped into its splash screen, drawn by the
 * overlay (see AppOpeningLayer), and is then started with that icon's bounds on screen, from the Best Match card, the
 * app row and Enter alike. With animations turned off in Android it opens at once.
 */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi", shadows = [RecordingLauncherApps::class])
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
    private val launches = mutableListOf<Pair<String, AppLaunchOptions?>>()
    private val camera = FakeDeviceSearchSources().apps.single { it.packageName == "com.example.camera" }

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        TutorialManager.completeTutorial(prefs)
        prefs.edit().put(SettingsManager.SEARCH_WEB_SUGGESTIONS, false).commit()
        RecordingLauncherApps.starts.clear()
    }

    private fun search(query: String) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                val viewModel: SearchViewModel = hiltViewModel()
                androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.onQueryChanged(query) }
                SearchOverlayScreen(
                    onOpenSettings = {},
                    onLaunchApp = { packageName, _, _, launch -> launches += packageName to launch },
                    viewModel = viewModel,
                    isKeyboardDisabled = true
                )
            }
        }
        compose.settle()
    }

    private fun icon(key: String): SemanticsNodeInteraction = compose.onNode(hasTestTag(launchIconTag(key)), useUnmergedTree = true)

    /** [icon]'s bounds on screen, in whole pixels covering all of it. */
    private fun onScreen(icon: SemanticsNodeInteraction): android.graphics.Rect {
        val bounds = icon.fetchSemanticsNode().boundsInWindow
        val window = IntArray(2).also(compose.activity.window.decorView::getLocationOnScreen)
        return android.graphics.Rect(
            floor(bounds.left).toInt() + window[0], floor(bounds.top).toInt() + window[1],
            ceil(bounds.right).toInt() + window[0], ceil(bounds.bottom).toInt() + window[1]
        )
    }

    private fun tap(icon: SemanticsNodeInteraction): android.graphics.Rect {
        val bounds = onScreen(icon)
        icon.performClick()
        compose.settle()
        return bounds
    }

    /** The launch tells the app it came from [icon] on screen, with its icon on the splash screen. */
    private fun assertOpensOutOf(icon: android.graphics.Rect, launch: AppLaunchOptions?) {
        assertNotNull("the launch carries its options and source", launch)
        assertEquals("the app is told the tapped icon's bounds on screen", icon, launch!!.sourceBounds)
        val options = launch.options!!
        assertEquals(AppLaunchAnimationTest.SPLASH_SCREEN_STYLE_ICON, options.getInt(AppLaunchAnimationTest.SPLASH_SCREEN_STYLE, -1))
    }

    /** Runs [block] with Android's animations on (the other tests run with them off, so apps open at once). */
    private fun withAnimations(block: () -> Unit) {
        val setScale = android.animation.ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType)
        val scale = android.animation.ValueAnimator.getDurationScale()
        setScale.invoke(null, 1f)
        try {
            block()
        } finally {
            setScale.invoke(null, scale)
        }
    }

    private fun openingLayers() = compose.onAllNodes(hasTestTag(APP_OPENING_TAG), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun frames(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    @Test
    fun tappingTheBestMatchOpensTheAppOutOfItsIcon() {
        search("Camera")
        val bounds = tap(icon(LaunchIconKeys.BEST_MATCH))
        assertEquals(listOf("com.example.camera"), launches.map { it.first })
        assertOpensOutOf(bounds, launches.single().second)
    }

    @Test
    fun tappingAnAppInTheAppRowOpensItOutOfThatIcon() {
        search("Camera")
        val bounds = tap(icon(LaunchIconKeys.app(camera)))
        assertEquals(listOf("com.example.camera"), launches.map { it.first })
        assertOpensOutOf(bounds, launches.single().second)
    }

    @Test
    fun enterOpensTheBestMatchOutOfItsIcon() {
        search("Camera")
        val bounds = onScreen(icon(LaunchIconKeys.BEST_MATCH))
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.settle()
        assertEquals(listOf("com.example.camera"), launches.map { it.first })
        assertOpensOutOf(bounds, launches.single().second)
    }

    @Test
    fun theAppGrowsOutOfItsIconBeforeItIsStarted() = withAnimations {
        search("Camera")
        val bounds = onScreen(icon(LaunchIconKeys.app(camera)))
        icon(LaunchIconKeys.app(camera)).performClick()
        frames(16)
        assertEquals("the overlay draws the opening over itself", 1, openingLayers())
        assertTrue("the app waits until it has grown toward the middle", launches.isEmpty())
        frames(800)
        assertEquals(listOf("com.example.camera"), launches.map { it.first })
        assertOpensOutOf(bounds, launches.single().second)
    }

    @Test
    fun theDrawnSplashIsGoneWhenTheOverlayComesBack() = withAnimations {
        search("Camera")
        icon(LaunchIconKeys.app(camera)).performClick()
        frames(800)
        assertEquals(1, launches.size)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        // Coming back to the overlay later shows it as it was, not the splash drawn over it.
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        frames(16)
        assertEquals(0, openingLayers())
    }

    @Test
    fun anAppThatNeverComesUpGivesTheOverlayBack() = withAnimations {
        search("Camera")
        icon(LaunchIconKeys.app(camera)).performClick()
        frames(800)
        assertEquals(1, launches.size)
        // The overlay stayed in front: nothing took over from the drawn splash.
        frames(APP_OPENING_HANDOFF_TIMEOUT_MS + 400)
        assertEquals(0, openingLayers())
    }

    @Test
    fun aSecondTapWhileAnAppOpensIsIgnored() = withAnimations {
        search("Camera")
        icon(LaunchIconKeys.app(camera)).performClick()
        frames(16)
        icon(LaunchIconKeys.BEST_MATCH).performClick()
        frames(800)
        assertEquals(listOf("com.example.camera"), launches.map { it.first })
    }
}
