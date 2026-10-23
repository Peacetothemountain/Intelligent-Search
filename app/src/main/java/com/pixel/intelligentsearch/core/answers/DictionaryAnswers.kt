package com.pixel.intelligentsearch.core.answers

import androidx.compose.runtime.Immutable
import com.pixel.intelligentsearch.core.net.HttpsGet
import com.pixel.intelligentsearch.core.net.fetch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.util.Locale

/** One meaning of a word: its part of speech ("noun"), the definition, and an example sentence if there is one. */
@Immutable
data class DictionarySense(val partOfSpeech: String, val definition: String, val example: String?)

/** A word's dictionary entry: how it's written, how it's said, and its senses, the most common first. */
@Immutable
data class DictionaryEntry(
    val word: String,
    val phonetic: String?,
    val senses: List<DictionarySense>,
    /** Where the definitions come from, for the attribution their licence asks for. */
    val sourceUrl: String?
)

/**
 * Reads the Free Dictionary API's answer (https://dictionaryapi.dev, built from Wiktionary): a list of entries, each
 * with meanings by part of speech. Senses are taken a part of speech at a time, its first definition before any
 * part's second, so the top few cover the word's main uses ("hello": interjection, noun, verb) rather than five
 * shades of one.
 */
object DictionaryResponseParser {
    /** The most senses kept: enough for the expanded card. */
    const val MAX_SENSES = 8
    const val MAX_BYTES = 256 * 1024

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): DictionaryEntry? = try {
        val entries = json.parseToJsonElement(body) as? JsonArray
        entries?.takeIf { it.isNotEmpty() }?.let(::entryFrom)
    } catch (_: Exception) {
        null
    }

    private fun entryFrom(entries: JsonArray): DictionaryEntry? {
        val objects = entries.mapNotNull { it as? JsonObject }
        val first = objects.firstOrNull() ?: return null
        val word = first.string("word")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val phonetic = objects.firstNotNullOfOrNull { entry ->
            entry.string("phonetic")?.takeIf { it.isNotBlank() }
                ?: (entry["phonetics"] as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonObject)?.string("text")?.takeIf(String::isNotBlank) }
        }?.trim()

        // Each part of speech's definitions, in the order the entries give them.
        val byPart = LinkedHashMap<String, MutableList<DictionarySense>>()
        for (entry in objects) {
            val meanings = entry["meanings"] as? JsonArray ?: continue
            for (meaning in meanings.mapNotNull { it as? JsonObject }) {
                val part = meaning.string("partOfSpeech")?.trim().orEmpty()
                val definitions = meaning["definitions"] as? JsonArray ?: continue
                for (definition in definitions.mapNotNull { it as? JsonObject }) {
                    val text = definition.string("definition")?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                    val example = definition.string("example")?.trim()?.takeIf { it.isNotEmpty() }
                    byPart.getOrPut(part) { mutableListOf() } += DictionarySense(part, text, example)
                }
            }
        }
        if (byPart.isEmpty()) return null

        // Round-robin across parts of speech.
        val senses = mutableListOf<DictionarySense>()
        var round = 0
        while (senses.size < MAX_SENSES) {
            var added = false
            for (list in byPart.values) {
                if (round < list.size && senses.size < MAX_SENSES) {
                    senses += list[round]
                    added = true
                }
            }
            if (!added) break
            round++
        }
        val source = (first["sourceUrls"] as? JsonArray)?.firstNotNullOfOrNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
        return DictionaryEntry(word, phonetic, senses, source)
    }

    private fun JsonObject.string(name: String): String? =
        runCatching { this[name]?.jsonPrimitive?.contentOrNull }.getOrNull()

    /** The lookup address for [word]; English only, which is what the service's Wiktionary data covers well. */
    fun urlFor(word: String): String =
        "https://api.dictionaryapi.dev/api/v2/entries/en/" + URLEncoder.encode(word.lowercase(Locale.ROOT), "UTF-8").replace("+", "%20")
}

/**
 * Definitions for the overlay's dictionary answers, cached for a month in memory and on disk (a word's meaning
 * doesn't change). [http] only ever sees the word being defined. Callers check the privacy settings first; this
 * class only fetches.
 */
class DictionaryRepository(
    private val http: HttpsGet,
    private val cache: AnswerCache<DictionaryEntry>,
    private val misses: AnswerCache<Boolean>
) {
    /** A cached entry for [word], from memory only, so it is safe to call on the main thread. */
    fun peek(word: String): DictionaryEntry? = cache.peek(key(word))

    /** Whether [word] was looked up a moment ago and nothing came back. */
    fun recentlyMissed(word: String): Boolean = misses.peek(key(word)) == true

    /** The cached entry for [word], reading the disk cache if needed. Blocking: call off the main thread. */
    fun cached(word: String): DictionaryEntry? = cache.get(key(word), DictionaryResponseParser::parse)

    /**
     * Fetches [word]'s entry; null when it isn't found or can't be fetched. Cancelling the caller (the next keystroke)
     * ends the request and records nothing.
     */
    suspend fun fetch(word: String): DictionaryEntry? {
        val bytes = http.fetch(DictionaryResponseParser.urlFor(word), DictionaryResponseParser.MAX_BYTES)
        val body = bytes?.toString(Charsets.UTF_8)
        val entry = body?.let(DictionaryResponseParser::parse)
        if (entry == null) {
            misses.putInMemory(key(word), true)
            return null
        }
        cache.put(key(word), entry, body)
        return entry
    }

    /** Deletes the definitions kept on disk past their lifetime. Touches the disk. */
    fun pruneExpired() = cache.pruneExpired()

    /** Forgets every definition kept, for when the user turns Web Suggestions or Web off. */
    fun clear() {
        cache.clear()
        misses.clear()
    }

    private fun key(word: String) = "en:" + word.trim().lowercase(Locale.ROOT)
}
