package com.pixel.intelligentsearch.feature.widget

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.pixel.intelligentsearch.core.occasion.OccasionCalendar
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Some doodles are kept only in some regions, so changing the phone's region moves the next doodle change. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class LocaleChangeRescheduleTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val defaultNow = OccasionCalendar.now
    private val defaultRegion = OccasionCalendar.region

    @Before fun setUp() {
        hilt.inject()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @After fun restoreClockAndRegion() {
        OccasionCalendar.now = defaultNow
        OccasionCalendar.region = defaultRegion
    }

    @Test
    fun theWidgetHearsTheRegionChange() {
        val receivers = context.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_LOCALE_CHANGED).setPackage(context.packageName), 0
        )
        assertTrue(receivers.any { it.activityInfo.name == SearchWidgetProvider::class.java.name })
    }

    @Test
    fun changingTheRegionMovesTheNextDoodleChange() {
        OccasionCalendar.now = { ZonedDateTime.of(2026, 11, 25, 9, 0, 0, 0, ZoneId.of("America/New_York")) }
        OccasionCalendar.region = { "GB" }
        WidgetDoodles.scheduleNextChange(context) // the first day of winter, December 21

        OccasionCalendar.region = { "US" }
        SearchWidgetProvider().onReceive(context, Intent(Intent.ACTION_LOCALE_CHANGED))

        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork("widget_doodle_change").get().single()
        // Thanksgiving starts at midnight in the US; the redraw waits half a minute past it.
        assertEquals(TimeUnit.HOURS.toMillis(15) + TimeUnit.SECONDS.toMillis(30), work.initialDelayMillis)
    }
}
