package com.pixel.intelligentsearch.core.search

import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import java.util.Calendar
import java.util.Locale

/**
 * On-device Natural Language Intent Parser and Date/Time Extractor.
 * Parses conversational commands into direct executable Android system intents:
 * - Calendar Events ("meeting tomorrow at 3pm", "lunch with Sarah on Friday at 12:30")
 * - Alarms ("set alarm for 7am", "wake me up tomorrow at 6:30")
 * - Timers ("set a timer for 10 minutes", "set timer 15m", "timer 5 minutes 30 seconds")
 * - Reminders ("remind me to call John in 2 hours")
 * - Direct Messaging ("text Sarah: I will be late", "message Dave on WhatsApp saying hi")
 * - Phone Calls ("call Mom", "dial 555-0199")
 * - Navigation ("directions to Central Park", "navigate to work", "directions home")
 */
object NaturalLanguageIntentParser {

    // Compiled once: parse() runs on every keystroke, so no pattern may be built per call.
    private val TIMER_PREFIXES = listOf(
        "set a timer for ", "set a timer ", "start a timer for ", "start a timer ",
        "set timer for ", "set timer ", "start timer for ", "start timer ", "timer for ", "timer "
    )
    private val HOME_NAVIGATION_PHRASES = setOf("directions home", "navigate home", "take me home", "route home")
    private val TIMER_HOURS_REGEX =Regex("(\\d+)\\s*(?:h|hr|hours?)")
    private val TIMER_MINUTES_REGEX = Regex("(\\d+)\\s*(?:m|min|mins|minutes?)")
    private val TIMER_SECONDS_REGEX = Regex("(\\d+)\\s*(?:s|sec|secs|seconds?)")
    private val TIMER_CLOCK_REGEX = Regex("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")

    // Recipient names are Unicode letters/digits so "text José hi" and "message Zoë saying ok" parse.
    private val MESSAGE_REGEX = Regex(
        "^(?:message|text|send message to|send text to)\\s+([\\p{L}\\p{N}_\\s]+?)(?:\\s+(?:on|via)\\s+(whatsapp))?(?:\\s*[:,]\\s*|\\s+(?:that|saying|to)\\s+)(.+)$",
        RegexOption.IGNORE_CASE
    )

    private val RELATIVE_TIME_REGEX = Regex("in\\s+(\\d+)\\s*(mins?|minutes?|hours?|hrs?)", RegexOption.IGNORE_CASE)
    private val EXPLICIT_TIME_REGEX = Regex("\\b(?:(at)\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b", RegexOption.IGNORE_CASE)
    private val CLOCK_TIME_REGEX = Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b", RegexOption.IGNORE_CASE)
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val WEEKDAYS = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
    private val WEEKDAY_REGEX = Regex("(?:\\bon\\s+)?\\b(${WEEKDAYS.joinToString("|")})\\b", RegexOption.IGNORE_CASE)

    /** AlarmClock.EXTRA_LENGTH accepts 1..86400 seconds. */
    private const val MAX_TIMER_SECONDS = 24L * 60L * 60L

    sealed class ParsedIntent {
        data class CalendarEvent(
            val title: String,
            val startMillis: Long,
            val endMillis: Long,
            val intent: Intent
        ) : ParsedIntent()

        data class Alarm(
            val hour: Int,
            val minute: Int,
            val message: String,
            val intent: Intent
        ) : ParsedIntent()

        data class Timer(
            val durationSeconds: Int,
            val label: String,
            val intent: Intent
        ) : ParsedIntent()

        data class Reminder(
            val task: String,
            val triggerMillis: Long,
            val intent: Intent
        ) : ParsedIntent()

        data class DirectMessage(
            val recipientName: String,
            val body: String,
            val isWhatsApp: Boolean,
            val intent: Intent
        ) : ParsedIntent()

        data class PhoneCall(
            val contactNameOrNumber: String,
            val intent: Intent,
            val resolvedPhoneNumber: String? = null
        ) : ParsedIntent()

