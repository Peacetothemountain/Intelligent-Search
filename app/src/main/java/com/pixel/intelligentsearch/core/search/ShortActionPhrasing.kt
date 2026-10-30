package com.pixel.intelligentsearch.core.search

import com.pixel.intelligentsearch.core.util.Durations
import android.content.Intent
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Shorter action phrasing, as Quick Search and Spotlight read it: a bare duration starts a timer ("15 min", "90 sec",
 * "1h 30m"), a bare time sets an alarm ("7:30am", "730am", "at 6"), and a task followed by when ("laundry in 2 hrs",
 * "call mom at 5pm", "pay rent tomorrow 9am") makes a reminder. The full phrasings ("set a timer for", "remind me to")
 * stay with [NaturalLanguageIntentParser], which is asked first; this only reads what it leaves.
 *
 * Nothing here fires by itself: each match is offered as the top suggested action, and [Suggestion.runsOnEnter] says
 * whether Enter may run it too. A bare clock time such as "7:30" could as well be a score, a verse or a search, so it
 * is offered but Enter keeps searching. Queries the calculator, a converter or date maths answer never reach this
 * (the coordinator skips it then), and plain numbers, phone numbers and questions are never read as actions.
 */
object ShortActionPhrasing {

    enum class Kind { TIMER, ALARM, REMINDER }

    /** An action to offer for a query, with what its row says and the intent it opens (the user confirms there). */
    data class Suggestion(
        val kind: Kind,
        val title: String,
        val subtitle: String,
        val intent: Intent,
        /** False for a reading that may well be meant as a search, which Enter then still runs. */
        val runsOnEnter: Boolean,
        val durationSeconds: Int = 0,
        val hour: Int = 0,
        val minute: Int = 0,
        val triggerMillis: Long = 0L
    ) {
        /** The search screen's DirectAction icon type of the row. */
        val iconType: String get() = when (kind) {
            Kind.TIMER -> "timer"
            Kind.ALARM -> "alarm"
            Kind.REMINDER -> "calendar"
        }
    }

    /** AlarmClock.EXTRA_LENGTH accepts 1..86400 seconds. */
    private const val MAX_TIMER_SECONDS = 24 * 60 * 60

    private const val HOUR_UNITS = "h|hr|hrs|hour|hours"
    private const val MINUTE_UNITS = "m|min|mins|minute|minutes"
    private const val SECOND_UNITS = "s|sec|secs|second|seconds"
    private const val AMOUNT = "\\d{1,3}(?:[.,]\\d{1,2})?"
    private val DURATION_PART = Regex("($AMOUNT)\\s*($HOUR_UNITS|$MINUTE_UNITS|$SECOND_UNITS)")
    private val DURATION = Regex(
        "^$AMOUNT\\s*(?:$HOUR_UNITS|$MINUTE_UNITS|$SECOND_UNITS)(?:\\s*(?:and\\s+)?$AMOUNT\\s*(?:$HOUR_UNITS|$MINUTE_UNITS|$SECOND_UNITS)){0,2}(?:\\s+timer)?$"
    )

    private const val MERIDIEM = "am|pm|a\\.m\\.|p\\.m\\."
    /** "7", "7:30", "19:30", each with an optional am/pm; "730am" (no colon) only with one. */
    private const val TIME = "(\\d{1,2})(?::(\\d{2}))?\\s*($MERIDIEM)?|(\\d{1,2})(\\d{2})\\s*($MERIDIEM)"
    private val ALARM = Regex("^(at\\s+)?(?:$TIME)(?:\\s+alarm)?$")

