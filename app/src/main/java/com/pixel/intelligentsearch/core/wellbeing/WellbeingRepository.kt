package com.pixel.intelligentsearch.core.wellbeing

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import com.pixel.intelligentsearch.core.util.queryIntentActivitiesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Everything the page shows: whole days from the summary and the phone's records, and today so far. */
class WellbeingData(
    val days: Map<LocalDate, DaySummary>,
    val today: LocalDate,
    /** Minutes since today's midnight. */
    val nowMin: Double,
    val labels: Map<String, String>,
    /** Whether this load read the phone's records from scratch (the first open). */
    val firstRead: Boolean
)

/**
 * Reads the phone's usage records (Usage Access) on the phone, keeps the page's daily summary up to date, and hands the
 * page its days. Everything runs off the main thread. The phone's records are read once per process (about 44,000
 * events for 10 days, 1.4 s on a Pixel 11, when days need working out again) and only the new ones after that; whole
 * days are kept in [HistoryStore], each checked against the phone's own totals before it is kept, so Week and Month
 * fill in over time. A day worked out by older rules ([DayCalculator.RULES]) is worked out again while the phone still
 * has its records. The page reads when it is opened; [WellbeingCatchUp] reads about once a day when it
 * isn't, so no day is lost to the phone letting its own records go.
 *
 * The phone keeps its records in files a day long and lets the oldest go after about 10 days. A Pixel 11 starts each
 * file 3 seconds before midnight; Android itself (the emulator, and phones that follow it) starts them at whatever
 * time of day it first began keeping records, 6:43 PM say. So the oldest day the records reach can hold only part of
 * that day: a day is whole only when the records begin by its midnight.
 */
class WellbeingRepository(context: Context) {
    private val app = context.applicationContext
    private val store = HistoryStore(File(app.filesDir, HistoryStore.FILE_NAME))

    fun hasAccess(): Boolean = hasAccess(app)

    /**
     * Brings the summary up to date and returns every day. [onProgress] hears (days read, days to read) while the phone's
     * records are read for the first time, which takes about a second. Null without Usage Access.
     */
    suspend fun load(nowMs: Long = System.currentTimeMillis(), onProgress: (Int, Int) -> Unit = { _, _ -> }): WellbeingData? =
        withContext(Dispatchers.IO) {
            if (!hasAccess()) return@withContext null
            lock.withLock { loadLocked(nowMs, onProgress) }
        }

