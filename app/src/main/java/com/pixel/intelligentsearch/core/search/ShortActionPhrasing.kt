package com.pixel.intelligentsearch.core.search

import com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser.NamedDay
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
 *
 * Enter also follows the full phrasing's certainty rule (THE CERTAINTY RULE FOR ENTER, [NaturalLanguageIntentParser]):
 * it runs only a reading with nothing guessed. A timer read whole is certain; an alarm when its time says itself
 * ("7:30am", "at 19:30", "at 7:30" on a 24-hour phone); a reminder when its time says itself and its day is named
 * ("pay rent tomorrow 9am"), or when it is a time from now ("laundry in 2 hrs"). "At 6", "call mom at 5pm" (which
 * day?) and "dinner at 7" are still offered, their day and time first in the row, for a tap.
 *
 * A reminder follows the full phrasing's whole-phrase rule ([NaturalLanguageIntentParser.isPlainTask]): once the day
 * and time are read, the task must be plain words, so "call mom tomorrrow 5pm" or "dentist 15.11 at 3pm" is none. Its
 * bare hour is read by the full phrasing's rule too ([NaturalLanguageIntentParser.bareHour]), on either clock; on a
 * 24-hour phone a time with minutes ("gym 6:30") is as written.
 */
object ShortActionPhrasing {

    enum class Kind { TIMER, ALARM, REMINDER }

