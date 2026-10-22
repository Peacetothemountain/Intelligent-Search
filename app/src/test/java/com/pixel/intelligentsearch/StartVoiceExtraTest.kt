package com.pixel.intelligentsearch

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.DeviceSearchSources
import com.pixel.intelligentsearch.core.data.DeviceSearchSourcesModule
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.feature.search.FakeDeviceSearchSources
import com.pixel.intelligentsearch.feature.search.SearchActivity
import com.pixel.intelligentsearch.feature.search.SearchViewModel
import com.pixel.intelligentsearch.feature.voice.IntelligentVoiceInteractionSession
import com.pixel.intelligentsearch.feature.widget.WidgetActivity
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
 * Only the assistant gesture may open search already listening. It asks through the private WidgetActivity; the
 * exported SearchActivity can be started by any app, so it must not turn the microphone on for them.
 */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class StartVoiceExtraTest {

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

    /** Whether opening [activity] with the start-voice extra asked the search screen to listen. */
    private fun asksToListen(activity: Class<out MainActivity>): Boolean {
        val intent = Intent(context, activity).putExtra(IntelligentVoiceInteractionSession.EXTRA_START_VOICE, true)
        val search = Robolectric.buildActivity(activity, intent).create().get()
        return ViewModelProvider(search)[SearchViewModel::class.java].takeVoiceRequest()
    }

    @Test
    fun anotherAppCannotOpenTheExportedSearchListening() {
        assertFalse(asksToListen(SearchActivity::class.java))
    }

    @Test
    fun theAssistantGestureStillOpensSearchListening() {
        assertTrue(asksToListen(WidgetActivity::class.java))
    }
}
