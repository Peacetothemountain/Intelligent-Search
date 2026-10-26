package com.pixel.intelligentsearch

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.DeviceSearchSources
import com.pixel.intelligentsearch.core.data.DeviceSearchSourcesModule
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.feature.search.FakeDeviceSearchSources
import com.pixel.intelligentsearch.feature.search.SearchActivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import javax.inject.Singleton

/**
 * Out of sight, the overlay closes for good, so the next search opens a fresh one. Brought back instead, Android first
 * shows a placeholder of the old one, drawn from the theme (the overlay keeps itself out of Recents screenshots): a
 * black screen under a lavender status bar, the "black splash" and "lavender head" seen on the Pixel 11.
 */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class OverlayClosesOutOfSightTest {

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
    fun outOfSightTheOverlayCloses() {
        val search = Robolectric.buildActivity(SearchActivity::class.java, Intent(context, SearchActivity::class.java)).setup()
        search.pause()
        assertFalse("only paused (an app opening over it), it stays", search.get().isFinishing)
        search.stop()
        assertTrue("out of sight, it closes", search.get().isFinishing)
        search.destroy()
    }

    @Test
    fun aConfigurationChangeDoesntCloseIt() {
        val search = Robolectric.buildActivity(SearchActivity::class.java, Intent(context, SearchActivity::class.java)).setup()
        search.configurationChange(android.content.res.Configuration(context.resources.configuration).apply { fontScale = 1.3f })
        assertFalse(search.get().isFinishing)
        search.pause().stop().destroy()
    }
}
