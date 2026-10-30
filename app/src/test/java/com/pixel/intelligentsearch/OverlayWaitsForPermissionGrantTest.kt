package com.pixel.intelligentsearch

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.DeviceSearchSources
import com.pixel.intelligentsearch.core.data.DeviceSearchSourcesModule
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.system.openSettingsPageToGrant
import com.pixel.intelligentsearch.feature.search.FakeDeviceSearchSources
import com.pixel.intelligentsearch.feature.search.SearchActivity
import com.pixel.intelligentsearch.feature.search.SearchViewModel
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
 * Turning on Usage Access from the screen time answer opens Android's page over the overlay, in its own task, and the
 * overlay waits behind it with what was typed instead of closing as it goes out of sight: Back from Android's page
 * comes back to the search, not to the home screen. Once back, it closes out of sight again as always.
 */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class OverlayWaitsForPermissionGrantTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object FakeSourcesModule {
        @Provides
        @Singleton
        fun provideSources(): DeviceSearchSources = FakeDeviceSearchSources()
    }

    @get:Rule val hilt = HiltAndroidRule(this)

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        hilt.inject()
        context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        // The overlay's endless animations would keep Robolectric's looper busy; nothing here is drawn.
        ShadowChoreographer.setPaused(true)
    }

    @After
    fun resumeFrames() = ShadowChoreographer.setPaused(false)

    @Test
    fun theOverlayWaitsWithTheQueryWhileUsageAccessIsTurnedOn() {
        val search = Robolectric.buildActivity(SearchActivity::class.java, Intent(context, SearchActivity::class.java)).setup()
        val activity = search.get()
        val viewModel = ViewModelProvider(activity)[SearchViewModel::class.java]
        viewModel.onQueryChanged("screen time")

        assertTrue(openSettingsPageToGrant(activity, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)))
        val opened = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, opened.action)
        assertEquals("opened in the overlay's own task, so Back returns to it", 0, opened.flags and Intent.FLAG_ACTIVITY_NEW_TASK)

        search.pause().stop()
        assertFalse("it waits behind Android's page", activity.isFinishing)
        assertEquals("screen time", viewModel.uiState.value.query)

        // Back from Android's page: the search is there, and out of sight later it closes as always.
        search.restart().resume()
        assertEquals("screen time", viewModel.uiState.value.query)
        search.pause().stop()
        assertTrue(activity.isFinishing)
        search.destroy()
    }
}
