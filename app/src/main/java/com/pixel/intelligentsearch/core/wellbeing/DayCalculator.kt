package com.pixel.intelligentsearch.core.wellbeing

import kotlin.math.max
import kotlin.math.min

/**
 * One usage event, as Android's usage statistics log it (UsageEvents.Event), kept as plain values so it tests off the
 * phone. [instance] is the phone's own id for the screen (activity) the event is about, or 0 when it didn't give one.
 */
class UsageEvent(val packageName: String, val type: Int, val timeMs: Long, val className: String? = null, val instance: Int = 0)

/** The usage event kinds Wellbeing reads (UsageEvents.Event constants; 12 is not in the public SDK, so it is a number). */
object EventKind {
    const val RESUMED = 1
    const val PAUSED = 2
    const val NOTIFICATION = 12
    const val SCREEN_ON = 15
    const val SCREEN_OFF = 16
    const val KEYGUARD_SHOWN = 17
    const val KEYGUARD_HIDDEN = 18
    const val STOPPED = 23
    const val ACTIVITY_DESTROYED = 24
    const val SHUTDOWN = 26
    const val STARTUP = 27
}

/** A stretch of time one app (by [key], see [Attribution]) was on screen. */
class Span(val key: String, val from: Long, val to: Long) {
    val length: Long get() = to - from
}

/**
 * Which app a moment of screen time belongs to. Gemini's overlay and its full screen run inside the Google app (its
 * activities live under `...search.assistant...`, e.g. the robin FloatyActivity), so by package Gemini would show almost
 * nothing: those activities are counted as Gemini, the rest of the Google app as the Google app.
 */
object Attribution {
    const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
    const val GEMINI = "com.google.android.apps.bard"

    fun isGeminiActivity(packageName: String, className: String?): Boolean =
        packageName == GOOGLE_APP && className != null &&
            (className.contains(".assistant.") || className.contains(".robin.") || className.contains("bard", ignoreCase = true))

    fun keyOf(event: UsageEvent): String =
        if (isGeminiActivity(event.packageName, event.className)) GEMINI else event.packageName

    /** Whether time under [key] counts, given which packages count: Gemini's counts when either Gemini or the Google app does. */
    fun counts(key: String, packageCounts: (String) -> Boolean): Boolean =
        if (key == GEMINI) packageCounts(GEMINI) || packageCounts(GOOGLE_APP) else packageCounts(key)
}

/**
 * One app's screens (activities) that are still showing. With the phone's own id for each screen it is exact: an app
 * often opens a second screen of the same class over the first (a Settings page from a Settings page, a post from a
 * post), and the first one stopping a moment later must not end the app's time while the second is on screen.
 *
 * Without ids, screens are told apart by class name, so that case ends the app's time early, until its next screen
 * comes to the front. It can't be done better from the events alone: on a Pixel 11 a fifth of all stops arrive with no
 * pause logged before them, so a stop can't be matched to the screen it belongs to, and guessing kept apps "open" for
 * an hour or more. Too little is the safe side.
 */
private class Showing {
    private val ids = HashSet<Int>()
    private val classes = HashSet<String>()

    val isEmpty: Boolean get() = ids.isEmpty() && classes.isEmpty()
    fun clear() { ids.clear(); classes.clear() }
    fun add(e: UsageEvent) { if (e.instance != 0) ids += e.instance else classes += e.className.orEmpty() }
    fun remove(e: UsageEvent) { if (e.instance != 0) ids -= e.instance else classes -= e.className.orEmpty() }
}

/**
 * Works out one day of Wellbeing from Android's usage events, the way Digital Wellbeing counts: only apps with an icon
 * in the app drawer, an app's time is its activities' time on screen (from resumed until stopped, so an app still
 * showing behind a dialog or a see-through screen keeps counting, ended early by the screen turning off or the phone
 * shutting down), and the day's total is the time any counted app was on screen, so split screen counts once.
 *
 * Screen time follows Intelligent Search's ScreenTimeCalculator (ScreenTimeAnswers.kt) with two changes the preview
 * makes first: time runs to STOPPED rather than PAUSED, and an app the phone never logged as closed (a crash, a forced
 * restart) ends at the last thing the phone logged before it started again or the screen came back on, not hours later.
 * The rest (hours, opens, unlocks, notifications, the night) is built on the same spans.
 */
