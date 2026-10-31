package com.pixel.intelligentsearch.core.answers

import com.pixel.intelligentsearch.core.util.Durations
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.ZoneId

/** One app's share of today's screen time. */
@Immutable
data class ScreenTimeApp(val packageName: String, val label: String, val durationMs: Long)

/** Today's screen time so far: the total, and the apps that took the most of it. */
@Immutable
data class ScreenTimeReport(val totalMs: Long, val topApps: List<ScreenTimeApp>)

/**
 * Works out screen time from usage events the way Digital Wellbeing counts it: the time each app's activities were
 * in the foreground since midnight. An app already open at midnight counts from midnight; one still open counts up
 * to now; the screen turning off ends whatever was showing.
 */
object ScreenTimeCalculator {
    /** The usage event kinds counted (UsageEvents.Event constants, so this stays testable off the phone). */
    const val RESUMED = 1
    const val PAUSED = 2
    const val STOPPED = 23
    const val SCREEN_NON_INTERACTIVE = 16
    const val DEVICE_SHUTDOWN = 26

    class Event(val packageName: String, val type: Int, val timeMs: Long, val className: String? = null)

    /**
     * Foreground time per package between [startMs] and [endMs], leaving out [excluded] packages (the home screen).
     * [eventsFromMs] is when [events] begin: read from before [startMs], an app open at the start shows its resume,
     * so a pause with none before it (an app handing over to its next screen) is nothing, not "open since the start".
     */
    fun perApp(
        events: List<Event>,
        startMs: Long,
        endMs: Long,
        excluded: Set<String> = emptySet(),
        eventsFromMs: Long = startMs
    ): Map<String, Long> {
        val totals = HashMap<String, Long>()
        // Open activities, by package and class: an app with two activities resumed at once is counted once.
        val openSince = HashMap<String, Long>()
        val openByPackage = HashMap<String, MutableSet<String>>()
        val seenAny = HashSet<String>()

        fun close(packageName: String, at: Long) {
            val since = openSince.remove(packageName) ?: return
            if (packageName !in excluded) totals.merge(packageName, (at - since).coerceAtLeast(0L), Long::plus)
        }

        for (event in events.sortedBy { it.timeMs }) {
            val at = event.timeMs.coerceIn(startMs, endMs)
            when (event.type) {
                RESUMED -> {
                    seenAny += event.packageName
                    val activities = openByPackage.getOrPut(event.packageName) { mutableSetOf() }
                    activities += event.className.orEmpty()
                    openSince.putIfAbsent(event.packageName, at)
                }
                PAUSED, STOPPED -> {
                    val activities = openByPackage[event.packageName]
                    if (event.packageName !in seenAny) {
                        // Open since before the events begin: count from the window's start. Events from before the
                        // window would have shown that resume, so then it wasn't open (Chrome's first screen pauses
                        // before its next resumes: counted from midnight, that was 20 hours for 2 seconds).
                        seenAny += event.packageName
                        val openAtStart = eventsFromMs >= startMs
                        if (openAtStart && event.packageName !in excluded) totals.merge(event.packageName, (at - startMs).coerceAtLeast(0L), Long::plus)
                    } else if (activities != null) {
                        activities -= event.className.orEmpty()
                        if (activities.isEmpty()) close(event.packageName, at)
                    }
                }
                SCREEN_NON_INTERACTIVE, DEVICE_SHUTDOWN -> {
                    for (packageName in openSince.keys.toList()) {
                        close(packageName, at)
                        openByPackage[packageName]?.clear()
                    }
                }
            }
        }
        for (packageName in openSince.keys.toList()) close(packageName, endMs)
        return totals.filterValues { it > 0L }
    }

    /** The report for [perApp] durations: their total and the [top] longest, named by [label]. */
    fun report(perApp: Map<String, Long>, top: Int = 5, label: (String) -> String?): ScreenTimeReport {
        val named = perApp.entries.sortedByDescending { it.value }
            .mapNotNull { (pkg, ms) -> label(pkg)?.let { ScreenTimeApp(pkg, it, ms) } }
        return ScreenTimeReport(perApp.values.sum(), named.take(top))
    }

    /** "3 hr 12 min", "45 min", "Under 1 min", in the app's one duration style ([Durations.minutes]). */
    fun formatDuration(ms: Long): String = Durations.minutes(ms)
}

/**
 * Today's screen time from Android's usage statistics, which needs Usage Access (the app asks for it for quick-app
 * suggestions too). Read on the phone only; nothing is sent anywhere. Blocking: call off the main thread.
 */
class ScreenTimeRepository(private val context: Context) {

    /** Whether Usage Access is granted. */
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else mode == AppOpsManager.MODE_ALLOWED
    }

    /** Today's report, or null when Usage Access isn't granted. */
    fun today(nowMs: Long = System.currentTimeMillis()): ScreenTimeReport? {
        if (!hasUsageAccess()) return null
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val midnight = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDate()
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val events = ArrayList<ScreenTimeCalculator.Event>()
        // Read from a day before midnight, so an app open at midnight shows its resume and a stray pause doesn't.
        val eventsFrom = midnight - LOOK_BACK_MS
        val raw = runCatching { usage.queryEvents(eventsFrom, nowMs) }.getOrNull() ?: return null
        val event = UsageEvents.Event()
        while (raw.hasNextEvent()) {
            raw.getNextEvent(event)
            events += ScreenTimeCalculator.Event(event.packageName ?: continue, event.eventType, event.timeStamp, event.className)
        }
        // The home screen isn't screen time to anyone; Digital Wellbeing leaves it out too.
        val excluded = setOfNotNull(homePackage(), "com.android.systemui")
        val perApp = ScreenTimeCalculator.perApp(events, midnight, nowMs, excluded, eventsFromMs = eventsFrom)
        val pm = context.packageManager
        return ScreenTimeCalculator.report(perApp) { pkg ->
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
        }
    }

    private fun homePackage(): String? = runCatching {
        context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.packageName
    }.getOrNull()

    private companion object {
        const val LOOK_BACK_MS = 24 * 60 * 60 * 1000L
    }
}
