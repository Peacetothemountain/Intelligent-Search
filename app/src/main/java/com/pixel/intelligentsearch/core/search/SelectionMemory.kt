package com.pixel.intelligentsearch.core.search

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * What the user opens from search, so ranking learns from it. For each result ([ResultKeys] key) it keeps how often
 * and how lately it was opened, which is the only usage signal contacts and files have (Android stopped sharing how
 * often contacts are reached in Android 10), and what was typed when it was picked, so the app picked after typing "c"
 * comes first the next time "c" is typed. Every weight halves each [HALF_LIFE_MS], so old habits fade.
 *
 * Kept on this phone only, in its own preferences file: the app's backups read only the settings file, and Android's
 * cloud backup and device transfer take nothing. Credential-encrypted storage can't be read before the first unlock,
 * so until then nothing is read or written.
 */
@Singleton
class SelectionMemory internal constructor(
    private val store: () -> SharedPreferences?,
    private val clock: () -> Long
) {

    @Inject constructor(@ApplicationContext context: Context) : this(
        store = {
            val unlocked = context.getSystemService(UserManager::class.java)?.isUserUnlocked != false
            if (unlocked) context.getSharedPreferences(FILE, Context.MODE_PRIVATE) else null
        },
        clock = System::currentTimeMillis
    )

    /** What was typed when a result was picked: [weight] picks, decayed to [atMs]. */
    data class Pick(val query: String, val weight: Float, val atMs: Long)

    /** One result's [opens], decayed to [lastMs] (its last open), and the queries it was picked for. */
    data class Entry(val opens: Float, val lastMs: Long, val picks: List<Pick>)

    /** One result's decayed open count and last open. */
    data class Usage(val opens: Float, val lastMs: Long)

    private val lock = Any()
    @Volatile private var loaded: Map<String, Entry>? = null

    /** Everything remembered, read once; empty (and read again later) while storage is still locked. */
    private fun entries(): Map<String, Entry> {
        loaded?.let { return it }
        synchronized(lock) {
            loaded?.let { return it }
            val prefs = store() ?: return emptyMap()
            return decode(prefs.getString(KEY_ENTRIES, null)).also { loaded = it }
        }
    }

    /**
     * Notes that the result [key] was opened, and picked for [query] when one was typed (null or blank when it wasn't,
     * or when searches aren't being remembered).
     */
    fun record(key: String, query: String?) {
        if (ResultKeys.kindOf(key) == null) return
        val prefs = store() ?: return
        val now = clock()
        val normalized = query?.let(::normalizeQuery).orEmpty()
        synchronized(lock) {
            val current = entries()
            val old = current[key]
            val picks = old?.picks.orEmpty().toMutableList()
            if (normalized.isNotEmpty()) {
                val index = picks.indexOfFirst { it.query == normalized }
                val previous = if (index >= 0) decayed(picks[index].weight, picks[index].atMs, now) else 0f
                val pick = Pick(normalized, previous + 1f, now)
                if (index >= 0) picks[index] = pick else picks += pick
            }
            val keptPicks = picks.sortedByDescending { decayed(it.weight, it.atMs, now) }.take(MAX_PICKS_PER_RESULT)
            val opens = (old?.let { decayed(it.opens, it.lastMs, now) } ?: 0f) + 1f
            val next = LinkedHashMap(current)
            next[key] = Entry(opens, now, keptPicks)
            save(prefs, prune(next, now))
        }
    }

    /** How often and how lately [key] was opened, or null when it never was. */
    fun usage(key: String?, now: Long = clock()): Usage? {
        val entry = key?.let { entries()[it] } ?: return null
        return Usage(decayed(entry.opens, entry.lastMs, now), entry.lastMs)
    }

    /**
     * How strongly [key] was picked for [normalizedQuery] ([normalizeQuery]'s form): picks for exactly that text count
     * fully, picks after typing more of it ("chr" while "ch" is typed) half, and picks after typing less of it ("c"
     * while "ch" is typed) three quarters. 0 when nothing relates.
     */
    fun learnedWeight(key: String?, normalizedQuery: String, now: Long = clock()): Float {
        if (normalizedQuery.isEmpty()) return 0f
        val entry = key?.let { entries()[it] } ?: return 0f
        var total = 0f
        for (pick in entry.picks) {
            val share = when {
                pick.query == normalizedQuery -> 1f
                pick.query.startsWith(normalizedQuery) -> TYPED_LESS_SHARE
                normalizedQuery.startsWith(pick.query) -> TYPED_MORE_SHARE
                else -> 0f
            }
            if (share > 0f) total += share * decayed(pick.weight, pick.atMs, now)
        }
        return total
    }

    /**
     * Results search should consider for [normalizedQuery] even when its alphabetical prefix lookups stop short of
     * them: those picked for related text, then the [frequent] most opened. Whether each matches is still the scoring's
     * call.
     */
    fun candidateKeys(normalizedQuery: String, frequent: Int = FREQUENT_CANDIDATES, now: Long = clock()): Set<String> {
        val all = entries()
        if (all.isEmpty()) return emptySet()
        val keys = LinkedHashSet<String>()
        if (normalizedQuery.isNotEmpty()) {
            for ((key, entry) in all) {
                if (entry.picks.any { it.query.startsWith(normalizedQuery) || normalizedQuery.startsWith(it.query) }) keys += key
            }
        }
        all.entries.sortedByDescending { decayed(it.value.opens, it.value.lastMs, now) }.take(frequent).mapTo(keys) { it.key }
        return keys
    }

    /**
     * The result picked most for exactly [query], when it was picked at least [BEST_MATCH_MIN_PICKS] times (decayed),
     * so one stray tap doesn't take over the Best Match. Null otherwise.
     */
    fun topPick(query: String, now: Long = clock()): String? {
        val normalized = normalizeQuery(query)
        if (normalized.isEmpty()) return null
        var best: String? = null
        var bestWeight = BEST_MATCH_MIN_PICKS
        for ((key, entry) in entries()) {
            val pick = entry.picks.firstOrNull { it.query == normalized } ?: continue
            val weight = decayed(pick.weight, pick.atMs, now)
            if (weight >= bestWeight) {
                best = key
                bestWeight = weight
            }
        }
        return best
    }

    /** Forgets what was typed for every result (the search history was cleared) and returns what was kept, for undo. */
    fun clearPicks(): Map<String, Entry> {
        val prefs = store() ?: return emptyMap()
        synchronized(lock) {
            val before = entries()
            save(prefs, before.mapValues { (_, entry) -> entry.copy(picks = emptyList()) })
            return before
        }
    }

    /** Forgets [query] as typed text for every result (one search was removed from the history). */
    fun forgetQuery(query: String) {
        val normalized = normalizeQuery(query)
        if (normalized.isEmpty()) return
        val prefs = store() ?: return
        synchronized(lock) {
            val before = entries()
            if (before.values.none { entry -> entry.picks.any { it.query == normalized } }) return
            save(prefs, before.mapValues { (_, entry) -> entry.copy(picks = entry.picks.filterNot { it.query == normalized }) })
        }
    }

    /** Puts back what [clearPicks] returned (Undo). */
    fun restore(snapshot: Map<String, Entry>) {
        if (snapshot.isEmpty()) return
        val prefs = store() ?: return
        synchronized(lock) { save(prefs, snapshot) }
    }

    private fun save(prefs: SharedPreferences, entries: Map<String, Entry>) {
        loaded = entries
        prefs.edit { putString(KEY_ENTRIES, encode(entries)) }
    }

    /** The [MAX_RESULTS] results opened most (by decayed count), so the file stays small. */
    private fun prune(entries: Map<String, Entry>, now: Long): Map<String, Entry> {
        if (entries.size <= MAX_RESULTS) return entries
        val kept = entries.entries.sortedByDescending { decayed(it.value.opens, it.value.lastMs, now) }.take(MAX_RESULTS)
        return kept.associateTo(LinkedHashMap()) { it.key to it.value }
    }

    @Serializable
    private data class StoredPick(val q: String, val w: Float, val t: Long)

    @Serializable
    private data class StoredEntry(val k: String, val o: Float, val t: Long, val p: List<StoredPick> = emptyList())

    private fun encode(entries: Map<String, Entry>): String = JSON.encodeToString(
        ListSerializer(StoredEntry.serializer()),
        entries.map { (key, entry) -> StoredEntry(key, entry.opens, entry.lastMs, entry.picks.map { StoredPick(it.query, it.weight, it.atMs) }) }
    )

    /** Reads what [encode] wrote; anything malformed reads as nothing remembered. */
    private fun decode(json: String?): Map<String, Entry> {
        if (json.isNullOrBlank()) return emptyMap()
        val stored = try {
            JSON.decodeFromString(ListSerializer(StoredEntry.serializer()), json)
        } catch (_: Exception) {
            return emptyMap()
        }
        val out = LinkedHashMap<String, Entry>()
        for (entry in stored.asSequence().take(MAX_RESULTS)) {
            if (ResultKeys.kindOf(entry.k) == null || !entry.o.isFinite() || entry.o < 0f) continue
            val picks = entry.p.asSequence()
                .filter { it.q.isNotEmpty() && it.q.length <= MAX_QUERY_LENGTH && it.w.isFinite() && it.w > 0f }
                .take(MAX_PICKS_PER_RESULT)
                .map { Pick(it.q, it.w, it.t) }
                .toList()
            out[entry.k] = Entry(entry.o, entry.t, picks)
        }
        return out
    }

    companion object {
        private const val FILE = "search_selection_memory"
        private const val KEY_ENTRIES = "entries"

        /** Every remembered weight halves in this time. */
        const val HALF_LIFE_MS = 30L * 24 * 60 * 60 * 1000

        const val MAX_RESULTS = 400
        const val MAX_PICKS_PER_RESULT = 8
        const val MAX_QUERY_LENGTH = 64
        const val FREQUENT_CANDIDATES = 50

        /** How many picks (decayed) of one result for one exact query make it the Best Match. */
        const val BEST_MATCH_MIN_PICKS = 1.5f

        private const val TYPED_LESS_SHARE = 0.5f
        private const val TYPED_MORE_SHARE = 0.75f

        private val JSON = Json { ignoreUnknownKeys = true }

        /** The form queries are remembered and compared in: trimmed, lowercased, accents and punctuation folded. */
        fun normalizeQuery(query: String): String =
            QueryNormalizer.normalize(query.trim().lowercase(java.util.Locale.ROOT)).take(MAX_QUERY_LENGTH)

        /** [weight] as it stands at [now], having halved every [HALF_LIFE_MS] since [atMs]. */
        internal fun decayed(weight: Float, atMs: Long, now: Long): Float {
            if (atMs <= 0L || now <= atMs) return weight
            return (weight * 2.0.pow(-(now - atMs).toDouble() / HALF_LIFE_MS)).toFloat()
        }
    }
}