        data class Navigation(
            val destination: String,
            val intent: Intent
        ) : ParsedIntent()
    }

    /** [contactLookup] resolves a name in "call ..." and "text ..." to a contact; without it only numbers resolve. */
    fun parse(
        rawQuery: String,
        currentTimeMs: Long = System.currentTimeMillis(),
        contactLookup: ((String) -> com.pixel.intelligentsearch.core.data.ContactItem?)? = null
    ): ParsedIntent? {
        val query = rawQuery.trim()
        if (query.isEmpty()) return null
        val lower = query.lowercase(Locale.ROOT)

        // 1. Timer Intent ("set timer 15m", "timer for 10 minutes", "timer 45s")
        parseTimer(lower)?.let { return it }

        // 2. Alarm Intent ("alarm 7am", "set alarm for 6:30 am", "wake me up at 8")
        parseAlarm(lower, currentTimeMs)?.let { return it }

        // 3. Reminder Intent ("remind me to ...")
        parseReminder(lower, query, currentTimeMs)?.let { return it }

        // 4. Calendar Meeting Intent ("meeting tomorrow at 3pm", "add event lunch on Friday at 1pm")
        parseCalendar(lower, query, currentTimeMs)?.let { return it }

        // 5. Direct Messaging Intent ("message ...", "text ...")
        parseDirectMessage(query, contactLookup)?.let { return it }

        // 6. Direct Phone Call Intent ("call ...", "dial ...")
        parsePhoneCall(lower, query, contactLookup)?.let { return it }

        // 7. Direct Navigation Intent ("directions to ...", "navigate to ...")
        parseNavigation(lower, query)?.let { return it }

        return null
    }

    // ---------------------------------------------------------------------------------------------
    // TIMER PARSER
    // ---------------------------------------------------------------------------------------------

    private fun parseTimer(lower: String): ParsedIntent.Timer? {
        val matchedPrefix = TIMER_PREFIXES.firstOrNull { lower.startsWith(it) } ?: return null
        val remainder = lower.removePrefix(matchedPrefix).trim()

        var totalSeconds = 0L
        var matched = false

        val hrMatch = TIMER_HOURS_REGEX.find(remainder)
        if (hrMatch != null) {
            totalSeconds += unitSeconds(hrMatch, 3600L)
            matched = true
        }

        val minMatch = TIMER_MINUTES_REGEX.find(remainder)
        if (minMatch != null) {
            totalSeconds += unitSeconds(minMatch, 60L)
            matched = true
        }

        val secMatch = TIMER_SECONDS_REGEX.find(remainder)
        if (secMatch != null) {
            totalSeconds += unitSeconds(secMatch, 1L)
            matched = true
        }

        // Fallbacks: "timer 15" -> 15 minutes, "timer 1:30" -> 1m 30s, "timer 1:05:00" -> 1h 5m.
        // Only a single number or a clock form is accepted; concatenating every digit turned
        // "timer 1:30" into 130 minutes.
        if (!matched) {
            val clock = TIMER_CLOCK_REGEX.matchEntire(remainder)
            if (clock != null) {
                val parts = clock.groupValues.drop(1).filter { it.isNotEmpty() }.map { it.toLong() }
                if (parts.drop(1).any { it >= 60L }) return null
                totalSeconds = if (parts.size == 3) {
                    parts[0] * 3600L + parts[1] * 60L + parts[2]
                } else {
                    parts[0] * 60L + parts[1]
                }
                matched = true
            } else {
                val minutes = remainder.toLongOrNull()
                if (minutes != null && minutes > 0) {
                    totalSeconds = minutes.coerceAtMost(MAX_TIMER_SECONDS) * 60L
                    matched = true
                }
            }
        }

        // AlarmClock.EXTRA_LENGTH only accepts 1..86400 seconds. Anything larger is rejected rather than
        // clamped so a typo never silently becomes a different duration.
        if (!matched || totalSeconds <= 0L || totalSeconds > MAX_TIMER_SECONDS) return null
        val durationSeconds = totalSeconds.toInt()

        return ParsedIntent.Timer(
            durationSeconds = durationSeconds,
            label = "Timer ${formatDuration(durationSeconds)}",
            intent = timerIntent(durationSeconds)
        )
    }

