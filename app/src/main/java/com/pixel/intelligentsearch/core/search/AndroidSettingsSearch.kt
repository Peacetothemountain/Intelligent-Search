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

/**
 * A Settings page offered for a query: tapping it opens [intent]. [app] is set for an app's App Info page.
 * [isStrongMatch] means the query named the page outright ("battery", "wifi settings", "app info chrome"), so the
 * overlay may lift the section to just after the apps.
 *
 * Results are equal when they show and open the same thing: [id] names the page, or the app and its profile, so
 * the intent follows from it. An Intent compares by identity and can be changed, so it is left out of equality
 * and never changed once made (opening a result launches a copy), which keeps the [androidx.compose.runtime.Immutable]
 * promise honest.
 */
@androidx.compose.runtime.Immutable
class AndroidSettingResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconType: String,
    val intent: Intent,
    val app: AppItem? = null,
    val isStrongMatch: Boolean = false
) {
    /** What TalkBack reads for the row, e.g. "Battery settings" or "App info for Chrome". */
    val spokenLabel: String get() = if (app != null) "App info for ${app.name}" else "$title settings"

    override fun equals(other: Any?): Boolean =
        other is AndroidSettingResult && id == other.id && title == other.title && subtitle == other.subtitle &&
            iconType == other.iconType && app == other.app && isStrongMatch == other.isStrongMatch

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "AndroidSettingResult(id=$id, title=$title, strong=$isStrongMatch)"
}

/**
 * Matches queries against [AndroidSettingsCatalog] with the same scoring the app and contact results use
 * (prefix, word, initials, phonetic and typo-tolerant matching), and answers "app info <app>" for installed
 * apps. Only pages whose intent resolves on this phone are offered; each answer is cached for the process,
 * since the Settings app's activities don't come and go.
 */
@Singleton
class AndroidSettingsSearch @Inject constructor(@param:ApplicationContext private val context: Context) {

    private class Term(val text: String, val isTitle: Boolean) {
        val metaphone: DoubleMetaphone.MetaphoneResult = DoubleMetaphone.encode(text)
        val words: List<String> = text.split(' ')
        val wordMetaphones: List<DoubleMetaphone.MetaphoneResult> = words.map(DoubleMetaphone::encode)
        val initials: String = words.joinToString("") { it.take(1) }
    }

    private class IndexedPage(val page: AndroidSettingsCatalog.Page, val terms: List<Term>)

    private val index: List<IndexedPage> = AndroidSettingsCatalog.pages.map { page ->
        val texts = (listOf(page.title) + page.keywords).map(QueryNormalizer::normalize).filter { it.isNotEmpty() }
        IndexedPage(page, texts.distinct().map { Term(it, isTitle = it == QueryNormalizer.normalize(page.title)) })
    }

    private val unlisted: Set<String> = AndroidSettingsCatalog.unlisted.map(QueryNormalizer::normalize).toSet()

    private val resolvable = ConcurrentHashMap<String, Boolean>()

    /**
     * Settings pages and App Info pages for [rawQuery], best match first. [findApps] looks installed apps up by
     * name; it should leave out apps the user hid, and find nothing while the Apps source is off. [peopleNames] are
     * the contacts shown for the query.
     *
     * App Info is offered only when asked for: "app info <app>", "<app> app info", "<app> settings" and the like. A
     * query that is exactly an installed app's name ("chrome") is left to the app: no App Info row, and no page that
     * merely shares a word with it. A query that only starts an app's or a contact's name ("insta", "carrie") is left
     * to them too: only a page it names outright is offered.
     */
    fun search(rawQuery: String, findApps: (String) -> List<AppItem> = { emptyList() }): List<AndroidSettingResult> =
        search(rawQuery, emptyList(), findApps)

