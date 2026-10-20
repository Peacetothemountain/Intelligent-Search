package com.pixel.intelligentsearch.feature.search

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.DeviceSearchSources
import com.pixel.intelligentsearch.core.data.DeviceSearchSourcesModule
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.feature.widget.WidgetActivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import javax.inject.Singleton

/** Text selected or shared in another app arrives in the search overlay's field, both on a fresh open and an open one. */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SelectedTextOverlayTest {

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
    }

    /** What the selection entry starts for [intent], as the overlay receives it. */
    private fun forwarded(intent: Intent): Intent {
        val entry = Robolectric.buildActivity(SelectedTextSearchActivity::class.java, intent).create().get()
        return shadowOf(entry).nextStartedActivity
    }

    @Test
    fun selectedTextIsTypedIntoTheOverlay() {
        val selection = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, "Eiffel\nTower")
        val overlay = Robolectric.buildActivity(WidgetActivity::class.java, forwarded(selection)).create()
        val viewModel = ViewModelProvider(overlay.get())[SearchViewModel::class.java]
        assertEquals("Eiffel Tower", viewModel.uiState.value.query)

        // Sharing more text while the overlay is open replaces the query.
        val shared = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Louvre")
        overlay.newIntent(forwarded(shared))
        assertEquals("Louvre", viewModel.uiState.value.query)
    }

    @Test
    fun withTheOverlayOffOnlyOrdinaryOpensFallBackToAWebSearch() {
        context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(SettingsManager.SEARCH_OVERLAY_ENABLED.name, false).commit()

        // Opening search with a query, as the widget does, still hands it to a web search when there is no Google app.
        val ordinary = Intent(context, WidgetActivity::class.java).putExtra(SearchManager.QUERY, "weather")
        val overlay = Robolectric.buildActivity(WidgetActivity::class.java, ordinary).create().get()
        val web = shadowOf(overlay).nextStartedActivity
        assertEquals(Intent.ACTION_WEB_SEARCH, web.action)
        assertEquals("weather", web.getStringExtra(SearchManager.QUERY))

        // Selected text never gets that far: nothing opens, so it is never searched without being confirmed.
        val selection = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").putExtra(Intent.EXTRA_PROCESS_TEXT, "private note")
        val entry = Robolectric.buildActivity(SelectedTextSearchActivity::class.java, selection).create().get()
        assertNull(shadowOf(entry).nextStartedActivity)
    }
}