object DayCalculator {
    /**
     * The rules a day is worked out by. Raised whenever they change, so every saved day the phone still has records for
     * is worked out again (WellbeingRepository). 3: an app counts until it stops (not when it pauses), a night needs
     * the screen to have gone off, and the longest stretch on screen and the night's pick-ups are kept.
     */
    const val RULES = 3
    private const val MERGE_GAP_MS = 30_000L
    private const val FOLLOW_UP_MS = 10 * 60_000L
    private const val BEFORE_SCREEN_ON_MS = 60_000L
    private const val FIRST_APP_MS = 15_000L
    private const val LONG_SESSION_MS = 15 * 60_000L
    private const val NIGHT_MIN_MS = 3 * 3_600_000L
    private const val DAY_MS = 24 * 3_600_000L
    private const val HALF_DAY_MS = 12 * 3_600_000L
    /** A look at the phone in the night: this short, with at least [REST_MS] of rest on both sides of it. */
    private const val BRIEF_MS = 5 * 60_000L
    private const val REST_MS = 3_600_000L
    /** A brief look counts as part of the night from this long before midnight, until 4 AM. */
    private const val EVENING_MS = 3 * 3_600_000L
    private const val SMALL_HOURS = 4
    /** Stretches on screen less than this apart are one sitting (the home screen between two apps). */
    private const val SITTING_GAP_MS = 60_000L
    /** An app whose time ended this close before the screen went off was on screen when it did. */
    private const val CAME_BACK_MS = 3_000L
    /** A phone start logs its events (the screen, the home screen, the start itself) within this of each other. */
    private const val BOOT_CLUSTER_MS = 2_000L

