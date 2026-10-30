package com.pixel.intelligentsearch.core.search

import com.pixel.intelligentsearch.core.util.Durations
import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import java.util.Calendar
import java.util.Locale

/**
 * On-device Natural Language Intent Parser and Date/Time Extractor.
 * Parses conversational commands into direct executable Android system intents:
 * - Calendar Events ("meeting tomorrow at 3pm", "lunch with Sarah on Friday at 12:30", "meeting on dec 5 at 3pm")
 * - Alarms ("set alarm for 7am", "wake me up tomorrow at 6:30", "alarm for 20 minutes")
 * - Timers ("set a timer for 10 minutes", "set timer 15m", "timer 5 minutes 30 seconds")
 * - Reminders ("remind me to call John in 2 hours")
 * - Direct Messaging ("text Sarah: I will be late", "message Dave on WhatsApp saying hi", "text mom hello")
 * - Phone Calls ("call Mom", "dial 555-0199")
 * - Navigation ("directions to Central Park", "navigate to work", "directions home")
 * - Media ("play lo-fi beats", "play Taylor Swift on Spotify", "play jazz on YouTube Music")
 */
object NaturalLanguageIntentParser {

    // Compiled once: parse() runs on every keystroke, so no pattern may be built per call.
    private val TIMER_PREFIXES = listOf(
        "set a timer for ", "set a timer ", "start a timer for ", "start a timer ",
        "set timer for ", "set timer ", "start timer for ", "start timer ", "timer for ", "timer "
    )
    private val HOME_NAVIGATION_PHRASES = setOf("directions home", "navigate home", "take me home", "route home")
    // Amounts may have a decimal point or comma ("1.5 hours", "2,5 min"); the guard keeps "1.5 hours" from also
    // reading as 5 hours.
    private const val TIMER_AMOUNT = "(?<![\\d.,])(\\d+(?:[.,]\\d+)?)"
    private val TIMER_HOURS_REGEX = Regex("$TIMER_AMOUNT\\s*(?:h|hr|hours?)")
    private val TIMER_MINUTES_REGEX = Regex("$TIMER_AMOUNT\\s*(?:m|min|mins|minutes?)")
    private val TIMER_SECONDS_REGEX = Regex("$TIMER_AMOUNT\\s*(?:s|sec|secs|seconds?)")
    private val TIMER_CLOCK_REGEX = Regex("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")
    // A bare number after the last unit: "2 hours 30" is 2 h 30 min, "1 m 30" is 1 min 30 s.
    private val TIMER_TRAILING_AMOUNT_REGEX = Regex("(?<![\\d.,])(\\d+)$")
    // The amount in words: "half an hour", "a half hour", "an hour", "one minute".
    private val TIMER_WORD_AMOUNT_REGEX = Regex("^(?:(half)\\s+(?:an?\\s+)?|an?\\s+(half\\s+)?|one\\s+)(hour|minute)$")
    // A timer can't run backward: "timer -5 minutes" is no timer rather than a 5 minute one.
    private val NEGATIVE_AMOUNT_REGEX = Regex("-\\s*\\d")

