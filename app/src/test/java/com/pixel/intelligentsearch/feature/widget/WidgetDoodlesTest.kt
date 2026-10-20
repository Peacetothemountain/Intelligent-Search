package com.pixel.intelligentsearch.feature.widget

import android.app.SearchManager
import android.content.Context
import android.content.Intent
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
    fun aPreviewShowsEachDoodleInTurnThenEnds() {
        today("2026-11-01")
        var redraws = 0
        val shown = generateSequence { WidgetDoodles.previewNext(context, prefs) { redraws++ } }.toList()

        assertEquals(Occasion.entries, shown)
        assertEquals(Occasion.entries.size + 1, redraws) // one more for ending the preview
        assertNull(WidgetDoodles.previewing(prefs))
        assertNull(WidgetDoodles.current(prefs))
    }

    @Test
    fun aPreviewWinsOverTodaysDoodle() {
        today("2026-10-31")
        WidgetDoodles.previewNext(context, prefs) {}
        assertEquals(Occasion.entries.first(), WidgetDoodles.current(prefs))
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

    @Test
    fun tappingADoodleSearchesForTheDayInTheSearchOverlay() {
        prefs.edit().put(SettingsManager.SEARCH_OVERLAY_ENABLED, true).commit()
        val intent = SearchWidgetProvider.doodleSearchIntent(context, prefs, appWidgetId = 7, doodle = Occasion.HALLOWEEN)

        assertEquals(WidgetActivity::class.java.name, intent.component?.className)
        assertEquals("Halloween", intent.getStringExtra(SearchManager.QUERY))
        // A different intent from the search bar's own, so neither tap replaces the other.
        val bar = SearchWidgetProvider.mainSearchIntent(context, prefs, appWidgetId = 7)
        assertNull(bar.getStringExtra(SearchManager.QUERY))
        assertEquals(false, bar.filterEquals(intent))
    }

    @Test
    fun withTheOverlayOffADoodleSearchesTheWeb() {
        prefs.edit().put(SettingsManager.SEARCH_OVERLAY_ENABLED, false).commit()
        val intent = SearchWidgetProvider.doodleSearchIntent(context, prefs, appWidgetId = 7, doodle = Occasion.FIRST_DAY_OF_SPRING)

        assertEquals(Intent.ACTION_WEB_SEARCH, intent.action)
        assertEquals("First Day of Spring", intent.getStringExtra(SearchManager.QUERY))
    }
}
