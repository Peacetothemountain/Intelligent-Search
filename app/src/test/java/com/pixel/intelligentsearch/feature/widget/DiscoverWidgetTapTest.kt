package com.pixel.intelligentsearch.feature.widget

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.MainActivity
import com.pixel.intelligentsearch.core.data.DeviceSearchSources
import com.pixel.intelligentsearch.core.data.DeviceSearchSourcesModule
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.security.InternalLaunchToken
import com.pixel.intelligentsearch.feature.search.FakeDeviceSearchSources
import com.pixel.intelligentsearch.feature.search.SearchActivity
import com.pixel.intelligentsearch.feature.search.SearchViewModel
import com.pixel.intelligentsearch.feature.settings.SettingsActivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import javax.inject.Singleton

/**
 * A tap on the Discover widget opens the search with the Discover page showing, when Discover is on; with it off the
 * search opens as it always does. A widget with nothing to show opens the settings page with Discover's switches.
 * Both go through the private WidgetActivity: the exported SearchActivity ignores the request.
 */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class DiscoverWidgetTapTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object FakeSourcesModule {
        @Provides
        @Singleton
        fun provideSources(): DeviceSearchSources = FakeDeviceSearchSources()
    }

    @get:Rule val hilt = HiltAndroidRule(this)

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = SettingsManager.storedPrefs(context)

    @Before
    fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
        // The overlay's endless animations would keep Robolectric's looper busy; nothing here is drawn.
        ShadowChoreographer.setPaused(true)
    }

    @After
    fun resumeFrames() = ShadowChoreographer.setPaused(false)

    private fun discoverOn(on: Boolean) = prefs.edit().putBoolean(SettingsManager.TODAY_BUTTON.name, on).commit()

    /** Whether opening [activity] with [intent]'s extras asked the search screen to show the Discover page. */
    private fun asksForDiscover(activity: Class<out MainActivity>, intent: Intent): Boolean {
        val search = Robolectric.buildActivity(activity, Intent(intent).setClass(context, activity)).create().get()
        return ViewModelProvider(search)[SearchViewModel::class.java].takeDiscoverRequest()
    }

    @Test
    fun theWidgetsTapOpensTheSearchWithDiscoverShowing() {
        discoverOn(true)
        val tap = discoverPageIntent(context, prefs, appWidgetId = 7)
        assertEquals(WidgetActivity::class.java.name, tap.component?.className)
        assertTrue(asksForDiscover(WidgetActivity::class.java, tap))
    }

    @Test
    fun withDiscoverOffTheTapOpensTheSearchAsItAlwaysDoes() {
        discoverOn(false)
        assertFalse(asksForDiscover(WidgetActivity::class.java, discoverPageIntent(context, prefs, appWidgetId = 7)))
    }

    @Test
    fun anotherAppCannotOpenTheExportedSearchOnDiscover() {
        discoverOn(true)
        assertFalse(asksForDiscover(SearchActivity::class.java, discoverPageIntent(context, prefs, appWidgetId = 7)))
    }

    @Test
    fun withTheOverlayOffTheTapGoesWhereTheSearchBarsDoes() {
        prefs.edit().putBoolean(SettingsManager.SEARCH_OVERLAY_ENABLED.name, false).commit()
        val tap = discoverPageIntent(context, prefs, appWidgetId = 7)
        assertEquals(SearchWidgetProvider.mainSearchIntent(context, prefs, 7).action, tap.action)
        assertFalse(tap.hasExtra(EXTRA_OPEN_DISCOVER))
    }

    @Test
    fun aWidgetWithNothingToShowOpensDiscoversSettings() {
        val opener = Robolectric.buildActivity(WidgetActivity::class.java, discoverSettingsIntent(context)).create().get()
        val started = shadowOf(context as Application).nextStartedActivity
        assertEquals(SettingsActivity::class.java.name, started.component?.className)
        assertEquals("appearance", started.getStringExtra("extra_screen"))
        // Settings honors a page only from this app's own launches.
        assertTrue(InternalLaunchToken.isInternal(started))
        // It shows nothing itself.
        assertTrue(opener.isFinishing)
    }
}