    /** An action to offer for a query, with what its row says and the intent it opens (the user confirms there). */
    data class Suggestion(
        val kind: Kind,
        val title: String,
        val subtitle: String,
        val intent: Intent,
        /**
         * False for a reading that may well be meant as a search, or whose day or time was guessed (THE CERTAINTY
         * RULE FOR ENTER): Enter then searches, and the row is a tap away.
         */
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
    /** Weekdays as quick notes shorten them: "dentist fri 2pm", "gym tues 6am". */
    private val SHORT_WEEKDAYS = listOf("tues", "thurs", "thur", "weds", "sun", "mon", "tue", "wed", "thu", "fri", "sat")
    private val DAY_WORD = "today|tonight|tomorrow|tmrw|tmr|tmw|(?:on\\s+)?(?:${(WEEKDAYS + SHORT_WEEKDAYS).joinToString("|")})"
    private val RELATIVE_REMINDER = Regex(
        "^(.+?)\\s+in\\s+(?:($AMOUNT)\\s*(min|mins|minute|minutes|m|hr|hrs|hour|hours|h|day|days)|(an?)\\s+(minute|hour|day))$",
        RegexOption.IGNORE_CASE
    )
    private val TIMED_REMINDER = Regex("^(.+?)\\s+(?:($DAY_WORD)\\s+)?(at\\s+)?(?:$TIME)$", RegexOption.IGNORE_CASE)
    private val DAY_FIRST_REMINDER = Regex("^($DAY_WORD)\\s+(at\\s+)?(?:$TIME)\\s+(.+)$", RegexOption.IGNORE_CASE)
    /**
     * A task that ends in a word that bounds or rounds the time after it ("call mom around 5pm", "pay rent by 5pm",
     * "lunch 12 - 1pm", "meeting from 2 to 3pm"), or makes it another time ("call mum half 5pm" is 5:30 in Britain):
     * the time it says is no time to set a reminder for.
     */
    private val ROUGH_BEFORE_TIME = Regex(
        "\\b(?:around|about|approx\\.?|approximately|roughly|circa|by|before|after|until|till|til|between|from|to|and|" +
            "half|quarter|past)\\s*$|[-–—]\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val WHITESPACE = Regex("\\s+")

    /** First words of searches that name a time without being a task ("weather at 5pm", "what time is it at 6"). */
    private val NOT_A_TASK = setOf(
        "what", "whats", "what's", "when", "where", "why", "who", "how", "is", "are", "does", "do", "did", "can",
        "will", "should", "time", "weather", "news", "score", "scores", "flight", "flights", "movie", "movies",
        "showtimes", "traffic", "sunrise", "sunset", "open", "opening", "convert", "define", "translate", "timer",
        "alarm", "remind", "reminder", "set", "start", "days", "weeks", "hours", "minutes", "at", "on", "in",
        "today", "tonight", "tomorrow", "tmrw", "tmr", "tmw"
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
        "groceries", "shopping", "school", "work", "doctor's", "doctors", "dr", "dr.", "appt", "checkup", "check-up",
        "therapy", "physio", "massage", "lesson", "lessons"
    )

    /**
     * Words that start a plan of one's own only with one of [TASK_CUES] after them: "team meeting", "zoom call", "swim
     * class", "piano lesson". On their own ("team liquid at 2pm") they start a search as often.
     */
    private val TASK_QUALIFIERS = setOf(
        "team", "staff", "client", "family", "zoom", "conference", "piano", "guitar", "swim", "soccer", "football",
        "basketball", "baseball", "hockey", "tennis", "dance", "parent", "parents", "sales", "board"
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
        // "call mom @ 5pm" is "call mom at 5pm".
        val original = NaturalLanguageIntentParser.spellClockTimes(rawQuery.trim().replace(WHITESPACE, " "))
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

    /**
     * A duration alone. "800m", "400m" and "200 m" are as often a race and "90s" or "80s" a decade, so a lone amount
     * in a bare "m" of 100 or more, or a bare "s" that is a decade (10s to 90s), is no timer without "timer" after it:
     * "800m" started a 13 hour 20 minute timer on Enter. "15m", "45s", "300s" and "800 min" are still timers.
     */
    private fun parseTimer(lower: String): Suggestion? {
        if (!DURATION.matches(lower)) return null
        val parts = DURATION_PART.findAll(lower).toList()
        if (!lower.endsWith("timer") && parts.size == 1 && readsAsDistanceOrDecade(parts.single(), lower)) return null
        var seconds = 0.0
        val seen = HashSet<Char>()
        for (part in parts) {
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

    /**
     * Whether [part] of [lower], a lone amount and unit, is a distance in meters ("800m") or a decade ("90s")
     * ([parseTimer]). The unit must end there: "90 sec" is no "90 s" decade.
     */
    private fun readsAsDistanceOrDecade(part: MatchResult, lower: String): Boolean {
        if (lower.getOrNull(part.range.last + 1)?.isLetter() == true) return false
        val amount = part.groupValues[1].toIntOrNull() ?: return false
        return when (part.groupValues[2]) {
            "m" -> amount >= 100
            "s" -> amount in 10..90 && amount % 10 == 0
            else -> false
        }
    }

    private fun unitOf(unit: String): Char = when {
        unit.startsWith("h") -> 'h'
        unit.startsWith("s") -> 's'
        else -> 'm'
    }

    // ---------------------------------------------------------------------------------------------
    // ALARMS: "7:30am", "7 pm", "730am", "at 6", "at 19:30", "6am alarm"; "7:30" alone is only offered
    // ---------------------------------------------------------------------------------------------

    /** [zeroPadded]: "07:00" and "09:30" are 24-hour notation, the hour meant exactly as written. */
    private class ClockTime(
        val hour: Int, val minute: Int, val meridiem: String?, val explicitAt: Boolean, val hasMinutes: Boolean, val zeroPadded: Boolean
    )

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
        return ClockTime(hour, minute, meridiem, explicitAt, hasMinutes = minuteText.isNotEmpty(), zeroPadded = hourText.length == 2 && hourText[0] == '0')
    }

    private fun parseAlarm(lower: String, nowMs: Long, is24Hour: Boolean, zone: TimeZone, locale: Locale): Suggestion? {
        val match = ALARM.matchEntire(lower) ?: return null
        val time = clockTime(match, first = 2, explicitAt = match.groupValues[1].isNotEmpty()) ?: return null
        val alarmWord = lower.endsWith(" alarm")
        // A lone number ("7", "730") is a number; it takes "at", am/pm, "alarm" or a colon to be a time.
        if (time.meridiem == null && !time.explicitAt && !alarmWord && !time.hasMinutes) return null
        val now = calendar(nowMs, zone)
        val hour24 = resolveHour(time, now, is24Hour)
        val rings = (now.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, hour24); set(Calendar.MINUTE, time.minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        if (rings.timeInMillis <= nowMs) rings.add(Calendar.DAY_OF_YEAR, 1)
        return Suggestion(
            kind = Kind.ALARM,
            title = "Set Alarm for ${Durations.timeOfDay(hour24, time.minute, is24Hour, locale)}",
            // The day and time first, as the region writes them, so a narrow row cuts the source, never when.
            subtitle = "${Durations.dayAndTime(rings.timeInMillis, nowMs, is24Hour, locale, zone.toZoneId())} · Clock Alarm",
            intent = NaturalLanguageIntentParser.alarmIntent(hour24, time.minute),
            // "7:30" alone may be meant as a search; "7:30am", "at 7:30" and "7:30 alarm" can't be. "At 6" is AM or PM
            // by a guess, so it is a tap away too.
            runsOnEnter = (time.meridiem != null || time.explicitAt || alarmWord) && saysItself(time, is24Hour),
            hour = hour24,
            minute = time.minute
        )
    }

    /**
     * Whether [time] says which half of the day it is, so nothing about it is guessed (THE CERTAINTY RULE FOR ENTER):
     * am/pm, a leading zero ("07:00"), an hour of 0 or past 12, or minutes on a 24-hour phone ("9:30").
     */
    private fun saysItself(time: ClockTime, is24Hour: Boolean): Boolean =
        time.meridiem != null || time.zeroPadded || time.hour == 0 || time.hour > 12 || (is24Hour && time.hasMinutes)

    /**
     * The 24-hour hour [time] means for an alarm, by the one rule both readers' alarms share. With am/pm it is exact.
     * Without, a leading zero ("07:00") or an hour of 0 or past 12 reads as written, and so do minutes on a 24-hour
     * phone ("7:30"); a bare hour, on either clock, is the next of its morning and evening times still ahead ("at 6" at
     * 2 PM is 6 PM), as the full phrasing's "set an alarm for 6" is. It was as written on a 24-hour phone (06:00). A
     * reminder's bare hour goes by [NaturalLanguageIntentParser.bareHour] instead.
     */
    private fun resolveHour(time: ClockTime, now: Calendar, is24Hour: Boolean): Int {
        val meridiem = time.meridiem
        if (meridiem != null) return (time.hour % 12) + if (meridiem == "pm") 12 else 0
        if (saysItself(time, is24Hour)) return time.hour
        val morning = time.hour % 12
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
        // "tomorrow 9am eastern call mom": a time in another zone, which would be set at the phone's own hour.
        if (NaturalLanguageIntentParser.namesTimeZone(original)) return null
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
        // "Call mom around 5pm", "pay rent by 5pm", "lunch 12 - 1pm": the time is said roughly, or as a bound or range.
        if (timed != null && dayWord.isEmpty() && ROUGH_BEFORE_TIME.containsMatchIn(taskText)) return null
        val now = calendar(nowMs, zone)
        // The day is read as the full phrasing reads it, wherever its words are ("gym tomorrow morning at 7", "dentist
        // on the 3rd at 2pm", "call mom on thanksgiving at 5"), with the time just after them ("dentist fri 2pm",
        // "dentist the 12th at 10am"). Day words it can't read, or that disagree, are no reminder; "buy candy for
        // halloween at 6pm" may be today or Halloween, so it is none either.
        val said = "$taskText $dayWord"
        val day = NaturalLanguageIntentParser.readDay(said, now, locale, clockFollows = true) ?: return null
        if (day.forOccasionOnly) return null
        val task = cleanTask(day.text.replace(WHITESPACE, " ")) ?: return null
        val at = (now.clone() as Calendar).apply { set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        day.date?.let { at.set(it.year, it.monthValue - 1, it.dayOfMonth) }
        val namedDay = day.namedDay
        val night = namedDay == NamedDay.TONIGHT || day.period == "night"
        // A bare hour (1 to 12, no am/pm or leading zero) goes by the first rule that says which half of the day it is,
        // as the full phrasing's does: words that say so ("dinner at 7" is 7 PM even said at 5 AM, "dinner tonight at
        // 5:30" 5:30 PM, "pills before bed at 10" 10 PM), then "tonight", which runs past midnight ("at 12" is midnight,
        // 1 to 4 the small hours after it, and 5 either, so no time), then a part of the day, then the full phrasing's
        // rule for any other bare hour ([NaturalLanguageIntentParser.bareHour]: "pick up kids at 3" is 3 PM). On a
        // 24-hour clock too: people there still say "dinner at 7" or "pick up kids at 3" for the evening and the
        // afternoon, which read as written set a reminder for the small hours. Minutes there are 24-hour notation
        // ("gym 6:30" is 06:30, as the full phrasing reads it), unless the words say the other half ("dinner at 7:30").
        val bare = time.meridiem == null && !saysItself(time, is24Hour) && time.hour in 1..12
        if (time.meridiem == null && !time.zeroPadded && is24Hour && time.hasMinutes &&
            NaturalLanguageIntentParser.wordsDisagreeWithWrittenHour(task, time.hour)
        ) return null
        val said24 = if (bare) NaturalLanguageIntentParser.hourForWords(task, time.hour) else null
        // "Pack lunch at 7": the words say a half of the day the hour isn't in.
        if (bare && said24 == null && NaturalLanguageIntentParser.wordsDisagreeWithHour(task, time.hour)) return null
        // THE CERTAINTY RULE FOR ENTER: only a time that says itself, or "tonight" at 6 to 11 or a part of the day,
        // is certain; every other rule below is a guess.
        var timeSaid = !bare
        val hour = when {
            said24 != null -> said24
            bare && night -> when (time.hour) {
                12 -> 0
                5 -> return null
                in 6..11 -> { timeSaid = true; time.hour + 12 }
                else -> time.hour
            }
            bare && day.period == "morning" -> { timeSaid = true; time.hour }
            bare && day.period != null -> { timeSaid = true; if (time.hour == 12) 12 else time.hour + 12 }
            bare -> NaturalLanguageIntentParser.bareHour(
                time.hour, time.minute, dayNamed = namedDay != NamedDay.NONE && namedDay != NamedDay.TODAY,
                nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            )
            else -> resolveHour(time, now, is24Hour)
        }
        // "This evening" names today; with no day named, "call mom at 5pm" may be today's or tomorrow's.
        val dayNamed = namedDay != NamedDay.NONE || (day.period != null && !day.periodWithIn)
        at.set(Calendar.HOUR_OF_DAY, hour)
        at.set(Calendar.MINUTE, time.minute)
        if (night && said24 == null && hour < 6) at.add(Calendar.DAY_OF_YEAR, 1)
        // No day named and the time has passed today: the next one, tomorrow. A day named ("tonight", "this friday")
        // whose time has passed has no time left to mean: no reminder rather than one already gone.
        if (namedDay == NamedDay.NONE && (day.period == null || day.periodWithIn) && at.timeInMillis <= nowMs) {
            at.add(Calendar.DAY_OF_YEAR, 1)
        }
        if (at.timeInMillis <= nowMs) return null
        return reminder(task, at, nowMs, is24Hour, zone, locale, runsOnEnter = timeSaid && dayNamed)
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
        val first = words.first().lowercase(Locale.ROOT).replace('’', '\'')
        val second = words.getOrNull(1)?.lowercase(Locale.ROOT)
        if (first in NOT_A_TASK) return null
        if (first !in TASK_CUES && !(first in TASK_QUALIFIERS && second in TASK_CUES)) return null
        if (words.any { it.lowercase(Locale.ROOT) in OPENING_HOURS_WORDS }) return null
        // What is left after the time must not itself be a calculation or conversion ("15 min in", "5 + 3").
        if (task.any { it in "+*/=^%" }) return null
        // The whole-phrase rule: plain words only, so no number or word of when the reader left unread.
        if (!NaturalLanguageIntentParser.isPlainTask(task)) return null
        return task.replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    /**
     * The row for a reminder to [task] at [at]. When it is set for is said as the full phrasings' rows say it
     * ([Durations.dayAndTime]: "Tomorrow, 9:00 AM", "Fr., 13. Nov., 14:00"), in the region's words and order, and first
     * in the subtitle, so a long task or large text never hides it.
     */
    private fun reminder(
        task: String, at: Calendar, nowMs: Long, is24Hour: Boolean, zone: TimeZone, locale: Locale, runsOnEnter: Boolean
    ): Suggestion {
        val setFor = Durations.dayAndTime(at.timeInMillis, nowMs, is24Hour, locale, zone.toZoneId())
        return Suggestion(
            kind = Kind.REMINDER,
            title = "Remind: $task",
            subtitle = "$setFor · Calendar Reminder",
            intent = NaturalLanguageIntentParser.reminderIntent(task, at.timeInMillis),
            runsOnEnter = runsOnEnter,
            hour = at.get(Calendar.HOUR_OF_DAY),
            minute = at.get(Calendar.MINUTE),
            triggerMillis = at.timeInMillis
        )
    }

    private fun calendar(ms: Long, zone: TimeZone): Calendar = Calendar.getInstance(zone).apply { timeInMillis = ms }

}
