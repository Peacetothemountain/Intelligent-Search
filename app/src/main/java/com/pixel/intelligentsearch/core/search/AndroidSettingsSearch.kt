package com.pixel.intelligentsearch.core.search

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.pixel.intelligentsearch.core.data.AppItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** A Settings page offered for a query: tapping it opens [intent]. [app] is set for an app's App Info page. */
@androidx.compose.runtime.Immutable
data class AndroidSettingResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconType: String,
    val intent: Intent,
    val app: AppItem? = null
) {
    /** What TalkBack reads for the row, e.g. "Battery settings" or "App info for Chrome". */
    val spokenLabel: String get() = if (app != null) "App info for ${app.name}" else "$title settings"
}

/**
 * Matches queries against [AndroidSettingsCatalog] with the same scoring the app and contact results use
 * (prefix, word, initials, phonetic and typo-tolerant matching), and answers "app info <app>" for installed
 * apps. Only pages whose intent resolves on this phone are offered; each answer is cached for the process,
 * since the Settings app's activities don't come and go.
 */
@Singleton
class AndroidSettingsSearch @Inject constructor(@param:ApplicationContext private val context: Context) {

    private class Term(val text: String, val metaphone: DoubleMetaphone.MetaphoneResult, val isTitle: Boolean) {
        val words: List<String> = text.split(' ')
        val initials: String = words.joinToString("") { it.take(1) }
    }

    private class IndexedPage(val page: AndroidSettingsCatalog.Page, val terms: List<Term>)

    private val index: List<IndexedPage> = AndroidSettingsCatalog.pages.map { page ->
        val texts = (listOf(page.title) + page.keywords).map(QueryNormalizer::normalize).filter { it.isNotEmpty() }
        IndexedPage(page, texts.distinct().map { Term(it, DoubleMetaphone.encode(it), isTitle = it == QueryNormalizer.normalize(page.title)) })
    }

    private val resolvable = ConcurrentHashMap<String, Boolean>()

    /**
     * Settings pages and App Info pages for [rawQuery], best match first. [findApps] looks installed apps up by
     * name for "app info <app>"; it should leave out apps the user hid.
     */
    fun search(rawQuery: String, findApps: (String) -> List<AppItem> = { emptyList() }): List<AndroidSettingResult> {
        val normalized = QueryNormalizer.normalize(rawQuery)
        if (normalized.isEmpty()) return emptyList()
        return appInfoResults(normalized, findApps) + pageResults(normalized)
    }

    private fun pageResults(normalized: String): List<AndroidSettingResult> {
        // "battery settings" and "settings wifi" mean the page; "settings" alone is the Settings app's to answer.
        val query = normalized.split(' ').filterNot { it in SETTINGS_WORDS }.joinToString(" ")
        if (query.isEmpty()) return emptyList()
        val queryMetaphone = DoubleMetaphone.encode(query)
        return index
            .mapNotNull { indexed -> score(query, queryMetaphone, indexed)?.let { indexed.page to it } }
            .sortedByDescending { it.second }
            .asSequence()
            .map { it.first }
            .filter { isAvailable(it.id) { Intent(it.action) } }
            .take(MAX_PAGES)
            .map { AndroidSettingResult(id = it.id, title = it.title, subtitle = SUBTITLE, iconType = it.iconType, intent = Intent(it.action)) }
            .toList()
    }

    /** The page's best score over its title and keywords, or null when nothing matches well enough. */
    private fun score(query: String, queryMetaphone: DoubleMetaphone.MetaphoneResult, indexed: IndexedPage): Float? {
        var best = 0f
        for (term in indexed.terms) {
            // Under three letters only a whole keyword counts ("bt", "dnd" is three): "b" alone would list half the catalogue.
            if (query.length < MIN_PARTIAL_QUERY && term.text != query) continue
            if (!isPlausible(query, queryMetaphone, term)) continue
            val match = SearchScoringEngine.evaluateScore(
                query = query,
                targetTitle = term.text,
                metadata = SETTINGS_METADATA,
                precomputedQueryMetaphone = queryMetaphone,
                precomputedTargetMetaphone = term.metaphone
            ).matchScore
            // The page's own name beats a page that merely lists the word ("display" -> Display, then Display Over Other Apps).
            val weighted = if (term.isTitle) match else match * KEYWORD_WEIGHT
            if (weighted > best) best = weighted
        }
        return best.takeIf { it >= MIN_MATCH_SCORE }
    }

