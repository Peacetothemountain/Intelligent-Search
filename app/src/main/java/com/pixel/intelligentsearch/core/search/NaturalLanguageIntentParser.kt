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
 * - Timers ("set timer 15m", "timer 5 minutes 30 seconds")
 * - Reminders ("remind me to call John in 2 hours")
 * - Direct Messaging ("text Sarah I will be late", "message Dave on WhatsApp")
 * - Phone Calls ("call Mom", "dial 555-0199")
 * - Navigation ("directions to Central Park", "navigate home")
 */
object NaturalLanguageIntentParser {

    sealed class ParsedIntent {
        data class CalendarEvent(
            val title: String,
            val startMillis: Long,
            val endMillis: Long,
            val description: String? = null,
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
            val intent: Intent
        ) : ParsedIntent()

        data class Navigation(
            val destination: String,
            val intent: Intent
        ) : ParsedIntent()
    }

    fun parse(rawQuery: String, currentTimeMs: Long = System.currentTimeMillis()): ParsedIntent? {
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
        parseDirectMessage(lower, query)?.let { return it }

        // 6. Direct Phone Call Intent ("call ...", "dial ...")
        parsePhoneCall(lower, query)?.let { return it }

        // 7. Direct Navigation Intent ("directions to ...", "navigate to ...")
        parseNavigation(lower, query)?.let { return it }

        return null
    }

    // ---------------------------------------------------------------------------------------------
    // TIMER PARSER
    // ---------------------------------------------------------------------------------------------

    private fun parseTimer(lower: String): ParsedIntent.Timer? {
        val timerPrefixes = listOf("timer for ", "set timer for ", "set timer ", "timer ")
        val matchedPrefix = timerPrefixes.firstOrNull { lower.startsWith(it) } ?: return null
        val remainder = lower.removePrefix(matchedPrefix).trim()

        var totalSeconds = 0
        var matched = false

        // Parse hours
        val hrMatch = Regex("(\\d+)\\s*(?:h|hr|hours?)").find(remainder)
        if (hrMatch != null) {
            totalSeconds += (hrMatch.groupValues[1].toIntOrNull() ?: 0) * 3600
            matched = true
        }

        // Parse minutes
        val minMatch = Regex("(\\d+)\\s*(?:m|min|mins|minutes?)").find(remainder)
        if (minMatch != null) {
            totalSeconds += (minMatch.groupValues[1].toIntOrNull() ?: 0) * 60
            matched = true
        }

        // Parse seconds
        val secMatch = Regex("(\\d+)\\s*(?:s|sec|secs|seconds?)").find(remainder)
        if (secMatch != null) {
            totalSeconds += secMatch.groupValues[1].toIntOrNull() ?: 0
            matched = true
        }

        // Simple number fallback e.g. "timer 15" -> 15 minutes
        if (!matched) {
            val simpleDigits = remainder.filter { it.isDigit() }.toIntOrNull()
            if (simpleDigits != null && simpleDigits > 0) {
                totalSeconds = simpleDigits * 60
                matched = true
            }
        }

        if (!matched || totalSeconds <= 0) return null

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, totalSeconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Intelligent Search Timer")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }

        return ParsedIntent.Timer(
            durationSeconds = totalSeconds,
            label = "Timer ${formatDuration(totalSeconds)}",
            intent = intent
        )
    }

    private fun formatDuration(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return if (m > 0 && s > 0) "${m}m ${s}s" else if (m > 0) "${m}m" else "${s}s"
    }

    // ---------------------------------------------------------------------------------------------
    // ALARM PARSER
    // ---------------------------------------------------------------------------------------------