    // Recipient names are Unicode letters/digits so "text José hi" and "message Zoë saying ok" parse, with the marks
    // names and numbers are written with: "O'Brien", "Jean-Luc", "+1 (415) 555-0100".
    private const val RECIPIENT = "[\\p{L}\\p{N}_\\s'’.+\\-()]"
    private const val MESSAGE_VERBS = "message|text|send message to|send text to|whatsapp"
    private val MESSAGE_REGEX = Regex(
        "^($MESSAGE_VERBS)\\s+($RECIPIENT+?)(?:\\s+(?:on|via)\\s+(whatsapp))?(?:\\s*[:,]\\s*|\\s+(?:that|saying|to)\\s+)(.+)$",
        RegexOption.IGNORE_CASE
    )
    // "text mom hello": nothing marks where the name ends, so it is only known once a contact (or a number) answers.
    private val MESSAGE_WITHOUT_SEPARATOR_REGEX = Regex("^($MESSAGE_VERBS)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val WORD_REGEX = Regex("\\S+")
    /** The most words a name is tried with when nothing marks where it ends ("text mary ann smith hi"). */
    private const val MAX_RECIPIENT_WORDS = 4

    // The units a time from now is read in: "10 minutes", "1.5 hours", "2 days", "an hour". The unit must end there:
    // "in 5 minutes" used to match as "in 5 min" and leave "utes" in the title.
    private const val RELATIVE_UNITS = "(minutes?|mins?|hours?|hrs?|h|days?|weeks?|wks?|m)\\b"
    // "in 10 minutes", "in an hour". "In" must be a word of its own: "check the cabin 2 hours" is not "in 2 hours".
    private val RELATIVE_TIME_REGEX = Regex("\\bin\\s+(\\d+(?:[.,]\\d+)?\\s*|an?\\s+)$RELATIVE_UNITS", RegexOption.IGNORE_CASE)
    // An alarm given a duration can only mean that long from now, so "alarm for 10 minutes" needs no "in". "A" or
    // "an" still does, so "alarm at a minute past 7" isn't a minute from now.
    private val ALARM_DURATION_REGEX = Regex(
        "(?:\\bin\\s+)?((?<![\\d.,])\\d+(?:[.,]\\d+)?\\s*|(?<=\\bin\\s{1,9})an?\\s+)$RELATIVE_UNITS",
        RegexOption.IGNORE_CASE
    )
    // An amount (a number or number words) and any unit of time, whether it can be read or not.
    private const val SPOKEN_DURATION =
        "(?:(?:\\d+(?:[.,]\\d+)?|(?:a|an|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|" +
            "thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|" +
            "eighty|ninety|hundred|half|couple|few|several|and|of)\\b)[\\s-]*){1,4}" +
            "(?:seconds?|secs?|minutes?|mins?|hours?|hrs?|days?|weeks?|wks?|fortnights?|months?|years?|yrs?)\\b"
    // A time from now that can't be read ("in two hours", "in 30 seconds", "in 3 months"): the phrase is then not
    // taken as an event or reminder at all rather than one set for now. "In" must be followed by an amount (a number
    // or a number word), so words that merely sit in the task, "fill in hours tomorrow at 9", are left to be read.
    private val UNREAD_RELATIVE_TIME_REGEX = Regex("\\bin\\s+$SPOKEN_DURATION", RegexOption.IGNORE_CASE)
    // The same for an alarm, "in" or not: "alarm 5 seconds" is not 5 o'clock.
    private val UNREAD_ALARM_DURATION_REGEX = Regex("\\b$SPOKEN_DURATION", RegexOption.IGNORE_CASE)
    // "am" or "pm", also written "a.m.", "p.m" or "p m"; only the letter is kept.
    private const val MERIDIEM = "\\s*([ap])\\.?\\s?m\\b\\.?"
    // Minutes follow a colon or a dot ("6:30", "6.30pm"). In an event or reminder a dot counts only with "at" or
    // am/pm, so "budget 12.50" is not a time.
    private val EXPLICIT_TIME_REGEX = Regex("\\b(?:(at)\\s+)?(\\d{1,2})(?:([:.])(\\d{2}))?(?:$MERIDIEM|\\b)", RegexOption.IGNORE_CASE)
    // An alarm is only a time of day, so a space can part the hour from the minutes too: "alarm 6 30".
    private val CLOCK_TIME_REGEX = Regex("\\b(\\d{1,2})(?:[:.\\s](\\d{2}))?(?:$MERIDIEM|\\b)", RegexOption.IGNORE_CASE)
    private val NOON_OR_MIDNIGHT_REGEX = Regex("\\b(noon|midday|midnight)\\b", RegexOption.IGNORE_CASE)
    // Whole words only, so "read todays report" keeps its "todays" and "today's report" its "'s".
    private val TOMORROW_REGEX = Regex("\\btomorrow\\b(?!['’]\\w)", RegexOption.IGNORE_CASE)
    private val TONIGHT_REGEX = Regex("\\btonight\\b(?!['’]\\w)", RegexOption.IGNORE_CASE)
    private val TODAY_REGEX = Regex("\\btoday\\b(?!['’]\\w)", RegexOption.IGNORE_CASE)
    // A month and day, either way round: "dec 5", "december 5th, 2027", "on the 5th of december", "5 dec". Numbers
    // alone ("12/5") are left be: whether the month or the day comes first depends on where the writer is from.
    private const val MONTHS =
        "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|" +
            "oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\b\\.?"
    private const val DATE_YEAR = "(?:,?\\s+(20\\d{2})\\b)?"
    // The day is not an hour: "dec 5 pm" and "dec 5:30" are left to be read as times.
    private val MONTH_DAY_REGEX = Regex(
        "(?:\\bon\\s+)?\\b$MONTHS\\s*(\\d{1,2})(?:st|nd|rd|th)?\\b(?!\\s*(?:[:.]\\d|[ap]\\.?\\s?m\\b))$DATE_YEAR",
        RegexOption.IGNORE_CASE
    )
    private val DAY_MONTH_REGEX = Regex(
        "(?:\\bon\\s+)?(?:\\bthe\\s+)?\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?$MONTHS$DATE_YEAR",
        RegexOption.IGNORE_CASE
    )
    private val MONTH_ABBREVIATIONS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val WEEKDAYS = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
    private val WEEKDAY_REGEX = Regex("(?:\\bon\\s+)?\\b(${WEEKDAYS.joinToString("|")})\\b", RegexOption.IGNORE_CASE)

    /** AlarmClock.EXTRA_LENGTH accepts 1..86400 seconds. */
    private const val MAX_TIMER_SECONDS = 24L * 60L * 60L

    private const val MINUTES_PER_DAY = 24 * 60

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

        /** [fallbackIntent] opens when [intent]'s app (WhatsApp) isn't installed. */
        data class DirectMessage(
            val recipientName: String,
            val body: String,
            val isWhatsApp: Boolean,
            val intent: Intent,
            val fallbackIntent: Intent? = null
        ) : ParsedIntent()

        data class PhoneCall(
            val contactNameOrNumber: String,
            val intent: Intent,
            val resolvedPhoneNumber: String? = null
        ) : ParsedIntent()

        /** [fallbackIntent] opens when [intent]'s app (Google Maps) isn't installed. */
        data class Navigation(
            val destination: String,
            val intent: Intent,
            val fallbackIntent: Intent? = null
        ) : ParsedIntent()

        /**
         * Plays [query] from a search in a media app: [appName]'s when one was named ([intent] is then sent to that
         * app, and [fallbackIntent], to any player, when it isn't installed), else whichever player answers.
         */
        data class MediaPlay(
            val query: String,
            val appName: String?,
            val intent: Intent,
            val fallbackIntent: Intent? = null
        ) : ParsedIntent()
    }