    /**
     * Whether [term] is a match worth scoring: the query starts the term or one of its words, spells its initials,
     * or is a typo of or sounds like a whole word or term. App names need the scorer's looser tiers; against this
     * short list of common words they turned app names into settings ("news" into "pair new device", "clock" into
     * "lock screen", "weather" into "other"), so typos and sound-alikes need five letters and the right first letter.
     */
    private fun isPlausible(query: String, queryMetaphone: DoubleMetaphone.MetaphoneResult, term: Term): Boolean {
        if (term.text.startsWith(query) || term.words.any { it.startsWith(query) }) return true
        if (term.words.size > 1 && term.initials.startsWith(query)) return true
        if (query.length < MIN_TYPO_QUERY || term.text[0] != query[0]) return false
        val edits = SearchScoringEngine.fuzzyEditsFor(query.length)
        if (LevenshteinDistance.computeDistance(query, term.text, maxDistance = edits) <= edits) return true
        if (term.words.any { it.length >= MIN_TYPO_QUERY && it[0] == query[0] && LevenshteinDistance.computeDistance(query, it, maxDistance = edits) <= edits }) return true
        // Metaphone codes are short, so "app info spotify" would sound like "app info": the word counts must agree too.
        return query.count { it == ' ' } + 1 == term.words.size && queryMetaphone.matches(term.metaphone)
    }

    /** App Info pages for "app info chrome", "chrome app info" and the like. */
    private fun appInfoResults(normalized: String, findApps: (String) -> List<AppItem>): List<AndroidSettingResult> {
        val appName = appInfoTarget(normalized) ?: return emptyList()
        if (!isAvailable(APP_INFO_ID) { appDetailsIntent(context.packageName) }) return emptyList()
        return findApps(appName)
            .distinctBy { it.packageName to it.userHandle }
            .take(MAX_APP_INFO)
            .map { app ->
                AndroidSettingResult(
                    id = "$APP_INFO_ID:${app.packageName}:${app.userHandle?.hashCode() ?: 0}",
                    title = "${app.name} App Info",
                    subtitle = APP_INFO_SUBTITLE,
                    iconType = APP_INFO_ID,
                    intent = appDetailsIntent(app.packageName),
                    app = app
                )
            }
    }

    /** Whether the intent [build] makes for page [id] opens an activity here; asked once per page. */
    private fun isAvailable(id: String, build: () -> Intent): Boolean = resolvable.getOrPut(id) {
        try {
            context.packageManager.resolveActivity(build(), PackageManager.MATCH_DEFAULT_ONLY) != null
        } catch (e: RuntimeException) {
            android.util.Log.w("AndroidSettingsSearch", "Could not resolve settings page $id", e)
            false
        }
    }

    companion object {
        private const val MAX_PAGES = 4
        private const val MAX_APP_INFO = 3
        private const val MIN_PARTIAL_QUERY = 3
        private const val MIN_TYPO_QUERY = 5
        /** Below this a match is a two-slip guess or a distant word ("chrome" for "home"). */
        private const val MIN_MATCH_SCORE = 60f
        private const val KEYWORD_WEIGHT = 0.9f
        private const val SUBTITLE = "Settings"
        private const val APP_INFO_SUBTITLE = "Settings › Apps"
        internal const val APP_INFO_ID = "app_info"

        private val SETTINGS_WORDS = setOf("settings", "setting")
        private val SETTINGS_METADATA = SearchScoringEngine.ScoringMetadata(domain = SearchScoringEngine.EntityDomain.SYSTEM_TOGGLE)
        private val APP_INFO_PREFIXES = listOf("app info for ", "app info of ", "app info ", "app details ", "app settings ", "application info ")
        private val APP_INFO_SUFFIXES = listOf(" app info", " app details", " app settings")

        /** The app named in an App Info query, or null if [query] (normalized) isn't one. */
        internal fun appInfoTarget(query: String): String? {
            val name = APP_INFO_PREFIXES.firstOrNull { query.startsWith(it) }?.let { query.removePrefix(it) }
                ?: APP_INFO_SUFFIXES.firstOrNull { query.endsWith(it) }?.let { query.removeSuffix(it) }
            return name?.trim()?.takeIf { it.isNotEmpty() }
        }

        /** Opens the App Info page of [packageName]. */
        fun appDetailsIntent(packageName: String): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    }
}
