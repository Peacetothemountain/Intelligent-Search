package com.ngdesigns.intelligentsearch.engine.actions

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Something a query asks the phone to do. */
sealed interface Action {
    data class Timer(val seconds: Int, val label: String? = null) : Action

    /** An alarm at [time] on [date] (the next time that clock time comes round, unless a day was named). */
    data class Alarm(val time: LocalTime, val date: LocalDate, val label: String? = null) : Action {
        val hour: Int get() = time.hour
        val minute: Int get() = time.minute
    }

    data class Reminder(val task: String, val at: LocalDateTime?) : Action

    data class Event(val title: String, val start: LocalDateTime) : Action

    data class Message(val recipient: String, val body: String?, val app: MessagingApp = MessagingApp.Sms) : Action

    data class Call(val target: String, val video: Boolean = false) : Action

    data class Navigate(val destination: String) : Action
}

enum class MessagingApp { Sms, WhatsApp, Telegram, Signal }

/**
 * Reads everyday commands ("set a timer for 10 minutes", "wake me up at 7:30", "text Sarah that I'm late",
 * "directions home", "lunch with Sam on Friday at 12:30"). Returns null for anything else, so ordinary searches
 * are never mistaken for commands. [now] is a parameter so time handling is testable.
 */
object Actions {

    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): Action? {
        val text = input.trim().replace(Regex("\\s+"), " ")
        if (text.length < 4) return null
        return timer(text) ?: alarm(text, now) ?: reminder(text, now) ?: message(text) ?: call(text) ?: navigate(text)
            ?: event(text, now)
    }

    // Timers

    private val timerLead = Regex("^(?:(?:set|start|make|create)\\s+(?:a\\s+|an\\s+|the\\s+)?)?(?:timer|countdown)(?:\\s+for)?\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val timerTrail = Regex("^(?:(?:set|start)\\s+(?:a\\s+|an\\s+)?)?(.+?)\\s+(?:timer|countdown)$", RegexOption.IGNORE_CASE)
    private const val DAY = 86_400

    private fun timer(text: String): Action.Timer? {
        val spec = (timerLead.find(text) ?: timerTrail.find(text))?.groupValues?.get(1)?.trim() ?: return null
        val seconds = duration(spec) ?: return null
        return if (seconds in 1..DAY) Action.Timer(seconds) else null
    }

    private val unitPart = Regex("(\\d+(?:\\.\\d+)?)\\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)(?=[0-9]|\\s|$)", RegexOption.IGNORE_CASE)

    /** "15" (minutes), "1:30" (m:ss), "1:05:00", "1h 30m 15s", "5 mins 30 secs", "90 seconds". */
    internal fun duration(spec: String): Int? {
        val s = spec.lowercase().removeSuffix(".").replace(" and ", " ").trim()
        Regex("^(\\d{1,2}):(\\d{2})(?::(\\d{2}))?$").find(s)?.let { m ->
            val (a, b, c) = m.destructured
            return if (c.isEmpty()) {
                if (b.toInt() >= 60) null else a.toInt() * 60 + b.toInt()
            } else {
                if (b.toInt() >= 60 || c.toInt() >= 60) null else a.toInt() * 3600 + b.toInt() * 60 + c.toInt()
            }
        }
        if (Regex("^\\d{1,6}$").matches(s)) return s.toLong().let { if (it * 60 > DAY) null else (it * 60).toInt() }
        val parts = unitPart.findAll(s).toList()
        if (parts.isEmpty()) return null
        // Everything must be accounted for: "timer 2 3" or "timer for pasta 5" are not durations.
        if (unitPart.replace(s, "").isNotBlank()) return null
        var total = 0.0
        for (p in parts) {
            val n = p.groupValues[1].toDoubleOrNull() ?: return null
            val unit = p.groupValues[2].lowercase()
            total += n * when (unit.first()) { 'h' -> 3600; 'm' -> 60; else -> 1 }
            if (total > DAY) return null
        }
        return total.toInt()
    }

    // Alarms

    private val alarmLead = Regex("^(?:(?:set|create|make)\\s+(?:an\\s+|the\\s+|a\\s+)?alarm|alarm|wake me(?:\\s+up)?)(?:\\s+(?:for|at))?\\s+(.+)$", RegexOption.IGNORE_CASE)

    private fun alarm(text: String, now: LocalDateTime): Action.Alarm? {
        val spec = alarmLead.find(text)?.groupValues?.get(1) ?: return null
        val w = When.parse(spec, now, preferFuture = true) ?: return null
        if (w.remainder.isNotBlank() && w.remainder.split(' ').size > 4) return null
        val at = w.at ?: return null
        return Action.Alarm(at.toLocalTime(), at.toLocalDate(), w.remainder.ifBlank { null })
    }

    // Reminders

    private val reminderLead = Regex("^(?:remind me|reminder|set a reminder|add a reminder)(?:\\s+to)?\\s+(.+)$", RegexOption.IGNORE_CASE)

    private fun reminder(text: String, now: LocalDateTime): Action.Reminder? {
        val rest = reminderLead.find(text)?.groupValues?.get(1) ?: return null
        val w = When.parse(rest, now, preferFuture = true)
        val task = (w?.remainder ?: rest).removePrefix("to ").trim()
        if (task.isEmpty()) return null
        return Action.Reminder(task, w?.at)
    }

    // Messages

    private val messageLead = Regex(
        "^(?:text|message|msg|sms|send (?:a )?(?:text|message|whatsapp)(?: to)?|whatsapp|telegram|signal)\\s+(.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val connector = Regex("\\s*(?::|,)\\s*|\\s+(?:saying|that|to say|to)\\s+", RegexOption.IGNORE_CASE)
    private val viaApp = Regex("\\s+(?:on|via|over|through)\\s+(whatsapp|telegram|signal|sms|text)\\b", RegexOption.IGNORE_CASE)

    private fun message(text: String): Action.Message? {
        val rest = messageLead.find(text)?.groupValues?.get(1) ?: return null
        val lead = text.substringBefore(' ').lowercase()
        var app = when {
            lead == "whatsapp" || text.contains("send a whatsapp", true) -> MessagingApp.WhatsApp
            lead == "telegram" -> MessagingApp.Telegram
            lead == "signal" -> MessagingApp.Signal
            else -> MessagingApp.Sms
        }
        var body = rest
        viaApp.find(body)?.let { m ->
            app = appNamed(m.groupValues[1])
            body = body.removeRange(m.range)
        }
        val split = connector.find(body)
        val (name, message) = if (split != null) {
            body.substring(0, split.range.first).trim() to body.substring(split.range.last + 1).trim()
        } else {
            body.substringBefore(' ') to body.substringAfter(' ', "").trim()
        }
        if (name.isEmpty() || name.split(' ').size > 3) return null
        return Action.Message(name, message.ifEmpty { null }, app)
    }

    private fun appNamed(s: String) = when (s.lowercase()) {
        "whatsapp" -> MessagingApp.WhatsApp
        "telegram" -> MessagingApp.Telegram
        "signal" -> MessagingApp.Signal
        else -> MessagingApp.Sms
    }

    // Calls

    private val callLead = Regex("^(video call|facetime|call|dial|phone|ring)\\s+(.+)$", RegexOption.IGNORE_CASE)

    private fun call(text: String): Action.Call? {
        val m = callLead.find(text) ?: return null
        val target = m.groupValues[2].trim().removeSuffix(" now")
        if (target.split(' ').size > 4) return null
        return Action.Call(target, video = m.groupValues[1].lowercase().let { it == "video call" || it == "facetime" })
    }

    // Navigation

    private val navLead = Regex(
        "^(?:directions|navigate|navigation|drive|take me|get me|go|how do i get)(?:\\s+(?:to|me to))?\\s+(.+)$",
        RegexOption.IGNORE_CASE,
    )

    private fun navigate(text: String): Action.Navigate? {
        val dest = navLead.find(text)?.groupValues?.get(1)?.trim()?.removeSuffix("?") ?: return null
        if (text.lowercase().startsWith("go ") && !text.lowercase().startsWith("go to ") && !text.lowercase().startsWith("go home")) return null
        val named = when (dest.lowercase()) { "home" -> "Home"; "work" -> "Work"; else -> dest }
        return Action.Navigate(named)
    }

    // Calendar events

    private val eventWords = Regex(
        "^(?:(?:schedule|add|create|book|put)(?:\\s+(?:an?|the))?\\s+(?:event|meeting|appointment)?\\s*|meeting|lunch|dinner|breakfast|brunch|coffee|drinks|appointment|party|interview|class|date|call with|event|dentist|doctor|haircut|gym)\\b",
        RegexOption.IGNORE_CASE,
    )

    private fun event(text: String, now: LocalDateTime): Action.Event? {
        if (!eventWords.containsMatchIn(text)) return null
        val w = When.parse(text, now, preferFuture = true) ?: return null
        val start = w.at ?: return null
        val title = w.remainder
            .replace(Regex("^(?:schedule|add|create|book|put)(?:\\s+(?:an?|the))?(?:\\s+event)?\\s*", RegexOption.IGNORE_CASE), "")
            .trim().ifEmpty { "Event" }
        return Action.Event(title.replaceFirstChar { it.uppercase() }, start)
    }
}

/**
 * A point in time named in a query ("tomorrow at 5pm", "tonight at 9", "on Friday", "in 2 hours", "at 6"), and
 * the query with those words taken out.
 */
internal data class When(val at: LocalDateTime?, val remainder: String) {
    companion object {
        private val inDuration = Regex("\\bin\\s+(\\d+|an?|one|two|three|half an?)\\s+(minutes?|mins?|hours?|hrs?|days?|weeks?)\\b", RegexOption.IGNORE_CASE)
        private val clock = Regex(
            "\\b(?:at\\s+|@\\s*|for\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)?(?=\\s|$|,|\\.)|\\b(?:at\\s+)?(noon|midday|midnight)\\b",
            RegexOption.IGNORE_CASE,
        )
        private val dayWord = Regex(
            "\\b(today|tonight|tomorrow|tmrw|tmr)(?:\\s+(morning|afternoon|evening|night))?\\b|\\b(?:on\\s+|next\\s+|this\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b",
            RegexOption.IGNORE_CASE,
        )
        private val partOfDay = Regex("\\b(?:in the\\s+|this\\s+)?(morning|afternoon|evening)\\b", RegexOption.IGNORE_CASE)

        fun parse(text: String, now: LocalDateTime, preferFuture: Boolean): When? {
            var rest = text
            fun cut(r: IntRange) { rest = (rest.substring(0, r.first) + " " + rest.substring(r.last + 1)) }

            inDuration.find(rest)?.let { m ->
                val n = when (val q = m.groupValues[1].lowercase()) {
                    "a", "an", "one" -> 1.0; "two" -> 2.0; "three" -> 3.0; "half a", "half an" -> 0.5; else -> q.toDouble()
                }
                val unit = m.groupValues[2].lowercase()
                val minutes = n * when { unit.startsWith("m") -> 1.0; unit.startsWith("h") -> 60.0; unit.startsWith("d") -> 1440.0; else -> 10080.0 }
                cut(m.range)
                return When(now.plusSeconds((minutes * 60).toLong()).truncatedTo(ChronoUnit.MINUTES), tidy(rest))
            }

            var date: LocalDate? = null
            var evening = false
            var period: String? = null
            dayWord.find(rest)?.let { m ->
                val today = now.toLocalDate()
                date = when (m.groupValues[1].lowercase()) {
                    "today" -> today
                    "tonight" -> { evening = true; today }
                    "tomorrow", "tmrw", "tmr" -> today.plusDays(1)
                    else -> {
                        val dow = DayOfWeek.valueOf(m.groupValues[3].uppercase())
                        val next = m.value.lowercase().startsWith("next")
                        today.with(if (next) TemporalAdjusters.next(dow) else TemporalAdjusters.nextOrSame(dow))
                    }
                }
                period = m.groupValues[2].ifEmpty { null }
                cut(m.range)
            }
            if (period == null) partOfDay.find(rest)?.let { period = it.groupValues[1]; cut(it.range) }
            if (period != null && period!!.lowercase() in setOf("evening", "night")) evening = true

            var time: LocalTime? = null
            var explicitHalf = false
            for (m in clock.findAll(rest)) {
                val word = m.groupValues[4].lowercase()
                if (word.isNotEmpty()) {
                    time = if (word == "midnight") LocalTime.MIDNIGHT else LocalTime.NOON
                    explicitHalf = true
                } else {
                    val h = m.groupValues[1].toInt()
                    val min = m.groupValues[2].ifEmpty { "0" }.toInt()
                    val ampm = m.groupValues[3].lowercase().replace(".", "")
                    // A bare number with no "at", colon or am/pm ("lunch for 2") isn't a time.
                    val bare = !m.value.trim().lowercase().startsWith("at") && !m.value.startsWith("@") && m.groupValues[2].isEmpty() && ampm.isEmpty()
                    if (bare || h > 23 || min > 59) continue
                    time = when {
                        ampm == "am" -> { explicitHalf = true; LocalTime.of(if (h == 12) 0 else h, min) }
                        ampm == "pm" -> { explicitHalf = true; LocalTime.of(if (h == 12) 12 else (h + 12) % 24, min) }
                        else -> LocalTime.of(h, min)
                    }
                }
                cut(m.range)
                break
            }
            if (time == null && period != null) {
                time = when (period!!.lowercase()) { "morning" -> LocalTime.of(9, 0); "afternoon" -> LocalTime.of(15, 0); else -> LocalTime.of(19, 0) }
                explicitHalf = true
            }
            if (date == null && time == null) return null

            val t0 = time ?: LocalTime.of(9, 0)
            val d0 = date
            val resolved: LocalDateTime = when {
                explicitHalf || t0.hour == 0 || t0.hour >= 12 ->
                    if (d0 != null) d0.atTime(t0) else now.toLocalDate().atTime(t0).let { if (preferFuture && !it.isAfter(now)) it.plusDays(1) else it }
                evening -> (d0 ?: now.toLocalDate()).atTime(t0.plusHours(12))
                d0 != null && d0.isAfter(now.toLocalDate()) ->
                    // A bare hour on a future day: 1 to 6 means afternoon or evening, 7 to 11 morning.
                    d0.atTime(if (t0.hour in 1..6) t0.plusHours(12) else t0)
                else -> {
                    // Today, ambiguous hour: the next time it comes round (3 at 13:00 is 15:00).
                    val base = (d0 ?: now.toLocalDate()).atTime(t0)
                    when {
                        base.isAfter(now) -> base
                        base.plusHours(12).isAfter(now) -> base.plusHours(12)
                        else -> base.plusDays(1)
                    }
                }
            }
            return When(resolved, tidy(rest))
        }

        private fun tidy(s: String) = s.replace(Regex("\\s+"), " ").trim().removeSuffix(" at").removeSuffix(" on").trim(' ', ',', '.')
    }
}