    /**
     * Seconds contributed by one captured amount. The amount is capped before scaling so absurdly long
     * digit runs can never overflow the accumulator.
     */
    private fun unitSeconds(match: MatchResult, secondsPerUnit: Long): Long {
        val amount = match.groupValues[1].toLongOrNull() ?: 0L
        return amount.coerceAtMost(MAX_TIMER_SECONDS) * secondsPerUnit
    }

    private fun formatDuration(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return when {
            h > 0 && m > 0 && s > 0 -> "${h}h ${m}m ${s}s"
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 && s > 0 -> "${h}h ${s}s"
            h > 0 -> "${h}h"
            m > 0 && s > 0 -> "${m}m ${s}s"
            m > 0 -> "${m}m"
            else -> "${s}s"
        }
    }

    // ---------------------------------------------------------------------------------------------
    // ALARM PARSER
    // ---------------------------------------------------------------------------------------------

    private fun parseAlarm(lower: String, currentTimeMs: Long): ParsedIntent.Alarm? {
        val prefixes = listOf("set alarm for ", "set alarm at ", "set alarm ", "alarm for ", "alarm at ", "alarm ", "wake me up at ", "wake me up ")
        val matchedPrefix = prefixes.firstOrNull { lower.startsWith(it) } ?: return null
        val remainder = lower.removePrefix(matchedPrefix).trim()

        // "alarm in 10 minutes" is a time from now, not 10 o'clock.
        val relative = RELATIVE_TIME_REGEX.find(remainder)
        val time = if (relative != null) {
            val amount = relative.groupValues[1].toIntOrNull() ?: return null
            val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMs }
            cal.add(if (relative.groupValues[2].startsWith("h", ignoreCase = true)) Calendar.HOUR_OF_DAY else Calendar.MINUTE, amount)
            Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        } else {
            extractTime(remainder) ?: return null
        }

        val intent = alarmIntent(time.first, time.second)