    /**
     * Deletes the daily summary. Days the phone itself still keeps come back on the next load: the records read so far
     * are let go too, so that load reads everything the phone keeps again (READ_BACK_DAYS), not only what is new. (It
     * kept only today and yesterday's records in memory, so the days before them were lost for good.)
     */
    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        lock.withLock {
            store.clear()
            stored = null
            cache = null
        }
    }

    private fun loadLocked(nowMs: Long, onProgress: (Int, Int) -> Unit, retry: Boolean = true): WellbeingData? {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val saved = stored ?: store.read().also { stored = it }
        // Only days before today can be whole. After a time zone or date change the summary can hold a "whole" day
        // that is today or later here: it is worked out again from the records instead of kept frozen.
        val days = HashMap(saved.days.filterKeys { it.isBefore(today) })
        val keptBefore = days.keys.toSet()
        val labels = HashMap(saved.labels)

        val usage = app.getSystemService(UsageStatsManager::class.java) ?: return null
        val firstRead = days.isEmpty()
        val readBackFrom = today.minusDays(READ_BACK_DAYS)
        val known = cache
        val c: EventCache
        val fresh = known == null
        // The phone's own day-long files: where each read has to begin, and the phone's own totals to check whole days
        // against. After the first read only the last two days' are needed.
        val filesFrom = if (fresh) readBackFrom.minusDays(1) else today.minusDays(2)
        val files = phoneFiles(usage, filesFrom.atStartOfDay(zone).toInstant().toEpochMilli(), nowMs)
        if (known == null) {
            // Read the phone's records a day at a time, so the page can show its progress: from the first day the phone
            // still holds whole that the summary lacks, holds in part, or worked out by older rules (and the day before
            // it, for its night); with nothing to redo, from yesterday.
            val recordsFrom = files.firstOrNull()?.begin
            val firstWhole = recordsFrom?.let { firstWholeDay(it, zone) } ?: readBackFrom
            val redo = generateSequence(maxOf(readBackFrom, firstWhole)) { it.plusDays(1) }.takeWhile { it.isBefore(today) }
                .firstOrNull { d -> days[d].let { it == null || !it.complete || it.rules < DayCalculator.RULES } }
            val from = maxOf(readBackFrom, (redo ?: today).minusDays(1))
            val chunks = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
            val events = ArrayList<UsageEvent>()
            chunks.forEachIndexed { i, day ->
                val a = day.atStartOfDay(zone).toInstant().toEpochMilli()
                val b = if (day == today) nowMs else day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                read(usage, readBegin(files, a), a, b, events)
                onProgress(i + 1, chunks.size)
            }
            val readFrom = from.atStartOfDay(zone).toInstant().toEpochMilli()
            // When the phone's records begin: the start of its oldest file of days, or failing that its oldest event.
            val begin = recordsFrom ?: events.minOfOrNull { it.timeMs } ?: nowMs
            c = EventCache(events, readFrom, nowMs, begin)
            cache = c
            // A summary saved by version 1 took the oldest day the records reached as a whole day. Where the phone's
            // files don't start at midnight that day held only part of one: the first day of each run of saved days is
            // let go (the days after it were read whole). Where they do start at midnight the days were whole, except a
            // first day made of the 3 seconds before one: a first day with under a minute on screen goes too.
            if (saved.version < 2) {
                val whole = atMidnight(begin, zone)
                days.keys.filter { !days.containsKey(it.minusDays(1)) && (!whole || days.getValue(it).totalSec < 60) }.forEach { days.remove(it) }
            }
        } else {
            c = known
            // Read again from a little before the last read: an event logged late with an earlier time isn't missed.
            val from = c.readTo - OVERLAP_MS
            c.events.removeAll { it.timeMs >= from }
            read(usage, readBegin(files, from), from, nowMs, c.events)
            c.readTo = nowMs
        }
        val events = c.events
        dedupe(events)

        // From when the records are whole: where this process began reading them, or, where the phone has already let
        // older days go, the moment its own records begin (partway through a day: that day is not a whole one).
        val coverFrom = maxOf(c.readFrom, c.recordsFrom)
        val counts = countedPackages(app)
        var d = Instant.ofEpochMilli(coverFrom).atZone(zone).toLocalDate()
        var redone = false
        var checked = 0
        val short = ArrayList<LocalDate>()
        while (!d.isAfter(today)) {
            val have = days[d]
            if (have == null || !have.complete || have.rules < DayCalculator.RULES || d == today) {
                val starts = hourStarts(d, zone)
                val from = maxOf(starts[0] - 24 * 3_600_000L, coverFrom)
                // From a day before, to now: an app still open at midnight is checked against what came after it.
                val slice = events.subList(lowerBound(events, from), events.size)
                if (slice.isNotEmpty()) {
                    val dayStart = starts[0]
                    val epochDay = d.toEpochDay()
                    val summary = DayCalculator.compute(d, slice, starts, nowMs, from, counts) { t ->
                        // The time on the clock, as minutes from this day's midnight: right on a day the clocks change too.
                        if (t == dayStart) 0 else Instant.ofEpochMilli(t).atZone(zone).let {
                            ((it.toLocalDate().toEpochDay() - epochDay) * 1440 + it.toLocalTime().toSecondOfDay() / 60).toInt()
                        }
                    }
                    // A past day is kept only whole: one the records cover in part would read as a light day. Before it
                    // is kept, it is checked against the phone's own totals for it: well short of them, some of its
                    // records weren't read. Such a day is shown but not taken as whole, so it is never kept in the
                    // summary (a whole day kept before stays as it was), and every load works it out again; the
                    // records are read from the phone again too.
                    var keep = summary
                    if (summary.complete && d.isBefore(today)) {
                        val off = shortOfPhone(summary, files, counts, zone)
                        if (off != null) checked++
                        if (off == true) {
                            short += d
                            keep = summary.notWhole()
                        }
                    }
                    if (summary.complete || d == today) {
                        if (keep.complete || d == today || have == null || !have.complete) {
                            if (have != null && d != today) redone = true
                            days[d] = keep
                        }
                    }
                }
            }
            d = d.plusDays(1)
        }
        if (short.isNotEmpty() && !fresh && retry && !shortAfterRead.containsAll(short)) {
            // What was kept from earlier reads has a hole: read again from the phone, from the day before the short one.
            // A day still short after a whole read of the phone's records isn't read again in this process (the page
            // loads every minute); it stays not whole, and a later process reads it again.
            cache = null
            return loadLocked(nowMs, onProgress, retry = false)
        }
        if (fresh) shortAfterRead += short
        if (fresh) {
            // Nothing about the person: how much was read, from when, whether the phone gave each screen's id, and how
            // many whole days matched the phone's own totals.
            android.util.Log.i(
                "Wellbeing",
                "Read ${events.size} events; records begin ${Instant.ofEpochMilli(c.recordsFrom)}; screen ids ${instanceId != null}; " +
                    "checked $checked days, ${short.size} short"
            )
        }

        // Names for every app shown; an app removed since keeps the name it had.
        val pm = app.packageManager
        for (day in days.values) for (key in day.apps.keys + listOfNotNull(day.firstApp)) {
            if (key in labels) continue
            labels[key] = labelOf(pm, key) ?: continue
        }
        // Gemini's activities inside the Google app are Gemini even when the Gemini app isn't installed.
        labels.putIfAbsent(Attribution.GEMINI, "Gemini")

        // Saved only when there is something new to save (a day that ended or was worked out again, a new app's name, a
        // repaired file): the page reads the records every minute while it is open.
        val whole = days.filterValues { it.complete && it.date.isBefore(today) }
        if (saved.changed || saved.version < HistoryStore.VERSION || redone || whole.keys != keptBefore || labels != saved.labels) {
            // A save that fails (no space left) costs nothing shown now; the next load tries again.
            runCatching { store.write(days.values, labels, today) }
        }
        stored = HistoryStore.Contents(whole, labels)

        // Keep only what today and yesterday still need in memory.
        val keepFrom = todayStart - 2 * 24 * 3_600_000L
        if (c.readFrom < keepFrom) {
            c.events.removeAll { it.timeMs < keepFrom }
            c.readFrom = keepFrom
        }
        val nowMin = (nowMs - todayStart) / 60_000.0
        return WellbeingData(days, today, nowMin, labels, firstRead)
    }

    /**
     * Whether [day] adds up to well under the phone's own totals for it (by more than 10 minutes or 5%): some of its
     * records weren't read. Each app's own time is what the phone totals, so split screen counts in both. Null where the
     * phone's files don't line up with the day (they start partway through days on some phones): it can't be told.
     */
    private fun shortOfPhone(day: DaySummary, files: List<PhoneFile>, counts: (String) -> Boolean, zone: ZoneId): Boolean? {
        val start = day.date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val its = files.filter { minOf(it.end, end) - maxOf(it.begin, start) > ALIGN_MS }
        if (its.isEmpty() || its.any { it.begin < start - ALIGN_MS || it.end > end + ALIGN_MS }) return null
        val phoneSec = its.sumOf { f -> f.visibleMs.entries.sumOf { (pkg, ms) -> if (counts(pkg)) ms else 0L } } / 1000
        val ours = day.apps.values.sumOf { it.totalSec.toLong() }
        return ours < phoneSec - maxOf(600L, phoneSec / 20)
    }

    /** [recordsFrom]: when the phone's own records begin, as found at the first read. */
    private class EventCache(val events: ArrayList<UsageEvent>, var readFrom: Long, var readTo: Long, val recordsFrom: Long)

    /** One of the phone's day-long files: when it begins, its last record, and its apps' time on screen. */
    internal class PhoneFile(val begin: Long, val end: Long, val visibleMs: Map<String, Long>)

    companion object {
        /** Whether Usage Access is granted: Wellbeing and the screen-time answers both need it. */
        fun hasAccess(context: Context): Boolean {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
            val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            return if (mode == AppOpsManager.MODE_DEFAULT) {
                context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
            } else mode == AppOpsManager.MODE_ALLOWED
        }

        /**
         * Which packages count, here and in the screen-time answers. Like Digital Wellbeing, only apps with an icon in
         * the app drawer: not the home screen, the system UI or the charging screen saver (an hour a day on a Pixel
         * under a system app with no icon). An app list that can't be read leaves out only the home screen and system UI.
         */
        fun countedPackages(context: Context): (String) -> Boolean {
            val pm = context.packageManager
            val home = runCatching { pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName }.getOrNull()
            val excluded = setOfNotNull(home, "com.android.systemui")
            val withIcons = runCatching {
                pm.queryIntentActivitiesCompat(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
                    .mapTo(HashSet()) { it.activityInfo.packageName }
            }.getOrDefault(emptySet())
            return if (withIcons.isEmpty()) { pkg -> pkg !in excluded } else { pkg -> pkg in withIcons && pkg !in excluded }
        }

        /**
         * The phone's usage events from [from] to [to], as Wellbeing reads them: from the oldest of the phone's day-long
         * files that still holds records after [from] (a read starting exactly at [from] can miss a whole file), each
         * event once. Screen time answers read today this way too, so both count the same day the same way.
         */
        fun readEvents(usage: UsageStatsManager, from: Long, to: Long): List<UsageEvent> {
            val files = phoneFiles(usage, from - DAY_MS, to)
            val out = ArrayList<UsageEvent>()
            read(usage, readBegin(files, from), from, to, out)
            dedupe(out)
            return out
        }

        /**
         * The events Wellbeing uses from [keepFrom] to [to], added to [out], read from the phone from [begin] (at or before
         * [keepFrom], see [readBegin]).
         */
        internal fun read(usage: UsageStatsManager, begin: Long, keepFrom: Long, to: Long, out: MutableList<UsageEvent>) {
            val raw = runCatching { usage.queryEvents(begin, to) }.getOrNull() ?: return
            val e = UsageEvents.Event()
            while (raw.hasNextEvent()) {
                raw.getNextEvent(e)
                if (e.timeStamp < keepFrom) continue
                when (e.eventType) {
                    EventKind.RESUMED, EventKind.PAUSED, EventKind.STOPPED, EventKind.ACTIVITY_DESTROYED ->
                        e.packageName?.let { out += UsageEvent(it, e.eventType, e.timeStamp, e.className, instanceOf(e)) }
                    EventKind.NOTIFICATION, EventKind.SCREEN_ON, EventKind.SCREEN_OFF, EventKind.KEYGUARD_HIDDEN, EventKind.KEYGUARD_SHOWN,
                    EventKind.SHUTDOWN, EventKind.STARTUP ->
                        e.packageName?.let { out += UsageEvent(it, e.eventType, e.timeStamp, e.className) }
                }
            }
        }

        /**
         * The phone's day-long files of records between [from] and [to], oldest first: each one's start, its last record,
         * and its apps' own time on screen (milliseconds by package).
         */
        internal fun phoneFiles(usage: UsageStatsManager, from: Long, to: Long): List<PhoneFile> =
            runCatching { usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, from, to) }.getOrNull().orEmpty()
                .filter { it.firstTimeStamp > 0 }
                .groupBy { it.firstTimeStamp }
                .map { (begin, list) ->
                    val visible = HashMap<String, Long>()
                    list.forEach { visible.merge(it.packageName, it.totalTimeVisible, Long::plus) }
                    PhoneFile(begin, list.maxOf { it.lastTimeStamp }, visible)
                }
                .sortedBy { it.begin }

        /** Ten days back plus the day before them: the phone keeps about 10 days. */
        private const val READ_BACK_DAYS = 11L
        private const val OVERLAP_MS = 5_000L
        private const val DAY_MS = 24 * 3_600_000L
        /** How far a file may start before a day, or end after it, and still be that day's (a Pixel's start 3 s before). */
        private const val ALIGN_MS = 10 * 60_000L

        /**
         * Where a read of the records from [t] has to begin. The phone answers a read from its newest file that starts
         * at or before the read's start, and nothing older. Its files usually follow one another, but one can start
         * later than an older one still runs: on a Pixel 11 a file dated midnight was opened at 10:39 AM, while the one
         * dated 3 seconds before midnight held everything up to then, and a read from midnight got only the newer file.
         * So a read begins at the oldest file that still holds records after [t]; where that is the newest one anyway
         * (the usual case), at [t] itself; and with no files to go by, a day before.
         */
        private fun readBegin(files: List<PhoneFile>, t: Long): Long {
            if (files.isEmpty()) return t - DAY_MS
            val newest = files.filter { it.begin <= t }.maxOfOrNull { it.begin } ?: return t
            val oldest = files.filter { it.begin <= t && it.end > t }.minOfOrNull { it.begin } ?: return t
            return if (oldest < newest) oldest else t
        }

        /** The first day the phone's records, beginning at [recordsFrom], hold from its midnight (within 5 minutes). */
        private fun firstWholeDay(recordsFrom: Long, zone: ZoneId): LocalDate {
            val d = Instant.ofEpochMilli(recordsFrom - 5 * 60_000L).atZone(zone).toLocalDate()
            return if (d.atStartOfDay(zone).toInstant().toEpochMilli() + 5 * 60_000L >= recordsFrom) d else d.plusDays(1)
        }

        /**
         * Sorts [events] by time and drops exact repeats (the same moment, kind, app, screen class and screen): a read
         * across two of the phone's files could return an event twice, and its own files hold a few twice too.
         */
        private fun dedupe(events: ArrayList<UsageEvent>) {
            events.sortBy { it.timeMs }
            var w = 0
            for (r in events.indices) {
                val e = events[r]
                var dup = false
                var k = w - 1
                while (k >= 0 && events[k].timeMs == e.timeMs) {
                    val o = events[k]
                    if (o.type == e.type && o.packageName == e.packageName && o.className == e.className && o.instance == e.instance) { dup = true; break }
                    k--
                }
                if (!dup) events[w++] = e
            }
            if (w < events.size) events.subList(w, events.size).clear()
        }

        // One copy per process: the page opens and closes many times; the records are read once.
        @Volatile private var cache: EventCache? = null
        @Volatile private var stored: HistoryStore.Contents? = null
        /** Past days found short of the phone's own totals right after a whole read of its records (under [lock]). */
        private val shortAfterRead = HashSet<LocalDate>()
        private val lock = Mutex()

        /**
         * The phone's own id for the screen an event is about. Android logs one with every activity event, and reads it
         * out through a method that isn't in the public SDK: where it can't be reached the id is 0, and the day is
         * worked out from class names instead ([DayCalculator]).
         */
        private val instanceId: java.lang.reflect.Method? =
            runCatching { UsageEvents.Event::class.java.getMethod("getInstanceId") }.getOrNull()

        private fun instanceOf(e: UsageEvents.Event): Int =
            instanceId?.let { m -> runCatching { m.invoke(e) as? Int }.getOrNull() } ?: 0

        fun labelOf(pm: PackageManager, key: String): String? =
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(key, 0)).toString() }.getOrNull()
                ?: AiApps.assistants[key] ?: AiApps.companions[key]

        /** The 25 boundaries of [date]'s hours in [zone] (a day with a clock change has a long or short hour). */
        fun hourStarts(date: LocalDate, zone: ZoneId): LongArray = LongArray(25) { h ->
            if (h == 24) date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            else date.atTime(LocalTime.of(h, 0)).atZone(zone).toInstant().toEpochMilli()
        }

        /** Whether [t] is within 5 minutes of a midnight in [zone]: where a phone that keeps whole days starts its files. */
        private fun atMidnight(t: Long, zone: ZoneId): Boolean =
            Instant.ofEpochMilli(t).atZone(zone).toLocalTime().toSecondOfDay().let { it <= 300 || it >= 86_400 - 300 }

        private fun lowerBound(events: List<UsageEvent>, t: Long): Int {
            var lo = 0
            var hi = events.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (events[mid].timeMs < t) lo = mid + 1 else hi = mid
            }
            return lo
        }
    }
}
