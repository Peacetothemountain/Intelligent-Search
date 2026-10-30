package com.pixel.intelligentsearch.core.search

import com.pixel.intelligentsearch.core.util.Durations
import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
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
 *
 * When an event, reminder or alarm is set for is read by [readDay]'s closed grammar, which lists every date and time
 * expression it reads. Any other word of when ("around 7", "every monday", "a week from today") makes the phrase no
 * action rather than one set for now or a guessed day.
 */
object NaturalLanguageIntentParser {

    // Compiled once: parse() runs on every keystroke, so no pattern may be built per call.
    private val TIMER_PREFIXES = listOf(
        "set a timer for ", "set a timer ", "start a timer for ", "start a timer ",
        "set timer for ", "set timer ", "start timer for ", "start timer ", "timer for ", "timer "
    )
    // Longest first, so "set an alarm for 7am" isn't read as "set an alarm" followed by "for 7am".
    private val ALARM_PREFIXES = listOf(
        "set an alarm for ", "set an alarm at ", "set an alarm ", "set alarm for ", "set alarm at ", "set alarm ",
        "alarm for ", "alarm at ", "alarm ", "wake me up at ", "wake me up ", "wake me "
    )
    private val HOME_NAVIGATION_PHRASES =setOf("directions home", "navigate home", "take me home", "route home")
    // Amounts may have a decimal point or comma ("1.5 hours", "2,5 min"); the guard keeps "1.5 hours" from also
    // reading as 5 hours. The unit must end there: "10ms" is no 10 minutes.
    private const val TIMER_AMOUNT = "(?<![\\d.,])(\\d+(?:[.,]\\d+)?)"
    private val TIMER_HOURS_REGEX = Regex("$TIMER_AMOUNT\\s*(?:h|hrs?|hours?)(?!\\p{L})")
    private val TIMER_MINUTES_REGEX = Regex("$TIMER_AMOUNT\\s*(?:m|min|mins|minutes?)(?!\\p{L})")
    private val TIMER_SECONDS_REGEX = Regex("$TIMER_AMOUNT\\s*(?:s|sec|secs|seconds?)(?!\\p{L})")
    private val TIMER_CLOCK_REGEX = Regex("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")
    // A bare number after the last unit: "2 hours 30" is 2 h 30 min, "1 m 30" is 1 min 30 s.
    private val TIMER_TRAILING_AMOUNT_REGEX = Regex("(?<![\\d.,])(\\d+)$")
    private val TIMER_AND_A_HALF_REGEX = Regex("\\s*and\\s+a\\s+half\\s*")
    // "2 and a half minutes" is 2.5 minutes.
    private val TIMER_NUMBER_AND_A_HALF_REGEX = Regex(
        "(?<![\\d.,])(\\d+)\\s+and\\s+a\\s+half\\s+(?=(?:h|hrs?|hours?|m|min|mins|minutes?|s|sec|secs|seconds?)(?!\\p{L}))"
    )
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
    private val NAME_TOKEN_SPLIT = Regex("[^\\p{L}\\p{N}]+")
    private val MARKS_REGEX = Regex("\\p{M}+")

    /**
     * Words that, right after "text", "message" or "whatsapp", start a search rather than name someone: the small words
     * of "text to speech", "message in a bottle", "text me when you get home", and the things "text editor", "message
     * board" and "whatsapp web" are. A contact's name is never one of them, so they never pick a recipient.
     */
    private val NOT_A_NAME = setOf(
        "to", "me", "in", "for", "a", "an", "the", "my", "your", "our", "his", "her", "their", "on", "at", "of", "from",
        "with", "and", "or", "about", "this", "that", "these", "is", "it", "its", "how", "what", "when", "where", "why",
        "saying", "all", "every", "everyone", "someone", "somebody", "people", "back", "not", "no", "free", "best",
        "editor", "editors", "board", "boards", "box", "file", "files", "art", "generator", "font", "fonts", "format",
        "message", "messages", "text", "texts", "app", "apps", "web", "desktop", "online", "bubble", "bubbles", "size",
        "style", "styles", "color", "tone", "tones", "template", "templates", "scanner", "converter", "translator",
        "speech", "voice", "video", "chat", "chats", "group", "groups", "backup", "status", "business", "settings",
        "sticker", "stickers", "emoji", "symbol", "symbols", "number", "numbers", "recovery", "deleted", "delete",
        "forwarding", "notification", "notifications", "alert", "alerts", "blocker", "spam", "download", "update"
    )

    /**
     * Whether [typed] names the contact called [contactName]: every typed word is a whole word of the name, accents
     * aside ("tom" and "tom baker" are Tom Baker, "jose" is José). The contacts search matches the start of any name,
     * so "to" finds Tom and "art" Arthur; a message only goes to someone named outright.
     */
    internal fun namesContact(typed: String, contactName: String): Boolean {
        val typedWords = nameTokens(typed)
        val nameWords = nameTokens(contactName).toSet()
        return typedWords.isNotEmpty() && nameWords.containsAll(typedWords)
    }

    // Letters with no accent to take off ("ø", "ß") are written as people type them without the key, as the search
    // does, so "bjorn" names Bjørn and "strasse" Straße.
    private fun nameTokens(text: String): List<String> =
        java.text.Normalizer.normalize(QueryNormalizer.foldLetters(text), java.text.Normalizer.Form.NFD)
            .replace(MARKS_REGEX, "").lowercase(Locale.ROOT).split(NAME_TOKEN_SPLIT).filter { it.isNotEmpty() }