    /**
     * Each counted app's spans on screen between [startMs] and [endMs]. [eventsFromMs] is when [events] begin (see
     * there). [events] must be in time order, and may run on past [endMs]: an app still open at the end of the window is
     * checked against what came next (see below).
     *
     * - An app's activity counts from ACTIVITY_RESUMED until ACTIVITY_STOPPED (or DESTROYED): a paused activity can still
     *   be on screen, and Digital Wellbeing counts it until it stops. The app is on screen while any of its activities
     *   is, each one followed on its own ([Showing]): two of the same class are two screens, not one.
     * - The screen turning off ends everything open.
     * - The phone shutting down ends everything open at the last activity, screen or lock event before it: Android
     *   writes a shutdown at its last save when it starts again after a crash, so the time up to it is unknown.
     * - The phone starting up, or the screen coming on, while something is still open means the phone never logged what
     *   closed it (a crash, a forced restart, a killed emulator): it ends at the last activity, screen or lock event the
     *   phone logged before that moment, not at the next screen-off hours or days later. Background events (a standby
     *   bucket change, a service starting) say nothing about the screen and don't count as being seen.
     */
    fun spans(
        events: List<UsageEvent>,
        startMs: Long,
        endMs: Long,
        packageCounts: (String) -> Boolean,
        eventsFromMs: Long = startMs
    ): List<Span> {
        val out = ArrayList<Span>()
        val openSince = HashMap<String, Long>()
        val showing = HashMap<String, Showing>()
        val seenAny = HashSet<String>()
        val counted = HashMap<String, Boolean>()
        // Recent times something was seen on screen, to find the last one before a phone start's own burst of events.
        val recent = ArrayDeque<Long>()
        fun isCounted(key: String) = counted.getOrPut(key) { Attribution.counts(key, packageCounts) }
        fun add(key: String, from: Long, to: Long) {
            if (to <= from || !isCounted(key)) return
            out += Span(key, from, to)
        }
        fun close(key: String, at: Long) {
            val since = openSince.remove(key) ?: return
            add(key, since, at)
        }
        fun closeAll(at: Long) {
            for (key in openSince.keys.toList()) {
                close(key, at)
                showing[key]?.clear()
            }
        }
        for (event in events) {
            // Past the window, only an end for what is still open matters: an activity event there shows the phone in
            // use, so what is open stays open to the window's end.
            val after = event.timeMs > endMs
            if (after && openSince.isEmpty()) break
            val at = event.timeMs.coerceIn(startMs, endMs)
            when (event.type) {
                EventKind.RESUMED -> {
                    if (after) break
                    val key = Attribution.keyOf(event)
                    seenAny += key
                    showing.getOrPut(key) { Showing() }.add(event)
                    openSince.putIfAbsent(key, at)
                }
                EventKind.PAUSED -> {
                    if (after) break
                    // Still on screen until it stops. Open since before the events begin: from the window's start, but
                    // only when the events start there (read from earlier, they would have shown its resume).
                    val key = Attribution.keyOf(event)
                    if (key !in seenAny) {
                        seenAny += key
                        if (eventsFromMs >= startMs) {
                            showing.getOrPut(key) { Showing() }.add(event)
                            openSince.putIfAbsent(key, startMs)
                        }
                    }
                }
                EventKind.STOPPED, EventKind.ACTIVITY_DESTROYED -> {
                    if (after) break
                    val key = Attribution.keyOf(event)
                    val screens = showing[key]
                    if (key !in seenAny) {
                        seenAny += key
                        if (eventsFromMs >= startMs) add(key, startMs, at)
                    } else if (screens != null) {
                        screens.remove(event)
                        if (screens.isEmpty) close(key, at)
                    }
                }
                EventKind.SCREEN_OFF -> {
                    closeAll(at)
                    if (after) break
                }
                EventKind.SHUTDOWN -> {
                    // Android also writes this when it starts after a crash, at its last save: either way nothing was
                    // seen on screen after the last activity, screen or lock event, so what is open ends there.
                    for (key in openSince.keys.toList()) {
                        val since = openSince.getValue(key)
                        close(key, (recent.lastOrNull() ?: since).coerceIn(since, at))
                        showing[key]?.clear()
                    }
                    if (after) break
                }
                EventKind.STARTUP, EventKind.SCREEN_ON -> {
                    // Nothing can have been on screen while the phone was off or the screen was dark: what is still open
                    // from before this moment ends at the last thing seen before it.
                    val cutoff = event.timeMs - BOOT_CLUSTER_MS
                    for (key in openSince.filterValues { it < cutoff }.keys) {
                        val since = openSince.getValue(key)
                        val last = recent.lastOrNull { it < cutoff } ?: since
                        close(key, last.coerceIn(since, at))
                        showing[key]?.clear()
                    }
                    if (after) break
                }
            }
            if (event.type != EventKind.NOTIFICATION) {
                recent.addLast(event.timeMs)
                if (recent.size > 64) recent.removeFirst()
            }
        }
        closeAll(endMs)
        return out
    }

    /** [spans] merged into the stretches any of them covers (time order), so a shared moment counts once. */
    fun union(spans: List<Span>, gapMs: Long = 0L): List<LongArray> {
        val sorted = spans.sortedBy { it.from }
        val out = ArrayList<LongArray>()
        for (s in sorted) {
            val last = out.lastOrNull()
            if (last != null && s.from <= last[1] + gapMs) {
                if (s.to > last[1]) last[1] = s.to
            } else out += longArrayOf(s.from, s.to)
        }
        return out
    }

    /** Adds the parts of [from, to) that fall in each hour of [hourStarts] (25 boundaries) to [into], in seconds. */
    private fun addToHours(from: Long, to: Long, hourStarts: LongArray, into: DoubleArray) {
        var h = 0
        while (h < 24 && hourStarts[h + 1] <= from) h++
        while (h < 24 && hourStarts[h] < to) {
            val a = max(from, hourStarts[h])
            val b = min(to, hourStarts[h + 1])
            if (b > a) into[h] += (b - a) / 1000.0
            h++
        }
    }