    /** Media apps "play ... on <app>" can name, as typed, with their shown name and package. */
    private val MEDIA_APPS = mapOf(
        "spotify" to ("Spotify" to "com.spotify.music"),
        "youtube music" to ("YouTube Music" to "com.google.android.apps.youtube.music"),
        "yt music" to ("YouTube Music" to "com.google.android.apps.youtube.music")
    )
    private val PLAY_ON_APP_REGEX = Regex(
        "^play (.+?) (?:on|in|with|using|via) (spotify|youtube music|yt music|youtube)$",
        RegexOption.IGNORE_CASE
    )
    /** "play store", "play games", "play books": searches for Google Play's apps, not for something to play. */
    private val NOT_MEDIA_FIRST_WORDS = setOf(
        "store", "games", "game", "console", "books", "movies", "services", "protect", "points", "pass", "billing"
    )

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

        // 8. Media Intent ("play ...", "play ... on spotify")
        parseMediaPlay(query)?.let { return it }

        return null
    }

    // ---------------------------------------------------------------------------------------------
    // TIMER PARSER
    // ---------------------------------------------------------------------------------------------

    private fun parseTimer(lower: String): ParsedIntent.Timer? {
        val matchedPrefix = TIMER_PREFIXES.firstOrNull { lower.startsWith(it) } ?: return null
        val remainder = lower.removePrefix(matchedPrefix).trim()
        if (NEGATIVE_AMOUNT_REGEX.containsMatchIn(remainder)) return null

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

        // "2 hours 30" and "1 m 30": a bare number after the last unit counts in the next smaller one. More than 59
        // of it is a typo, not a longer timer.
        val lastUnit = listOfNotNull(hrMatch, minMatch, secMatch).maxByOrNull { it.range.last }
        val trailing = TIMER_TRAILING_AMOUNT_REGEX.find(remainder)
        if (lastUnit != null && trailing != null && trailing.range.first > lastUnit.range.last && lastUnit !== secMatch) {
            val amount = trailing.value.toLongOrNull()?.takeIf { it < 60L } ?: return null
            totalSeconds += amount * if (lastUnit === hrMatch) 60L else 1L
        }

        // Fallbacks: "timer 15" -> 15 minutes, "timer 1:30" -> 1m 30s, "timer 1:05:00" -> 1h 5m, "timer half an
        // hour" -> 30m. Only a single number or a clock form is accepted; concatenating every digit turned
        // "timer 1:30" into 130 minutes.
        if (!matched) {
            val clock = TIMER_CLOCK_REGEX.matchEntire(remainder)
            val words = TIMER_WORD_AMOUNT_REGEX.matchEntire(remainder)
            if (words != null) {
                val half = words.groupValues[1].isNotEmpty() || words.groupValues[2].isNotEmpty()
                val unitSeconds = if (words.groupValues[3] == "hour") 3600L else 60L
                totalSeconds = if (half) unitSeconds / 2 else unitSeconds
                matched = true
            } else if (clock != null) {
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
            label = "Timer ${Durations.short(durationSeconds.toLong())}",
            intent = timerIntent(durationSeconds)
        )
    }

    /**
     * Seconds contributed by one captured amount. The amount is capped before scaling so absurdly long
     * digit runs can never overflow the accumulator.
     */
    private fun unitSeconds(match: MatchResult, secondsPerUnit: Long): Long {
        val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0
        return Math.round(amount.coerceAtMost(MAX_TIMER_SECONDS.toDouble()) * secondsPerUnit)
    }

    // ---------------------------------------------------------------------------------------------
    // ALARM PARSER
    // ---------------------------------------------------------------------------------------------

    private fun parseAlarm(lower: String, currentTimeMs: Long): ParsedIntent.Alarm? {
        val prefixes = listOf("set alarm for ", "set alarm at ", "set alarm ", "alarm for ", "alarm at ", "alarm ", "wake me up at ", "wake me up ")
        val matchedPrefix = prefixes.firstOrNull { lower.startsWith(it) } ?: return null
        val remainder = lower.removePrefix(matchedPrefix).trim()

        // "alarm in 10 minutes" and "alarm for 10 minutes" are a time from now, not 10 o'clock. An alarm is only an
        // hour and minute, so a day or more ahead ("alarm 2 days") can't be set, and "alarm 5 seconds" is not 5 o'clock.
        val relative = ALARM_DURATION_REGEX.find(remainder)
        val time = if (relative != null) {
            val minutes = relativeMinutes(relative) ?: return null
            if (minutes >= MINUTES_PER_DAY) return null
            val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMs }
            cal.add(Calendar.MINUTE, Math.round(minutes).toInt())
            Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        } else {
            if (UNREAD_ALARM_DURATION_REGEX.containsMatchIn(remainder)) return null
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

        val temporalResult = extractTemporalInfo(rawTask, currentTimeMs) ?: return null
        // "remind me tomorrow at 9" names no task; its time words aren't one.
        val cleanTask = temporalResult.cleanText.ifBlank { "Reminder" }
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
        val temporalResult = extractTemporalInfo(rawBody, currentTimeMs) ?: return null

        // "meeting tomorrow at 3pm" names no event beyond the meeting itself; its time words aren't a title.
        val cleanTitle = temporalResult.cleanText.ifBlank { if ("meeting" in matchedTrigger) "Meeting" else "Event" }
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
        // "text Sarah: hi", "message Dave saying ok": the words mark where the name ends. When that name finds no one
        // but a shorter one does ("text mom hello to everyone"), or nothing marks it ("text mom hello"), the name is
        // the most words that find a contact or make a number.
        val separated = MESSAGE_REGEX.find(original)
        val separatedTo = separated?.let { resolveRecipient(it.groupValues[2].trim(), contactLookup) }
        val verb: String
        val typedName: String
        val body: String
        val recipient: Recipient?
        var onWhatsApp = false
        val split = if (separated == null || separatedTo == null) splitAtRecipient(original, contactLookup) else null
        if (split != null) {
            verb = split.verb
            typedName = split.name
            recipient = split.recipient
            body = split.body
        } else {
            separated ?: return null
            verb = separated.groupValues[1]
            typedName = separated.groupValues[2].trim()
            recipient = separatedTo
            onWhatsApp = separated.groupValues[3].isNotEmpty()
            body = separated.groupValues[4].trim()
        }
        val whatsAppVerb = verb.equals("whatsapp", ignoreCase = true)
        // Searches start with "whatsapp" too ("whatsapp, backup chats"); as a verb it needs someone to message.
        if (whatsAppVerb && recipient == null) return null
        val isWhatsApp = onWhatsApp || whatsAppVerb

        // Resolved the same way calls are, so the composer opens addressed to them.
        val number = recipient?.number

        val sms = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:" + (number?.let { Uri.encode(it) } ?: ""))
            putExtra("sms_body", body)
        }
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
            sms
        }

        return ParsedIntent.DirectMessage(
            // The contact's own name, as "call" shows it, rather than however it was typed.
            recipientName = recipient?.name ?: typedName,
            body = body,
            isWhatsApp = isWhatsApp,
            intent = intent,
            // Without WhatsApp the message still goes, as a text.
            fallbackIntent = sms.takeIf { isWhatsApp }
        )
    }

    /** Who a message goes to: the name to show and the number to send to. */
    private data class Recipient(val name: String, val number: String)

    /** A message with nothing marking where the name ends, split where the name finds someone. */
    private data class SplitMessage(val verb: String, val name: String, val recipient: Recipient, val body: String)

    /**
     * "text mom hello" read as "mom" and "hello": the most leading words (up to [MAX_RECIPIENT_WORDS], leaving at least
     * one for the message) that find a contact or make a number. Null when none do, so a search like "text to speech
     * app" is never taken for a message.
     */
    private fun splitAtRecipient(
        original: String,
        contactLookup: ((String) -> com.pixel.intelligentsearch.core.data.ContactItem?)?
    ): SplitMessage? {
        val match = MESSAGE_WITHOUT_SEPARATOR_REGEX.find(original) ?: return null
        val rest = match.groupValues[2]
        val words = WORD_REGEX.findAll(rest).toList()
        for (count in minOf(words.size - 1, MAX_RECIPIENT_WORDS) downTo 1) {
            val name = rest.substring(0, words[count - 1].range.last + 1).trim()
            val recipient = resolveRecipient(name, contactLookup) ?: continue
            return SplitMessage(match.groupValues[1], name, recipient, rest.substring(words[count].range.first).trim())
        }
        return null
    }

    /** [target] as someone to message: a number if it is one, else a matching contact with a number. */
    private fun resolveRecipient(
        target: String,
        contactLookup: ((String) -> com.pixel.intelligentsearch.core.data.ContactItem?)?
    ): Recipient? {
        if (target.isEmpty()) return null
        if (target.all { it.isDigit() || it in "+- ()" }) {
            val number = target.filter { it.isDigit() || it == '+' }.takeIf { it.count(Char::isDigit) >= 3 } ?: return null
            return Recipient(target, number)
        }
        val contact = contactLookup?.invoke(target)?.takeIf { it.phoneNumber.isNotBlank() } ?: return null
        val number = contact.phoneNumber.filter { it.isDigit() || it == '+' }.takeIf { it.isNotEmpty() } ?: return null
        return Recipient(contact.name.ifBlank { target }, number)
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
            intent = intent,
            // Any maps app answers a geo: search for the place when Google Maps isn't there. Built as a plain view of
            // a geo: address, never parsed from text, so nothing typed can make it another kind of intent.
            fallbackIntent = Intent(Intent.ACTION_VIEW, geoSearchUri(dest))
        )
    }

    /**
     * "geo:0,0?q=<place>", the place encoded. Built step by step and typed nullable: in plain JVM tests the stubbed
     * builder returns null from each call.
     */
    private fun geoSearchUri(place: String): Uri? {
        val builder = Uri.Builder()
        builder.scheme("geo")
        builder.encodedOpaquePart("0,0?q=" + Uri.encode(place))
        return builder.build()
    }

    // ---------------------------------------------------------------------------------------------
    // MEDIA PARSER
    // ---------------------------------------------------------------------------------------------

    private fun parseMediaPlay(original: String): ParsedIntent.MediaPlay? {
        val collapsed = original.trim().replace(WHITESPACE_REGEX, " ")
        if (!collapsed.startsWith(PLAY, ignoreCase = true)) return null
        val named = PLAY_ON_APP_REGEX.matchEntire(collapsed)
        val what: String
        var app: Pair<String, String>? = null
        if (named != null) {
            // "play x on youtube" means a video, which YouTube's own search finds better than a music player.
            app = MEDIA_APPS[named.groupValues[2].lowercase(Locale.ROOT)] ?: return null
            what = named.groupValues[1].trim()
        } else {
            what = collapsed.substring(PLAY.length).trim()
            if (what.substringBefore(' ').lowercase(Locale.ROOT) in NOT_MEDIA_FIRST_WORDS) return null
        }
        if (what.none { it.isLetterOrDigit() }) return null
        val anyPlayer = mediaPlayIntent(what)
        return if (app != null) {
            ParsedIntent.MediaPlay(what, app.first, mediaPlayIntent(what).setPackage(app.second), fallbackIntent = anyPlayer)
        } else {
            ParsedIntent.MediaPlay(what, null, anyPlayer)
        }
    }

    private const val PLAY = "play "

    /** Asks a media app to search for [query] and play the best match, as "play ..." said to the Assistant does. */
    internal fun mediaPlayIntent(query: String): Intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
        putExtra(SearchManager.QUERY, query)
        // Any kind of media: the app decides whether the words name an artist, an album, a song or a playlist.
        putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
    }

    // ---------------------------------------------------------------------------------------------
    // TEMPORAL EXTRACTION UTILITIES
    // ---------------------------------------------------------------------------------------------

    private data class TemporalResult(val timestampMs: Long, val cleanText: String)

    /** The day word a phrase named, which decides how a bare hour like "at 3" is read. */
    private enum class NamedDay { NONE, TODAY, TONIGHT, TOMORROW, WEEKDAY, DATE }

    /**
     * When [rawText] names, from [baseTimeMs]; null when it names a time from now, a date or a clock time that can't
     * be read, so the phrase is not taken as one set at some other time.
     */
    private fun extractTemporalInfo(rawText: String, baseTimeMs: Long): TemporalResult? {
        val cal = Calendar.getInstance().apply { timeInMillis = baseTimeMs }
        var text = rawText
        var namedDay = NamedDay.NONE
        val isTonight = TONIGHT_REGEX.containsMatchIn(text)

        // Check named days and dates
        val weekdayMatch = WEEKDAY_REGEX.find(text)
        val monthFirst = MONTH_DAY_REGEX.find(text)
        val dateMatch = monthFirst ?: DAY_MONTH_REGEX.find(text)
        if (dateMatch != null) {
            val (monthText, dayText) = if (monthFirst != null) {
                dateMatch.groupValues[1] to dateMatch.groupValues[2]
            } else {
                dateMatch.groupValues[2] to dateMatch.groupValues[1]
            }
            val month = MONTH_ABBREVIATIONS.indexOf(monthText.take(3).lowercase(Locale.ROOT))
            if (!moveToDate(cal, month, dayText.toInt(), dateMatch.groupValues[3].toIntOrNull())) return null
            text = text.replace(dateMatch.value, " ")
            // "on friday, dec 5": the date says the day; the weekday is just said with it.
            if (weekdayMatch != null) text = text.replace(weekdayMatch.value, " ")
            namedDay = NamedDay.DATE
        } else if (TOMORROW_REGEX.containsMatchIn(text)) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            text = text.replace(TOMORROW_REGEX, "")
            namedDay = NamedDay.TOMORROW
        } else if (isTonight) {
            cal.set(Calendar.HOUR_OF_DAY, 20)
            cal.set(Calendar.MINUTE, 0)
            text = text.replace(TONIGHT_REGEX, "")
            namedDay = NamedDay.TONIGHT
        } else if (TODAY_REGEX.containsMatchIn(text)) {
            text = text.replace(TODAY_REGEX, "")
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

        // Relative duration "in X minutes / hours / days / weeks"
        val relativeMatch = RELATIVE_TIME_REGEX.find(text)
        if (relativeMatch != null) {
            val minutes = relativeMinutes(relativeMatch) ?: return null
            val days = minutes / MINUTES_PER_DAY
            val inDays = relativeMatch.groupValues[2].lowercase(Locale.ROOT)[0] in "dw"
            // Whole days and weeks keep the clock time across a daylight saving change. Hours and minutes are exact:
            // "in 24 hours" is 24 real hours even on the night the clocks change.
            if (inDays && days >= 1.0 && days == kotlin.math.floor(days)) {
                cal.add(Calendar.DAY_OF_YEAR, days.toInt())
            } else {
                cal.add(Calendar.MINUTE, Math.round(minutes).toInt())
            }
            text = text.replace(relativeMatch.value, "")
        } else if (UNREAD_RELATIVE_TIME_REGEX.containsMatchIn(text)) {
            return null
        }

        // Explicit time "at 3pm", "at 15:30", "at 10:00 am", "at 6.30pm", "at 3"
        val timeMatch = EXPLICIT_TIME_REGEX.findAll(text).firstOrNull {
            it.groupValues[1].isNotEmpty() || it.groupValues[5].isNotEmpty() || it.groupValues[3] == ":"
        }
        if (timeMatch != null) {
            var hour = timeMatch.groupValues[2].toIntOrNull() ?: 9
            val minute = timeMatch.groupValues[4].toIntOrNull() ?: 0
            val amPm = timeMatch.groupValues[5].lowercase(Locale.ROOT)
            // "07:00" is 24-hour notation: the hour is meant exactly as written.
            val zeroPadded = timeMatch.groupValues[2].length == 2 && timeMatch.groupValues[2][0] == '0'

            // A time that doesn't exist ("at 25:00", "13pm") is no time: Calendar is lenient and would roll it into
            // another day, and clamping it would set the event at a time nobody asked for.
            if (minute !in 0..59 || (amPm.isEmpty() && hour !in 0..23) || (amPm.isNotEmpty() && hour !in 1..12)) return null
            if (amPm == "p" && hour < 12) hour += 12
            if (amPm == "a" && hour == 12) hour = 0
            // Without am/pm: "tonight at 9" is 21:00. With no day or "today", "at 3" is the next 3 o'clock still
            // ahead. Tomorrow, a weekday or a date has no "already passed" to go by, so 1-7 is read as the afternoon
            // or evening people mean ("tomorrow at 3") and 8-11 as the morning, unless the hour has a leading zero.
            if (amPm.isEmpty() && hour in 1..11) {
                if (isTonight) {
                    hour += 12
                } else if (relativeMatch == null) {
                    when (namedDay) {
                        NamedDay.TOMORROW, NamedDay.WEEKDAY, NamedDay.DATE -> if (hour <= 7 && !zeroPadded) hour += 12
                        else -> {
                            val morning = (cal.clone() as Calendar).apply {
                                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
                            }
                            if (morning.timeInMillis < baseTimeMs && morning.timeInMillis + 12 * 3_600_000L > baseTimeMs) hour += 12
                        }
                    }
                }
            }

            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            text = text.replace(timeMatch.value, "")

            if (namedDay == NamedDay.NONE && relativeMatch == null && cal.timeInMillis < baseTimeMs) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val cleanTitle = text.replace(WHITESPACE_REGEX, " ").trim()
        return TemporalResult(cal.timeInMillis, cleanTitle)
    }

    /** The minutes a [RELATIVE_TIME_REGEX] match puts ahead, or null when unreadable or more than a year. */
    private fun relativeMinutes(match: MatchResult): Double? {
        val amountText = match.groupValues[1].trim()
        val amount = if (amountText[0].isLetter()) 1.0 else amountText.replace(',', '.').toDoubleOrNull() ?: return null
        val minutes = amount * when (match.groupValues[2].lowercase(Locale.ROOT)[0]) {
            'w' -> 7 * MINUTES_PER_DAY
            'd' -> MINUTES_PER_DAY
            'h' -> 60
            else -> 1
        }
        return minutes.takeIf { it.isFinite() && it <= 366.0 * MINUTES_PER_DAY }
    }

    /**
     * Moves [cal] to [day] of [month] (0-based), keeping its time of day: in [year] when one was given, else the next
     * time that date comes round, today included. False when the date doesn't exist ("feb 30").
     */
    private fun moveToDate(cal: Calendar, month: Int, day: Int, year: Int?): Boolean {
        if (month < 0 || day !in 1..31) return false
        val today = cal.get(Calendar.YEAR) * 10_000 + cal.get(Calendar.MONTH) * 100 + cal.get(Calendar.DAY_OF_MONTH)
        // "feb 29" may be a few years ahead.
        for (candidateYear in (year ?: cal.get(Calendar.YEAR))..(year ?: (cal.get(Calendar.YEAR) + 8))) {
            val date = (cal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.YEAR, candidateYear)
                set(Calendar.MONTH, month)
            }
            if (day > date.getActualMaximum(Calendar.DAY_OF_MONTH)) continue
            if (year == null && candidateYear * 10_000 + month * 100 + day < today) continue
            date.set(Calendar.DAY_OF_MONTH, day)
            cal.timeInMillis = date.timeInMillis
            return true
        }
        return false
    }

    private fun extractTime(text: String): Pair<Int, Int>? {
        NOON_OR_MIDNIGHT_REGEX.find(text)?.let { return Pair(if (it.groupValues[1].equals("midnight", ignoreCase = true)) 0 else 12, 0) }
        val match = CLOCK_TIME_REGEX.find(text) ?: return null

        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val amPm = match.groupValues[3].lowercase(Locale.ROOT)

        if (amPm.isNotEmpty() && hour !in 1..12) return null
        if (amPm == "p" && hour < 12) hour += 12
        if (amPm == "a" && hour == 12) hour = 0

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
