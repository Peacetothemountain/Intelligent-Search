package com.ngdesigns.intelligentsearch.engine

import com.ngdesigns.intelligentsearch.engine.actions.Action
import com.ngdesigns.intelligentsearch.engine.actions.Actions
import com.ngdesigns.intelligentsearch.engine.calc.Answer
import com.ngdesigns.intelligentsearch.engine.calc.Calculator
import com.ngdesigns.intelligentsearch.engine.calc.CurrencyRates
import com.ngdesigns.intelligentsearch.engine.toggles.ToggleRequest
import com.ngdesigns.intelligentsearch.engine.toggles.Toggles
import com.ngdesigns.intelligentsearch.engine.toggles.VolumeStream
import com.ngdesigns.intelligentsearch.engine.web.WebShortcut
import com.ngdesigns.intelligentsearch.engine.web.WebShortcuts
import java.time.LocalDateTime

/** Something search answers on the spot, shown above the results. */
sealed interface Instant {
    data class Calculation(val answer: Answer) : Instant
    data class Command(val action: Action) : Instant
    data class Switch(val request: ToggleRequest) : Instant
    data class Volume(val stream: VolumeStream) : Instant
    data class Shortcut(val use: WebShortcuts.Use) : Instant
    /** The query is a web address; [url] is it with https added when missing. */
    data class Address(val url: String) : Instant
}

/** The user's search preferences that shape results. */
data class SearchPreferences(
    /** Priority per kind, -2 (lower) to +2 (higher); 0 when absent. */
    val priority: Map<Kind, Int> = emptyMap(),
    /** Most results shown per kind; absent means no limit. */
    val limits: Map<Kind, Int> = emptyMap(),
    val typos: Boolean = true,
    val calculator: Boolean = true,
    val shortcutSymbol: String = "!",
    val shortcuts: List<WebShortcut> = WebShortcuts.builtIn,
)

data class Section(val kind: Kind, val items: List<Ranked>)

data class SearchResult(
    val query: String,
    val instants: List<Instant>,
    /** The most likely thing the user wants, highlighted and opened by Enter. */
    val best: Ranked?,
    /** What typing would complete to, for ghost text: the rest of [best]'s name, when the query starts it. */
    val completion: String?,
    val sections: List<Section>,
)

/**
 * The whole query pipeline: instant answers (shortcut, address, calculation, command, toggles), then every catalog
 * ranked, weighted by the user's priorities and cut to their limits, with one best match. Pure and fast; the app
 * calls it on each keystroke with its current catalogs.
 */
object Search {
    private const val PRIORITY_STEP = 80f

    fun run(
        query: String,
        catalogs: Map<Kind, Catalog>,
        preferences: SearchPreferences = SearchPreferences(),
        now: LocalDateTime = LocalDateTime.now(),
        nowMillis: Long = System.currentTimeMillis(),
        rates: CurrencyRates = CurrencyRates.Fallback,
    ): SearchResult {
        val q = query.trim()
        if (q.isEmpty()) return SearchResult(query, emptyList(), null, null, emptyList())

        val instants = buildList {
            WebShortcuts.parse(q, preferences.shortcuts, preferences.shortcutSymbol)?.let { add(Instant.Shortcut(it)) }
            webAddress(q)?.let { add(Instant.Address(it)) }
            if (preferences.calculator) Calculator.evaluate(q, rates)?.let { add(Instant.Calculation(it)) }
            Actions.parse(q, now)?.let { add(Instant.Command(it)) }
            Toggles.find(q).take(2).forEach { add(Instant.Switch(it)) }
            Toggles.volumes(q).forEach { add(Instant.Volume(it)) }
        }

        val options = RankingOptions(typos = preferences.typos, now = nowMillis)
        val sections = catalogs.mapNotNull { (kind, catalog) ->
            val limit = preferences.limits[kind] ?: Int.MAX_VALUE
            if (limit <= 0) return@mapNotNull null
            val boost = (preferences.priority[kind] ?: 0).coerceIn(-2, 2) * PRIORITY_STEP
            val items = catalog.search(q, limit, options).map { it.copy(score = it.score + boost) }
            if (items.isEmpty()) null else Section(kind, items)
        }.sortedByDescending { s -> s.items.first().score }

        val best = sections.asSequence()
            .filter { it.kind != Kind.History && it.kind != Kind.Web }
            .flatMap { it.items }
            .filter { it.match.kind <= MatchKind.Acronym || it.match.kind == MatchKind.Typo && instants.isEmpty() }
            .maxByOrNull { it.score }
        val completion = best?.candidate?.title?.let { title ->
            if (title.length > q.length && title.startsWith(q, ignoreCase = true)) title.substring(q.length) else null
        }
        return SearchResult(query, instants, best, completion, sections)
    }

    private val fileEndings = setOf(
        "zip", "rar", "7z", "apk", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "json", "xml",
        "jpg", "jpeg", "png", "gif", "webp", "heic", "mp3", "mp4", "mov", "mkv", "avi", "wav", "flac", "kt", "java",
    )

    /** "example.com", "www.site.org/page", "https://x.io": an address to open, not a search. */
    internal fun webAddress(q: String): String? {
        if (q.contains(' ') || q.length < 4) return null
        val m = Regex("^(?:(https?)://)?([a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)+)(?::\\d{1,5})?(?:[/?#].*)?$").find(q) ?: return null
        val host = m.groupValues[2]
        val tld = host.substringAfterLast('.')
        if (tld.length < 2 || !tld.all { it.isLetter() }) return null
        if (m.groupValues[1].isEmpty() && tld.lowercase() in fileEndings && host.count { it == '.' } == 1) return null
        return if (m.groupValues[1].isEmpty()) "https://$q" else q.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
    }
}