    private fun hourOf(t: Long, hourStarts: LongArray): Int {
        var h = 0
        while (h < 23 && hourStarts[h + 1] <= t) h++
        return h
    }

    /**
     * One day, from [events] in time order (they may start before the day, ideally 24 hours before, and run on after it,
     * so an app left open at midnight can be checked against what came next). [hourStarts] holds
     * the day's 25 hour boundaries (the day runs hourStarts[0] to hourStarts[24]); [nowMs] ends a day still running.
     * [eventsFromMs] is when the events begin. [packageCounts] says which packages count: apps with an icon in the app
     * drawer, not the home screen or the system UI. [clockMinute] gives a moment's time of day as minutes from this
     * day's midnight on the clock (negative the evening before); without it minutes are counted from midnight, which
     * is the same thing except on a day the clocks change.
     */
    fun compute(
        date: java.time.LocalDate,
        events: List<UsageEvent>,
        hourStarts: LongArray,
        nowMs: Long,
        eventsFromMs: Long,
        packageCounts: (String) -> Boolean,
        clockMinute: ((Long) -> Int)? = null
    ): DaySummary {
        val dayStart = hourStarts[0]
        val dayEnd = hourStarts[24]
        val end = min(nowMs, dayEnd)
        val daySpans = spans(events, dayStart, end, packageCounts, eventsFromMs)

        // Per app: time, hours, opens (stretches with gaps under 30 s merged), the longest stretch.
        val byKey = daySpans.groupBy { it.key }
        val appHours = HashMap<String, DoubleArray>()
        val sessions = HashMap<String, List<LongArray>>()
        for ((key, list) in byKey) {
            val hours = DoubleArray(24)
            for (s in list) addToHours(s.from, s.to, hourStarts, hours)
            appHours[key] = hours
            sessions[key] = union(list, MERGE_GAP_MS)
        }

        // The day: time any counted app was on screen, by hour.
        val onScreen = union(daySpans)
        val hours = DoubleArray(24)
        for (s in onScreen) addToHours(s[0], s[1], hourStarts, hours)

        // Notifications from counted apps, and whether the app came to the front within 10 minutes of each.
        val notifyTimes = ArrayList<Long>()
        val notifications = IntArray(24)
        val received = HashMap<String, Int>()
        val followed = HashMap<String, Int>()
        val ownSpans = byKey
        // A notification late in the evening can be followed just past midnight: those minutes are looked at too.
        val pastMidnight = if (end == dayEnd && nowMs > dayEnd)
            spans(events, dayEnd, min(nowMs, dayEnd + FOLLOW_UP_MS), packageCounts, eventsFromMs).groupBy { it.key } else emptyMap()
        for (e in events) {
            if (e.timeMs >= end) break
            if (e.type != EventKind.NOTIFICATION || !packageCounts(e.packageName)) continue
            notifyTimes += e.timeMs
            if (e.timeMs < dayStart || e.timeMs >= end) continue
            notifications[hourOf(e.timeMs, hourStarts)]++
            received.merge(e.packageName, 1, Int::plus)
            val its = ownSpans[e.packageName].orEmpty()
            val inFront = its.any { it.from <= e.timeMs && e.timeMs < it.to }
            val until = e.timeMs + FOLLOW_UP_MS
            if (!inFront && (its.any { it.from > e.timeMs && it.from <= until } ||
                    (until > dayEnd && pastMidnight[e.packageName].orEmpty().any { it.from > e.timeMs && it.from <= until }))
            ) followed.merge(e.packageName, 1, Int::plus)
        }

        // The day with the day before it: for the night, and for what was on screen when the screen last went off.
        val wideFrom = max(eventsFromMs, dayStart - DAY_MS)
        val wide = spans(events, wideFrom, end, packageCounts, eventsFromMs)
        val wideByKey = wide.groupBy { it.key }

        // Unlocks; after a notification when one arrived from a minute before the screen came on up to the unlock; and
        // for the others, the first app opened within 15 seconds. An app that was on screen when the screen went off
        // comes back by itself at the unlock: that is not an app opened, and it is left out.
        val unlocks = IntArray(24)
        var afterNotification = 0
        val firstOwn = HashMap<String, Int>()
        var lastScreenOn = Long.MIN_VALUE
        var lastScreenOff = Long.MIN_VALUE
        for ((i, e) in events.withIndex()) {
            if (e.timeMs >= end) break
            if (e.type == EventKind.SCREEN_ON) lastScreenOn = e.timeMs
            if (e.type == EventKind.SCREEN_OFF) lastScreenOff = e.timeMs
            if (e.type != EventKind.KEYGUARD_HIDDEN || e.timeMs < dayStart || e.timeMs >= end) continue
            unlocks[hourOf(e.timeMs, hourStarts)]++
            val on = if (lastScreenOn != Long.MIN_VALUE && e.timeMs - lastScreenOn <= 30 * 60_000L) lastScreenOn else e.timeMs
            val prompted = notifyTimes.any { it >= on - BEFORE_SCREEN_ON_MS && it <= e.timeMs }
            if (prompted) {
                afterNotification++
            } else {
                var j = i + 1
                while (j < events.size && events[j].timeMs <= e.timeMs + FIRST_APP_MS) {
                    val f = events[j]
                    if (f.type == EventKind.RESUMED) {
                        val key = Attribution.keyOf(f)
                        if (Attribution.counts(key, packageCounts)) {
                            val cameBack = lastScreenOff != Long.MIN_VALUE &&
                                wideByKey[key].orEmpty().any { it.to >= lastScreenOff - CAME_BACK_MS && it.to <= lastScreenOff }
                            if (!cameBack) firstOwn.merge(key, 1, Int::plus)
                            break
                        }
                    }
                    j++
                }
            }
        }

        // The night before: the longest stretch of at least 3 hours with nothing on screen that ends this morning
        // (before noon). Its end is the day's first use; its start is when the phone went down. The screen has to have
        // gone off in it (or the phone shut down): hours with no records at all are not a night's rest.
        val wideUnion = union(wide)
        val darkAt = events.filter { (it.type == EventKind.SCREEN_OFF || it.type == EventKind.SHUTDOWN) && it.timeMs >= wideFrom - CAME_BACK_MS && it.timeMs < end }.map { it.timeMs }
        fun wentDark(a: Long, b: Long) = darkAt.any { it >= a - CAME_BACK_MS && it < b }
        var downFrom = -1L
        var downTo = -1L
        var main = -1
        for (k in 1 until wideUnion.size) {
            val a = wideUnion[k - 1][1]
            val b = wideUnion[k][0]
            if (b > dayStart && b <= dayStart + HALF_DAY_MS && b - a >= NIGHT_MIN_MS && b - a > downTo - downFrom && wentDark(a, b)) {
                downFrom = a
                downTo = b
                main = k
            }
        }
        // A brief look at the phone in the night (the time, a message) doesn't end the night: a few minutes on screen
        // between late evening and 4 AM, with an hour or more of rest on its other side too, is a pick-up inside it.
        var nightPickups = 0
        var nightUseMs = 0L
        if (main > 0) {
            fun brief(u: LongArray) = u[1] - u[0] <= BRIEF_MS && u[0] >= dayStart - EVENING_MS
            var i = main - 1
            while (i >= 1 && brief(wideUnion[i]) && wideUnion[i][0] - wideUnion[i - 1][1] >= REST_MS && wentDark(wideUnion[i - 1][1], wideUnion[i][0])) {
                nightPickups++; nightUseMs += wideUnion[i][1] - wideUnion[i][0]
                downFrom = wideUnion[i - 1][1]
                i--
            }
            var j = main
            while (j + 1 < wideUnion.size && brief(wideUnion[j]) && wideUnion[j][0] < hourStarts[SMALL_HOURS] && wideUnion[j + 1][0] - wideUnion[j][1] >= REST_MS && wentDark(wideUnion[j][1], wideUnion[j + 1][0])) {
                nightPickups++; nightUseMs += wideUnion[j][1] - wideUnion[j][0]
                downTo = wideUnion[j + 1][0]
                j++
            }
        }
        fun minuteOf(t: Long) = clockMinute?.invoke(t) ?: Math.floorDiv(t - dayStart, 60_000L).toInt()
        fun appAt(t: Long): String? = wide.filter { it.from <= t && t < it.to }.minByOrNull { it.from }?.key
            ?: daySpans.filter { it.from >= t }.minByOrNull { it.from }?.key
        // With no night before it, the first stretch on screen is the first use, unless it was already running at
        // midnight: then the day had no first pick-up, and the page says so rather than "12:00 AM".
        val atMidnight = downTo <= 0 && onScreen.firstOrNull()?.get(0) == dayStart
        val firstUse = if (downTo > 0) downTo else onScreen.firstOrNull()?.get(0)
        val lastUse = onScreen.lastOrNull()?.get(1)

        // The longest sitting: time on screen with no break of a minute or more.
        val sitting = union(daySpans, SITTING_GAP_MS).maxByOrNull { it[1] - it[0] }

        // The longest stretch away between the first and the last use (gaps closed by a later use only).
        var awayFrom = -1L
        var awayLen = 0L
        if (firstUse != null) {
            for (k in 1 until onScreen.size) {
                val a = onScreen[k - 1][1]
                val b = onScreen[k][0]
                if (a >= firstUse && b - a > awayLen) {
                    awayFrom = a
                    awayLen = b - a
                }
            }
        }
        val notificationsWhileDown = if (downTo > 0) notifyTimes.count { it >= max(downFrom, dayStart) && it < downTo } else 0

        val apps = HashMap<String, AppDay>()
        for (key in (byKey.keys + received.keys)) {
            val hrs = appHours[key]
            val ses = sessions[key].orEmpty()
            apps[key] = AppDay(
                totalSec = byKey[key].orEmpty().sumOf { it.length }.let { (it / 1000).toInt() },
                hours = IntArray(24) { h -> hrs?.get(h)?.toInt() ?: 0 },
                opens = ses.size,
                longestSec = (ses.maxOfOrNull { it[1] - it[0] } ?: 0L).let { (it / 1000).toInt() },
                over15 = ses.count { it[1] - it[0] >= LONG_SESSION_MS },
                notifications = received[key] ?: 0,
                followed = followed[key] ?: 0
            )
        }
        val totalMs = onScreen.sumOf { it[1] - it[0] }
        return DaySummary(
            date = date,
            totalSec = (totalMs / 1000).toInt(),
            hours = IntArray(24) { hours[it].toInt() },
            unlocks = unlocks,
            notifications = notifications,
            apps = apps,
            unlocksAfterNotification = afterNotification,
            firstAfterOwnUnlock = firstOwn,
            firstUseMin = if (atMidnight) null else firstUse?.let { minuteOf(it) }?.takeIf { it in 0 until 1440 },
            firstApp = firstUse?.let { appAt(it) },
            // A day still in use as it ended reads 1440, whatever the clocks did that day.
            lastUseMin = lastUse?.let { if (it >= dayEnd) 1440 else minuteOf(it) },
            phoneDownStartMin = if (downTo > 0) minuteOf(downFrom) else null,
            awayStartMin = if (awayFrom >= 0) minuteOf(awayFrom) else null,
            awayLenMin = if (awayFrom >= 0) (awayLen / 60_000L).toInt() else null,
            notificationsWhileDown = notificationsWhileDown,
            complete = nowMs >= dayEnd && eventsFromMs <= dayStart + 5 * 60_000L,
            inUseAtMidnight = atMidnight,
            phoneDownLenMin = if (downTo > 0) ((downTo - downFrom) / 60_000L).toInt() else null,
            nightPickups = if (downTo > 0) nightPickups else 0,
            nightUseSec = if (downTo > 0) (nightUseMs / 1000).toInt() else 0,
            longestOnStartMin = sitting?.let { minuteOf(it[0]) },
            longestOnLenMin = sitting?.let { ((it[1] - it[0]) / 60_000L).toInt() },
            rules = RULES
        )
    }
}
