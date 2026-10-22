package com.pixel.intelligentsearch.core.data.widget

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import org.json.JSONException
import org.json.JSONObject
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
 * The countdown widgets' events, one per placed widget, stored as one JSON setting ([SettingsManager.COUNTDOWN_EVENTS])
 * so they're included in backups. Days are counted on the phone's calendar ([today]), which follows its time zone, so
 * a countdown changes at local midnight and daylight saving changes never skip or repeat a day.
 */
class CountdownEvents(private val prefs: SharedPreferences) {

    fun get(appWidgetId: Int): CountdownEvent? = read()[appWidgetId]

    fun put(appWidgetId: Int, event: CountdownEvent) {
        write(read() + (appWidgetId to event.copy(title = event.title.trim().take(CountdownEvent.MAX_TITLE_LENGTH))))
    }

    /** Forgets the events of widgets that were removed. */
    fun remove(appWidgetIds: IntArray) {
        val events = read()
        if (appWidgetIds.none { it in events }) return
        write(events - appWidgetIds.toSet())
    }

    private fun read(): Map<Int, CountdownEvent> = try {
        val json = JSONObject(prefs[SettingsManager.COUNTDOWN_EVENTS])
        json.keys().asSequence().mapNotNull { key ->
            val id = key.toIntOrNull() ?: return@mapNotNull null
            val item = json.optJSONObject(key) ?: return@mapNotNull null
            val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val date = try { LocalDate.parse(item.optString("date")) } catch (_: DateTimeParseException) { return@mapNotNull null }
            id to CountdownEvent(title.take(CountdownEvent.MAX_TITLE_LENGTH), date, item.optBoolean("yearly"))
        }.toMap()
    } catch (e: JSONException) {
        Log.w(TAG, "Unreadable countdown events", e)
        emptyMap()
    }

    private fun write(events: Map<Int, CountdownEvent>) {
        val json = JSONObject()
        for ((id, event) in events) {
            json.put(id.toString(), JSONObject().put("title", event.title).put("date", event.date.toString()).put("yearly", event.yearly))
        }
        prefs.edit { put(SettingsManager.COUNTDOWN_EVENTS, json.toString()) }
    }

    companion object {
        private const val TAG = "CountdownEvents"

        /** The first moment of the day after [now]'s, where [now] is: when every countdown next changes. */
        fun nextMidnight(now: ZonedDateTime): ZonedDateTime = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
    }
}
