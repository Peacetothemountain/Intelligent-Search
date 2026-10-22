package com.pixel.intelligentsearch.core.data

import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import com.pixel.intelligentsearch.core.search.QueryNormalizer
import com.pixel.intelligentsearch.core.search.ResultKeys
import com.pixel.intelligentsearch.core.search.ResultShortcuts
import com.pixel.intelligentsearch.core.search.SearchScoringEngine
import com.pixel.intelligentsearch.core.security.CustomSearchUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** A web address the user saved under a name, found by that name in search ("bank", "timesheet"). */
@Immutable
@Serializable
data class SavedLink(val name: String, val url: String) {
    /** The site's name for the row's second line: "github.com". */
    val host: String get() = url.toUri().host.orEmpty().removePrefix("www.")
}

/** Saved Links: checking, reading and writing them, and finding them for a query. */
object SavedLinks {

    const val MAX_LINKS = 100
    const val MAX_NAME_LENGTH = 40

    private val JSON = Json { ignoreUnknownKeys = true }

    /** Why a typed address can't be saved, or the https address to save. */
    sealed interface Check {
        data class Valid(val url: String) : Check
        data class Invalid(val reason: String) : Check
    }

    /**
     * Checks a typed address as a custom search address is checked (https only, no credentials, no look-alike
     * hosts), but as a site: "github.com/notifications" is saved as https://github.com/notifications.
     */
    fun check(raw: String): Check = when (val result = CustomSearchUrl.validateSite(raw)) {
        is CustomSearchUrl.Validation.Valid -> Check.Valid(result.template)
        is CustomSearchUrl.Validation.Invalid -> Check.Invalid(result.reason)
    }

    /** [name] as stored: trimmed, inner spaces collapsed; null if blank or too long. */
    fun cleanName(name: String): String? =
        name.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH }

    /**
     * Reads what [toJson] wrote. The text may come from a backup someone else made, so each link is checked as the
     * editor checks it, one link per address, at most [MAX_LINKS].
     */
    fun fromJson(json: String): List<SavedLink> {
        if (json.isBlank()) return emptyList()
        val stored = try {
            JSON.decodeFromString(ListSerializer(SavedLink.serializer()), json)
        } catch (_: Exception) {
            return emptyList()
        }
        val seen = HashSet<String>()
        return stored.asSequence()
            .mapNotNull { link ->
                val name = cleanName(link.name) ?: return@mapNotNull null
                val url = (check(link.url) as? Check.Valid)?.url ?: return@mapNotNull null
                SavedLink(name, url).takeIf { seen.add(url) }
            }
            .take(MAX_LINKS)
            .toList()
    }

    fun toJson(links: List<SavedLink>): String = JSON.encodeToString(ListSerializer(SavedLink.serializer()), links)

    /**
     * The links [query] finds, best first: a name (or one of its words) or a nickname starting with the query, ranked
     * like every other result, pins first.
     */
    fun search(query: String, links: List<SavedLink>, shortcuts: ResultShortcuts, nowMs: Long = System.currentTimeMillis()): List<SavedLink> {
        val q = QueryNormalizer.normalize(query)
        if (q.isEmpty() || links.isEmpty()) return emptyList()
        val nicknamed = shortcuts.keysWithNicknameFor(query)
        return links.asSequence()
            .filter { link ->
                val name = QueryNormalizer.normalize(link.name)
                name.startsWith(q) || QueryNormalizer.tokenize(link.name).any { QueryNormalizer.normalize(it).startsWith(q) } ||
                    ResultKeys.link(link) in nicknamed
            }
            .map { link ->
                val key = ResultKeys.link(link)
                link to SearchScoringEngine.evaluateScore(
                    query = query,
                    targetTitle = link.name,
                    metadata = SearchScoringEngine.ScoringMetadata(isPinned = shortcuts.isPinned(key), domain = SearchScoringEngine.EntityDomain.WEB_SUGGESTION),
                    currentTimeMs = nowMs,
                    nicknames = shortcuts.nicknamesOf(key)
                ).totalScore
            }
            .filter { it.second > 0f }
            .sortedByDescending { it.second }
            .map { it.first }
            .toList()
    }
}

/**
 * The saved links, kept as one setting ([SettingsManager.SAVED_LINKS]) so backups carry them like every other setting;
 * parsed once per change, so search can read them on every keystroke.
 */
@Singleton
class SavedLinkStore @Inject constructor(private val settingsManager: SettingsManager) {

    @Volatile private var parsedFrom: String? = null
    @Volatile private var parsed: List<SavedLink> = emptyList()

    val current: List<SavedLink>
        get() {
            val json = settingsManager[SettingsManager.SAVED_LINKS]
            if (json != parsedFrom) {
                parsed = SavedLinks.fromJson(json)
                parsedFrom = json
            }
            return parsed
        }

    val flow: Flow<List<SavedLink>> = settingsManager.observe(SettingsManager.SAVED_LINKS)
        .map { SavedLinks.fromJson(it) }
        .distinctUntilChanged()

    fun update(change: (List<SavedLink>) -> List<SavedLink>) {
        synchronized(this) {
            val before = current
            val after = change(before).distinctBy { it.url }.take(SavedLinks.MAX_LINKS)
            if (after != before) settingsManager.set(SettingsManager.SAVED_LINKS, SavedLinks.toJson(after))
        }
    }

    /** The saved link a [ResultKeys.link] key names, if it is still saved. */
    fun forKey(key: String): SavedLink? = current.firstOrNull { ResultKeys.link(it) == key }
}