    // The units a time from now is read in: "10 minutes", "1.5 hours", "2 days", "1h". The unit must end there: "in 5
    // minutes" used to match as "in 5 min" and leave "utes" in the title. A digit may follow it, as in "1h30m".
    private const val RELATIVE_UNITS = "(minutes?|mins?|hours?|hrs?|h|days?|weeks?|wks?|m)(?!\\p{L})"
    private const val RELATIVE_PART = "(?<![\\d.,])\\d+(?:[.,]\\d+)?\\s*$RELATIVE_UNITS"
    // "1 hour and a half", and "1 hour 30": a bare number after the last unit counts in the next smaller one ("2 hours
    // 15" is 2 h 15 min), at the end or before "to" ("remind me in 1 hour 30 to call").
    private const val RELATIVE_HALF = "\\s+and\\s+a\\s+half(?!\\p{L})"
    private const val RELATIVE_TRAILING = "\\s*(?<![\\d.,])\\d{1,2}(?=\\s*$|\\s+to\\b)"
    // Every part of an amount counts: "1 hour 30 minutes", "2 hours and 15 minutes" and "1h30m" kept only the first.
    private const val RELATIVE_AMOUNT = "$RELATIVE_PART(?:\\s*(?:,\\s*|and\\s+)?$RELATIVE_PART)*(?:$RELATIVE_HALF|$RELATIVE_TRAILING)?"
    private val RELATIVE_HALF_REGEX = Regex("^\\s*and\\s+a\\s+half", RegexOption.IGNORE_CASE)
    private val RELATIVE_TRAILING_REGEX = Regex("^\\s*(\\d{1,2})")
    // The amount in words: "an hour", "half an hour", "a half hour", "an hour and a half".
    private const val RELATIVE_WORDS =
        "(?:half\\s+an?\\s+hour|an?\\s+half\\s+hour|half\\s+hour|an?\\s+(?:minute|hour|day|week)(?:\\s+and\\s+a\\s+half)?)(?!\\p{L})"
    private val RELATIVE_PART_REGEX = Regex("(\\d+(?:[.,]\\d+)?)\\s*$RELATIVE_UNITS", RegexOption.IGNORE_CASE)
    private val RELATIVE_WORD_UNIT_REGEX = Regex("minute|hour|day|week", RegexOption.IGNORE_CASE)
    // "in 10 minutes", "in an hour". "In" must be a word of its own: "check the cabin 2 hours" is not "in 2 hours".
    private val RELATIVE_TIME_REGEX = Regex("\\bin\\s+(?:$RELATIVE_AMOUNT|$RELATIVE_WORDS)", RegexOption.IGNORE_CASE)
    // An alarm given a duration can only mean that long from now, so "alarm for 10 minutes" needs no "in". Words
    // still do, so "alarm at a minute past 7" isn't a minute from now.
    private val ALARM_DURATION_REGEX = Regex("(?:\\bin\\s+)?$RELATIVE_AMOUNT|\\bin\\s+$RELATIVE_WORDS", RegexOption.IGNORE_CASE)
    // "10 to 7" and "quarter past 6" are clock times said another way: not 10 o'clock, so no alarm rather than a wrong one.
    private val SPOKEN_CLOCK_REGEX = Regex("\\b(?:\\d{1,2}|quarter|half)\\s+(?:to|past|after|before)\\s+\\d", RegexOption.IGNORE_CASE)
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
    private val EXPLICIT_TIME_REGEX = Regex(
        "\\b(?:(at)\\s+)?(\\d{1,2})(?:([:.])(\\d{2}))?(?:$MERIDIEM|\\s*o['’]?clock\\b|\\b)",
        RegexOption.IGNORE_CASE
    )
    // "730pm": the hour and minutes run together, which only am/pm makes a time.
    private val COMPACT_TIME_REGEX = Regex("\\b(\\d{1,2})(\\d{2})(?=$MERIDIEM)", RegexOption.IGNORE_CASE)
    // "2-3pm", "from 2 to 3pm", "10:30-11am", "at 2-3": a time range, which starts at its first time.
    private val TIME_RANGE_REGEX = Regex(
        "(?:\\b(from|at)\\s+)?(?<![\\d/.:,-])\\b(\\d{1,2})(?:[:.](\\d{2}))?(?:$MERIDIEM)?\\s*(?:-|–|—|\\bto\\b|\\buntil\\b|\\btill\\b)" +
            "\\s*(\\d{1,2})(?:[:.](\\d{2}))?(?:$MERIDIEM)?(?![\\d/:])",
        RegexOption.IGNORE_CASE
    )
    // What, left in a task once its clock time is read, is another time the reader couldn't place: "at 1930", "at 7
    // 30"'s "30", a second "6pm".
    private val LEFTOVER_TIME_REGEX = Regex("\\bat\\s+\\d|\\b\\d{1,2}(?:[:.]\\d{2})?$MERIDIEM|\\b\\d{1,2}:\\d{2}\\b", RegexOption.IGNORE_CASE)
    private val MINUTES_AFTER_REGEX = Regex("^\\s+\\d{2}\\b")
    // An alarm is only a time of day, so a space can part the hour from the minutes too: "alarm 6 30".
    private val CLOCK_TIME_REGEX = Regex("\\b(\\d{1,2})(?:[:.\\s](\\d{2}))?(?:$MERIDIEM|\\b)", RegexOption.IGNORE_CASE)
    private val NOON_OR_MIDNIGHT_REGEX = Regex("\\b(noon|midday|midnight)\\b", RegexOption.IGNORE_CASE)
    // Whole words only, so "read todays report" keeps its "todays" and "today's report" its "'s". Quick notes write
    // tomorrow "tmrw", "tmr" or "tmw".
    private val TOMORROW_REGEX = Regex("\\b(?:tomorrow|tmrw|tmr|tmw)\\b(?!['’]\\w)", RegexOption.IGNORE_CASE)
    private val TONIGHT_REGEX = Regex("\\btonight\\b(?!['’]\\w)", RegexOption.IGNORE_CASE)
    private val TODAY_REGEX = Regex("\\btoday\\b(?!['’]\\w)", RegexOption.IGNORE_CASE)
    // "at noon", "at 12 noon", "midnight".
    private val NOON_OR_MIDNIGHT_TIME_REGEX = Regex("\\b(?:at\\s+)?(?:12\\s+)?(noon|midday|midnight)\\b", RegexOption.IGNORE_CASE)
    // "this evening", "in the morning", "tomorrow night", "friday afternoon": the part of a day, which a day word may
    // come before ("today", "tomorrow" and every weekday end in "day").
    private val PART_OF_DAY_REGEX = Regex(
        "(?:\\b(this|in\\s+the)\\s+|(?<=(?:tomorrow|tmrw|tmr|tmw|day)\\s))(morning|afternoon|evening|night)\\b",
        RegexOption.IGNORE_CASE
    )
    /** The hour a part of the day starts at when no time is given: "this evening" is 6 PM, "tomorrow night" 8 PM. */
    private val PART_OF_DAY_HOURS = mapOf("morning" to 9, "afternoon" to 13, "evening" to 18, "night" to 20)
    private val DAY_AFTER_TOMORROW_REGEX = Regex("\\b(?:the\\s+)?day\\s+after\\s+tomorrow\\b", RegexOption.IGNORE_CASE)
    // "next week", not "next week's".
    private val NEXT_WEEK_REGEX = Regex("\\bnext\\s+week\\b(?!['’])", RegexOption.IGNORE_CASE)
    // A date may be said with what it's for: "appointment for nov 12", "submit by friday".
    private const val DATE_LEAD = "(?:\\b(?:on|for|by)\\s+)?"
    // A month and day, either way round: "dec 5", "december 5th, 2027", "on the 5th of december", "5 dec".
    private const val MONTHS =
        "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|" +
            "oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\b\\.?"
    private const val DATE_YEAR = "(?:,?\\s+(20\\d{2})\\b)?"
    // The day is not an hour: "dec 5 pm" and "dec 5:30" are left to be read as times.
    private val MONTH_DAY_REGEX = Regex(
        "$DATE_LEAD\\b$MONTHS\\s*(\\d{1,2})(?:st|nd|rd|th)?\\b(?!\\s*(?:[:.]\\d|[ap]\\.?\\s?m\\b))$DATE_YEAR",
        RegexOption.IGNORE_CASE
    )
    private val DAY_MONTH_REGEX = Regex(
        "$DATE_LEAD(?:\\bthe\\s+)?\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?$MONTHS$DATE_YEAR",
        RegexOption.IGNORE_CASE
    )
    // "on 11/5", "12/5/2027": the month or the day first as the phone's region writes dates ([monthBeforeDay]).
    private val NUMERIC_DATE_REGEX = Regex(
        "(?:\\b(on|for|by)\\s+)?(?<![\\d/.:,])\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}|\\d{2}))?(?![/\\d])",
        RegexOption.IGNORE_CASE
    )
    /** "first" to "thirty-first", the days of a month written out. */
    private val SPELLED_ORDINALS: Map<String, Int> = buildMap {
        val units = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth")
        units.forEachIndexed { index, word -> put(word, index + 1); put("twenty-$word", index + 21) }
        listOf("tenth", "eleventh", "twelfth", "thirteenth", "fourteenth", "fifteenth", "sixteenth", "seventeenth",
            "eighteenth", "nineteenth", "twentieth").forEachIndexed { index, word -> put(word, index + 10) }
        put("thirtieth", 30)
        put("thirty-first", 31)
    }
    private val SPELLED_ORDINAL = SPELLED_ORDINALS.keys.sortedByDescending { it.length }.joinToString("|") { it.replace("-", "[\\s-]") }
    // "on the 1st", "by the 21st", "on the fifteenth": a day of this month or the next that has it. It takes "on",
    // "for" or "by": "the 3rd" alone is as often a chapter or a floor.
    private val ORDINAL_DAY_REGEX = Regex(
        "\\b(on|for|by)\\s+(?:(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)|the\\s+($SPELLED_ORDINAL))(?![\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE
    )
    // "the 12th", "12th", "the fifth" with no "on": a date only with a clock time right after it ("dentist the 12th at
    // 10am"). Left anywhere else, and not describing the word after it, it is a date the reader can't place.
    private val BARE_ORDINAL_REGEX = Regex(
        "(?<![\\p{L}\\p{N}])(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)(?![\\p{L}\\p{N}])|\\bthe\\s+($SPELLED_ORDINAL)(?![\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE
    )
    // "in may", "in march 2027": the first of that month.
    private val MONTH_ALONE_REGEX = Regex("\\bin\\s+$MONTHS$DATE_YEAR", RegexOption.IGNORE_CASE)
    /** Reads the days named for an occasion ("christmas", "valentine's day", "thanksgiving"), as date maths does. */
    private val OCCASIONS = DateMathEngine()
    private val OCCASION_NAMES = OCCASIONS.occasionNames.sortedByDescending { it.length }.joinToString("|") { name ->
        name.replace(".", "\\.").replace("'", "['’]").replace(" ", "\\s+")
    }
    // "on christmas", "for valentine's day": the occasion's next date.
    private val OCCASION_REGEX = Regex("\\b(on|for)\\s+(?:the\\s+)?($OCCASION_NAMES)(?!\\p{L})", RegexOption.IGNORE_CASE)
    /**
     * Days of the year the reader has no date for ("on juneteenth", "for passover", "on payday", "super bowl sunday").
     * A day named "<words> day" ("on election day") is found by [hasUnreadDayLeft] instead.
     */
    private const val UNKNOWN_HOLIDAYS =
        "juneteenth|hanukk?ah|chanukk?ah|kwanzaa|diwali|eid|ramadan|passover|rosh\\s+hashanah?|yom\\s+kippur|purim|lent|" +
            "mardi\\s+gras|cinco\\s+de\\s+mayo|super\\s+bowl|payday|pay\\s+day|(?:lunar|chinese)\\s+new\\s+year|bank\\s+holiday"
    /**
     * Days whose date depends on the country or the person ("mother's day", "labor day", "my birthday"): never read,
     * so a phrase that names one for when it is no reminder rather than one set for now.
     */
    private val UNREAD_OCCASION_REGEX = Regex(
        "\\b(on|for|by)\\s+(?:the\\s+)?(?:mother['’]?s\\s+day|father['’]?s\\s+day|memorial\\s+day|labou?r\\s+day|" +
            "good\\s+friday|cyber\\s+monday|(?:my|his|her|their|your|our|\\p{L}+['’]s)\\s+(?:birthday|bday|anniversary))(?!\\p{L})",
        RegexOption.IGNORE_CASE
    )
    /**
     * Words that may come right after a date ("on the 3rd at 2pm", "for christmas tomorrow", "on the 5th to pay
     * rent"). Any other word is one the date describes, "the 3rd floor", "christmas presents", "the first time", so
     * the date words are then part of the task, not when it is.
     */
    private val DATE_FOLLOWERS = setOf(
        "at", "by", "on", "in", "to", "and", "or", "then", "from", "until", "till", "before", "after", "around", "about",
        "with", "for", "so", "but", "if", "when", "while", "every", "this", "next", "coming", "please", "today", "tonight",
        "tomorrow", "tmrw", "tmr", "tmw", "morning", "afternoon", "evening", "night", "noon", "midday", "midnight", "sunday", "monday",
        "tuesday", "wednesday", "thursday", "friday", "saturday", "because", "re", "regarding", "as", "since", "unless",
        "again", "too", "also", "instead"
    )
    /**
     * When-words that need a clock time with them ("before bed", "at lunch", "after work", "by eod", "later"): they
     * say no hour, so a phrase with nothing else to say one is no reminder rather than one set for now.
     */
    private val UNTIMED_WHEN_REGEX = Regex(
        "\\b(?:before|after)\\s+(?:bed|bedtime|work|school|class|lunch|dinner|breakfast)\\b|" +
            "\\b(?:at|by|around)\\s+(?:lunch|lunchtime|dinner|dinnertime|breakfast|bedtime|eod|cob|close\\s+of\\s+business)\\b|" +
            "\\bend\\s+of\\s+(?:the\\s+)?(?:day|business\\s+day|workday)\\b|\\beod\\b|\\blater(?:\\s+on)?\\s*$",
        RegexOption.IGNORE_CASE
    )
    private const val FULL_MONTHS =
        "january|february|march|april|may|june|july|august|september|october|november|december"
    /**
     * When-words left in a task once everything readable is out ("this weekend", "next month", "by june", "end of the
     * week", "half past 5"): a time that can't be read, so the phrase is no event or reminder, rather than one set for
     * now with the words in its title.
     */
    private val UNREAD_DATE_REGEX = Regex(
        "\\b(?:next|this|coming)\\s+(?:week|weekend|month|year)\\b(?!['’])|\\b(?:on|at|over)\\s+the\\s+weekend\\b|" +
            "\\b(?:end|beginning|start|middle)\\s+of\\s+(?:the\\s+)?(?:week|month|year|$FULL_MONTHS)\\b|" +
            "\\b(?:by|before|until|till|after|during|early|late|mid|in)\\s+(?:$FULL_MONTHS)\\b|" +
            "\\b(?:quarter|half)\\s+(?:past|to|after|before)\\b|\\b(?:this|in\\s+the)\\s+(?:morning|afternoon|evening|night)\\b",
        RegexOption.IGNORE_CASE
    )
    private val MONTH_ABBREVIATIONS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val LEADING_TO_REGEX = Regex("^to\\s+", RegexOption.IGNORE_CASE)
    // What a title is left with when the time words came out from between commas: "meeting on friday, dec 4 at 3pm".
    private val STRAY_PUNCTUATION = charArrayOf(',', ';', ':', '-', ' ')
    // "meeting with john" names the meeting by who it's with, "meeting on the 3rd floor" by where; the title says so,
    // "Meeting with john" and "Meeting on the 3rd floor", not "with john" and "on the 3rd floor".
    private val MEETING_PREPOSITION_REGEX = Regex("^(?:with|about|re|for|on|at|in)\\s", RegexOption.IGNORE_CASE)
    private val WEEKDAYS = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")
    /** Weekdays as quick notes shorten them ("fri", "tues"), by the full name. */
    private val SHORT_WEEKDAYS = mapOf(
        "sun" to "sunday", "mon" to "monday", "tue" to "tuesday", "tues" to "tuesday", "wed" to "wednesday",
        "weds" to "wednesday", "thu" to "thursday", "thur" to "thursday", "thurs" to "thursday", "fri" to "friday",
        "sat" to "saturday"
    )
    private val WEEKDAY_WORDS = (WEEKDAYS + SHORT_WEEKDAYS.keys).sortedByDescending { it.length }.joinToString("|")
    // "on friday", "this friday", "next tuesday", "fri": "next" is the one in next week. "Black friday", "good friday"
    // and "cyber monday" are days of their own, and "c'mon" is no Monday. "Saturday's game" names a thing, not when.
    private val WEEKDAY_REGEX = Regex(
        "(?:\\b(on|for|by)\\s+)?(?:\\b(next|this|coming)\\s+)?(?<!black\\s)(?<!good\\s)(?<!cyber\\s)(?<!['’])\\b($WEEKDAY_WORDS)\\b(?!['’])\\.?",
        RegexOption.IGNORE_CASE
    )
    // "friday the 13th", "tuesday, 11/10", "monday nov 2": a weekday said with its date.
    private val WEEKDAY_DATE_REGEX = Regex(
        "(?<!['’])\\b($WEEKDAY_WORDS)\\b\\.?,?\\s+(?:(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)(?![\\p{L}\\p{N}])|" +
            "the\\s+($SPELLED_ORDINAL)(?![\\p{L}\\p{N}])|(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}|\\d{2}))?(?![/\\d])|(?=$MONTHS))",
        RegexOption.IGNORE_CASE
    )
    /** Words before a short weekday that make it a thing: "the sun", "my sat score", "get some sun". */
    private val DETERMINERS = setOf(
        "the", "a", "an", "my", "your", "his", "her", "our", "their", "its", "some", "more", "any", "no", "much", "enough", "less"
    )
    // A clock time right after a word: "fri 2pm", "fri at 2", "the 12th at 10am", "sat noon".
    private val TIME_FOLLOWS_REGEX = Regex(
        "^\\s*,?\\s*(?:(?:at|@)\\s*\\d|\\d{1,2}(?:[:.]\\d{2})?$MERIDIEM|\\d{1,2}:\\d{2}|(?:at\\s+)?(?:noon|midday|midnight)\\b)",
        RegexOption.IGNORE_CASE
    )

