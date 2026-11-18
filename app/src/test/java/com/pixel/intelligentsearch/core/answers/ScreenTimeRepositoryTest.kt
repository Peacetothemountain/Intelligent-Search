package com.pixel.intelligentsearch.core.answers

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

    /** Gives [packageName] an icon in the app drawer, as the apps people use have. */
    private fun withIcon(packageName: String) {
        val main = ComponentName(packageName, "$packageName.Main")
        val packages = shadowOf(context.packageManager)
        packages.addActivityIfNotPresent(main)
        packages.addIntentFilterForActivity(main, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
    }

    @Test
    fun anAppsFirstScreenPausingBeforeItsNextResumesIsSecondsNotTheWholeDay() {
        withIcon("com.android.chrome")
        // Chrome's first screen hands over to its next at 8:15 PM, its pause logged before the next one's resume.
        event("com.android.chrome", UsageEvents.Event.ACTIVITY_PAUSED, at(20, 15, 49), "FirstRun")
        event("com.android.chrome", UsageEvents.Event.ACTIVITY_RESUMED, at(20, 15, 49), "Main")
        event("com.android.chrome", UsageEvents.Event.ACTIVITY_PAUSED, at(20, 15, 51), "Main")
        event("com.android.chrome", UsageEvents.Event.ACTIVITY_STOPPED, at(20, 15, 51), "Main")

        val report = ScreenTimeRepository(context).today(nowMs = at(20, 30))!!
        // Counted as open since midnight, this was "Chrome 20 hr 15 min".
        assertEquals(2 * second, report.totalMs)
    }

    @Test
    fun anAppOpenSinceBeforeMidnightCountsFromMidnight() {
        withIcon("com.example.reader")
        event("com.example.reader", UsageEvents.Event.ACTIVITY_RESUMED, at(23, 40, day = today.minusDays(1)), "Book")
        event("com.example.reader", UsageEvents.Event.ACTIVITY_PAUSED, at(0, 15), "Book")
        event("com.example.reader", UsageEvents.Event.ACTIVITY_STOPPED, at(0, 15), "Book")

        val report = ScreenTimeRepository(context).today(nowMs = at(9, 0))!!
        assertEquals(15 * minute, report.totalMs)
    }

    @Test
    fun theChargingScreenSaverIsNotScreenTime() {
        // On a Pixel on its charger the screen saver runs as a system app with no icon; Digital Wellbeing leaves it
        // out, and counting it made the answer an hour longer than Digital Wellbeing's.
        withIcon("com.example.video")
        event("com.example.video", UsageEvents.Event.ACTIVITY_RESUMED, at(8, 0), "Player")
        event("com.example.video", UsageEvents.Event.ACTIVITY_PAUSED, at(8, 20), "Player")
        event("com.example.video", UsageEvents.Event.ACTIVITY_STOPPED, at(8, 20), "Player")
        event("com.google.assistant.hubui", UsageEvents.Event.ACTIVITY_RESUMED, at(8, 20), "android.service.dreams.DreamActivity")
        event("com.google.assistant.hubui", UsageEvents.Event.ACTIVITY_PAUSED, at(9, 26), "android.service.dreams.DreamActivity")

        val report = ScreenTimeRepository(context).today(nowMs = at(10, 0))!!
        assertEquals(20 * minute, report.totalMs)
    }
}