    private fun parseAlarm(lower: String, currentTimeMs: Long): ParsedIntent.Alarm? {
        val prefixes = listOf("set alarm for ", "set alarm at ", "set alarm ", "alarm for ", "alarm at ", "alarm ", "wake me up at ")
        val matchedPrefix = prefixes.firstOrNull { lower.startsWith(it) } ?: return null
        val remainder = lower.removePrefix(matchedPrefix).trim()

        val time = extractTime(remainder) ?: return null

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, time.first)
            putExtra(AlarmClock.EXTRA_MINUTES, time.second)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Alarm")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }

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

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, cleanTask)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, triggerTime)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, triggerTime + 30 * 60 * 1000)
            putExtra(CalendarContract.Events.ALL_DAY, 0)
        }

        return ParsedIntent.Reminder(
            task = cleanTask,
            triggerMillis = triggerTime,
            intent = intent
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
            description = "Created by Intelligent Search",
            intent = intent
        )
    }

    // ---------------------------------------------------------------------------------------------
    // DIRECT MESSAGING & CALL PARSERS
    // ---------------------------------------------------------------------------------------------

    private fun parseDirectMessage(lower: String, original: String): ParsedIntent.DirectMessage? {
        val messageRegex = Regex("^(?:message|text|send message to|send text to)\\s+([a-zA-Z0-9_\\s]+?)(?:\\s+(?:on|via)\\s+(whatsapp))?\\s+(?:that|saying|to|:|,)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val match = messageRegex.find(original) ?: return null

        val recipient = match.groupValues[1].trim()
        val isWhatsApp = match.groupValues[2].equals("whatsapp", ignoreCase = true)
        val body = match.groupValues[3].trim()

        val intent = if (isWhatsApp) {
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?text=${java.net.URLEncoder.encode(body, "UTF-8")}")
                setPackage("com.whatsapp")
            }
        } else {
            Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:")
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

    private fun parsePhoneCall(lower: String, original: String): ParsedIntent.PhoneCall? {
        val callTriggers = listOf("call ", "dial ", "phone ")
        val matchedTrigger = callTriggers.firstOrNull { lower.startsWith(it) } ?: return null
        val target = original.substring(matchedTrigger.length).trim()
        if (target.isEmpty()) return null

        val uri = if (target.all { it.isDigit() || it in "+- ()" }) {
            Uri.parse("tel:${target.filter { it.isDigit() || it == '+' }}")
        } else {
            Uri.parse("tel:")
        }

        val intent = Intent(Intent.ACTION_DIAL, uri)
        return ParsedIntent.PhoneCall(
            contactNameOrNumber = target,
            intent = intent
        )
    }

    // ---------------------------------------------------------------------------------------------
    // NAVIGATION PARSER
    // ---------------------------------------------------------------------------------------------

    private fun parseNavigation(lower: String, original: String): ParsedIntent.Navigation? {
        val navTriggers = listOf("directions to ", "navigate to ", "route to ")
        val matchedTrigger = navTriggers.firstOrNull { lower.startsWith(it) } ?: return null
        val dest = original.substring(matchedTrigger.length).trim()
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

    private fun extractTemporalInfo(rawText: String, baseTimeMs: Long): TemporalResult {
        val cal = Calendar.getInstance().apply { timeInMillis = baseTimeMs }
        var text = rawText

        // Check relative days
        if (text.contains("tomorrow", ignoreCase = true)) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            text = text.replace("tomorrow", "", ignoreCase = true)
        } else if (text.contains("tonight", ignoreCase = true)) {
            cal.set(Calendar.HOUR_OF_DAY, 20)
            cal.set(Calendar.MINUTE, 0)
            text = text.replace("tonight", "", ignoreCase = true)
        }

        // Relative duration "in X minutes / hours"
        val relativeMatch = Regex("in\\s+(\\d+)\\s*(mins?|minutes?|hours?|hrs?)", RegexOption.IGNORE_CASE).find(text)
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

        // Explicit time "at 3pm", "at 15:30", "at 10:00 am"
        val timeRegex = Regex("(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE)
        val timeMatch = timeRegex.findAll(text).firstOrNull { it.groupValues[3].isNotEmpty() || it.groupValues[2].isNotEmpty() }
        if (timeMatch != null) {
            var hour = timeMatch.groupValues[1].toIntOrNull() ?: 9
            val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
            val amPm = timeMatch.groupValues[3].lowercase()

            if (amPm == "pm" && hour < 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0

            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            text = text.replace(timeMatch.value, "")
        }

        val cleanTitle = text.replace("\\s+".toRegex(), " ").trim()
        return TemporalResult(cal.timeInMillis, cleanTitle)
    }

    private fun extractTime(text: String): Pair<Int, Int>? {
        val regex = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE)
        val match = regex.find(text) ?: return null

        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val amPm = match.groupValues[3].lowercase()

        if (amPm == "pm" && hour < 12) hour += 12
        if (amPm == "am" && hour == 12) hour = 0

        return Pair(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }
}
