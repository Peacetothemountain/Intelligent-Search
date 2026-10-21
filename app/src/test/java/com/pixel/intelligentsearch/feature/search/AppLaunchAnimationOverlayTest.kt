package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.AppShortcutItem
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
import org.junit.Assert.assertNull
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
 * Opening an app from the real overlay: with App Animations on, the app opens out of the icon that was tapped and is
 * started with that icon's bounds on screen, from the Best Match card, the app row, a shortcut and Enter alike; off,
 * the launch carries nothing of its own.
 */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi", shadows = [RecordingLauncherApps::class])
class AppLaunchAnimationOverlayTest {

    /** The stand-in device, with one app shortcut: Camera's "Selfie". */
    class SourcesWithShortcut(private val device: FakeDeviceSearchSources = FakeDeviceSearchSources()) : DeviceSearchSources by device {
        override fun allShortcuts(): List<AppShortcutItem> {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val info = ShortcutInfo.Builder(context, "selfie").setShortLabel("Selfie").setIntent(Intent(Intent.ACTION_MAIN)).build()
            return listOf(AppShortcutItem("selfie", "com.example.camera", "Selfie", "Take a selfie", info))
        }
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object FakeSourcesModule {
        @Provides
        @Singleton
        fun provideSources(): DeviceSearchSources = SourcesWithShortcut()
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

    private fun search(query: String, appAnimations: Boolean = true) {
        prefs.edit().put(SettingsManager.APP_ANIMATIONS, appAnimations).commit()
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

    /** The launch opens out of [icon] on screen and tells the app it came from there. */
    private fun assertOpensOutOf(icon: android.graphics.Rect, launch: AppLaunchOptions?) {
        assertNotNull("App Animations on: the launch carries its animation and source", launch)
        assertEquals("the app is told the tapped icon's bounds on screen", icon, launch!!.sourceBounds)
        val options = launch.options!!
        assertEquals(AppLaunchAnimationTest.ANIM_CLIP_REVEAL, options.getInt(AppLaunchAnimationTest.ANIM_TYPE))
        assertEquals(icon.left, options.getInt(AppLaunchAnimationTest.START_X))
        assertEquals(icon.top, options.getInt(AppLaunchAnimationTest.START_Y))
        assertEquals(icon.width(), options.getInt(AppLaunchAnimationTest.WIDTH))
        assertEquals(icon.height(), options.getInt(AppLaunchAnimationTest.HEIGHT))
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
    fun tappingAShortcutOpensItOutOfItsRowsIcon() {
        search("Selfie")
        val shortcut = RecordingLauncherApps.starts
        val bounds = tap(icon(LaunchIconKeys.shortcut("com.example.camera", "selfie", android.os.Process.myUserHandle())))
        assertEquals(listOf("com.example.camera/selfie"), shortcut.map { it.target })
        assertOpensOutOf(bounds, AppLaunchOptions(shortcut.single().options, shortcut.single().sourceBounds))
    }

    @Test
    fun withAppAnimationsOffTheSystemDefaultPlays() {
        search("Camera", appAnimations = false)
        tap(icon(LaunchIconKeys.BEST_MATCH))
        assertEquals(listOf("com.example.camera"), launches.map { it.first })
        assertNull(launches.single().second)
    }

    @Test
    fun withAppAnimationsOffAShortcutOpensWithTheSystemDefault() {
        search("Selfie", appAnimations = false)
        tap(icon(LaunchIconKeys.shortcut("com.example.camera", "selfie", android.os.Process.myUserHandle())))
        val start = RecordingLauncherApps.starts.single()
        assertNull(start.options)
        assertNull(start.sourceBounds)
    }
}
