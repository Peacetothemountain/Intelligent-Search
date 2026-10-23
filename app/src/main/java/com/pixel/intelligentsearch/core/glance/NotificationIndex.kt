package com.pixel.intelligentsearch.core.glance

import android.app.PendingIntent
import com.pixel.intelligentsearch.core.search.QueryNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the notification index keeps of one active notification: the words it shows and what tapping it does.
 * [chronometer] is set for a clock app's running timer or stopwatch.
 */
data class IndexedNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val category: String? = null,
    val contentIntent: PendingIntent? = null,
    val chronometer: Chronometer? = null
) {
    /** A running chronometer: a timer counting down to [baseTime], or a stopwatch counting up from it (wall clock). */
    data class Chronometer(val countDown: Boolean, val baseTime: Long)

    /** The text search looks through, normalized once when the entry is made, as every other result is. */
    internal val searchable: String = QueryNormalizer.normalize("$title\n$text\n$appLabel")
    internal val foldedTitle: String = QueryNormalizer.normalize(title)
}

/**
 * The phone's active notifications, held in memory only so typed searches can find them: never written to disk,
 * preferences, the database or backups, and gone with the process. Filled by [NotificationAccessListener] while the
 * user has Search Your Notifications on and notification access granted, and cleared the moment either goes.
 */
@Singleton
class NotificationIndex @Inject constructor() {

    private val _entries = MutableStateFlow<List<IndexedNotification>>(emptyList())

    /** The indexed notifications, newest first. */
    val entries: StateFlow<List<IndexedNotification>> = _entries.asStateFlow()

    /** Adds [entry] or replaces the one with the same key (an updated notification). */
    fun post(entry: IndexedNotification) {
        _entries.update { current ->
            (listOf(entry) + current.filterNot { it.key == entry.key })
                .sortedByDescending { it.postedAt }
                .take(MAX_ENTRIES)
        }
    }

    fun remove(key: String) {
        _entries.update { current -> current.filterNot { it.key == key } }
    }

    /** Replaces everything with [entries], as when the listener connects and reads what is already showing. */
    fun replaceAll(entries: List<IndexedNotification>) {
        _entries.value = entries.distinctBy { it.key }.sortedByDescending { it.postedAt }.take(MAX_ENTRIES)
    }

    /** Forgets every notification: access revoked, the switch turned off or the listener disconnected. */
    fun clear() {
        _entries.value = emptyList()
    }

    /** The notifications matching [query], best first; see [search]. */
    fun search(query: String, limit: Int = DEFAULT_LIMIT): List<IndexedNotification> = search(_entries.value, query, limit)

    companion object {
        /** More than a phone shows at once; a flood from one app can't grow the index without bound. */
        const val MAX_ENTRIES = 200
        const val DEFAULT_LIMIT = 4
        /** Queries shorter than this match nearly every notification, so they show none. */
        const val MIN_QUERY_LENGTH = 2

        /**
         * The [entries] whose title, text or app name hold every word of [query]. A title starting with the query
         * ranks first, then a title word starting with it, then any match; newer before older within each.
         */
        fun search(entries: List<IndexedNotification>, query: String, limit: Int = DEFAULT_LIMIT): List<IndexedNotification> {
            // Folded like every other result (QueryNormalizer): "cafe" finds "Café", but a voicing mark or a
            // Devanagari vowel sign still tells letters apart.
            val folded = QueryNormalizer.normalize(query)
            if (folded.length < MIN_QUERY_LENGTH) return emptyList()
            val words = folded.split(' ')
            return entries
                .filter { entry -> words.all { it in entry.searchable } }
                .sortedWith(
                    compareByDescending<IndexedNotification> { rank(it, folded, words.first()) }
                        .thenByDescending { it.postedAt }
                )
                .take(limit)
        }

        private fun rank(entry: IndexedNotification, query: String, firstWord: String): Int = when {
            entry.foldedTitle.startsWith(query) -> 2
            entry.foldedTitle.split(' ').any { it.startsWith(firstWord) } -> 1
            else -> 0
        }
    }
}
