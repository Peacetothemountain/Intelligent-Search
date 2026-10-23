package com.pixel.intelligentsearch.core.data.widget

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import com.pixel.intelligentsearch.core.data.settings.ParsedSetting
import android.content.SharedPreferences
import android.util.Log
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** What a countdown widget counts down to: a [title] ("Halloween") on a [date], every year when [yearly]. */
data class CountdownEvent(val title: String, val date: LocalDate, val yearly: Boolean) {

    /**
     * The day being counted down to as of [today]: the date itself, or for a yearly event the next time it comes round
     * (today included). A yearly February 29 falls on February 28 in other years.
     */
    fun target(today: LocalDate): LocalDate {
        if (!yearly) return date
        val thisYear = date.withYear(today.year)
        return if (thisYear.isBefore(today)) date.withYear(today.year + 1) else thisYear
    }

    /** Whole days from [today] to [target]: 0 on the day, negative once a one-off date has passed. */
    fun daysUntil(today: LocalDate): Long = ChronoUnit.DAYS.between(today, target(today))

    companion object {
        /** Titles longer than this are cut, so a widget's caption stays readable. */
        const val MAX_TITLE_LENGTH = 40
    }
}

/**
 * The countdown widgets' events, one per placed widget by its id, stored as one JSON setting
 * ([SettingsManager.COUNTDOWN_EVENTS]); widget ids are this phone's own, so backups leave it out. Days are counted on
 * the phone's calendar ([CountdownEvent.daysUntil]), which follows its time zone, so a countdown changes at local
 * midnight and daylight saving changes never skip or repeat a day.
 */
class CountdownEventStore(prefs: SharedPreferences) {

    /** One event as stored, its date as ISO text ("2026-10-31"). */
    @Serializable
    private data class StoredEvent(val title: String, val date: String, val yearly: Boolean = false)

    private val events = ParsedSetting({ prefs }, SettingsManager.COUNTDOWN_EVENTS, ::decode, ::encode)

    fun get(appWidgetId: Int): CountdownEvent? = events.current[appWidgetId]

    fun put(appWidgetId: Int, event: CountdownEvent) {
        events.update { it + (appWidgetId to event.copy(title = event.title.trim().take(CountdownEvent.MAX_TITLE_LENGTH))) }
    }

    /** Forgets the events of widgets that were removed. */
    fun remove(appWidgetIds: IntArray) {
        events.update { it - appWidgetIds.toSet() }
    }

    companion object {
        private const val TAG = "CountdownEventStore"
        private val JSON = Json { ignoreUnknownKeys = true }

        /** The stored events; unreadable text reads as none, and an unreadable event is left out. */
        private fun decode(text: String): Map<Int, CountdownEvent> {
            val stored = try {
                JSON.parseToJsonElement(text) as? JsonObject
            } catch (e: SerializationException) {
                Log.w(TAG, "Unreadable countdown events", e)
                null
            } ?: return emptyMap()
            return stored.mapNotNull { (key, element) ->
                val id = key.toIntOrNull() ?: return@mapNotNull null
                val item = runCatching { JSON.decodeFromJsonElement(StoredEvent.serializer(), element) }.getOrNull() ?: return@mapNotNull null
                val title = item.title.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val date = try { LocalDate.parse(item.date) } catch (_: DateTimeParseException) { return@mapNotNull null }
                id to CountdownEvent(title.take(CountdownEvent.MAX_TITLE_LENGTH), date, item.yearly)
            }.toMap()
        }

        private fun encode(events: Map<Int, CountdownEvent>): String = JSON.encodeToString(
            MapSerializer(String.serializer(), StoredEvent.serializer()),
            events.entries.associate { (id, event) -> id.toString() to StoredEvent(event.title, event.date.toString(), event.yearly) }
        )

        /** The first moment of the day after [now]'s, where [now] is: when every countdown next changes. */
        fun nextMidnight(now: ZonedDateTime): ZonedDateTime = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
    }
}
