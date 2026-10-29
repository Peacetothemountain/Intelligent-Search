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
import org.junit.Assert.assertTrue
import org.junit.After
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
        // The overlay runs endless animations. Robolectric moves its clock on every frame and each frame asks for the
        // next, so the looper would never go idle; these tests check what is opened and typed, not what is drawn, so
        // frames run only when asked for.
        ShadowChoreographer.setPaused(true)
    }

    @After
    fun resumeFrames() = ShadowChoreographer.setPaused(false)

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
    fun selectedTextWaitsForTheUserBeforeAnythingLeavesThePhone() {
        val selection = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").putExtra(Intent.EXTRA_PROCESS_TEXT, "private note")
        val overlay = Robolectric.buildActivity(WidgetActivity::class.java, forwarded(selection)).create()
        val viewModel = ViewModelProvider(overlay.get())[SearchViewModel::class.java]
        assertEquals("private note", viewModel.uiState.value.query)
        assertEquals(false, viewModel.queryConfirmed.value)

        // A query typed by the user is theirs; one shared into the open overlay waits again.
        viewModel.onQueryChanged("private notes")
        assertEquals(true, viewModel.queryConfirmed.value)
        val shared = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Louvre")
        overlay.newIntent(forwarded(shared))
        assertEquals("Louvre", viewModel.uiState.value.query)
        assertEquals(false, viewModel.queryConfirmed.value)
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

    private fun turnOverlayOff() {
        context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(SettingsManager.SEARCH_OVERLAY_ENABLED.name, false).commit()
    }

    /** Every activity [activity] has started so far. */
    private fun startedBy(activity: android.app.Activity): List<Intent> =
        generateSequence { shadowOf(activity).nextStartedActivity }.toList()

    /** Whether any extra of [intent] holds [text]. */
    private fun carries(intent: Intent, text: String): Boolean {
        val extras = intent.extras ?: return false
        return extras.keySet().any { extras.getString(it)?.contains(text) == true }
    }

    @Test
    fun turningTheOverlayOffFromItsSettingsNeverWebSearchesTheSelectedText() {
        // No Google app, so with the overlay off its fallback is a web search.
        val selection = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").putExtra(Intent.EXTRA_PROCESS_TEXT, "private note")
        val overlay = Robolectric.buildActivity(WidgetActivity::class.java, forwarded(selection)).setup()
        val viewModel = ViewModelProvider(overlay.get())[SearchViewModel::class.java]
        assertEquals("private note", viewModel.uiState.value.query)
        assertEquals(emptyList<Intent>(), startedBy(overlay.get()))

        // The user opens Settings from the overlay, turns the overlay off there and comes back.
        overlay.pause().stop()
        turnOverlayOff()
        overlay.restart().start().resume()

        val started = startedBy(overlay.get())
        assertTrue(started.none { it.action == Intent.ACTION_WEB_SEARCH && it.hasExtra(SearchManager.QUERY) })
        assertTrue(started.none { carries(it, "private note") })
    }

    @Test
    fun anOverlayRestoredAfterTheOverlayIsTurnedOffNeverWebSearchesItsOldQuery() {
        val selection = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").putExtra(Intent.EXTRA_PROCESS_TEXT, "private note")
        val launch = forwarded(selection)
        val state = android.os.Bundle()
        // The process is killed while the overlay is in the background: the first instance is gone when Android
        // restores it. (Left alive, its running animations would also keep Robolectric's looper busy forever.)
        Robolectric.buildActivity(WidgetActivity::class.java, launch).setup()
            .pause().saveInstanceState(state).stop().destroy()

        // Android restores the activity from its original launch intent, which still holds the query.
        turnOverlayOff()
        val restored = Robolectric.buildActivity(WidgetActivity::class.java, launch).create(state)
        val started = startedBy(restored.get())
        restored.destroy()
        assertTrue(started.none { it.action == Intent.ACTION_WEB_SEARCH && it.hasExtra(SearchManager.QUERY) })
        assertTrue(started.none { carries(it, "private note") })
    }
}