    private const val CLOCK_WORDS = "one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve"
    /**
     * When-words outside what the reader reads ([readDay] lists what it does), wherever they sit: a time said roughly
     * ("at 7ish", "noonish") or the spoken way ("at 10 to 7", "20 past 6"), a day counted from another ("a week from today", "2 days from now", "the week after
     * next", "after 2 days"), part of a week ("early next week", "by the end of next week"), the sun ("at dawn", "at
     * sunset") or "at night", a task that repeats ("every monday", "each morning", "the 1st of every month", "on
     * weekdays"), a day misspelled or gone ("tomorow", "tonite", "yesterday"), "asap", "soon", "in a bit", a bound on a
     * day ("before friday", "until the 5th", "from monday", "by christmas"), a day counted back from another ("2 days
     * before christmas", "the day before thanksgiving"), the nth weekday of a month ("the second friday of november"),
     * "a week on friday", and a holiday the reader has no date for ([UNKNOWN_HOLIDAYS]).
     */
    private val NEVER_READ_REGEX = Regex(
        "\\d\\s*-?\\s*ish\\b|\\b(?:noon|midday|midnight|$CLOCK_WORDS)-?ish\\b|" +
            "\\bat\\s+\\d{1,2}\\s+(?:to|past|after|before)\\s+\\d|\\b\\d{1,2}\\s+(?:past|after)\\s+\\d|" +
            "(?<!\\bday\\s)\\b(?:from|after)\\s+(?:now|today|tomorrow|tonight|next)\\b|\\bweek\\s+after\\b|" +
            "\\b(?:after|within)\\s+$SPOKEN_DURATION|\\b$SPOKEN_DURATION\\s+(?:from|later|after|hence|ago)\\b|" +
            "\\b(?:early|late|mid|(?:end|beginning|start|middle|rest)\\s+of)[\\s-]+(?:the\\s+)?(?:next|this|coming)\\s+(?:week|month|year)\\b|" +
            "\\b(?:at|by|before|after|around|until|till|from|toward|towards)\\s+(?:dawn|dusk|sunrise|sunset|sundown|sunup|" +
            "daybreak|nightfall|first\\s+light|night)\\b|" +
            "\\b(?:every|each)\\s+(?:other\\s+|single\\s+|\\d+\\s*|few\\s+|couple\\s+of\\s+)?(?:day|week|month|year|hour|minute|" +
            "morning|afternoon|evening|night|weekday|weekend|time|$WEEKDAY_WORDS)s?\\b|\\bof\\s+(?:the|this|next|every|each|a)\\s+month\\b|" +
            "\\b(?:weekdays|weekends|weeknights|sundays|mondays|tuesdays|wednesdays|thursdays|fridays|saturdays)\\b|" +
            "\\b(?:yesterday|tomorow|tommorow|tommorrow|tomorrw|tmorrow|tomoro|tomorro|2morrow|2moro|2morow|tonite|2nite|" +
            "tonigt|tonght|tomoz|2day|asap|soon)\\b|\\bin\\s+a\\s+(?:bit|while|sec|moment|jiffy|few|little\\s+(?:bit|while))\\b|" +
            "(?<!\\bday\\s)\\b(?:before|after|until|till|til)\\s+(?:the\\s+)?(?:\\d|(?:$WEEKDAY_WORDS|today|tomorrow|tonight|next)\\b|$MONTHS)|" +
            "\\bfrom\\s+(?:(?:$WEEKDAY_WORDS)\\b|$MONTHS)|" +
            "\\b(?:before|after|until|till|til|by|since|around|from)\\s+(?:the\\s+)?(?:$OCCASION_NAMES)(?!\\p{L})|" +
            "\\b$SPOKEN_DURATION\\s+(?:before|prior\\s+to)\\b|\\b(?:day|night|morning|evening|weekend)\\s+(?:before|prior\\s+to)\\b|" +
            "\\b(?:first|second|third|fourth|fifth|last|[1-5](?:st|nd|rd|th))\\s+(?:$WEEKDAY_WORDS|weekday|weekend|day|week)\\s+(?:of|in)\\b|" +
            "\\b(?:a|one|1)\\s+week\\s+on\\s+(?:$WEEKDAY_WORDS)\\b|" +
            "\\b(?:on|for|by|before|after|until|till|til|since|from|over|during|around|through)\\s+(?:the\\s+)?(?:$UNKNOWN_HOLIDAYS)\\b|" +
            "\\b(?:$UNKNOWN_HOLIDAYS|ash|palm|holy|maundy|shrove|fat|giving)\\s+(?:$WEEKDAY_WORDS)\\b",
        RegexOption.IGNORE_CASE
    )
    /**
     * A clock time said roughly or as a bound ("around 7", "about 5pm", "by 5", "before 9am", "between 2 and 3"), or in
     * words ("at seven"): unread, unless the word after it is one the number describes ("about 3 things", "at one
     * point").
     */
    private val ROUGH_TIME_REGEX = Regex(
        "\\b(?:(?:around|about|approx\\.?|approximately|roughly|circa|by|before|after|until|till|til|between|" +
            "no\\s+later\\s+than)\\s+(?:\\d{1,2}(?:[:.]\\d{2})?|noon|midday|midnight|$CLOCK_WORDS)|" +
            "at\\s+(?:$CLOCK_WORDS))(?:$MERIDIEM|\\s*o['’]?clock\\b)?(?![\\p{L}\\p{N}/])",
        RegexOption.IGNORE_CASE
    )
    /**
     * Words that say a task repeats ("take vitamins daily", "weekly"), or a rough part of the day ("in the early
     * afternoon", "late morning"): unread, unless they describe the word after them ("the weekly report", "a late
     * night snack").
     */
    private val REPEAT_OR_ROUGH_PART_REGEX = Regex(
        "\\b(?:daily|weekly|monthly|yearly|annually|nightly|hourly|biweekly|fortnightly|" +
            "(?:early|late|mid)[\\s-]+(?:morning|afternoon|evening|night)|mid(?:morning|afternoon|evening))\\b",
        RegexOption.IGNORE_CASE
    )
    // What [hasUnreadDayLeft] looks for in the words the reader left. Digits that may be a date ("11/15", "12-1",
    // "15/11/26", "2026-11-15"), not after a price or inside a time ("$10-15", "10:30-11").
    private val DATE_SHAPED_REGEX = Regex(
        "(?<![\\d.,:/$€£¥])\\b(\\d{1,2})([/-])(\\d{1,2})(?:[/-](?:\\d{4}|\\d{2}))?(?![\\d/])|\\b\\d{4}-\\d{1,2}-\\d{1,2}\\b"
    )
    // A range of hours, read with the clock time: "2-3pm", "10-11:30", "at 10-11", "from 2-3".
    private val RANGE_END_TIME_REGEX = Regex("^\\s*(?:[ap]\\.?\\s?m\\b|[:.]\\d)", RegexOption.IGNORE_CASE)
    private val RANGE_STARTS = setOf("at", "from", "@")
    // "on the 15", "the 1": a day of the month with no suffix. "On the 3 pm" is a time, "the 15th" was read already.
    private val BARE_DAY_NUMBER_REGEX = Regex(
        "\\b(?:on|the)\\s+(?:the\\s+)?\\d{1,2}(?![\\d:./,]|\\s*(?:[ap]\\.?\\s?m\\b|o['’]?clock\\b|st\\b|nd\\b|rd\\b|th\\b))",
        RegexOption.IGNORE_CASE
    )
    // A month beside a day ("december first", "the fifth of december"), "on december", or "of" any month. A clock
    // time after it is no day: "call june 5pm".
    private val MONTH_LEFT_REGEX = Regex(
        "\\b$MONTHS\\s*(?:\\d(?!\\d?\\s*(?:[:.]\\d|[ap]\\.?\\s?m\\b))|(?:$SPELLED_ORDINAL)\\b)|(?:\\b\\d{1,2}(?:st|nd|rd|th)?|\\b(?:$SPELLED_ORDINAL))\\s+(?:of\\s+)?$MONTHS|" +
            "\\bof\\s+$MONTHS|\\bon\\s+(?:$FULL_MONTHS)\\b",
        RegexOption.IGNORE_CASE
    )
    private val FULL_WEEKDAY_LEFT_REGEX = Regex("(?<!['’])\\b(?:${WEEKDAYS.joinToString("|")})\\b", RegexOption.IGNORE_CASE)
    private val SHORT_WEEKDAY_LEFT_REGEX = Regex("(?<!['’])\\b(?:${SHORT_WEEKDAYS.keys.joinToString("|")})\\b", RegexOption.IGNORE_CASE)
    // "on election day", "for veterans day", "by payday": a day named for something, which the reader has no date for
    // unless it is one of [OCCASIONS]'s. "For the day" and "on a day" name none.
    private val NAMED_DAY_LEFT_REGEX = Regex(
        "\\b(?:on|for|by|before|after|until|till|til|since)\\s+(?:the\\s+)?" +
            "((?:(?!(?:the|a|an|this|that|each|every|one|per)\\s)\\p{L}+['’]?s?\\s+){1,3}day|\\p{L}+day)\\b",
        RegexOption.IGNORE_CASE
    )
    // "in 5", "in 45": minutes or hours, the unit unsaid.
    private val IN_BARE_NUMBER_REGEX = Regex("\\bin\\s+\\d", RegexOption.IGNORE_CASE)
    // "the 7.30 train": a time written with a dot, which the clock-time reader takes only with "at" or am/pm.
    private val TIME_LEFT_REGEX = Regex("\\bthe\\s+(?:[1-9]|1\\d|2[0-3])\\.[0-5]\\d\\b", RegexOption.IGNORE_CASE)
    // A misspelled weekday ("wensday", "thrusday", "fryday"): a day that can't be read.
    private val UNKNOWN_WEEKDAY_REGEX = Regex("\\b(?:mo|tu|we|th|fr|sa|su)\\p{L}{0,7}day\\b", RegexOption.IGNORE_CASE)
    /** Words [UNKNOWN_WEEKDAY_REGEX] finds that are no misspelling. */
    private val KNOWN_DAY_WORDS = WEEKDAYS.toSet() + "weekday"

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

