package com.pixel.intelligentsearch

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.DeviceSearchSources
import com.pixel.intelligentsearch.core.data.DeviceSearchSourcesModule
import com.pixel.intelligentsearch.core.data.GlobalSearchProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.feature.search.FakeDeviceSearchSources
import com.pixel.intelligentsearch.feature.search.SearchActivity
import com.pixel.intelligentsearch.feature.search.SearchViewModel
import com.pixel.intelligentsearch.feature.search.launchWebSearch
import com.pixel.intelligentsearch.feature.search.systemWebSearchIntent
import com.pixel.intelligentsearch.feature.widget.WidgetActivity
import com.pixel.intelligentsearch.feature.widget.getLensTranslateIntent
import com.pixel.intelligentsearch.feature.widget.webSearchFromWidgetIntent
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import org.robolectric.shadows.ShadowToast
import javax.inject.Singleton

/**
 * Hand-offs from the search to other apps, on phones missing the app they'd go to: each either opens something or
 * says what's needed, never nothing at all, and never comes back to this app.
 */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class OverlayHandOffTest {

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

    private fun install(packageName: String, filter: IntentFilter): ComponentName {
        val component = ComponentName(packageName, "$packageName.Main")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(component)
            addIntentFilterForActivity(component, filter.apply { addCategory(Intent.CATEGORY_DEFAULT) })
        }
        return component
    }

    @Test
    fun cameraTranslateWithoutLensSaysWhatToInstall() {
        // No Lens and no Google app: the tap flashed the search and closed, saying nothing.
        val activity = Robolectric.buildActivity(WidgetActivity::class.java, getLensTranslateIntent(context)).create().get()
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals("To translate with your camera, install Google Lens or the Google app.", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun voiceThroughAPlainRecognizerSearchesWhatWasSaid() {
        install("org.example.speech", IntentFilter(RecognizerIntent.ACTION_RECOGNIZE_SPEECH))
        val shortcut = Intent(context, WidgetActivity::class.java).putExtra("auto_voice", true)
        val controller = Robolectric.buildActivity(WidgetActivity::class.java, shortcut).setup()
        val activity = controller.get()

        // Started with startActivity before, so the words never came back and the search had closed.
        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, started.intent.action)
        assertFalse(activity.isFinishing)

        shadowOf(activity).receiveResult(
            started.intent,
            Activity.RESULT_OK,
            Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf("weather in paris", "whether in paris"))
        )
        assertEquals("weather in paris", ViewModelProvider(activity)[SearchViewModel::class.java].uiState.value.query)
        controller.pause().stop().destroy()
    }

    @Test
    fun anAppRowFromAndroidsSearchOpensTheApp() {
        val notes = install("org.example.notes", IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        // Android's search builds the row's intent from its action and data alone; an "intent:" link as VIEW data
        // opened nothing.
        val (action, data) = GlobalSearchProvider.appRowIntent("org.example.notes")
        val row = Intent(action).setData(Uri.parse(data))
        assertEquals(SearchActivity::class.java.name, context.packageManager.resolveActivity(row, 0)?.activityInfo?.name)

        val activity = Robolectric.buildActivity(SearchActivity::class.java, row.setClass(context, SearchActivity::class.java)).create().get()
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(notes.packageName, started?.component?.packageName ?: started?.`package`)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun aWebSearchNeverComesBackToThisApp() {
        // No Google app, no browser: only this app answers the system web search, and opening it reopened the search.
        assertFalse(launchWebSearch(context, systemWebSearchIntent("cats"), "cats"))
        assertNull(shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity)

        // Another app that searches the web is picked by name, so Android can't hand it back here.
        val other = install("org.example.search", IntentFilter(Intent.ACTION_WEB_SEARCH))
        assertTrue(launchWebSearch(context, systemWebSearchIntent("cats"), "cats"))
        val started = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
        assertNotNull(started)
        assertEquals(other, started.component)
        assertEquals("cats", started.getStringExtra(android.app.SearchManager.QUERY))
    }

    @Test
    fun aDoodleTappedWithNothingToSearchInSaysSoInsteadOfReopeningTheSearch() {
        // Drawn with no Google app and no browser, the doodle's tap reopened the search with the day typed in.
        val activity = Robolectric.buildActivity(WidgetActivity::class.java, webSearchFromWidgetIntent(context, "Halloween")).create().get()
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals("No app available to search the web.", ShadowToast.getTextOfLatestToast())
        assertTrue(activity.isFinishing)
    }

    @Test
    fun aDoodleTappedAfterAWebSearchAppArrivedSearchesThere() {
        val other = install("org.example.search", IntentFilter(Intent.ACTION_WEB_SEARCH))
        val activity = Robolectric.buildActivity(WidgetActivity::class.java, webSearchFromWidgetIntent(context, "Halloween")).create().get()
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(other, started?.component)
        assertEquals("Halloween", started?.getStringExtra(android.app.SearchManager.QUERY))
        assertTrue(activity.isFinishing)
    }
}