    private val WEEKDAYS = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
    private val DAY_WORD = "today|tonight|tomorrow|tmrw|tmr|(?:on\\s+)?(?:${WEEKDAYS.joinToString("|")})"
    private val RELATIVE_REMINDER = Regex(
        "^(.+?)\\s+in\\s+(?:($AMOUNT)\\s*(min|mins|minute|minutes|m|hr|hrs|hour|hours|h|day|days)|(an?)\\s+(minute|hour|day))$",
        RegexOption.IGNORE_CASE
    )
    private val TIMED_REMINDER = Regex("^(.+?)\\s+(?:($DAY_WORD)\\s+)?(at\\s+)?(?:$TIME)$", RegexOption.IGNORE_CASE)
    private val DAY_FIRST_REMINDER = Regex("^($DAY_WORD)\\s+(at\\s+)?(?:$TIME)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("\\s+")

    /** First words of searches that name a time without being a task ("weather at 5pm", "what time is it at 6"). */
    private val NOT_A_TASK = setOf(
        "what", "whats", "what's", "when", "where", "why", "who", "how", "is", "are", "does", "do", "did", "can",
        "will", "should", "time", "weather", "news", "score", "scores", "flight", "flights", "movie", "movies",
        "showtimes", "traffic", "sunrise", "sunset", "open", "opening", "convert", "define", "translate", "timer",
        "alarm", "remind", "reminder", "set", "start", "days", "weeks", "hours", "minutes", "at", "on", "in",
        "today", "tonight", "tomorrow", "tmrw", "tmr"
    ) + WEEKDAYS + listOf(
        // "dec 25 at 5pm" is a date, not a task called "Dec 25".
        "jan", "january", "feb", "february", "mar", "march", "apr", "april", "may", "jun", "june", "jul", "july",
        "aug", "august", "sep", "sept", "september", "oct", "october", "nov", "november", "dec", "december"
    )

    /**
     * Words anywhere in a task that make it a search for when a place is open ("restaurants open at 7pm", "costco hours
     * at 10", "bars open till 2am", "happy hour at 5"): read as a reminder, Enter opened the calendar instead of searching.
     */
    private val OPENING_HOURS_WORDS = setOf(
        "open", "opens", "opening", "closes", "closing", "closed", "hours", "hour", "till", "until", "near", "nearby"
    )

    /**
     * How a task starts: a thing to do ("call mom", "pick up kids", "pay rent") or a plan of one's own ("dinner",
     * "dentist", "gym"). A time after anything else is a search as often as not ("trains to london at 8am", "nba game
     * tonight at 8", "mass at 9", "uber at 5am"), and Enter would have opened the calendar for it, so it reads as none.
     */
    private val TASK_CUES = setOf(
        // Things to do
        "call", "text", "email", "message", "phone", "ring", "ask", "tell", "remind", "pick", "drop", "collect", "buy",
        "get", "grab", "order", "pay", "book", "cancel", "renew", "return", "send", "post", "take", "bring", "feed",
        "walk", "water", "wash", "clean", "cook", "make", "bake", "prep", "do", "finish", "check", "charge", "change",
        "fix", "pack", "unpack", "put", "turn", "close", "lock", "leave", "go", "visit", "meet", "see", "study", "read",
        "write", "submit", "print", "sign", "file", "review", "prepare", "practice", "practise", "exercise", "run",
        "stretch", "sleep", "shower", "mow", "vacuum", "iron", "plan", "confirm", "follow", "reply", "respond", "update",
        "install", "apply", "register", "refill", "defrost", "thaw", "preheat", "load", "empty", "fill", "move", "sort",
        "tidy", "shop", "pickup", "dropoff", "meditate", "pray", "watch", "listen", "join", "attend", "log", "backup",
        // Plans of one's own
        "meeting", "appointment", "event", "dentist", "doctor", "vet", "haircut", "interview", "standup", "stand-up",
        "meds", "medicine", "pills", "laundry", "gym", "workout", "yoga", "class", "lecture", "rehearsal", "dinner",
        "lunch", "breakfast", "brunch", "coffee", "date", "chores", "homework", "trash", "bins", "garbage", "recycling",
        "groceries", "shopping", "school", "work"
    )

    /**
     * The action [rawQuery] reads as at [nowMs], or null when it reads as none. [is24Hour] is the phone's clock
     * setting: it decides how a bare hour ("at 6") is read and how times are shown.
     */
    fun parse(
        rawQuery: String,
        nowMs: Long = System.currentTimeMillis(),
        is24Hour: Boolean = false,
        zone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): Suggestion? {
        val original = rawQuery.trim().replace(WHITESPACE, " ")
        if (original.isEmpty() || original.length > 80 || '?' in original) return null
        val lower = original.lowercase(Locale.ROOT)
        // Every phrasing has a number, but "in an hour": most queries leave here, before any pattern runs.
        if (original.none { it.isDigit() } && " in a" !in lower) return null
        return parseTimer(lower)
            ?: parseAlarm(lower, nowMs, is24Hour, zone, locale)
            ?: parseReminder(original, nowMs, is24Hour, zone, locale)
    }

    // ---------------------------------------------------------------------------------------------
    // TIMERS: "15 min", "15m", "90 sec", "1h 30m", "1.5 hours", "10 minutes timer"
    // ---------------------------------------------------------------------------------------------

    private fun parseTimer(lower: String): Suggestion? {
        if (!DURATION.matches(lower)) return null
        var seconds = 0.0
        val seen = HashSet<Char>()
        for (part in DURATION_PART.findAll(lower)) {
            val amount = part.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
            val unit = unitOf(part.groupValues[2])
            // "5m 5m" isn't a duration anyone types; refusing it keeps typos from becoming one.
            if (!seen.add(unit)) return null
            seconds += amount * when (unit) { 'h' -> 3600.0; 'm' -> 60.0; else -> 1.0 }
        }
        val total = Math.round(seconds).toInt()
        if (total <= 0 || total > MAX_TIMER_SECONDS) return null
        return Suggestion(
            kind = Kind.TIMER,
            title = "Start ${Durations.short(total.toLong())} Timer",
            subtitle = "Clock Timer",
            intent = NaturalLanguageIntentParser.timerIntent(total),
            runsOnEnter = true,
            durationSeconds = total
        )
    }

    private fun unitOf(unit: String): Char = when {
        unit.startsWith("h") -> 'h'
        unit.startsWith("s") -> 's'
        else -> 'm'
    }

    // ---------------------------------------------------------------------------------------------
    // ALARMS: "7:30am", "7 pm", "730am", "at 6", "at 19:30", "6am alarm"; "7:30" alone is only offered
    // ---------------------------------------------------------------------------------------------

    private class ClockTime(val hour: Int, val minute: Int, val meridiem: String?, val explicitAt: Boolean, val hasMinutes: Boolean)

    /** The clock time in [match]'s [TIME] groups starting at [first], or null when it isn't a real time. */
    private fun clockTime(match: MatchResult, first: Int, explicitAt: Boolean): ClockTime? {
        val g = match.groupValues
        val compact = g[first + 3].isNotEmpty()
        val hourText = if (compact) g[first + 3] else g[first]
        val minuteText = if (compact) g[first + 4] else g[first + 1]
        val meridiem = (if (compact) g[first + 5] else g[first + 2]).replace(".", "").ifEmpty { null }
        val hour = hourText.toIntOrNull() ?: return null
        val minute = if (minuteText.isEmpty()) 0 else minuteText.toIntOrNull() ?: return null
        if (minute !in 0..59) return null
        if (meridiem != null) {
            if (hour !in 1..12) return null
        } else if (hour !in 0..23) return null
        return ClockTime(hour, minute, meridiem, explicitAt, hasMinutes = minuteText.isNotEmpty())
    }

    private fun parseAlarm(lower: String, nowMs: Long, is24Hour: Boolean, zone: TimeZone, locale: Locale): Suggestion? {
        val match = ALARM.matchEntire(lower) ?: return null
        val time = clockTime(match, first = 2, explicitAt = match.groupValues[1].isNotEmpty()) ?: return null
        val alarmWord = lower.endsWith(" alarm")
        // A lone number ("7", "730") is a number; it takes "at", am/pm, "alarm" or a colon to be a time.
        if (time.meridiem == null && !time.explicitAt && !alarmWord && !time.hasMinutes) return null
        val now = calendar(nowMs, zone)
        val hour24 = resolveHour(time, now, is24Hour, preferEvening = false)
        val today = (now.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, hour24); set(Calendar.MINUTE, time.minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val day = if (today.timeInMillis <= nowMs) "Tomorrow" else "Today"
        return Suggestion(
            kind = Kind.ALARM,
            title = "Set Alarm for ${Durations.timeOfDay(hour24, time.minute, is24Hour, locale)}",
            subtitle = "Clock Alarm · $day",
            intent = NaturalLanguageIntentParser.alarmIntent(hour24, time.minute),
            // "7:30" alone may be meant as a search; "7:30am", "at 7:30" and "7:30 alarm" can't be.
            runsOnEnter = time.meridiem != null || time.explicitAt || alarmWord,
            hour = hour24,
            minute = time.minute
        )
    }

    /**
     * The 24-hour hour [time] means. With am/pm it is exact. Without, a 24-hour clock reads the hour as written; a
     * 12-hour clock takes the next of its morning and evening times still ahead ("at 6" at 2 PM is 6 PM), or, when
     * [preferEvening] (a day other than today was named), 1 to 7 as the afternoon and evening people mean.
     */
    private fun resolveHour(time: ClockTime, now: Calendar, is24Hour: Boolean, preferEvening: Boolean): Int {
        val meridiem = time.meridiem
        if (meridiem != null) return (time.hour % 12) + if (meridiem == "pm") 12 else 0
        if (is24Hour || time.hour == 0 || time.hour > 12) return time.hour
        val morning = time.hour % 12
        if (preferEvening) return if (time.hour in 1..7) morning + 12 else if (time.hour == 12) 12 else morning
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val morningMinutes = morning * 60 + time.minute
        val eveningMinutes = morningMinutes + 12 * 60
        return when {
            morningMinutes > nowMinutes -> morning
            eveningMinutes > nowMinutes -> morning + 12
            else -> morning
        }
    }

    // ---------------------------------------------------------------------------------------------
    // REMINDERS: "laundry in 2 hrs", "call mom at 5pm", "pay rent tomorrow 9am", "tomorrow 9am pay rent"
    // ---------------------------------------------------------------------------------------------

    private fun parseReminder(original: String, nowMs: Long, is24Hour: Boolean, zone: TimeZone, locale: Locale): Suggestion? {
        RELATIVE_REMINDER.matchEntire(original)?.let { match ->
            val task = cleanTask(match.groupValues[1]) ?: return null
            val amount = match.groupValues[2].replace(',', '.').toDoubleOrNull() ?: 1.0
            val unit = (match.groupValues[3].ifEmpty { match.groupValues[5] }).lowercase(Locale.ROOT)
            val minutes = amount * when {
                unit.startsWith("d") -> 24 * 60.0
                unit.startsWith("h") -> 60.0
                else -> 1.0
            }
            if (minutes < 1 || minutes > 366 * 24 * 60) return null
            val at = calendar(nowMs + Math.round(minutes * 60_000), zone).apply { set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            // "Things to do in 2 days" is as likely a search as a task, so a day away is offered but not run by Enter.
            return reminder(task, at, nowMs, is24Hour, zone, locale, runsOnEnter = !unit.startsWith("d"))
        }
        val timed = TIMED_REMINDER.matchEntire(original)
        val (taskText, dayWord, time) = if (timed != null) {
            val time = clockTime(lowerGroups(timed), first = 4, explicitAt = timed.groupValues[3].isNotEmpty()) ?: return null
            Triple(timed.groupValues[1], timed.groupValues[2], time)
        } else {
            val dayFirst = DAY_FIRST_REMINDER.matchEntire(original) ?: return null
            val time = clockTime(lowerGroups(dayFirst), first = 3, explicitAt = dayFirst.groupValues[2].isNotEmpty()) ?: return null
            Triple(dayFirst.groupValues[9], dayFirst.groupValues[1], time)
        }
        // A trailing number is a time only with "at", am/pm or a colon: "iphone 15" and "bus 42" are searches.
        if (time.meridiem == null && !time.explicitAt && !time.hasMinutes) return null
        val task = cleanTask(taskText) ?: return null
        val now = calendar(nowMs, zone)
        val day = dayWord.lowercase(Locale.ROOT).removePrefix("on ").trim()
        val at = (now.clone() as Calendar).apply { set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        when (day) {
            "tomorrow", "tmrw", "tmr" -> at.add(Calendar.DAY_OF_YEAR, 1)
            "", "today", "tonight" -> {}
            else -> {
                val target = WEEKDAYS.indexOf(day) + Calendar.SUNDAY
                var ahead = (target - at.get(Calendar.DAY_OF_WEEK) + 7) % 7
                if (ahead == 0) ahead = 7
                at.add(Calendar.DAY_OF_YEAR, ahead)
            }
        }
        val hour = if (day == "tonight" && time.meridiem == null && time.hour in 1..11 && !is24Hour) {
            time.hour + 12
        } else {
            resolveHour(time, now, is24Hour, preferEvening = day.isNotEmpty() && day != "today")
        }
        at.set(Calendar.HOUR_OF_DAY, hour)
        at.set(Calendar.MINUTE, time.minute)
        // No day named and the time has passed today: the next one, tomorrow.
        if (day.isEmpty() && at.timeInMillis <= nowMs) at.add(Calendar.DAY_OF_YEAR, 1)
        return reminder(task, at, nowMs, is24Hour, zone, locale)
    }

    /** [match]'s groups lowercased, for the time groups (the task keeps its case). */
    private fun lowerGroups(match: MatchResult): MatchResult = object : MatchResult by match {
        override val groupValues: List<String> = match.groupValues.map { it.lowercase(Locale.ROOT) }
    }

    /** The task as the reminder names it, or null when the words are a search rather than something to do. */
    private fun cleanTask(raw: String): String? {
        val task = raw.trim().trimEnd(',', '-', ':').trim()
        if (task.isEmpty() || task.length > 60 || task.none { it.isLetter() }) return null
        val words = task.split(' ')
        if (words.size > 8) return null
        val first = words.first().lowercase(Locale.ROOT)
        if (first in NOT_A_TASK || first !in TASK_CUES) return null
        if (words.any { it.lowercase(Locale.ROOT) in OPENING_HOURS_WORDS }) return null
        // What is left after the time must not itself be a calculation or conversion ("15 min in", "5 + 3").
        if (task.any { it in "+*/=^%" }) return null
        return task.replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    private fun reminder(
        task: String, at: Calendar, nowMs: Long, is24Hour: Boolean, zone: TimeZone, locale: Locale, runsOnEnter: Boolean = true
    ): Suggestion {
        val time = Durations.timeOfDay(at.get(Calendar.HOUR_OF_DAY), at.get(Calendar.MINUTE), is24Hour, locale)
        val days = daysBetween(calendar(nowMs, zone), at)
        val whenText = when {
            days == 0 -> "at $time"
            days == 1 -> "tomorrow at $time"
            days in 2..6 -> "on ${at.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, locale)} at $time"
            else -> "on ${at.getDisplayName(Calendar.MONTH, Calendar.SHORT, locale)} ${at.get(Calendar.DAY_OF_MONTH)} at $time"
        }
        val date = "${at.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, locale)}, " +
            "${at.getDisplayName(Calendar.MONTH, Calendar.SHORT, locale)} ${at.get(Calendar.DAY_OF_MONTH)}"
        return Suggestion(
            kind = Kind.REMINDER,
            title = "Remind: $task $whenText",
            subtitle = "Calendar Reminder · $date",
            intent = NaturalLanguageIntentParser.reminderIntent(task, at.timeInMillis),
            runsOnEnter = runsOnEnter,
            hour = at.get(Calendar.HOUR_OF_DAY),
            minute = at.get(Calendar.MINUTE),
            triggerMillis = at.timeInMillis
        )
    }

    private fun daysBetween(from: Calendar, to: Calendar): Int {
        fun dayNumber(c: Calendar): Long {
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
            }
            return utc.timeInMillis / 86_400_000L
        }
        return (dayNumber(to) - dayNumber(from)).toInt()
    }

    private fun calendar(ms: Long, zone: TimeZone): Calendar = Calendar.getInstance(zone).apply { timeInMillis = ms }

}