        val formattedTime = String.format(Locale.US, "%02d:%02d", time.first, time.second)
        return ParsedIntent.Alarm(
            hour = time.first,
            minute = time.second,
            message = "Alarm for $formattedTime",
            intent = intent
        )
    }

    // ---------------------------------------------------------------------------------------------
    // REMINDER PARSER
    // ---------------------------------------------------------------------------------------------

    private fun parseReminder(lower: String, original: String, currentTimeMs: Long): ParsedIntent.Reminder? {
        if (!lower.startsWith("remind me to ") && !lower.startsWith("reminder to ") && !lower.startsWith("remind me ")) return null

        val prefixLen = if (lower.startsWith("remind me to ")) 13 else if (lower.startsWith("reminder to ")) 12 else 10
        val rawTask = original.substring(prefixLen).trim()

        val temporalResult = extractTemporalInfo(rawTask, currentTimeMs)
        val cleanTask = temporalResult.cleanText.ifBlank { rawTask }
        val triggerTime = temporalResult.timestampMs

        return ParsedIntent.Reminder(
            task = cleanTask,
            triggerMillis = triggerTime,
            intent = reminderIntent(cleanTask, triggerTime)
        )
    }

    // ---------------------------------------------------------------------------------------------
    // CALENDAR PARSER
    // ---------------------------------------------------------------------------------------------

    private fun parseCalendar(lower: String, original: String, currentTimeMs: Long): ParsedIntent.CalendarEvent? {
        val calendarTriggers = listOf("add meeting ", "new meeting ", "meeting ", "add event ", "schedule ", "new event ")
        val matchedTrigger = calendarTriggers.firstOrNull { lower.startsWith(it) } ?: return null

        val rawBody = original.substring(matchedTrigger.length).trim()
        val temporalResult = extractTemporalInfo(rawBody, currentTimeMs)

        val cleanTitle = temporalResult.cleanText.ifBlank { rawBody }
        val startMs = temporalResult.timestampMs
        val endMs = startMs + 60 * 60 * 1000 // 1-hour duration

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, cleanTitle)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs)
        }

        return ParsedIntent.CalendarEvent(
            title = cleanTitle,
            startMillis = startMs,
            endMillis = endMs,
            intent = intent
        )
    }

    // ---------------------------------------------------------------------------------------------
    // DIRECT MESSAGING & CALL PARSERS
    // ---------------------------------------------------------------------------------------------

    private fun parseDirectMessage(
        original: String,
        contactLookup: ((String) -> com.pixel.intelligentsearch.core.data.ContactItem?)?
    ): ParsedIntent.DirectMessage? {
        val match = MESSAGE_REGEX.find(original) ?: return null

        val recipient = match.groupValues[1].trim()
        val isWhatsApp = match.groupValues[2].equals("whatsapp", ignoreCase = true)
        val body = match.groupValues[3].trim()

        // Resolve the recipient to a number the same way calls do, so the composer opens addressed to them.
        val number = resolveRecipientNumber(recipient, contactLookup)

        val intent = if (isWhatsApp) {
            // WhatsApp needs the full international number; without a country code it rejects the link, so
            // only address the chat when the stored number has one and otherwise let the user pick.
            val phoneParam = number?.takeIf { it.startsWith("+") }?.filter { it.isDigit() }
                ?.takeIf { it.isNotEmpty() }?.let { "phone=$it&" } ?: ""
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?${phoneParam}text=${java.net.URLEncoder.encode(body, "UTF-8")}")
                setPackage("com.whatsapp")
            }
        } else {
            Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:" + (number?.let { Uri.encode(it) } ?: ""))
                putExtra("sms_body", body)
            }
        }

        return ParsedIntent.DirectMessage(
            recipientName = recipient,
            body = body,
            isWhatsApp = isWhatsApp,
            intent = intent
        )
    }

    /** A dialable number for [target]: the target itself if it is one, else a matching contact's number. */
    private fun resolveRecipientNumber(
        target: String,
        contactLookup: ((String) -> com.pixel.intelligentsearch.core.data.ContactItem?)?
    ): String? {
        if (target.isEmpty()) return null
        if (target.all { it.isDigit() || it in "+- ()" }) {
            return target.filter { it.isDigit() || it == '+' }.takeIf { it.count(Char::isDigit) >= 3 }
        }
        val contact = contactLookup?.invoke(target)?.takeIf { it.phoneNumber.isNotBlank() }
        return contact?.phoneNumber?.filter { it.isDigit() || it == '+' }?.takeIf { it.isNotEmpty() }
    }

    private fun parsePhoneCall(
        lower: String,
        original: String,
        contactLookup: ((String) -> com.pixel.intelligentsearch.core.data.ContactItem?)?
    ): ParsedIntent.PhoneCall? {
        val callTriggers = listOf("call ", "dial ", "phone ")
        val matchedTrigger = callTriggers.firstOrNull { lower.startsWith(it) } ?: return null
        val target = original.substring(matchedTrigger.length).trim()
        if (target.isEmpty()) return null

        val isNumeric = target.all { it.isDigit() || it in "+- ()" }
        val cleanNumber = if (isNumeric) {
            target.filter { it.isDigit() || it == '+' }.takeIf { it.count(Char::isDigit) >= 3 }
        } else null

        val matchedContact = if (cleanNumber == null) {
            contactLookup?.invoke(target)?.takeIf { it.phoneNumber.isNotBlank() }
        } else null

        if (cleanNumber == null && matchedContact == null) return null

        // A contact's number is dialled as saved: service codes need their '*' and '#' ("*86#"), and built into a
        // "tel:" link with Uri.parse, everything from '#' on would be dropped.
        val number = cleanNumber ?: matchedContact!!.phoneNumber.trim()
        val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
        return ParsedIntent.PhoneCall(
            contactNameOrNumber = matchedContact?.name ?: target,
            intent = intent,
            resolvedPhoneNumber = matchedContact?.phoneNumber ?: cleanNumber
        )
    }

    // ---------------------------------------------------------------------------------------------
    // NAVIGATION PARSER
    // ---------------------------------------------------------------------------------------------

    private fun parseNavigation(lower: String, original: String): ParsedIntent.Navigation? {
        // "directions home" has no "to"; Maps resolves "Home" to the user's saved home address.
        val dest = if (lower.trim() in HOME_NAVIGATION_PHRASES) {
            "Home"
        } else {
            val navTriggers = listOf("directions to ", "navigate to ", "route to ", "take me to ")
            val matchedTrigger = navTriggers.firstOrNull { lower.startsWith(it) } ?: return null
            original.substring(matchedTrigger.length).trim()
        }
        if (dest.isEmpty()) return null

        val uri = Uri.parse("google.navigation:q=${java.net.URLEncoder.encode(dest, "UTF-8")}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }

        return ParsedIntent.Navigation(
            destination = dest,
            intent = intent
        )
    }

    // ---------------------------------------------------------------------------------------------
    // TEMPORAL EXTRACTION UTILITIES
    // ---------------------------------------------------------------------------------------------

    private data class TemporalResult(val timestampMs: Long, val cleanText: String)

    /** The day word a phrase named, which decides how a bare hour like "at 3" is read. */
    private enum class NamedDay { NONE, TODAY, TONIGHT, TOMORROW, WEEKDAY }

    private fun extractTemporalInfo(rawText: String, baseTimeMs: Long): TemporalResult {
        val cal = Calendar.getInstance().apply { timeInMillis = baseTimeMs }
        var text = rawText
        var namedDay = NamedDay.NONE
        val isTonight = text.contains("tonight", ignoreCase = true)

        // Check relative days
        val weekdayMatch = WEEKDAY_REGEX.find(text)
        if (text.contains("tomorrow", ignoreCase = true)) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            text = text.replace("tomorrow", "", ignoreCase = true)
            namedDay = NamedDay.TOMORROW
        } else if (text.contains("tonight", ignoreCase = true)) {
            cal.set(Calendar.HOUR_OF_DAY, 20)
            cal.set(Calendar.MINUTE, 0)
            text = text.replace("tonight", "", ignoreCase = true)
            namedDay = NamedDay.TONIGHT
        } else if (text.contains("today", ignoreCase = true)) {
            text = text.replace("today", "", ignoreCase = true)
            namedDay = NamedDay.TODAY
        } else if (weekdayMatch != null) {
            // "on Friday" means the next Friday; naming today's weekday means a week from today.
            val target = WEEKDAYS.indexOf(weekdayMatch.groupValues[1].lowercase(Locale.ROOT)) + Calendar.SUNDAY
            var daysAhead = (target - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
            if (daysAhead == 0) daysAhead = 7
            cal.add(Calendar.DAY_OF_YEAR, daysAhead)
            text = text.replace(weekdayMatch.value, "")
            namedDay = NamedDay.WEEKDAY
        }

        // Relative duration "in X minutes / hours"
        val relativeMatch = RELATIVE_TIME_REGEX.find(text)
        if (relativeMatch != null) {
            val amount = relativeMatch.groupValues[1].toIntOrNull() ?: 0
            val unit = relativeMatch.groupValues[2].lowercase()
            if (unit.startsWith("h")) {
                cal.add(Calendar.HOUR_OF_DAY, amount)
            } else {
                cal.add(Calendar.MINUTE, amount)
            }
            text = text.replace(relativeMatch.value, "")
        }

        // Explicit time "at 3pm", "at 15:30", "at 10:00 am", "at 3"
        val timeMatch = EXPLICIT_TIME_REGEX.findAll(text).firstOrNull {
            it.groupValues[1].isNotEmpty() || it.groupValues[3].isNotEmpty() || it.groupValues[4].isNotEmpty()
        }
        if (timeMatch != null) {
            var hour = timeMatch.groupValues[2].toIntOrNull() ?: 9
            val minute = timeMatch.groupValues[3].toIntOrNull() ?: 0
            val amPm = timeMatch.groupValues[4].lowercase()
            // "07:00" is 24-hour notation: the hour is meant exactly as written.
            val zeroPadded = timeMatch.groupValues[2].length == 2 && timeMatch.groupValues[2][0] == '0'

            if (amPm == "pm" && hour < 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0
            // Without am/pm: "tonight at 9" is 21:00. With no day or "today", "at 3" is the next 3 o'clock still
            // ahead. Tomorrow or a weekday has no "already passed" to go by, so 1-7 is read as the afternoon or
            // evening people mean ("tomorrow at 3") and 8-11 as the morning, unless the hour has a leading zero.
            if (amPm.isEmpty() && hour in 1..11) {
                if (isTonight) {
                    hour += 12
                } else if (relativeMatch == null) {
                    when (namedDay) {
                        NamedDay.TOMORROW, NamedDay.WEEKDAY -> if (hour <= 7 && !zeroPadded) hour += 12
                        else -> {
                            val morning = (cal.clone() as Calendar).apply {
                                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute.coerceIn(0, 59)); set(Calendar.SECOND, 0)
                            }
                            if (morning.timeInMillis < baseTimeMs && morning.timeInMillis + 12 * 3_600_000L > baseTimeMs) hour += 12
                        }
                    }
                }
            }

            // Calendar is lenient, so an out-of-range "at 27:90" would silently roll into another day.
            cal.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            cal.set(Calendar.MINUTE, minute.coerceIn(0, 59))
            cal.set(Calendar.SECOND, 0)
            text = text.replace(timeMatch.value, "")

            if (namedDay == NamedDay.NONE && relativeMatch == null && cal.timeInMillis < baseTimeMs) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val cleanTitle = text.replace(WHITESPACE_REGEX, " ").trim()
        return TemporalResult(cal.timeInMillis, cleanTitle)
    }

    private fun extractTime(text: String): Pair<Int, Int>? {
        val match = CLOCK_TIME_REGEX.find(text) ?: return null

        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val amPm = match.groupValues[3].lowercase()

        if (amPm.isNotEmpty() && hour !in 1..12) return null
        if (amPm == "pm" && hour < 12) hour += 12
        if (amPm == "am" && hour == 12) hour = 0

        if (hour !in 0..23 || minute !in 0..59) return null
        return Pair(hour, minute)
    }

    // ---------------------------------------------------------------------------------------------
    // ACTION INTENTS (shared with ShortActionPhrasing, which offers "15 min", "7:30am", "laundry in 2 hrs")
    // ---------------------------------------------------------------------------------------------

    /** The clock's timer for [durationSeconds] (1..86400), shown to the user to start rather than started silently. */
    internal fun timerIntent(durationSeconds: Int): Intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
        putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds)
        putExtra(AlarmClock.EXTRA_MESSAGE, "Intelligent Search Timer")
        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
    }

    /** The clock's new alarm at [hour]:[minute] (24-hour), shown to the user to confirm. */
    internal fun alarmIntent(hour: Int, minute: Int): Intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
        putExtra(AlarmClock.EXTRA_HOUR, hour)
        putExtra(AlarmClock.EXTRA_MINUTES, minute)
        putExtra(AlarmClock.EXTRA_MESSAGE, "Alarm")
        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
    }

    /** A reminder: a half-hour calendar event named [task] at [triggerMillis], opened in the calendar to save. */
    internal fun reminderIntent(task: String, triggerMillis: Long): Intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, task)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, triggerMillis)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, triggerMillis + 30 * 60 * 1000)
        putExtra(CalendarContract.Events.ALL_DAY, 0)
    }
}
