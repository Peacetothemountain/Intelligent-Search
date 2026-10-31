package com.pixel.intelligentsearch.feature.widget

import android.app.Activity
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.net.toUri
import com.pixel.intelligentsearch.R
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.occasion.Occasion
import com.pixel.intelligentsearch.core.occasion.OccasionCalendar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class WidgetDoodlesTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs by lazy { context.getSharedPreferences("doodles_test", Context.MODE_PRIVATE) }
    private val defaultNow = OccasionCalendar.now

    @After fun restoreClock() {
        OccasionCalendar.now = defaultNow
    }

    private fun today(date: String) {
        OccasionCalendar.now = { LocalDate.parse(date).atTime(9, 0).atZone(ZoneId.of("America/New_York")) }
    }

    @Test
    fun theWidgetsShowTodaysDoodle() {
        today("2026-10-31")
        assertEquals(Occasion.HALLOWEEN, WidgetDoodles.current(prefs))
        today("2026-11-01")
        assertNull(WidgetDoodles.current(prefs))
    }

    @Test
    fun aPreviewShowsAnyDoodleUntilItIsTurnedOff() {
        today("2026-11-01")
        var redraws = 0
        for (doodle in Occasion.entries) {
            WidgetDoodles.preview(context, doodle, prefs) { redraws++ }
            assertEquals(doodle, WidgetDoodles.current(prefs))
        }
        WidgetDoodles.preview(context, null, prefs) { redraws++ }

        assertEquals(Occasion.entries.size + 1, redraws) // the widgets are redrawn every time
        assertNull(WidgetDoodles.previewing(prefs))
        assertNull(WidgetDoodles.current(prefs))
    }

    @Test
    fun aPreviewWinsOverTodaysDoodle() {
        today("2026-10-31")
        WidgetDoodles.preview(context, Occasion.PI_DAY, prefs) {}
        assertEquals(Occasion.PI_DAY, WidgetDoodles.current(prefs))
    }

    @Test
    fun theNextRedrawIsArrangedForJustAfterTheNextChange() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val now = ZonedDateTime.of(2026, 10, 30, 23, 0, 0, 0, ZoneId.of("America/New_York"))
        OccasionCalendar.now = { now }

        WidgetDoodles.scheduleNextChange(context)

        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork("widget_doodle_change").get().single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        // Halloween starts in an hour; the redraw waits half a minute past midnight.
        assertEquals(TimeUnit.HOURS.toMillis(1) + TimeUnit.SECONDS.toMillis(30), work.initialDelayMillis)
    }

    /** The doodle plays around the G, so a widget with its G hidden has none, even on a doodle day. */
    @Test
    fun aWidgetWithoutItsGHasNoDoodle() {
        today("2026-10-31")
        prefs.edit().putBoolean("widget_material_design_saved", true).putBoolean("widget_material_show_g_icon", false).commit()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        for (size in listOf(SearchWidgetProvider.ULTRA_COMPACT_WIDTH, SearchWidgetProvider.EXPANDED_WIDTH)) {
            val widget = SearchWidgetProvider.buildResponsiveViews(context, appWidgetId = 7, isMaterialYou = true, prefs = prefs)
                .getValue(size)
                .apply(activity, FrameLayout(activity))
            assertEquals(0, widget.findViewById<ViewGroup>(R.id.widget_doodle).childCount)
            assertEquals(View.GONE, widget.findViewById<View>(R.id.widget_g_slot).visibility)
        }
    }

    /** Another app that answers the system web search, as this app does too. */
    private fun installWebSearchApp(): ComponentName {
        val other = ComponentName("org.example.search", "org.example.search.Main")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(other)
            addIntentFilterForActivity(other, IntentFilter(Intent.ACTION_WEB_SEARCH).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        }
        return other
    }

    @Test
    fun tappingADoodleOpensTheDaysWebSearchStraightAway() {
        prefs.edit().put(SettingsManager.SEARCH_ENGINE, "Google").commit()
        // A phone with an app that searches the web: a web search never comes back to this app.
        val other = installWebSearchApp()
        val intent = SearchWidgetProvider.doodleSearchIntent(context, prefs, appWidgetId = 7, doodle = Occasion.HALLOWEEN)

        // No Google app here, so the system's own web search answers it, in the other app.
        assertEquals(Intent.ACTION_WEB_SEARCH, intent.action)
        assertEquals("Halloween", intent.getStringExtra(SearchManager.QUERY))
        assertEquals(other, intent.component)
    }

    @Test
    fun aDoodleWithNothingElseToSearchInNeverReopensTheSearchWithTheDay() {
        prefs.edit().put(SettingsManager.SEARCH_ENGINE, "Google").commit()
        // No Google app, no browser: only this app answers the system web search, which the tap opened.
        val intent = SearchWidgetProvider.doodleSearchIntent(context, prefs, appWidgetId = 7, doodle = Occasion.HALLOWEEN)

        assertEquals(WidgetActivity::class.java.name, intent.component?.className)
        assertEquals(ACTION_SEARCH_WEB, intent.action)
        assertEquals("Halloween", intent.getStringExtra(SearchManager.QUERY))
    }

    @Test
    fun aDoodleSearchesWithTheChosenEngine() {
        prefs.edit().put(SettingsManager.SEARCH_ENGINE, "DuckDuckGo").commit()
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_VIEW, "https://duckduckgo.com/?q=Pi%20Day".toUri()),
            ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = "browser"; name = "Browser" } }
        )
        val intent = SearchWidgetProvider.doodleSearchIntent(context, prefs, appWidgetId = 7, doodle = Occasion.PI_DAY)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://duckduckgo.com/?q=Pi%20Day", intent.dataString)
    }

    @Test
    fun withTorButNoTorBrowserADoodleOpensTheSearchBarInstead() {
        prefs.edit().put(SettingsManager.SEARCH_ENGINE, "Tor Browser").put(SettingsManager.SEARCH_OVERLAY_ENABLED, true).commit()
        val intent = SearchWidgetProvider.doodleSearchIntent(context, prefs, appWidgetId = 7, doodle = Occasion.FIRST_DAY_OF_SPRING)

        // Never a normal browser for a Tor user: the day is typed into the search bar.
        assertEquals(WidgetActivity::class.java.name, intent.component?.className)
        assertEquals("First Day of Spring", intent.getStringExtra(SearchManager.QUERY))
    }
}
