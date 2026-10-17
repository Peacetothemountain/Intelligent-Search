package com.ngdesigns.intelligentsearch.engine

import com.ngdesigns.intelligentsearch.engine.text.EditDistance
import com.ngdesigns.intelligentsearch.engine.text.Soundalike
import kotlin.math.ln
import kotlin.math.pow

/** What a search result is. */
enum class Kind { App, Shortcut, Contact, Event, File, Setting, History, Web }

/** How often and how recently the user opened something; drives ranking among equal matches. */
data class Usage(val count: Int = 0, val lastUsedMillis: Long = 0L) {
    companion object { val None = Usage() }
}

/**
 * Anything search can find. [keywords] are extra words it answers to (an app's package name, a setting's synonyms).
 * [soundsLike] turns on sound-alike matching, meant for people's names.
 */
data class Candidate(
    val id: String,
    val kind: Kind,
    val title: String,
    val subtitle: String? = null,
    val keywords: List<String> = emptyList(),
    val usage: Usage = Usage.None,
    val soundsLike: Boolean = false,
)

/** How a query matched a candidate, strongest first. */
enum class MatchKind { Exact, Prefix, WordPrefix, Acronym, Keyword, Phonetic, Substring, Typo }

data class Match(val kind: MatchKind, val quality: Float)

data class Ranked(val candidate: Candidate, val match: Match, val score: Float)

/** Ranking knobs; [now] is a parameter so ranking is testable. */
data class RankingOptions(
    val typos: Boolean = true,
    val now: Long = System.currentTimeMillis(),
    val recencyHalfLifeMillis: Long = 7L * 24 * 60 * 60 * 1000,
)

/**
 * A searchable set of candidates, prepared once (normalized words, acronym, sound-alike keys) so each keystroke
 * only compares. Thousands of entries search in well under a few milliseconds.
 */
class Catalog(candidates: List<Candidate>) {

    private class Entry(val candidate: Candidate) {
        val words: List<String> = nameWords(candidate.title)
        val flat: String = normalize(candidate.title)
        val joined: String = words.joinToString("")
        val acronym: String = words.joinToString("") { it.take(1) }
        val keywordWords: List<String> = candidate.keywords.flatMap { words(normalize(it)) }
        val rawKeywords: List<String> = candidate.keywords.map { it.lowercase() }
        val sounds: List<String> = if (candidate.soundsLike) words.map(Soundalike::key) else emptyList()
    }

    private val entries = candidates.map(::Entry)

    val size: Int get() = entries.size

    fun search(query: String, limit: Int = Int.MAX_VALUE, options: RankingOptions = RankingOptions()): List<Ranked> {
        val q = normalize(query)
        if (q.isEmpty() || limit <= 0) return emptyList()
        val raw = query.trim().lowercase()
        val qWords = words(q)
        val result = ArrayList<Ranked>()
        for (e in entries) {
            val m = match(e, q, qWords, raw, options.typos) ?: continue
            result += Ranked(e.candidate, m, score(e.candidate.usage, m, options))
        }
        result.sortWith(compareByDescending<Ranked> { it.score }.thenBy { it.candidate.title.length }.thenBy { it.candidate.title })
        return if (result.size > limit) result.subList(0, limit) else result
    }

    private fun match(e: Entry, q: String, qWords: List<String>, raw: String, typos: Boolean): Match? {
        val qJoined = qWords.joinToString("")
        if (e.flat == q || e.joined == qJoined) return Match(MatchKind.Exact, 1f)
        if (e.flat.startsWith(q) || e.joined.startsWith(qJoined)) {
            return Match(MatchKind.Prefix, 0.9f + 0.05f * q.length / e.flat.length.coerceAtLeast(1))
        }
        wordPrefix(e.words, qWords)?.let { firstWord -> return Match(MatchKind.WordPrefix, 0.84f - 0.02f * firstWord.coerceAtMost(5)) }
        if (qWords.size == 1 && q.length >= 2 && e.words.size >= 2 && e.acronym.startsWith(q)) {
            return Match(MatchKind.Acronym, if (e.acronym == q) 0.8f else 0.76f)
        }
        // Package names and other keywords: dotted identifiers only when the query itself is dotted, so "com"
        // doesn't match every app whose package starts with "com.".
        if (raw.contains('.') && e.rawKeywords.any { it.startsWith(raw) }) return Match(MatchKind.Keyword, 0.7f)
        if (wordPrefix(e.keywordWords.filterNot { it == "com" || it == "android" || it == "google" || it == "apps" }, qWords) != null) {
            return Match(MatchKind.Keyword, 0.66f)
        }
        if (e.sounds.isNotEmpty() && q.length >= 3) {
            val qSounds = qWords.map(Soundalike::key)
            if (qSounds.all { s -> s.isNotEmpty() && e.sounds.any { it.startsWith(s) } }) return Match(MatchKind.Phonetic, 0.62f)
        }
        if (q.length >= 3 && e.joined.contains(qJoined)) return Match(MatchKind.Substring, 0.56f)
        if (typos) typo(e, qWords)?.let { return Match(MatchKind.Typo, 0.6f - 0.07f * it) }
        return null
    }

    /** The index of the name word where the query's words start matching in order, each a word prefix. */
    private fun wordPrefix(nameWords: List<String>, qWords: List<String>): Int? {
        if (qWords.isEmpty() || nameWords.isEmpty()) return null
        for (start in nameWords.indices) {
            var w = start
            var ok = true
            for (qw in qWords) {
                while (w < nameWords.size && !nameWords[w].startsWith(qw)) w++
                if (w == nameWords.size) { ok = false; break }
                w++
            }
            if (ok && nameWords[start].startsWith(qWords[0])) return start
        }
        return null
    }

    /**
     * Total edits when each query word is a slightly mistyped start of a name word, in order ("gogle ma" for
     * "Google Maps", "yuotu" for "YouTube"), or null. Words under four letters must match exactly (a typo there
     * matches too much: "com" would find Camera), four or five letters allow one edit, longer words two; the whole name
     * run together is tried too, for names typed as one word.
     */
    private fun typo(e: Entry, qWords: List<String>): Int? {
        if (qWords.sumOf { it.length } < 3) return null
        fun allowed(word: String) = when { word.length < 4 -> 0; word.length < 6 -> 1; else -> 2 }
        var total = 0
        var w = 0
        for (qw in qWords) {
            val limit = allowed(qw)
            var found = false
            while (w < e.words.size) {
                val d = EditDistance.toPrefix(qw, e.words[w], limit)
                w++
                if (d <= limit && (d == 0 || qw.first() == e.words[w - 1].first() || qw.length >= 5)) { total += d; found = true; break }
            }
            if (!found) {
                if (qWords.size > 1) return null
                val joined = qWords[0]
                val d = EditDistance.toPrefix(joined, e.joined, allowed(joined))
                return if (d <= allowed(joined) && (joined.first() == e.joined.first() || joined.length >= 5)) d else null
            }
        }
        return total.takeIf { it > 0 }
    }

    private fun score(usage: Usage, m: Match, o: RankingOptions): Float {
        val frequency = if (usage.count > 0) (60.0 * ln(1.0 + usage.count)).coerceAtMost(250.0) else 0.0
        val recency = if (usage.lastUsedMillis > 0) {
            val age = (o.now - usage.lastUsedMillis).coerceAtLeast(0L).toDouble()
            350.0 * 0.5.pow(age / o.recencyHalfLifeMillis)
        } else 0.0
        return (m.quality * 1000.0 + frequency + recency).toFloat()
    }
}
