package com.pixel.intelligentsearch

import android.app.SearchManager
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
 * With the overlay off and no Google app, search hands its query on as a web search. When this app is itself the
 * web search handler, that web search comes straight back here, and handing it on again would loop forever.
 */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class OverlayDisabledForwardTest {

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
        context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE).edit().clear()
            .putBoolean(SettingsManager.SEARCH_OVERLAY_ENABLED.name, false).commit()
        // The overlay's endless animations would keep Robolectric's looper busy; nothing here is drawn.
        ShadowChoreographer.setPaused(true)
    }

    @After
    fun resumeFrames() = ShadowChoreographer.setPaused(false)

    @Test
    fun aWebSearchHandedToThisAppIsShownInsteadOfHandedOnAgain() {
        // Robolectric has no Google app, and this app's SearchActivity is the only web search handler.
        val webSearch = Intent(Intent.ACTION_WEB_SEARCH).setClass(context, SearchActivity::class.java)
            .putExtra(SearchManager.QUERY, "x")
        val search = Robolectric.buildActivity(SearchActivity::class.java, webSearch).setup()

        val started = generateSequence { shadowOf(search.get()).nextStartedActivity }.toList()
        assertEquals(emptyList<String>(), started.filter { it.action == Intent.ACTION_WEB_SEARCH }.map { it.toString() })
        // With nowhere else to send it, the overlay shows the query.
        assertFalse(search.get().isFinishing)
        assertEquals("x", ViewModelProvider(search.get())[SearchViewModel::class.java].uiState.value.query)
        search.pause().stop().destroy()
    }
}