    /** [search], knowing which contacts are shown for the query ([peopleNames]). */
    fun search(rawQuery: String, peopleNames: List<String>, findApps: (String) -> List<AppItem>): List<AndroidSettingResult> {
        val normalized = QueryNormalizer.normalize(rawQuery)
        if (normalized.isEmpty()) return emptyList()
        // "battery settings" and "settings wifi" mean the page; "settings" alone is the Settings app's to answer.
        val query = normalized.split(' ').filterNot { it in SETTINGS_WORDS }.joinToString(" ")
        if (query.isEmpty()) return emptyList()

        appInfoTarget(normalized)?.let { appName ->
            return appInfoResults(findApps(appName), isStrongMatch = true) + pageResults(query)
        }

        val apps = findApps(query)
        val namedApps = apps.filter { QueryNormalizer.normalize(it.name) == query }
        // "chrome settings" asks for the app's settings outright. A bare "chrome" is the app itself, which the apps
        // already show: it gets no App Info row, and no page that merely shares a word with it ("chrome" is not Cast).
        if (namedApps.isNotEmpty()) {
            return if (query != normalized) appInfoResults(namedApps, isStrongMatch = true) else emptyList()
        }
        val pages = pageResults(query)
        val startsAName = apps.any { QueryNormalizer.normalize(it.name).startsWith(query) } ||
            peopleNames.any { name -> QueryNormalizer.normalize(name).let { it.startsWith(query) || it.split(' ').any { word -> word.startsWith(query) } } }
        return if (startsAName) pages.filter { it.isStrongMatch } else pages
    }

    /** Catalogue page [id] as a result, if it opens on this phone: for pinned pages. */
    fun page(id: String): AndroidSettingResult? {
        val page = index.firstOrNull { it.page.id == id }?.page ?: return null
        if (!isAvailable(page.id) { Intent(page.action) }) return null
        return AndroidSettingResult(page.id, page.title, SUBTITLE, page.iconType, Intent(page.action))
    }

    private fun pageResults(query: String): List<AndroidSettingResult> {
        if (query in unlisted || query in TOO_GENERIC) return emptyList()
        val queryWords = query.split(' ')
        val queryMetaphone = DoubleMetaphone.encode(query)
        return index
            .mapNotNull { indexed -> score(query, queryWords, queryMetaphone, indexed)?.let { indexed to it } }
            .sortedByDescending { it.second }
            .asSequence()
            .map { it.first }
            .filter { isAvailable(it.page.id) { Intent(it.page.action) } }
            .take(MAX_PAGES)
            .map { indexed ->
                val page = indexed.page
                AndroidSettingResult(
                    id = page.id,
                    title = page.title,
                    subtitle = SUBTITLE,
                    iconType = page.iconType,
                    intent = Intent(page.action),
                    isStrongMatch = indexed.terms.any { namesOutright(query, it) }
                )
            }
            .toList()
    }

    /**
     * Whether [query] names the page of [term] outright: it is the page's title, a keyword of several words ("dark
     * mode"), or a settings word nobody types for anything else ("wifi", "hotspot"). A one-word keyword that is also
     * an everyday word ("time", "pin", "print") is only a weak match, shown below the web and contacts.
     */
    private fun namesOutright(query: String, term: Term): Boolean =
        term.text == query && (term.isTitle || term.words.size > 1 || query in UNAMBIGUOUS_KEYWORDS)

