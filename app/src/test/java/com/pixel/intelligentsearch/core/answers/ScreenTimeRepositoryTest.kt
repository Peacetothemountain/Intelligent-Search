package com.pixel.intelligentsearch.core.answers

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowUsageStatsManager.EventBuilder
import java.time.LocalDate
import java.time.ZoneId

/** Today's screen time read from the phone's usage events, as the "screen time" answer shows it. */
@RunWith(RobolectricTestRunner::class)
class ScreenTimeRepositoryTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val usage get() = context.getSystemService(UsageStatsManager::class.java)
    private val today = LocalDate.of(2026, 10, 29)
    private val second = 1_000L
    private val minute = 60 * second

    @Before fun grantUsageAccess() {
        shadowOf(context.getSystemService(AppOpsManager::class.java))
            .setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, AppOpsManager.MODE_ALLOWED)
    }

    private fun at(hour: Int, minute: Int, second: Int = 0, day: LocalDate = today) =
        day.atTime(hour, minute, second).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun event(packageName: String, type: Int, time: Long, className: String) {
        shadowOf(usage).addEvent(
            EventBuilder.buildEvent().setPackage(packageName).setClass(className).setEventType(type).setTimeStamp(time).build()
        )
    }

    @Test
    fun anAppsFirstScreenPausingBeforeItsNextResumesIsSecondsNotTheWholeDay() {
        // Chrome's first screen hands over to its next at 8:15 PM, its pause logged before the next one's resume.
        event("com.android.chrome", UsageEvents.Event.ACTIVITY_PAUSED, at(20, 15, 49), "FirstRun")
        event("com.android.chrome", UsageEvents.Event.ACTIVITY_RESUMED, at(20, 15, 49), "Main")
        event("com.android.chrome", UsageEvents.Event.ACTIVITY_PAUSED, at(20, 15, 51), "Main")

        val report = ScreenTimeRepository(context).today(nowMs = at(20, 30))!!
        // Counted as open since midnight, this was "Chrome 20 hr 15 min".
        assertEquals(2 * second, report.totalMs)
    }

    @Test
    fun anAppOpenSinceBeforeMidnightCountsFromMidnight() {
        event("com.example.reader", UsageEvents.Event.ACTIVITY_RESUMED, at(23, 40, day = today.minusDays(1)), "Book")
        event("com.example.reader", UsageEvents.Event.ACTIVITY_PAUSED, at(0, 15), "Book")

        val report = ScreenTimeRepository(context).today(nowMs = at(9, 0))!!
        assertEquals(15 * minute, report.totalMs)
    }
}