        /** [ringsAtMillis] is the next time [hour]:[minute] comes round, when the clock rings. */
        data class Alarm(
            val hour: Int,
            val minute: Int,
            val message: String,
            val intent: Intent,
            val ringsAtMillis: Long = 0L
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

    /**
     * [contactLookup] resolves a name in "call ..." and "text ..." to a contact; without it only numbers resolve.
     * [locale]'s region says whether "11/5" is November 5 or May 11, and which day a week starts on.
     */
    fun parse(
        rawQuery: String,
        currentTimeMs: Long = System.currentTimeMillis(),
        contactLookup: ((String) -> com.pixel.intelligentsearch.core.data.ContactItem?)? = null,
        locale: Locale = Locale.getDefault()
    ): ParsedIntent? {
        val query = rawQuery.trim()
        if (query.isEmpty()) return null
        val lower = query.lowercase(Locale.ROOT)

        // 1. Timer Intent ("set timer 15m", "timer for 10 minutes", "timer 45s")
        parseTimer(lower)?.let { return it }

        // 2. Alarm Intent ("alarm 7am", "set alarm for 6:30 am", "wake me up at 8")
        parseAlarm(lower, currentTimeMs, locale)?.let { return it }

        // 3. Reminder Intent ("remind me to ...")
        parseReminder(lower, query, currentTimeMs, locale)?.let { return it }

        // 4. Calendar Meeting Intent ("meeting tomorrow at 3pm", "add event lunch on Friday at 1pm")
        parseCalendar(lower, query, currentTimeMs, locale)?.let { return it }

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
        val remainder = lower.removePrefix(matchedPrefix).trim().replace(TIMER_NUMBER_AND_A_HALF_REGEX, "$1.5 ")
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

        // "2 hours 30" and "1 m 30": a bare number right after the last unit counts in the next smaller one. More than
        // 59 of it is a typo, not a longer timer. Words between ("45 minutes then 10") leave it unread: no timer.
        val lastUnit = listOfNotNull(hrMatch, minMatch, secMatch).maxByOrNull { it.range.last }
        // "1 hour and a half" is half the last unit more.
        if (lastUnit != null && lastUnit !== secMatch &&
            TIMER_AND_A_HALF_REGEX.matches(remainder.substring(lastUnit.range.last + 1).trimStart { it.isLetter() })
        ) {
            totalSeconds += if (lastUnit === hrMatch) 1_800L else 30L
        }
        val trailing = TIMER_TRAILING_AMOUNT_REGEX.find(remainder)
        if (lastUnit != null && trailing != null && trailing.range.first > lastUnit.range.last && lastUnit !== secMatch) {
            // The unit's match may end inside its word ("h" of "hours"), so the rest of that word isn't in between.
            val between = remainder.substring(lastUnit.range.last + 1, trailing.range.first).trimStart { it.isLetter() }
            if (between.isNotBlank()) return null
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

    private fun parseAlarm(lower: String, currentTimeMs: Long, locale: Locale): ParsedIntent.Alarm? {
        val matchedPrefix = ALARM_PREFIXES.firstOrNull { lower.startsWith(it) } ?: return null
        val remainder = lower.removePrefix(matchedPrefix).trim()
        if (SPOKEN_CLOCK_REGEX.containsMatchIn(remainder)) return null

        // "alarm in 10 minutes" and "alarm for 10 minutes" are a time from now, not 10 o'clock. An alarm is only an
        // hour and minute, so a day or more ahead ("alarm 2 days") can't be set, and "alarm 5 seconds" is not 5 o'clock.
        val relative = ALARM_DURATION_REGEX.find(remainder)
        val now = Calendar.getInstance().apply { timeInMillis = currentTimeMs }
        val rings = (now.clone() as Calendar).apply { set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        if (relative != null) {
            val minutes = relativeMinutes(relative) ?: return null
            if (minutes >= MINUTES_PER_DAY) return null
            rings.add(Calendar.MINUTE, Math.round(minutes).toInt())
        } else {
            if (UNREAD_ALARM_DURATION_REGEX.containsMatchIn(remainder)) return null
            if (!ringsOnTheDayNamed(remainder, now, rings, locale)) return null
        }
        val hour = rings.get(Calendar.HOUR_OF_DAY)
        val minute = rings.get(Calendar.MINUTE)

        val formattedTime = String.format(Locale.US, "%02d:%02d", hour, minute)
        return ParsedIntent.Alarm(
            hour = hour,
            minute = minute,
            message = "Alarm for $formattedTime",
            intent = alarmIntent(hour, minute),
            ringsAtMillis = rings.timeInMillis
        )
    }

    /**
     * Sets [rings] to the next time the clock time in [words] comes round after [now], and says whether that is the
     * day [words] name, if any. The clock's alarm is only a time of day (it rings the next time that comes round), so
     * "wake me up at 7 tomorrow" said at 9 PM is an alarm, but said at 6:40 AM it would ring in 20 minutes, and "alarm
     * monday at 6" said on a Friday would ring on Saturday: those are no alarm. A bare hour is as written ("alarm 7" is
     * 7 AM) unless a part of the day or "tonight" says otherwise ("alarm at 7 in the evening" is 7 PM). False too when
     * [words] have no clock time, or when-words the reader can't read ([readDay]).
     */
    private fun ringsOnTheDayNamed(words: String, now: Calendar, rings: Calendar, locale: Locale): Boolean {
        val day = readDay(words, now, locale) ?: return false
        val (typedHour, minute, bare) = extractTime(day.text) ?: return false
        var hour = typedHour
        val night = day.namedDay == NamedDay.TONIGHT || day.period == "night"
        var smallHours = false
        if (bare) {
            when {
                night -> when (hour) {
                    12 -> { hour = 0; smallHours = true }
                    5 -> return false
                    in 6..11 -> hour += 12
                    else -> smallHours = true
                }
                day.period != null && day.period != "morning" && hour != 12 -> hour += 12
            }
        }
        rings.set(Calendar.HOUR_OF_DAY, hour)
        rings.set(Calendar.MINUTE, minute)
        if (!rings.after(now)) rings.add(Calendar.DAY_OF_YEAR, 1)
        val today = LocalDate.of(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
        // "This morning", like "today", names today.
        val named = when {
            day.date != null -> if (smallHours) day.date.plusDays(1) else day.date
            day.period != null && !day.periodWithIn -> if (smallHours) today.plusDays(1) else today
            else -> return true
        }
        return named == LocalDate.of(rings.get(Calendar.YEAR), rings.get(Calendar.MONTH) + 1, rings.get(Calendar.DAY_OF_MONTH))
    }

    // ---------------------------------------------------------------------------------------------
    // REMINDER PARSER
    // ---------------------------------------------------------------------------------------------

    private fun parseReminder(lower: String, original: String, currentTimeMs: Long, locale: Locale): ParsedIntent.Reminder? {
        if (!lower.startsWith("remind me to ") && !lower.startsWith("reminder to ") && !lower.startsWith("remind me ")) return null

        val prefixLen = if (lower.startsWith("remind me to ")) 13 else if (lower.startsWith("reminder to ")) 12 else 10
        val rawTask = original.substring(prefixLen).trim()

        val temporalResult = extractTemporalInfo(rawTask, currentTimeMs, locale) ?: return null
        // "remind me tomorrow at 9" names no task; its time words aren't one. "Remind me on friday to call mom" puts its
        // "to" after the time, which is no part of the task either.
        val cleanTask = temporalResult.cleanText.replace(LEADING_TO_REGEX, "").ifBlank { "Reminder" }
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

    private fun parseCalendar(lower: String, original: String, currentTimeMs: Long, locale: Locale): ParsedIntent.CalendarEvent? {
        val calendarTriggers = listOf("add meeting ", "new meeting ", "meeting ", "add event ", "schedule ", "new event ")
        val matchedTrigger = calendarTriggers.firstOrNull { lower.startsWith(it) } ?: return null

        val rawBody = original.substring(matchedTrigger.length).trim()
        val temporalResult = extractTemporalInfo(rawBody, currentTimeMs, locale) ?: return null

        // "meeting tomorrow at 3pm" names no event beyond the meeting itself; its time words aren't a title.
        val isMeeting = "meeting" in matchedTrigger
        val text = temporalResult.cleanText
        val cleanTitle = when {
            text.isBlank() -> if (isMeeting) "Meeting" else "Event"
            isMeeting && MEETING_PREPOSITION_REGEX.containsMatchIn(text) -> "Meeting $text"
            else -> text
        }
        val startMs = temporalResult.timestampMs
        // A range ("2-3pm") ends when it says; anything else is an hour long.
        val endMs = temporalResult.endMs ?: (startMs + 60 * 60 * 1000)

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
            // "text editor, free" and "message saying sorry to her" name no one: no contact answers and the words are
            // a search's. A name no contact answers is still taken when it could be one ("text Sarah: running late").
            if (separatedTo == null && startsAsSearch(separated.groupValues[2])) return null
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

    /** Whether [name], as typed where a recipient would be, starts with one of the [NOT_A_NAME] words. */
    private fun startsAsSearch(name: String): Boolean =
        WORD_REGEX.find(name)?.value?.lowercase(Locale.ROOT)?.trim(',', ':', '.') in NOT_A_NAME

    /**
     * "text mom hello" read as "mom" and "hello": the most leading words (up to [MAX_RECIPIENT_WORDS], leaving at least
     * one for the message) that name a contact outright or make a number. Null when none do, or when the words start
     * as a search does ([NOT_A_NAME]), so "text to speech app" is never taken for a message, whoever is in contacts.
     */
    private fun splitAtRecipient(
        original: String,
        contactLookup: ((String) -> com.pixel.intelligentsearch.core.data.ContactItem?)?
    ): SplitMessage? {
        val match = MESSAGE_WITHOUT_SEPARATOR_REGEX.find(original) ?: return null
        val rest = match.groupValues[2]
        if (startsAsSearch(rest)) return null
        val words = WORD_REGEX.findAll(rest).toList()
        for (count in minOf(words.size - 1, MAX_RECIPIENT_WORDS) downTo 1) {
            val name = rest.substring(0, words[count - 1].range.last + 1).trim()
            val recipient = resolveRecipient(name, contactLookup) ?: continue
            // "text will not send", "message mark won't go through": a search for help with messages, whoever is in
            // contacts. "Text mom: not coming" still goes, its colon marking the message.
            val body = rest.substring(words[count].range.first).trim()
            if (TROUBLESHOOTING.containsMatchIn(body.replace('’', '\''))) return null
            return SplitMessage(match.groupValues[1], name, recipient, body)
        }
        return null
    }

    /**
     * What, right after a name, makes "text <name> ..." a question about messages not working ("text will not send",
     * "message mark not delivered", "text grace keeps failing"): a not-word or a stuck-word, then a word about sending
     * with nothing after it but what a phone sends ("not sending pictures", "won't receive texts from iphone"), or "not
     * working" with nothing after it. A message that only starts with one ("text mom can't make it", "text sarah stuck
     * in traffic", "text boss not working today", "text mom didn't receive the package") still goes.
     */
    private val TROUBLESHOOTING = Regex(
        "^(?:(?:not|won'?t|doesn'?t|isn'?t|can'?t|cannot|didn'?t|aren'?t|wasn'?t|stopped|stuck)\\s+" +
            "(?:(?:be|being|get|getting)\\s+)?(?:send(?:s|ing)?|sent|deliver(?:s|ed|ing)?|go(?:es|ing)?\\s+through|" +
            "fail(?:s|ed|ing)?|load(?:s|ed|ing)?|receiv(?:e|es|ed|ing)|show(?:s|ing)?\\s+up)" +
            "(?:\\s+(?:any\\s+|my\\s+|the\\s+|group\\s+)?(?:messages?|texts?|sms|mms|imessages?|pictures?|photos?|pics?|" +
            "images?|videos?|attachments?|links?|gifs?|emojis?|codes?|verification\\s+codes?))?" +
            "(?:\\s+(?:to|on|from|with)\\s+(?:an?\\s+)?(?:android|iphone|ios|samsung|pixel|my\\s+phone|wi-?fi))?\\W*$|" +
            "(?:not|won'?t|doesn'?t|isn'?t|aren'?t|stopped)\\s+work(?:s|ing)?\\W*$|keeps\\s+fail(?:s|ing)?(?!\\p{L}))",
        RegexOption.IGNORE_CASE
    )

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
        val contact = contactLookup?.invoke(target)
            ?.takeIf { it.phoneNumber.isNotBlank() && namesContact(target, it.name) } ?: return null
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

        // Only someone the words name outright, as for a message: the contacts search finds Will Smith for "will's mom"
        // too, who isn't the one to call.
        val matchedContact = if (cleanNumber == null) {
            contactLookup?.invoke(target)?.takeIf { it.phoneNumber.isNotBlank() && namesContact(target, it.name) }
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

    private data class TemporalResult(val timestampMs: Long, val cleanText: String, val endMs: Long? = null)

    /** The day word a phrase named, which decides how a bare hour like "at 3" is read. */
    internal enum class NamedDay { NONE, TODAY, TONIGHT, TOMORROW, WEEKDAY, DATE }

    /**
     * What a phrase's words of when say about its day ([readDay]): the [date] (null when no day was named), how it was
     * named, the part of the day ("morning", "night"; [periodWithIn] when said "in the ..."), and [text], the phrase
     * with those words blanked out.
     */
    internal class DayReading(
        val date: LocalDate?,
        val namedDay: NamedDay,
        val period: String?,
        val periodWithIn: Boolean,
        val text: String,
        /** The day is only a "for <holiday>" ("book flights for christmas"): beside a clock time, too unclear to use. */
        val forOccasionOnly: Boolean,
        /** Words of when that say no hour ("before bed", an unread "on the 3rd ..."): only a clock time makes it clear. */
        val needsTime: Boolean
    )

    /** One reading of a day in a phrase: where its words are, the day ([date] null when it can't be read), and how. */
    private class DayWords(
        val ranges: List<IntRange>,
        val date: LocalDate?,
        val namedDay: NamedDay,
        /** False for words that may as well say what the task is for: "for christmas", "in may". */
        val strong: Boolean,
        val forOccasion: Boolean = false,
        /** "dec 4", "11/5", "the 3rd", "on christmas": a weekday said beside one only has to fall on it. */
        val calendarDate: Boolean = false,
        /** The weekday named, as Calendar.DAY_OF_WEEK, for a weekday's reading. */
        val weekday: Int? = null
    )

    /**
     * When [rawText] names, from [baseTimeMs]; null when it names a time from now, a date or a clock time that can't
     * be read, or one already past, so the phrase is not taken as one set at some other time. [locale] says how a
     * numeric date is written. A time range ("2-3pm") also gives its end, [TemporalResult.endMs].
     */
    private fun extractTemporalInfo(rawText: String, baseTimeMs: Long, locale: Locale): TemporalResult? {
        val cal = Calendar.getInstance().apply { timeInMillis = baseTimeMs }
        val day = readDay(rawText, cal, locale) ?: return null
        // "730pm" is 7:30 PM.
        var text = day.text.replace(COMPACT_TIME_REGEX) { "${it.groupValues[1]}:${it.groupValues[2]}" }
        val namedDay = day.namedDay
        val period = day.period
        val isTonight = namedDay == NamedDay.TONIGHT
        day.date?.let { cal.set(it.year, it.monthValue - 1, it.dayOfMonth) }
        if (isTonight) {
            cal.set(Calendar.HOUR_OF_DAY, 20)
            cal.set(Calendar.MINUTE, 0)
        }
        // "This evening at 7" names today as surely as "today" does: a time of it already past is no time.
        val today = namedDay == NamedDay.TODAY || (period != null && !day.periodWithIn && namedDay == NamedDay.NONE)

        // Relative duration "in X minutes / hours / days / weeks"
        val relativeMatch = RELATIVE_TIME_REGEX.find(text)
        if (relativeMatch != null) {
            val minutes = relativeMinutes(relativeMatch) ?: return null
            val days = minutes / MINUTES_PER_DAY
            // Whole days and weeks keep the clock time across a daylight saving change. Hours and minutes are exact:
            // "in 24 hours" is 24 real hours even on the night the clocks change.
            if (inDaysOnly(relativeMatch) && days >= 1.0 && days == kotlin.math.floor(days)) {
                cal.add(Calendar.DAY_OF_YEAR, days.toInt())
            } else {
                cal.add(Calendar.MINUTE, Math.round(minutes).toInt())
            }
            text = text.replace(relativeMatch.value, "")
        } else if (UNREAD_RELATIVE_TIME_REGEX.containsMatchIn(text)) {
            return null
        }

        // "2-3pm", "from 2 to 3pm": the range is read as its start ("at 2 pm", the end's am/pm when the start has none),
        // and its end kept for an event. A bare "9-10" or "2 to 3", with no "from", am/pm or minutes, may as well be
        // pages or people, and with no other time it is a time that can't be read.
        var rangeEnd: ClockTime? = null
        var bareRange = false
        for (range in TIME_RANGE_REGEX.findAll(text)) {
            if (describesNext(text, range.range.last + 1)) continue
            val g = range.groupValues
            if (g[1].isEmpty() && g[3].isEmpty() && g[4].isEmpty() && g[6].isEmpty() && g[7].isEmpty()) {
                bareRange = true
                continue
            }
            val startHour = g[2].toInt()
            var startMeridiem = g[4].lowercase(Locale.ROOT)
            if (startMeridiem.isEmpty() && g[7].isNotEmpty() && startHour in 1..12) {
                // "11-1pm" starts in the morning, "12-1pm" at noon, "2-3pm" in the afternoon.
                val end = g[5].toInt() % 12 + if (g[7].equals("p", ignoreCase = true)) 12 else 0
                val asEnd = startHour % 12 + if (g[7].equals("p", ignoreCase = true)) 12 else 0
                startMeridiem = if (asEnd <= end) g[7].lowercase(Locale.ROOT) else if (g[7].equals("p", ignoreCase = true)) "a" else "p"
            }
            val start = " at ${g[2]}${if (g[3].isNotEmpty()) ":${g[3]}" else ""}${if (startMeridiem.isNotEmpty()) " ${startMeridiem}m" else ""} "
            rangeEnd = ClockTime(g[5].toIntOrNull() ?: return null, g[6].toIntOrNull() ?: 0, g[7].lowercase(Locale.ROOT), g[5].startsWith("0"))
            text = text.replaceRange(range.range, start)
            break
        }

        // "at noon" is 12:00; "at midnight" the end of the day named, or of today. "A midnight snack" is no time.
        val noonOrMidnight = NOON_OR_MIDNIGHT_TIME_REGEX.findAll(text).firstOrNull { !describesNext(text, it.range.last + 1) }
        if (noonOrMidnight != null) {
            val midnight = noonOrMidnight.groupValues[1].equals("midnight", ignoreCase = true)
            cal.set(Calendar.HOUR_OF_DAY, if (midnight) 0 else 12)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            if (midnight) cal.add(Calendar.DAY_OF_YEAR, 1)
            text = text.replace(noonOrMidnight.value, " ")
            if (namedDay == NamedDay.NONE && !today && relativeMatch == null && cal.timeInMillis < baseTimeMs) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Explicit time "at 3pm", "at 15:30", "at 10:00 am", "at 6.30pm", "at 3"
        val timeMatch = if (noonOrMidnight != null) null else EXPLICIT_TIME_REGEX.findAll(text).firstOrNull {
            it.groupValues[1].isNotEmpty() || it.groupValues[5].isNotEmpty() || it.groupValues[3] == ":"
        }
        if (timeMatch != null) {
            var hour = timeMatch.groupValues[2].toIntOrNull() ?: 9
            val minute = timeMatch.groupValues[4].toIntOrNull() ?: 0
            val amPm = timeMatch.groupValues[5].lowercase(Locale.ROOT)
            // "07:00" is 24-hour notation: the hour is meant exactly as written, whatever day is named or not.
            val zeroPadded = timeMatch.groupValues[2].length == 2 && timeMatch.groupValues[2][0] == '0'

            // A time that doesn't exist ("at 25:00", "13pm") is no time: Calendar is lenient and would roll it into
            // another day, and clamping it would set the event at a time nobody asked for. "At 7 30" is a time with
            // minutes the reader doesn't take apart.
            if (minute !in 0..59 || (amPm.isEmpty() && hour !in 0..23) || (amPm.isNotEmpty() && hour !in 1..12)) return null
            if (MINUTES_AFTER_REGEX.containsMatchIn(text.substring(timeMatch.range.last + 1))) return null
            if (amPm == "p" && hour < 12) hour += 12
            if (amPm == "a" && hour == 12) hour = 0
            val night = isTonight || period == "night"
            // A bare hour (no am/pm, no leading zero) is read by the first rule that says which half of the day it is:
            // - words that say so: a meal, bedtime, "after work" ([hourForWords]): "dinner tonight at 5:30" is 5:30 PM;
            // - "tonight" and a night run past midnight: "tonight at 9" is 21:00, "at 12" midnight, the end of the
            //   day, and 1 to 4 the small hours after it. "Tonight at 5" may be either, so it is no time;
            // - a part of the day: "tomorrow morning at 7" is 7 AM, "this evening at 7" 7 PM;
            // - tomorrow, a weekday or a date has no "already passed" to go by, so 1-7 is read as the afternoon or
            //   evening people mean ("tomorrow at 3") and 8-11 as the morning;
            // - with no day or "today", the next one still ahead: "at 3" said at 10 AM is 3 PM.
            var said: Int? = null
            if (amPm.isEmpty() && !zeroPadded && hour in 1..12) {
                said = hourForWords(text, hour)
                when {
                    said != null -> hour = said
                    night -> hour = when (hour) {
                        12 -> 0
                        5 -> return null
                        in 6..11 -> hour + 12
                        else -> hour
                    }
                    hour == 12 -> {}
                    period != null -> if (period != "morning") hour += 12
                    relativeMatch != null -> {}
                    namedDay == NamedDay.TOMORROW || namedDay == NamedDay.WEEKDAY || namedDay == NamedDay.DATE -> if (hour <= 7) hour += 12
                    else -> {
                        val morning = (cal.clone() as Calendar).apply {
                            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
                        }
                        if (morning.timeInMillis < baseTimeMs && morning.timeInMillis + 12 * 3_600_000L > baseTimeMs) hour += 12
                    }
                }
            }

            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            if (night && said == null && hour < 6) cal.add(Calendar.DAY_OF_YEAR, 1)
            text = text.replace(timeMatch.value, "")

            if (namedDay == NamedDay.NONE && !today && relativeMatch == null && cal.timeInMillis < baseTimeMs) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        } else if (period != null && noonOrMidnight == null && relativeMatch == null) {
            // "this evening" with no time is 6 PM. Once that has passed, "in the morning" is tomorrow's; "this
            // evening" or "today" has no time left to mean, so it is no reminder rather than one already gone.
            cal.set(Calendar.HOUR_OF_DAY, PART_OF_DAY_HOURS.getValue(period))
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            if (cal.timeInMillis <= baseTimeMs) {
                if (namedDay != NamedDay.NONE || !day.periodWithIn) return null
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val clockTime = noonOrMidnight != null || timeMatch != null
        // Another time left in the task ("at 1930", "at 5pm 6pm"), or a bare range with nothing else to say when: a
        // time that can't be read.
        if (LEFTOVER_TIME_REGEX.containsMatchIn(text) || (bareRange && !clockTime && relativeMatch == null)) return null
        val timed = relativeMatch != null || clockTime || period != null || isTonight
        // "Before bed" or "the 3rd floor" with no time read: when is unclear. "Book flights for christmas at 5pm" may
        // be today at 5 or Christmas at 5.
        if (day.needsTime && !timed) return null
        if (day.forOccasionOnly && clockTime) return null
        // Never a reminder or event already gone ("tonight" said at 21:00, "on 1/2/25"); the minute now is still now.
        if (cal.timeInMillis < baseTimeMs - Math.floorMod(baseTimeMs, 60_000L)) return null
        val endMs = if (rangeEnd != null && timeMatch != null) {
            rangeEndMillis(cal, rangeEnd) ?: return null
        } else null
        val cleanTitle = text.replace(WHITESPACE_REGEX, " ").trim(*STRAY_PUNCTUATION)
        return TemporalResult(cal.timeInMillis, cleanTitle, endMs)
    }

    /** A clock time as typed: the hour (1-12 with [meridiem] "a" or "p", else 0-23), minutes and a leading zero. */
    private class ClockTime(val hour: Int, val minute: Int, val meridiem: String, val zeroPadded: Boolean)

    /**
     * When a range that starts at [start] ends, at [end]: the first such time after the start, on its day or the next
     * ("10pm-1am"). Null when it is no time, or more than a day on.
     */
    private fun rangeEndMillis(start: Calendar, end: ClockTime): Long? {
        if (end.minute !in 0..59) return null
        val hours = when {
            end.meridiem.isNotEmpty() -> if (end.hour in 1..12) listOf(end.hour % 12 + if (end.meridiem == "p") 12 else 0) else return null
            end.zeroPadded || end.hour == 0 || end.hour in 13..23 -> listOf(end.hour)
            end.hour in 1..12 -> listOf(end.hour % 12, end.hour % 12 + 12)
            else -> return null
        }
        val startMinutes = start.get(Calendar.HOUR_OF_DAY) * 60 + start.get(Calendar.MINUTE)
        val minutesOn = (hours.map { it * 60 + end.minute } + hours.map { it * 60 + end.minute + MINUTES_PER_DAY })
            .filter { it > startMinutes }.minOrNull() ?: return null
        return start.timeInMillis + (minutesOn - startMinutes) * 60_000L
    }

    /**
     * The day [rawText] names, read from [now] (its date, in its zone). The reader has a closed grammar: it reads these
     * and nothing else.
     * - Day words: "today", "tonight", "tomorrow" (also written "tmrw", "tmr" or "tmw"), "the day after tomorrow",
     *   "next week".
     * - A weekday: "friday", "on friday", "this friday", "next friday", "friday next week". Its short name ("fri",
     *   "tues", "sat") only where a day goes: after "on", "by", "this", "next" or "coming", or right before a clock time
     *   with no "the", "some" or the like before it ("fri 2pm"), so "sat down", "the sun" and "get some sun at 2pm" are
     *   no days.
     * - A weekday with its date: "friday the 13th", "tuesday, 11/10", "monday nov 2". The weekday must fall on the date.
     * - A month and day, either way round: "dec 5", "december 5th, 2027", "the 5th of december".
     * - "on", "for" or "by" and a day of the month ("on the 3rd", "by the fifteenth") or a numeric date ("on 11/5"). A
     *   day of the month alone ("the 12th") is one only right before a clock time ("dentist the 12th at 10am"), and a
     *   numeric date only when it opens the phrase ("12/5 at 3pm"): "take 1/2" and "episode 4/5" are amounts.
     * - "On" or "for" and a holiday ("on thanksgiving", "for christmas"), and "in <month>".
     * - A part of the day: "this evening", "in the morning", "tomorrow night", "friday afternoon".
     * [extractTemporalInfo] then reads the clock time: "at 3", "3pm", "15:30", "07:00" (a leading zero is the hour as
     * written), "6.30pm", "730pm", "5 o'clock", "noon", "midnight", a range ("2-3pm", "from 2 to 3pm"), or a time from
     * now ("in 10 minutes", "in an hour"); words that say which half of the day a bare hour is ("dinner at 7", "before
     * bed") go before any other rule ([hourForWords]).
     *
     * A date, holiday, month or weekday followed by a word it describes is part of the task instead: "the 3rd floor",
     * "christmas presents", "the first time", "sunday school" ([DATE_FOLLOWERS] lists the words that may follow a day).
     * Such a weekday leaves the phrase with no day unless another is named ("call the sunday school teacher tomorrow"),
     * as "friday lunch" may as well be Friday's.
     *
     * When more than one reading is found they must agree. "For <holiday>" and "in <month>" may say what the task is
     * for ("buy candy for halloween tomorrow"), so any other reading wins over them and they stay in the task. A weekday
     * said with a date ("friday, dec 4") must fall on it.
     *
     * Anything else that reads as a when-word gives null: readings that disagree ("on the 3rd ... tomorrow"), a date
     * that doesn't exist, a day that can't be read ("on mother's day", "wensday", "a week from today", "early next
     * week", "2 days before christmas", "the second friday of november"), a time said roughly or as a bound ("at 7ish",
     * "around 7", "by 5", "by christmas", "at seven"), and a task that repeats ("every monday", "the 1st of every month",
     * "daily").
     *
     * Last, the words left once all of that is out, the task, are looked over ([hasUnreadDayLeft]): any that still look
     * like a date or a day ("dentist 11/15 at 3pm", "on the 15", "december first", "call mom fri", "saturday's game",
     * "on election day", "in 5") give null too. So the grammar is closed both ways: a when-word the lists above don't
     * know can't fall through to today or now. When in doubt the phrase is searched rather than set on a guessed day or
     * for now, as no action is better than a wrong one.
     *
     * [clockFollows] says a clock time comes right after [rawText] (the short phrasings read the time apart), which "the
     * 12th" and "fri" at its end are then right before.
     */
    internal fun readDay(rawText: String, now: Calendar, locale: Locale, clockFollows: Boolean = false): DayReading? {
        if (hasUnreadWhenWords(rawText)) return null
        val today = LocalDate.of(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
        // Words read are blanked with as many spaces, so every match keeps its place. [scan] loses each reading as it
        // is found, so no words are read twice; [text] only the ones taken.
        var scan = rawText
        var text = rawText
        fun blank(source: String, range: IntRange) = source.replaceRange(range, " ".repeat(range.last - range.first + 1))
        val readings = ArrayList<DayWords>()
        fun add(reading: DayWords) {
            readings += reading
            for (range in reading.ranges) scan = blank(scan, range)
        }
        // Whether a clock time comes right after [from]: "fri 2pm", "the 12th at 10am".
        fun timeFollows(from: Int): Boolean =
            TIME_FOLLOWS_REGEX.containsMatchIn(rawText.substring(from)) || (clockFollows && scan.substring(from).isBlank())
        var unreadDayOfMonth = false

        // The part of the day goes first, while the day word before it ("tomorrow morning") is still there to find it.
        val partOfDay = PART_OF_DAY_REGEX.find(scan)
        if (partOfDay != null) {
            scan = blank(scan, partOfDay.range)
            text = blank(text, partOfDay.range)
        }
        val period = partOfDay?.groupValues?.get(2)?.lowercase(Locale.ROOT)

        for (match in OCCASION_REGEX.findAll(scan).toList() + UNREAD_OCCASION_REGEX.findAll(scan)) {
            if (describesNext(scan, match.range.last + 1)) continue
            val forOccasion = match.groupValues[1].equals("for", ignoreCase = true)
            // An unread day ([UNREAD_OCCASION_REGEX]) has no name to read: its reading is one that can't be.
            val name = match.groupValues.getOrNull(2)?.lowercase(Locale.ROOT)?.replace('’', '\'')?.replace(WHITESPACE_REGEX, " ")
            add(DayWords(listOf(match.range), name?.let { OCCASIONS.occasionOnOrAfter(it, today) }, NamedDay.DATE,
                strong = !forOccasion, forOccasion = forOccasion, calendarDate = !forOccasion))
        }
        DAY_AFTER_TOMORROW_REGEX.find(scan)?.let { add(DayWords(listOf(it.range), today.plusDays(2), NamedDay.TOMORROW, strong = true)) }
        // A weekday's date ("friday the 13th", "tuesday 11/10") is read here, as no "on" comes before it; the weekday is
        // read below and must fall on it. A month and day after one ("monday nov 2") is read as any other.
        val datedWeekdays = HashSet<Int>()
        for (match in WEEKDAY_DATE_REGEX.findAll(scan)) {
            val weekday = match.groups[1]!!.range
            val date = when {
                match.groups[2] != null || match.groups[3] != null -> {
                    // "monday the 3rd floor": no date.
                    if (describesNext(scan, match.range.last + 1)) continue
                    val dayOfMonth = match.groups[2]?.value?.toInt()
                        ?: SPELLED_ORDINALS[match.groupValues[3].lowercase(Locale.ROOT).replace(WHITESPACE_REGEX, "-")] ?: return null
                    dayOfMonthOnOrAfter(today, dayOfMonth) ?: return null
                }
                match.groups[4] != null -> numericDate(match.groupValues[4], match.groupValues[5], match.groupValues[6], today, locale) ?: return null
                else -> null
            }
            datedWeekdays += weekday.first
            if (date != null) add(DayWords(listOf(weekday.last + 1..match.range.last), date, NamedDay.DATE, strong = true, calendarDate = true))
        }
        for (match in MONTH_DAY_REGEX.findAll(scan).toList() + DAY_MONTH_REGEX.findAll(scan)) {
            if (scan.substring(match.range).isBlank()) continue
            val monthFirst = match.groupValues[1].firstOrNull()?.isLetter() == true
            val (monthText, dayText) = if (monthFirst) match.groupValues[1] to match.groupValues[2] else match.groupValues[2] to match.groupValues[1]
            val month = MONTH_ABBREVIATIONS.indexOf(monthText.take(3).lowercase(Locale.ROOT)) + 1
            val date = dateOn(today, month, dayText.toInt(), match.groupValues[3].toIntOrNull()) ?: return null
            add(DayWords(listOf(match.range), date, NamedDay.DATE, strong = true, calendarDate = true))
        }
        for (match in NUMERIC_DATE_REGEX.findAll(scan)) {
            if (!readsAsDate(match, scan)) continue
            val date = numericDate(match.groupValues[2], match.groupValues[3], match.groupValues[4], today, locale) ?: return null
            add(DayWords(listOf(match.range), date, NamedDay.DATE, strong = true, calendarDate = true))
        }
        for (match in ORDINAL_DAY_REGEX.findAll(scan)) {
            if (describesNext(scan, match.range.last + 1)) {
                // "Meeting on the 3rd floor": no date, but with no time either it's unclear.
                if (!match.groupValues[1].equals("for", ignoreCase = true)) unreadDayOfMonth = true
                continue
            }
            val dayOfMonth = match.groupValues[2].toIntOrNull()
                ?: SPELLED_ORDINALS[match.groupValues[3].lowercase(Locale.ROOT).replace(WHITESPACE_REGEX, "-")] ?: return null
            val date = dayOfMonthOnOrAfter(today, dayOfMonth) ?: return null
            add(DayWords(listOf(match.range), date, NamedDay.DATE, strong = true, calendarDate = true))
        }
        for (match in BARE_ORDINAL_REGEX.findAll(scan)) {
            if (scan.substring(match.range).isBlank() || !timeFollows(match.range.last + 1)) continue
            val dayOfMonth = match.groupValues[1].toIntOrNull()
                ?: SPELLED_ORDINALS[match.groupValues[2].lowercase(Locale.ROOT).replace(WHITESPACE_REGEX, "-")] ?: return null
            val date = dayOfMonthOnOrAfter(today, dayOfMonth) ?: return null
            add(DayWords(listOf(match.range), date, NamedDay.DATE, strong = true, calendarDate = true))
        }
        for (match in MONTH_ALONE_REGEX.findAll(scan)) {
            if (describesNext(scan, match.range.last + 1)) continue
            val month = MONTH_ABBREVIATIONS.indexOf(match.groupValues[1].take(3).lowercase(Locale.ROOT)) + 1
            val year = match.groupValues[2].toIntOrNull()
            // "In october" said in October may be this month or next year's: no reading.
            val date = if (year == null && month == today.monthValue) null else dateOn(today, month, 1, year)
            add(DayWords(listOf(match.range), date, NamedDay.DATE, strong = false))
        }
        for ((regex, offset, named) in listOf(
            Triple(TOMORROW_REGEX, 1L, NamedDay.TOMORROW), Triple(TONIGHT_REGEX, 0L, NamedDay.TONIGHT),
            Triple(TODAY_REGEX, 0L, NamedDay.TODAY)
        )) {
            for (match in regex.findAll(scan).toList()) add(DayWords(listOf(match.range), today.plusDays(offset), named, strong = true))
        }
        val nextWeek = NEXT_WEEK_REGEX.find(scan)
        var weekdayRead = false
        var unreadWeekday = false
        for (match in WEEKDAY_REGEX.findAll(scan).toList()) {
            val word = match.groups[3]!!
            val typed = word.value.lowercase(Locale.ROOT)
            val short = typed in SHORT_WEEKDAYS
            val lead = match.groupValues[1].lowercase(Locale.ROOT)
            val modifier = match.groupValues[2].lowercase(Locale.ROOT)
            val after = match.range.last + 1
            // "The sun", "my sat score": a thing, not a day.
            if (short && lead.isEmpty() && modifier.isEmpty() && wordBefore(rawText, word.range.first) in DETERMINERS) continue
            if (word.range.first !in datedWeekdays && !timeFollows(after)) {
                // A short name is a day only where a day goes ("on sat", "next fri"), and "put on sun cream" is a thing.
                // A full one followed by a word it describes ("sunday school", "the friday standup") is part of the task.
                val describes = describesNext(rawText, after)
                if (short) {
                    if (describes || (modifier.isEmpty() && lead != "on" && lead != "by" && !(clockFollows && scan.substring(after).isBlank()))) continue
                } else if (describes) {
                    unreadWeekday = true
                    continue
                }
            }
            val target = WEEKDAYS.indexOf(SHORT_WEEKDAYS[typed] ?: typed) + Calendar.SUNDAY
            val ahead = weekdayAhead(now, target, modifier, nextWeek != null, locale)
            val named = if (ahead == 0) NamedDay.TODAY else NamedDay.WEEKDAY
            add(DayWords(listOfNotNull(match.range, nextWeek?.range?.takeIf { !weekdayRead }), today.plusDays(ahead.toLong()), named,
                strong = true, weekday = target))
            weekdayRead = true
        }
        // "next week" with no day is a week from now, as "in a week" is.
        if (!weekdayRead && nextWeek != null) add(DayWords(listOf(nextWeek.range), today.plusDays(7), NamedDay.WEEKDAY, strong = true))

        // A weekday said with a date only says which day of the week that is; one it isn't makes no reading.
        val dates = readings.filter { it.strong && it.calendarDate }
        val agreed = readings.map { reading ->
            if (reading.weekday == null || dates.isEmpty()) return@map reading
            val date = dates.first().date
            if (date == null || date.dayOfWeek.value % 7 + Calendar.SUNDAY != reading.weekday) return null
            DayWords(reading.ranges, date, NamedDay.DATE, strong = true, calendarDate = true)
        }
        val chosen = agreed.filter { it.strong }.ifEmpty { agreed }
        if (chosen.map { it.date }.distinct().size > 1 || chosen.any { it.date == null }) return null
        // "The sunday school teacher" is no Sunday, but "friday lunch" may well be Friday's: with no other day named,
        // which is meant can't be told.
        if (unreadWeekday && chosen.isEmpty()) return null
        for (reading in chosen) for (range in reading.ranges) text = blank(text, range)
        // A time from now is read later; its "an hour and a half to stretch" is no "half to 5".
        if (UNREAD_DATE_REGEX.containsMatchIn(RELATIVE_TIME_REGEX.replace(scan, " "))) return null
        // A day of the month left unread that describes nothing ("pay rent the 12th", "the 1st and the 15th").
        if (BARE_ORDINAL_REGEX.findAll(scan).any { scan.substring(it.range).isNotBlank() && !describesNext(scan, it.range.last + 1) }) {
            return null
        }
        if (hasUnreadDayLeft(scan)) return null

        val namedDay = listOf(NamedDay.TONIGHT, NamedDay.TOMORROW, NamedDay.TODAY, NamedDay.WEEKDAY, NamedDay.DATE)
            .firstOrNull { kind -> chosen.any { it.namedDay == kind } } ?: NamedDay.NONE
        return DayReading(
            date = chosen.firstOrNull()?.date,
            namedDay = namedDay,
            period = period,
            periodWithIn = partOfDay?.groupValues?.get(1)?.startsWith("in", ignoreCase = true) == true,
            text = text,
            forOccasionOnly = chosen.isNotEmpty() && chosen.all { it.forOccasion },
            needsTime = UNTIMED_WHEN_REGEX.containsMatchIn(scan) || (unreadDayOfMonth && chosen.isEmpty())
        )
    }

    /**
     * Whether [text] has when-words outside the reader's grammar ([NEVER_READ_REGEX], [ROUGH_TIME_REGEX],
     * [REPEAT_OR_ROUGH_PART_REGEX], a misspelled weekday): a phrase with any is no event, reminder or alarm.
     */
    private fun hasUnreadWhenWords(text: String): Boolean =
        NEVER_READ_REGEX.containsMatchIn(text) ||
            ROUGH_TIME_REGEX.findAll(text).any { !describesNext(text, it.range.last + 1) } ||
            REPEAT_OR_ROUGH_PART_REGEX.findAll(text).any { !describesNext(text, it.range.last + 1) } ||
            UNKNOWN_WEEKDAY_REGEX.findAll(text).any { it.value.lowercase(Locale.ROOT) !in KNOWN_DAY_WORDS }

    /**
     * Whether [left], a phrase's words with everything [readDay] read blanked out, still holds a date or a day: the
     * grammar is closed for what it reads, and this keeps it closed for what it leaves, so a when-word it doesn't know
     * makes no action rather than one set for today or now. These count, unless they plainly describe the word after
     * them ("the 3rd floor", "5-10 minutes", "sunday school"):
     * - digits that may be a date ("11/15", "12-1", "15/11"), but not a fraction or a count of small numbers ("take
     *   1/2", "episode 4/5", "chapters 2-3") or a time range ("2-3pm", "at 10-11");
     * - a day of the month with no suffix after "on" or "the" ("on the 15");
     * - a month beside a day ("december first", "the fifth of december"), "on december" or "of november";
     * - a weekday, its short name (not after "the", "some" and the like: "the sun", "get some sun") or its possessive
     *   ("saturday's game");
     * - a day named for something ("on election day", "for veterans day") that isn't one of [OCCASIONS];
     * - "in" and a number with no unit ("in 5"), and a time with a dot after "the" ("the 7.30 train").
     * When in doubt, the phrase counts as having one: a search is better than a reminder on the wrong day.
     */
    private fun hasUnreadDayLeft(left: String): Boolean {
        for (match in DATE_SHAPED_REGEX.findAll(left)) {
            if (match.groups[1] == null) return true
            val first = match.groupValues[1].toInt()
            val second = match.groupValues[3].toInt()
            if (first < 1 || second < 1 || !((first <= 12 && second <= 31) || (second <= 12 && first <= 31))) continue
            if (first < second && second <= 9) continue
            // "The 9/11 memorial" names an event, as "christmas presents" does.
            if (first == 9 && second == 11 && match.groupValues[2] == "/" && describesNext(left, match.range.last + 1)) continue
            if (match.groupValues[2] == "-" && (RANGE_END_TIME_REGEX.containsMatchIn(left.substring(match.range.last + 1)) ||
                    wordBefore(left, match.range.first) in RANGE_STARTS || describesNext(left, match.range.last + 1))
            ) continue
            return true
        }
        if (BARE_DAY_NUMBER_REGEX.findAll(left).any { !describesNext(left, it.range.last + 1) }) return true
        if (MONTH_LEFT_REGEX.containsMatchIn(left)) return true
        if (FULL_WEEKDAY_LEFT_REGEX.findAll(left).any { !describesNext(left, it.range.last + 1) }) return true
        if (SHORT_WEEKDAY_LEFT_REGEX.findAll(left).any {
                wordBefore(left, it.range.first) !in DETERMINERS && !describesNext(left, it.range.last + 1)
            }) return true
        for (match in NAMED_DAY_LEFT_REGEX.findAll(left)) {
            val name = match.groupValues[1].lowercase(Locale.ROOT).replace('’', '\'').replace(WHITESPACE_REGEX, " ")
            if (name in OCCASIONS.occasionNames || name in WEEKDAYS || name == "today" || describesNext(left, match.range.last + 1)) continue
            return true
        }
        return IN_BARE_NUMBER_REGEX.containsMatchIn(RELATIVE_TIME_REGEX.replace(left, " ")) || TIME_LEFT_REGEX.containsMatchIn(left)
    }

    /** The word right before [index] in [text], lowercased ("the" in "the sun"), or "" at its start. */
    private fun wordBefore(text: String, index: Int): String =
        text.substring(0, index).trimEnd().substringAfterLast(' ').lowercase(Locale.ROOT)

    /** A numeric date ("11/10", "10/11/27"), the month or the day first as [locale]'s region writes dates. */
    private fun numericDate(first: String, second: String, year: String, today: LocalDate, locale: Locale): LocalDate? {
        val (month, dayOfMonth) = if (monthBeforeDay(locale)) first.toInt() to second.toInt() else second.toInt() to first.toInt()
        return dateOn(today, month, dayOfMonth, year.toIntOrNull()?.let { if (it < 100) 2000 + it else it })
    }

    /**
     * Days from [now] to the weekday [target] (Calendar.DAY_OF_WEEK). "Friday" and "on friday" are the next Friday,
     * and naming today's weekday means a week from today; "this friday" said on a Friday is today. "Next friday" and
     * "friday next week" are the Friday of next week, which starts on the region's first day of the week, but never
     * tomorrow, which would have been said so: "next sunday" said on a Saturday is the Sunday a week later.
     */
    private fun weekdayAhead(now: Calendar, target: Int, modifier: String, nextWeek: Boolean, locale: Locale): Int {
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
        val coming = (target - dayOfWeek + 7) % 7
        if (modifier == "next" || nextWeek) {
            val firstDay = Calendar.getInstance(locale).firstDayOfWeek
            val intoWeek = (dayOfWeek - firstDay + 7) % 7
            val inNextWeek = 7 - intoWeek + (target - firstDay + 7) % 7
            return if (inNextWeek == 1) 8 else inNextWeek
        }
        if (modifier == "this" && coming == 0) return 0
        return if (coming == 0) 7 else coming
    }

    /**
     * Whether the word at [from] in [text] is one a date or time before it describes ("the 3rd floor", "christmas
     * presents", "a midnight snack"), rather than one that may follow a date ([DATE_FOLLOWERS]).
     */
    private fun describesNext(text: String, from: Int): Boolean {
        val next = WORD_REGEX.find(text, from)?.value ?: return false
        if (!next.first().isLetter()) return false
        val word = next.lowercase(Locale.ROOT).replace('’', '\'').trimEnd(',', '.', ';', ':', '!').removeSuffix("'s")
        return word !in DATE_FOLLOWERS
    }

    /**
     * Whether [match], digits like "11/5", is a date: said with "on", "for" or "by", or opening [text] with nothing
     * after it but a time. "Take 1/2 at 9pm" and "buy 1/2 gallon milk" are amounts, and stay in the task.
     */
    private fun readsAsDate(match: MatchResult, text: String): Boolean {
        if (match.groupValues[1].isNotEmpty()) return true
        if (text.substring(0, match.range.first).isNotBlank()) return false
        val after = text.substring(match.range.last + 1).trimStart()
        return after.isEmpty() || !after.first().isLetter() || after.startsWith("at ", ignoreCase = true)
    }

    private val MEAL_REGEX = Regex("\\b(?:breakfast|brunch|lunch|dinner|supper)\\b", RegexOption.IGNORE_CASE)
    private val BEDTIME_REGEX = Regex("\\b(?:before|at|by)\\s+bed\\b|\\bbedtime\\b", RegexOption.IGNORE_CASE)
    private val AFTER_WORK_REGEX = Regex("\\bafter\\s+(work|school|class)\\b", RegexOption.IGNORE_CASE)
    private val BEFORE_WORK_REGEX = Regex("\\bbefore\\s+(?:work|school|class)\\b", RegexOption.IGNORE_CASE)

    /**
     * The 24-hour hour a bare [hour] (no am/pm) means beside the words of [words] that say which half of the day: a
     * meal (breakfast is the morning, brunch late morning to early afternoon, lunch 11 to 3, dinner and supper the
     * evening), bedtime (6 to 11 at night), "after work" (the afternoon or evening), "after school" or "after class" (1
     * to 7 in the afternoon or evening) and "before work", "school" or "class" (5 to 11 in the morning). These go before
     * any other rule, "tonight"'s included: "dinner tonight at 5:30" is 5:30 PM. A word counts only when it is the plan
     * itself, at the time or ending the task ("dinner at 7", "make dinner at 7", "at 7 to eat dinner"): one that
     * describes the word after it ("book dinner reservations at 10") or says what the task is for ("take the chicken
     * out for dinner at 8") says nothing about the hour. Null when no such word is there or the hour doesn't fit it,
     * which is then read as any other.
     */
    internal fun hourForWords(words: String, hour: Int): Int? {
        fun counts(word: MatchResult) = !describesNext(words, word.range.last + 1)
        val meal = MEAL_REGEX.findAll(words).firstOrNull { counts(it) && wordBefore(words, it.range.first) != "for" }
        when (meal?.value?.lowercase(Locale.ROOT)) {
            "breakfast" -> return hour.takeIf { it in 5..11 }
            "brunch" -> return when (hour) { in 9..12 -> hour; in 1..2 -> hour + 12; else -> null }
            "lunch" -> return when (hour) { 11, 12 -> hour; in 1..3 -> hour + 12; else -> null }
            "dinner", "supper" -> return if (hour in 1..11) hour + 12 else null
        }
        if (BEDTIME_REGEX.findAll(words).any { counts(it) }) return if (hour in 6..11) hour + 12 else null
        AFTER_WORK_REGEX.findAll(words).firstOrNull { counts(it) }?.let { after ->
            val latest = if (after.groupValues[1].equals("work", ignoreCase = true)) 11 else 7
            return if (hour in 1..latest) hour + 12 else null
        }
        if (BEFORE_WORK_REGEX.findAll(words).any { counts(it) }) return hour.takeIf { it in 5..11 }
        return null
    }

    /**
     * Whether the phone's region writes the month before the day ("11/5" is November 5 in the US, May 11 in the UK),
     * from its own short date pattern.
     */
    private fun monthBeforeDay(locale: Locale): Boolean {
        val pattern = (DateFormat.getDateInstance(DateFormat.SHORT, locale) as? SimpleDateFormat)?.toPattern() ?: return true
        val month = pattern.indexOfFirst { it == 'M' || it == 'L' }
        val day = pattern.indexOf('d')
        return month < 0 || day < 0 || month < day
    }

    /**
     * [day] of this month, or of the next month that has it once it's past ("on the 31st" in November is December 31).
     * Null when no month has it.
     */
    private fun dayOfMonthOnOrAfter(today: LocalDate, day: Int): LocalDate? {
        if (day !in 1..31) return null
        for (monthsAhead in 0L..12L) {
            val month = today.withDayOfMonth(1).plusMonths(monthsAhead)
            if (day > month.lengthOfMonth()) continue
            val date = month.withDayOfMonth(day)
            if (date >= today) return date
        }
        return null
    }

    /**
     * [day] of [month] (1-12): in [year] when one was given, else the next time that date comes round, today included.
     * Null when the date doesn't exist ("feb 30").
     */
    private fun dateOn(today: LocalDate, month: Int, day: Int, year: Int?): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        // "feb 29" may be a few years ahead.
        for (candidateYear in (year ?: today.year)..(year ?: (today.year + 8))) {
            val date = runCatching { LocalDate.of(candidateYear, month, day) }.getOrNull() ?: continue
            if (year == null && date < today) continue
            return date
        }
        return null
    }

    /**
     * The minutes a [RELATIVE_TIME_REGEX] or [ALARM_DURATION_REGEX] match puts ahead, every part added up ("1 hour 30
     * minutes" is 90), or null when unreadable or more than a year.
     */
    private fun relativeMinutes(match: MatchResult): Double? {
        val text = match.value.lowercase(Locale.ROOT)
        val parts = RELATIVE_PART_REGEX.findAll(text).toList()
        val minutes = if (parts.isNotEmpty()) {
            val said = parts.sumOf { part ->
                val amount = part.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
                amount * minutesPer(part.groupValues[2])
            }
            // "1 hour and a half" is half the last unit more; "1 hour 30" is 30 of the next smaller one, minutes after
            // hours and seconds after minutes. 60 or more of it is a typo, and days have no unit left unsaid.
            val lastUnit = minutesPer(parts.last().groupValues[2])
            val after = text.substring(parts.last().range.last + 1)
            val trailing = RELATIVE_TRAILING_REGEX.find(after)?.groupValues?.get(1)?.toInt()
            when {
                RELATIVE_HALF_REGEX.containsMatchIn(after) -> said + lastUnit / 2.0
                trailing != null -> if (lastUnit > 60 || trailing >= 60) return null else said + trailing * lastUnit / 60.0
                else -> said
            }
        } else {
            val unit = RELATIVE_WORD_UNIT_REGEX.find(text)?.value ?: return null
            val amount = when {
                text.contains("and a half") -> 1.5
                text.contains("half") -> 0.5
                else -> 1.0
            }
            amount * minutesPer(unit)
        }
        return minutes.takeIf { it.isFinite() && it <= 366.0 * MINUTES_PER_DAY }
    }

    /** Whether a time from now is whole days or weeks only, which keep the clock time across a daylight saving change. */
    private fun inDaysOnly(match: MatchResult): Boolean {
        val text = match.value.lowercase(Locale.ROOT)
        val units = RELATIVE_PART_REGEX.findAll(text).map { it.groupValues[2] }.toList()
            .ifEmpty { listOfNotNull(RELATIVE_WORD_UNIT_REGEX.find(text)?.value) }
        return units.isNotEmpty() && units.all { it[0] in "dw" }
    }

    private fun minutesPer(unit: String): Int = when (unit[0]) {
        'w' -> 7 * MINUTES_PER_DAY
        'd' -> MINUTES_PER_DAY
        'h' -> 60
        else -> 1
    }

    /** An alarm's time of day, 24-hour; [bare] when typed with no am/pm or leading zero ("7", "7:30"). */
    private data class AlarmTime(val hour: Int, val minute: Int, val bare: Boolean)

    private fun extractTime(text: String): AlarmTime? {
        NOON_OR_MIDNIGHT_REGEX.find(text)?.let { return AlarmTime(if (it.groupValues[1].equals("midnight", ignoreCase = true)) 0 else 12, 0, false) }
        val match = CLOCK_TIME_REGEX.find(text) ?: return null

        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val amPm = match.groupValues[3].lowercase(Locale.ROOT)

        if (amPm.isNotEmpty() && hour !in 1..12) return null
        if (amPm == "p" && hour < 12) hour += 12
        if (amPm == "a" && hour == 12) hour = 0

        if (hour !in 0..23 || minute !in 0..59) return null
        return AlarmTime(hour, minute, bare = amPm.isEmpty() && hour in 1..12 && !match.groupValues[1].startsWith("0"))
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
