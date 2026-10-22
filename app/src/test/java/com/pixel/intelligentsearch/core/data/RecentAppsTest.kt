package com.pixel.intelligentsearch.core.data

import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowUsageStatsManager.UsageStatsBuilder

/** The recent apps the overlay offers before anything is typed. */
@RunWith(RobolectricTestRunner::class)
class RecentAppsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun installLaunchable(packageName: String) {
        val component = ComponentName(packageName, "$packageName.Main")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(component)
            addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        }
    }

    @Test
    fun appsUsedBothTodayAndYesterdayTakeOneSlotEach() = runBlocking {
        val hour = 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        val usage = shadowOf(context.getSystemService(UsageStatsManager::class.java))
        fun bucket(packageName: String, start: Long, end: Long, lastUsed: Long) = usage.addUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            UsageStatsBuilder.newBuilder()
                .setPackageName(packageName)
                .setFirstTimeStamp(start)
                .setLastTimeStamp(end)
                .setLastTimeUsed(lastUsed)
                .setTotalTimeInForeground(60_000L)
                .build()
        )

        // The last 24 hours span two daily buckets: five apps were used in both, three only yesterday.
        val everyDay = (1..5).map { "org.example.daily$it" }
        val yesterdayOnly = (1..3).map { "org.example.once$it" }
        (everyDay + yesterdayOnly).forEach(::installLaunchable)
        everyDay.forEachIndexed { i, pkg ->
            bucket(pkg, now - 6 * hour, now, lastUsed = now - (i + 1) * 60_000L)
            bucket(pkg, now - 30 * hour, now - 6 * hour, lastUsed = now - 7 * hour - i * 60_000L)
        }
        yesterdayOnly.forEachIndexed { i, pkg ->
            bucket(pkg, now - 30 * hour, now - 6 * hour, lastUsed = now - 10 * hour - i * 60_000L)
        }

        val recents = SystemDataProvider.getRecentApps(context).map { it.packageName }
        assertEquals(everyDay + yesterdayOnly, recents)
    }
}
