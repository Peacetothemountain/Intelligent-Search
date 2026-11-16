package com.pixel.intelligentsearch.core.answers

import com.pixel.intelligentsearch.core.util.Durations
import com.pixel.intelligentsearch.core.util.queryIntentActivitiesCompat
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
 * to now; the screen turning off ends whatever was showing. The total is the time any counted app was on screen, so
 * two apps side by side count that time once, though each app's own time includes it.
 */
object ScreenTimeCalculator {
    /** The usage event kinds counted (UsageEvents.Event constants, so this stays testable off the phone). */
    const val RESUMED = 1
    const val PAUSED = 2
    const val STOPPED = 23
    const val SCREEN_NON_INTERACTIVE = 16
    const val DEVICE_SHUTDOWN = 26

    class Event(val packageName: String, val type: Int, val timeMs: Long, val className: String? = null)

    /** Each counted app's foreground time, and the time any of them was on screen. */
    class Tally(val perApp: Map<String, Long>, val onScreenMs: Long)

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
    ): Map<String, Long> = tally(events, startMs, endMs, { it !in excluded }, eventsFromMs).perApp

    /** [perApp] for the packages [counts] accepts, with the time any of them was on screen. */
    fun tally(
        events: List<Event>,
        startMs: Long,
        endMs: Long,
        counts: (String) -> Boolean,
        eventsFromMs: Long = startMs
    ): Tally {
        val totals = HashMap<String, Long>()
        // When each counted app was in front, to find the time any of them was.
        val spans = ArrayList<LongArray>()
        // Open activities, by package and class: an app with two activities resumed at once is counted once.
        val openSince = HashMap<String, Long>()
        val openByPackage = HashMap<String, MutableSet<String>>()
        val seenAny = HashSet<String>()
        val counted = HashMap<String, Boolean>()
        fun isCounted(packageName: String) = counted.getOrPut(packageName) { counts(packageName) }

        fun count(packageName: String, from: Long, to: Long) {
            if (to <= from || !isCounted(packageName)) return
            totals.merge(packageName, to - from, Long::plus)
            spans += longArrayOf(from, to)
        }

        fun close(packageName: String, at: Long) {
            val since = openSince.remove(packageName) ?: return
            count(packageName, since, at)
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
                        if (openAtStart) count(event.packageName, startMs, at)
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
        return Tally(totals.filterValues { it > 0L }, mergedLength(spans))
    }

    /** How long [spans] cover, counting a stretch they share once. */
    private fun mergedLength(spans: MutableList<LongArray>): Long {
        spans.sortBy { it[0] }
        var total = 0L
        var from = Long.MIN_VALUE
        var to = Long.MIN_VALUE
        for ((start, end) in spans) {
            if (start > to) {
                if (to > from) total += to - from
                from = start
                to = end
            } else if (end > to) {
                to = end
            }
        }
        if (to > from) total += to - from
        return total
    }

    /**
     * The report for [perApp] durations: [totalMs] (by default their sum; [Tally.onScreenMs] counts time two apps
     * shared once) and the [top] longest, named by [label].
     */
    fun report(perApp: Map<String, Long>, top: Int = 5, totalMs: Long = perApp.values.sum(), label: (String) -> String?): ScreenTimeReport {
        val named = perApp.entries.sortedByDescending { it.value }
            .mapNotNull { (pkg, ms) -> label(pkg)?.let { ScreenTimeApp(pkg, it, ms) } }
        return ScreenTimeReport(totalMs, named.take(top))
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
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
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
        // Like Digital Wellbeing, only apps with an icon in the app drawer count: not the home screen, the system UI,
        // or the screen saver on a charging Pixel (an hour a day logged under a system app with no icon).
        val excluded = setOfNotNull(homePackage(), "com.android.systemui")
        val withIcons = appsWithIcons()
        // An app list that couldn't be read leaves only the home screen and system UI out, as before.
        val counts: (String) -> Boolean = if (withIcons.isEmpty()) { pkg -> pkg !in excluded } else { pkg -> pkg in withIcons && pkg !in excluded }
        val tally = ScreenTimeCalculator.tally(events, midnight, nowMs, counts, eventsFromMs = eventsFrom)
        val pm = context.packageManager
        return ScreenTimeCalculator.report(tally.perApp, totalMs = tally.onScreenMs) { pkg ->
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
        }
    }

    private fun homePackage(): String? = runCatching {
        context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.packageName
    }.getOrNull()

    private fun appsWithIcons(): Set<String> = runCatching {
        context.packageManager.queryIntentActivitiesCompat(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
            .mapTo(HashSet()) { it.activityInfo.packageName }
    }.getOrDefault(emptySet())

    private companion object {
        const val LOOK_BACK_MS = 24 * 60 * 60 * 1000L
    }
}