    /** The page's best score over its title and keywords, or null when nothing matches well enough. */
    private fun score(query: String, queryWords: List<String>, queryMetaphone: DoubleMetaphone.MetaphoneResult, indexed: IndexedPage): Float? {
        var best = 0f
        for (term in indexed.terms) {
            // Under three letters only a whole keyword counts ("bt", "dnd" is three): "b" alone would list half the catalogue.
            if (query.length < MIN_PARTIAL_QUERY && term.text != query) continue
            if (!isPlausible(query, queryWords, term)) continue
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
     * Whether [term] is a match worth scoring: the query is the term, starts it or one of its words, spells its
     * initials, or is a typo of or sounds like it. App names need the scorer's looser tiers; against this short list
     * of common words they turned app names, people and everyday words into settings ("news" into "pair new device",
     * "clock" into "lock screen", "tim" into Date & Time, "carrie" into "carrier", "patreon" into "pattern"). So:
     * - a title may be started from three letters ("blu"); a word inside it, or a keyword, only from four;
     * - a keyword's initials must be typed whole;
     * - a multi-word query must match the term word for word, so "system update" isn't "system language" for
     *   sharing its first word, and a slip or sound-alike counts only within its own word;
     * - a single word gets typos and sound-alikes against titles from five letters, but against keywords, which are
     *   often everyday words, only one slip in seven letters or more ("hotpsot"), or in five for a settings word
     *   nobody types for anything else ("wifii"), and no sound-alikes.
     */
    private fun isPlausible(query: String, queryWords: List<String>, term: Term): Boolean {
        if (term.text == query) return true
        if (term.text.startsWith(query) && (term.isTitle || query.length >= MIN_WORD_PREFIX)) return true
        if (query.length >= MIN_WORD_PREFIX && term.words.any { it.startsWith(query) }) return true
        if (term.words.size > 1 && (if (term.isTitle) term.initials.startsWith(query) else term.initials == query)) return true
        if (queryWords.size > 1) {
            if (queryWords.size != term.words.size) return false
            return queryWords.indices.all { i ->
                val typed = queryWords[i]
                val word = term.words[i]
                typed == word || (i == queryWords.lastIndex && word.startsWith(typed)) ||
                    isSlipOf(typed, word, term.wordMetaphones[i], strict = false)
            }
        }
        // One word: a slip of the whole term or of one of its words; sounding alike only against a one-word term.
        val strict = !term.isTitle
        if (term.words.size > 1 && isSlipOf(query, term.text, null, strict)) return true
        return term.words.indices.any { i -> isSlipOf(query, term.words[i], term.wordMetaphones[i].takeIf { term.words.size == 1 }, strict) }
    }

    /**
     * Whether [typed] is a typo of [word], or sounds like it when [wordMetaphone] is given; both need the right first
     * letter. [strict] is for keywords: seven letters (five for [UNAMBIGUOUS_KEYWORDS]) and a single slip, and no
     * sound-alikes.
     */
    private fun isSlipOf(typed: String, word: String, wordMetaphone: DoubleMetaphone.MetaphoneResult?, strict: Boolean): Boolean {
        if (word.isEmpty() || typed[0] != word[0]) return false
        if (strict) {
            // A settings word nobody types for anything else may be slipped on from five letters ("wifii").
            val minLength = if (word in UNAMBIGUOUS_KEYWORDS) MIN_TYPO_QUERY else MIN_KEYWORD_TYPO_QUERY
            return typed.length >= minLength &&
                LevenshteinDistance.computeDistance(typed, word, maxDistance = 1) <= 1
        }
        if (typed.length < MIN_TYPO_QUERY || word.length < MIN_TYPO_QUERY) return false
        val edits = SearchScoringEngine.fuzzyEditsFor(typed.length)
        if (LevenshteinDistance.computeDistance(typed, word, maxDistance = edits) <= edits) return true
        return wordMetaphone != null && DoubleMetaphone.encode(typed).matches(wordMetaphone)
    }

    /** App Info pages for [apps]: those named by "app info chrome" and the like, or by the query itself. */
    private fun appInfoResults(apps: List<AppItem>, isStrongMatch: Boolean): List<AndroidSettingResult> {
        if (apps.isEmpty() || !isAvailable(APP_INFO_ID) { appDetailsIntent(context.packageName) }) return emptyList()
        return apps
            .distinctBy { it.packageName to it.userHandle }
            .take(MAX_APP_INFO)
            .map { app ->
                AndroidSettingResult(
                    id = "$APP_INFO_ID:${app.packageName}:${app.userHandle?.hashCode() ?: 0}",
                    title = "${app.name} App Info",
                    subtitle = APP_INFO_SUBTITLE,
                    iconType = APP_INFO_ID,
                    intent = appDetailsIntent(app.packageName),
                    app = app,
                    isStrongMatch = isStrongMatch
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
        /** Letters needed before a query may start a keyword, or a word inside a title. */
        private const val MIN_WORD_PREFIX = 4
        private const val MIN_TYPO_QUERY = 5
        private const val MIN_KEYWORD_TYPO_QUERY = 7
        /** Below this a match is a two-slip guess or a distant word ("chrome" for "home"). */
        private const val MIN_MATCH_SCORE = 60f
        private const val KEYWORD_WEIGHT = 0.9f
        private const val SUBTITLE = "Settings"
        private const val APP_INFO_SUBTITLE = "Settings › Apps"
        internal const val APP_INFO_ID = "app_info"

        private val SETTINGS_WORDS = setOf("settings", "setting")

        /**
         * One-word keywords that mean the setting whatever else is around, so typing one names its page outright
         * (a strong match). Other one-word keywords are everyday words too ("time", "pin", "print", "power") and only
         * match weakly; a page's own title is always strong.
         */
        internal val UNAMBIGUOUS_KEYWORDS = setOf(
            "wifi", "wlan", "bluetooth", "bt", "hotspot", "tethering", "nfc", "vpn", "apn", "airplane", "battery",
            "brightness", "ringtone", "vibration", "haptics", "dnd", "storage", "display", "sound", "wallpaper",
            "wallpapers", "screensaver", "autorotate", "notifications", "gps", "fingerprint", "biometrics", "a11y",
            "talkback", "tts", "adb", "imei", "timezone"
        )

        /** Words that alone name no page, but are part of many ("mode", "data"): alone they match nothing. */
        private val TOO_GENERIC = setOf("mode", "data")
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
