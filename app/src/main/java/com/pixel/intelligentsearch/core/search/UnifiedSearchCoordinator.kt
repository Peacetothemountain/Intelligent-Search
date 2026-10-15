package com.pixel.intelligentsearch.core.search

import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.SettingsManager
import android.content.Context
import android.content.Intent
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.AppShortcutItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.FileItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified Search Coordinator & In-Memory Intelligence Engine.
 *
 * Combines:
 * 1. Compressed Radix Tree prefix lookups (< 0.5ms)
 * 2. Double Metaphone phonetic inverted index (< 0.2ms)
 * 3. Bounded Levenshtein fuzzy matching
 * 4. Multi-factor Dynamic Recency & Relevance Scoring Matrix
 * 5. Direct Scientific Math & Unit Expression Evaluator
 * 6. Natural Language Date/Time & Intent Parser
 * 7. System Actions & Live Volume Slider Router
 *
 * Guarantees sub-5ms local search response across thousands of indexed entities.
 */
@Singleton
class UnifiedSearchCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    val systemActionRouter: SystemActionRouter,
    private val multiProfileManager: com.pixel.intelligentsearch.core.profile.MultiProfileManager,
    settingsManager: SettingsManager
) {
    data class RankedSearchResult(
        val item: SearchItem,
        val scoreBreakdown: SearchScoringEngine.ScoreBreakdown
    )

    private class ScoredCandidate<T>(val item: T, val score: Float)

    data class SearchItem(
        val id: String,
        val title: String,
        val subtitle: String? = null,
        val domain: SearchScoringEngine.EntityDomain,
        val payload: Any,
        val metadata: SearchScoringEngine.ScoringMetadata = SearchScoringEngine.ScoringMetadata(),
        val precomputedMetaphone: DoubleMetaphone.MetaphoneResult? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SearchItem) return false
            return id == other.id && domain == other.domain
        }

        override fun hashCode(): Int {
            return 31 * id.hashCode() + domain.hashCode()
        }
    }

    data class Tier0SearchResults(
        val query: String,
        val mathResult: MathematicalExpressionEngine.MathEvaluationResult? = null,
        val systemAction: SystemActionRouter.ActionResult? = null,
        val apps: List<AppItem> = emptyList(),
        val executionTimeMs: Long = 0L
    )

    data class UnifiedSearchResults(
        val query: String,
        val mathResult: MathematicalExpressionEngine.MathEvaluationResult? = null,
        val parsedIntent: NaturalLanguageIntentParser.ParsedIntent? = null,
        val systemAction: SystemActionRouter.ActionResult? = null,
        val apps: List<AppItem> = emptyList(),
        val contacts: List<ContactItem> = emptyList(),
        val shortcuts: List<AppShortcutItem> = emptyList(),
        val files: List<FileItem> = emptyList(),
        val totalExecutionTimeMs: Long = 0L
    )

    private val appRadixTree = RadixTreeIndex<SearchItem>()
    // Package names live in their own tree: nearly every app shares the "com." prefix, so in the name tree
    // they used up the prefix-collection limit for "c"/"co" before names like "Camera" were reached.
    private val appPackageRadixTree = RadixTreeIndex<SearchItem>()
    private val contactRadixTree = RadixTreeIndex<SearchItem>()
    private val shortcutRadixTree = RadixTreeIndex<SearchItem>()
    private val fileRadixTree = RadixTreeIndex<SearchItem>()
    private val phoneticIndex = ConcurrentHashMap<String, MutableSet<SearchItem>>()

    private val prefs = settingsManager.prefs
    @Volatile private var cachedAppWeightMul: Float = 1.0f
    @Volatile private var cachedContactWeightMul: Float = 1.0f
    @Volatile private var cachedFileWeightMul: Float = 1.0f
    @Volatile private var cachedFuzzySearchEnabled: Boolean = true
    @Volatile private var cachedContactSearchEnabled: Boolean = false

    // Settings read on every keystroke are cached and refreshed when any of them changes.
    private val cachedSettingNames = listOf(
        SettingsManager.SEARCH_WEIGHT_APPS, SettingsManager.SEARCH_WEIGHT_CONTACTS, SettingsManager.SEARCH_WEIGHT_FILES,
        SettingsManager.APP_FUZZY_SEARCH, SettingsManager.SEARCH_CONTACTS
    ).flatMap { it.allNames }.toSet()

    private val prefChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in cachedSettingNames) refreshCachedSettings()
    }

    init {
        refreshCachedSettings()
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
    }

    private fun refreshCachedSettings() {
        cachedAppWeightMul = weightMultiplier(prefs[SettingsManager.SEARCH_WEIGHT_APPS])
        cachedContactWeightMul = weightMultiplier(prefs[SettingsManager.SEARCH_WEIGHT_CONTACTS])
        cachedFileWeightMul = weightMultiplier(prefs[SettingsManager.SEARCH_WEIGHT_FILES])
        cachedFuzzySearchEnabled = prefs[SettingsManager.APP_FUZZY_SEARCH]
        cachedContactSearchEnabled = prefs[SettingsManager.SEARCH_CONTACTS]
    }

    /** A priority weight of 50 is neutral (x1). */
    private fun weightMultiplier(weight: Int): Float = (weight / 50f).coerceAtLeast(0.01f)

    // ---------------------------------------------------------------------------------------------
    // INGESTION & INDEXING API
    // ---------------------------------------------------------------------------------------------

    /**
     * Replaces every indexed contact with [contacts] without ever exposing an empty or half-built
     * contact index to concurrent searches: the new phonetic entries are published first, the radix
     * tree is swapped atomically, and only then are entries of vanished contacts retired.
     */
    fun replaceContacts(contacts: List<ContactItem>, lastUsedMs: Long = 0L) {
        val keys = ArrayList<Pair<String, SearchItem>>(contacts.size * 4)
        val fresh = HashSet<SearchItem>(contacts.size * 2)
        for (contact in contacts) {
            val item = newContactItem(contact, lastUsedMs)
            collectContactKeys(contact, item, keys)
            fresh.add(item)
        }
        contactRadixTree.replaceAll(keys)
        removeFromPhoneticIndex { it.domain == SearchScoringEngine.EntityDomain.CONTACT && it !in fresh }
    }

    /**
     * Atomically replaces every indexed shortcut with [shortcuts], so shortcuts that were removed or
     * disabled since the last sync no longer linger in the index.
     */
    fun replaceShortcuts(shortcuts: List<AppShortcutItem>) {
        val keys = ArrayList<Pair<String, SearchItem>>(shortcuts.size * 2)
        for (shortcut in shortcuts) {
            collectShortcutKeys(shortcut, keys)
        }
        shortcutRadixTree.replaceAll(keys)
    }

    private fun collectShortcutKeys(shortcut: AppShortcutItem, out: MutableList<Pair<String, SearchItem>>) {
        val metadata = SearchScoringEngine.ScoringMetadata(
            domain = SearchScoringEngine.EntityDomain.APP_SHORTCUT
        )
        val metaphone = DoubleMetaphone.encode(shortcut.shortLabel)
        val searchItem = SearchItem(
            id = "shortcut:${shortcut.packageName}/${shortcut.id}",
            title = shortcut.shortLabel,
            subtitle = shortcut.longLabel,
            domain = SearchScoringEngine.EntityDomain.APP_SHORTCUT,
            payload = shortcut,
            metadata = metadata,
            precomputedMetaphone = metaphone
        )

        out.add(shortcut.shortLabel to searchItem)
        if (shortcut.longLabel.isNotBlank()) {
            out.add(shortcut.longLabel to searchItem)
        }
    }

    /**
     * Atomically replaces every indexed file with [files], so deleted or renamed files no longer
     * linger in the index.
     */
    fun replaceFiles(files: List<FileItem>) {
        val keys = ArrayList<Pair<String, SearchItem>>(files.size)
        for (file in files) {
            keys.add(file.name to newFileItem(file, file.lastModifiedMs))
        }
        fileRadixTree.replaceAll(keys)
    }

    private fun newFileItem(file: FileItem, modifiedMs: Long): SearchItem {
        return SearchItem(
            // The content URI is unique; paths can repeat across chosen folders and picker items have none.
            id = "file:${file.uri}",
            title = file.name,
            subtitle = file.path,
            domain = SearchScoringEngine.EntityDomain.FILE,
            payload = file,
            metadata = SearchScoringEngine.ScoringMetadata(
                lastUsedTimestampMs = modifiedMs,
                domain = SearchScoringEngine.EntityDomain.FILE
            ),
            precomputedMetaphone = DoubleMetaphone.encode(file.name)
        )
    }

    /**
     * Atomically replaces every indexed app with [apps], so apps that were uninstalled, disabled or
     * moved out of an unlocked profile since the last sync no longer linger in results. An empty list
     * is ignored because it signals a failed PackageManager query rather than a device with no apps.
     */
    fun bulkIndexApps(apps: List<AppItem>) {
        if (apps.isEmpty()) return
        val keys = ArrayList<Pair<String, SearchItem>>(apps.size * 5)
        val packageKeys = ArrayList<Pair<String, SearchItem>>(apps.size)
        val fresh = HashSet<SearchItem>(apps.size * 2)
        for (app in apps) {
            val item = newAppItem(
                app,
                SearchScoringEngine.ScoringMetadata(domain = SearchScoringEngine.EntityDomain.APPLICATION)
            )
            collectAppKeys(app, item, keys)
            packageKeys.add(app.packageName to item)
            fresh.add(item)
        }
        appRadixTree.replaceAll(keys)
        appPackageRadixTree.replaceAll(packageKeys)
        removeFromPhoneticIndex { it.domain == SearchScoringEngine.EntityDomain.APPLICATION && it !in fresh }
    }

    private fun newAppItem(app: AppItem, metadata: SearchScoringEngine.ScoringMetadata): SearchItem {
        return SearchItem(
            id = "app:${app.packageName}:${app.userHandle?.hashCode() ?: 0}:${app.profileType}",
            title = app.name,
            subtitle = app.packageName,
            domain = SearchScoringEngine.EntityDomain.APPLICATION,
            payload = app,
            metadata = metadata,
            precomputedMetaphone = DoubleMetaphone.encode(app.name)
        )
    }

    /**
     * Appends every name key under which an app is reachable (title, normalized title, acronym,
     * individual words) to [out] and registers its phonetic codes. The package name is indexed separately
     * in [appPackageRadixTree].
     */
    private fun collectAppKeys(app: AppItem, item: SearchItem, out: MutableList<Pair<String, SearchItem>>) {
        out.add(app.name to item)
        val normalizedTitle = QueryNormalizer.normalize(app.name)
        if (normalizedTitle.isNotEmpty() && !normalizedTitle.equals(app.name, ignoreCase = true)) {
            out.add(normalizedTitle to item)
        }

        // Acronym / initials (e.g. "yt" -> "YouTube", "gpm" -> "Google Play Music")
        val initials = QueryNormalizer.extractInitials(app.name)
        if (initials.isNotEmpty()) {
            out.add(initials to item)
        }
        for (word in QueryNormalizer.tokenize(app.name)) {
            out.add(word to item)
        }

        item.precomputedMetaphone?.let {
            addToPhoneticIndex(it.primary, item)
            addToPhoneticIndex(it.alternate, item)
        }
    }

    private fun newContactItem(contact: ContactItem, lastUsedMs: Long): SearchItem {
        return SearchItem(
            id = "contact:${contact.lookupUri}",
            title = contact.name,
            subtitle = contact.phoneNumber,
            domain = SearchScoringEngine.EntityDomain.CONTACT,
            payload = contact,
            metadata = SearchScoringEngine.ScoringMetadata(
                launchCount = 0,
                lastUsedTimestampMs = lastUsedMs,
                isPinned = false,
                domain = SearchScoringEngine.EntityDomain.CONTACT
            ),
            precomputedMetaphone = DoubleMetaphone.encode(contact.name)
        )
    }

    /**
     * Appends every radix key under which a contact is reachable (full name, normalized name, phone
     * digits, individual name tokens) to [out] and registers its phonetic codes.
     */
    private fun collectContactKeys(contact: ContactItem, item: SearchItem, out: MutableList<Pair<String, SearchItem>>) {
        out.add(contact.name to item)
        val normalizedContact = QueryNormalizer.normalize(contact.name)
        if (normalizedContact.isNotEmpty() && !normalizedContact.equals(contact.name, ignoreCase = true)) {
            out.add(normalizedContact to item)
        }
        val digits = contact.phoneNumber.filter { it.isDigit() }
        out.add(digits to item)
        // Lookup is prefix-only, so also index the number without its country / trunk code: "415555"
        // must find a contact saved as +1 415-555-1234.
        for (localLength in intArrayOf(10, 7)) {
            if (digits.length > localLength) out.add(digits.takeLast(localLength) to item)
        }

        // Individual name tokens (first name, last name)
        for (token in QueryNormalizer.tokenize(contact.name)) {
            out.add(token to item)
            val tokenMetaphone = DoubleMetaphone.encode(token)
            addToPhoneticIndex(tokenMetaphone.primary, item)
            addToPhoneticIndex(tokenMetaphone.alternate, item)
        }

        // Full name phonetics
        item.precomputedMetaphone?.let { addToPhoneticIndex(it.primary, item) }
    }

    /**
     * Publishes [item] under a phonetic [code]. The per-key atomic `compute` guarantees the entry can
     * never land in a bucket that a concurrent [removeFromPhoneticIndex] has just discarded, and an
     * already present equal item is replaced so its payload never goes stale.
     */
    private fun addToPhoneticIndex(code: String, item: SearchItem) {
        if (code.isEmpty()) return
        phoneticIndex.compute(code) { _, existing ->
            val bucket = existing ?: ConcurrentHashMap.newKeySet()
            bucket.remove(item)
            bucket.add(item)
            bucket
        }
    }

    /**
     * Removes every phonetic entry matching [predicate], dropping buckets that become empty. Each bucket
     * is mutated through an atomic per-key `computeIfPresent`, so it cannot race with [addToPhoneticIndex].
     */
    private fun removeFromPhoneticIndex(predicate: (SearchItem) -> Boolean) {
        for (code in phoneticIndex.keys) {
            phoneticIndex.computeIfPresent(code) { _, bucket ->
                bucket.removeIf(predicate)
                if (bucket.isEmpty()) null else bucket
            }
        }
    }

    /**
     * Immediately purges all Private Space apps and shortcuts from in-memory indices.
     */
    fun purgePrivateProfileItems() {
        val privateUsers = multiProfileManager.profilesState.value
            .filter { it.profileType == com.pixel.intelligentsearch.core.data.ProfileType.PRIVATE }
            .mapTo(HashSet()) { it.userHandle }
        val isPrivateItem: (SearchItem) -> Boolean = { item ->
            when (val payload = item.payload) {
                is AppItem -> payload.isPrivateProfile || payload.profileType == com.pixel.intelligentsearch.core.data.ProfileType.PRIVATE
                is AppShortcutItem -> payload.userHandle in privateUsers
                else -> false
            }
        }
        appRadixTree.removeIf(isPrivateItem)
        appPackageRadixTree.removeIf(isPrivateItem)
        shortcutRadixTree.removeIf(isPrivateItem)
        removeFromPhoneticIndex(isPrivateItem)
    }

    private fun isPrivateSpaceItemBlocked(item: SearchItem, isPrivateLocked: Boolean, privateUsers: Set<android.os.UserHandle>): Boolean {
        if (!isPrivateLocked) return false
        return when (val payload = item.payload) {
            is AppItem -> payload.isPrivateProfile || payload.profileType == com.pixel.intelligentsearch.core.data.ProfileType.PRIVATE
            is AppShortcutItem -> payload.userHandle in privateUsers
            else -> false
        }
    }

    /**
     * Clears all in-memory indices.
     */
    fun clearIndex() {
        appRadixTree.clear()
        appPackageRadixTree.clear()
        contactRadixTree.clear()
        shortcutRadixTree.clear()
        fileRadixTree.clear()
        phoneticIndex.clear()
    }

    // ---------------------------------------------------------------------------------------------
    // HIGH-PERFORMANCE SEARCH PIPELINES (< 2ms Tier 0 / < 5ms Full)
    // ---------------------------------------------------------------------------------------------

    /**
     * Tier 0 Instant Search (< 2ms) executed synchronously on keystrokes.
     * Evaluates instant calculators, system toggles/actions, and Radix Tree app prefix lookups.
     */
    /**
     * App prefix matches by name, plus package-name matches once the query looks like one (4+ characters
     * or containing a dot), so "com" alone never pulls in every installed app.
     */
    private fun appPrefixMatches(query: String, limit: Int): List<SearchItem> {
        val byName = appRadixTree.searchPrefix(query, limit)
        if (query.length < 4 && '.' !in query) return byName
        return byName + appPackageRadixTree.searchPrefix(query, limit)
    }

    fun executeTier0Search(rawQuery: String): Tier0SearchResults {
        val startTime = System.nanoTime()
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            return Tier0SearchResults(query = query)
        }

        // 1. Instant System Action / Slider Router
        val systemAction = systemActionRouter.matchAction(query)

        // 2. Instant Math, Scientific, & Unit Evaluator
        val mathResult = MathematicalExpressionEngine.evaluate(query)

        // 3. Fast Radix prefix search for apps only
        val candidates = mutableSetOf<SearchItem>()
        val directMatches = appPrefixMatches(query, limit = 20)
        candidates.addAll(directMatches)

        val normalized = QueryNormalizer.normalize(query)
        if (normalized.isNotEmpty() && !normalized.equals(query, ignoreCase = true)) {
            val normMatches = appPrefixMatches(normalized, limit = 20)
            candidates.addAll(normMatches)
        }

        // If candidates are small and query has multiple words, search first token or initials
        if (candidates.size < 3) {
            val tokens = QueryNormalizer.tokenize(query)
            if (tokens.isNotEmpty()) {
                val tokenMatches = appRadixTree.searchPrefix(tokens[0], limit = 20)
                candidates.addAll(tokenMatches)
            }
            val initials = QueryNormalizer.extractInitials(query)
            if (initials.isNotEmpty()) {
                val initialMatches = appRadixTree.searchPrefix(initials, limit = 10)
                candidates.addAll(initialMatches)
            }
        }

        val now = System.currentTimeMillis()
        val queryMetaphone = DoubleMetaphone.encode(query)
        val isPrivateLocked = multiProfileManager.isPrivateSpaceLocked()
        val privateUsers = if (isPrivateLocked) {
            multiProfileManager.profilesState.value
                .filter { it.profileType == com.pixel.intelligentsearch.core.data.ProfileType.PRIVATE }
                .mapTo(HashSet()) { it.userHandle }
        } else emptySet()
        val scoredApps = ArrayList<ScoredCandidate<AppItem>>(candidates.size)
        for (item in candidates) {
            if (isPrivateSpaceItemBlocked(item, isPrivateLocked, privateUsers)) continue
            val score = SearchScoringEngine.evaluateScore(
                query = query,
                targetTitle = item.title,
                metadata = item.metadata,
                currentTimeMs = now,
                domainWeightMultiplier = 1.0f,
                precomputedQueryMetaphone = queryMetaphone,
                precomputedTargetMetaphone = item.precomputedMetaphone,
                targetSubtitle = item.subtitle
            )
            if (score.totalScore > 0f) {
                scoredApps.add(ScoredCandidate(item.payload as AppItem, score.totalScore))
            }
        }
        scoredApps.sortByDescending { it.score }
        val takeCount = minOf(8, scoredApps.size)
        val matchedApps = ArrayList<AppItem>(takeCount)
        for (i in 0 until takeCount) {
            matchedApps.add(scoredApps[i].item)
        }

        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

        return Tier0SearchResults(
            query = query,
            mathResult = mathResult,
            systemAction = systemAction,
            apps = matchedApps,
            executionTimeMs = elapsedMs
        )
    }

    /**
     * In-memory search for apps (< 1ms).
     */
    fun searchApps(query: String, limit: Int = 12): List<AppItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val candidates = mutableSetOf<SearchItem>()
        appPrefixMatches(q, limit = limit * 3).filterTo(candidates) { it.domain == SearchScoringEngine.EntityDomain.APPLICATION }

        val normalized = QueryNormalizer.normalize(q)
        if (normalized.isNotEmpty() && !normalized.equals(q, ignoreCase = true)) {
            appPrefixMatches(normalized, limit = limit * 3).filterTo(candidates) { it.domain == SearchScoringEngine.EntityDomain.APPLICATION }
        }

        val now = System.currentTimeMillis()
        val queryMetaphone = DoubleMetaphone.encode(q)
        val isPrivateLocked = multiProfileManager.isPrivateSpaceLocked()
        val privateUsers = if (isPrivateLocked) {
            multiProfileManager.profilesState.value
                .filter { it.profileType == com.pixel.intelligentsearch.core.data.ProfileType.PRIVATE }
                .mapTo(HashSet()) { it.userHandle }
        } else emptySet()
        val scoredApps = ArrayList<ScoredCandidate<AppItem>>(candidates.size)
        for (item in candidates) {
            if (isPrivateSpaceItemBlocked(item, isPrivateLocked, privateUsers)) continue
            val score = SearchScoringEngine.evaluateScore(
                query = q,
                targetTitle = item.title,
                metadata = item.metadata,
                currentTimeMs = now,
                domainWeightMultiplier = cachedAppWeightMul,
                precomputedQueryMetaphone = queryMetaphone,
                precomputedTargetMetaphone = item.precomputedMetaphone,
                targetSubtitle = item.subtitle
            )
            if (score.totalScore > 0f) {
                scoredApps.add(ScoredCandidate(item.payload as AppItem, score.totalScore))
            }
        }
        scoredApps.sortByDescending { it.score }
        val takeCount = minOf(limit, scoredApps.size)
        val resultApps = ArrayList<AppItem>(takeCount)
        for (i in 0 until takeCount) {
            resultApps.add(scoredApps[i].item)
        }
        return resultApps
    }

    /**
     * Queries indexed contacts from memory (< 1ms).
     */
    fun searchContacts(query: String, limit: Int = 10): List<ContactItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val candidates = mutableSetOf<SearchItem>()
        contactRadixTree.searchPrefix(q, limit = limit * 3).filterTo(candidates) { it.domain == SearchScoringEngine.EntityDomain.CONTACT }

        val normalized = QueryNormalizer.normalize(q)
        if (normalized.isNotEmpty() && !normalized.equals(q, ignoreCase = true)) {
            contactRadixTree.searchPrefix(normalized, limit = limit * 3).filterTo(candidates) { it.domain == SearchScoringEngine.EntityDomain.CONTACT }
        }

        val queryMetaphone = DoubleMetaphone.encode(q)
        if (queryMetaphone.primary.isNotEmpty()) {
            phoneticIndex[queryMetaphone.primary]?.filterTo(candidates) { it.domain == SearchScoringEngine.EntityDomain.CONTACT }
        }

        val now = System.currentTimeMillis()
        val scoredContacts = ArrayList<ScoredCandidate<ContactItem>>(candidates.size)
        for (item in candidates) {
            val score = SearchScoringEngine.evaluateScore(
                query = q,
                targetTitle = item.title,
                metadata = item.metadata,
                currentTimeMs = now,
                domainWeightMultiplier = cachedContactWeightMul,
                precomputedQueryMetaphone = queryMetaphone,
                precomputedTargetMetaphone = item.precomputedMetaphone,
                targetSubtitle = item.subtitle
            )
            if (score.totalScore > 0f) {
                scoredContacts.add(ScoredCandidate(item.payload as ContactItem, score.totalScore))
            }
        }
        scoredContacts.sortByDescending { it.score }
        val takeCount = minOf(limit, scoredContacts.size)
        val resultContacts = ArrayList<ContactItem>(takeCount)
        for (i in 0 until takeCount) {
            resultContacts.add(scoredContacts[i].item)
        }
        return resultContacts
    }

    /**
     * Queries indexed shortcuts from memory (< 1ms).
     */
    fun searchShortcuts(query: String, limit: Int = 6): List<AppShortcutItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val candidates = mutableSetOf<SearchItem>()
        shortcutRadixTree.searchPrefix(q, limit = limit * 2).filterTo(candidates) { it.domain == SearchScoringEngine.EntityDomain.APP_SHORTCUT }

        val normalized = QueryNormalizer.normalize(q)
        if (normalized.isNotEmpty() && !normalized.equals(q, ignoreCase = true)) {
            shortcutRadixTree.searchPrefix(normalized, limit = limit * 2).filterTo(candidates) { it.domain == SearchScoringEngine.EntityDomain.APP_SHORTCUT }
        }

        val now = System.currentTimeMillis()
        val queryMetaphone = DoubleMetaphone.encode(q)
        val isPrivateLocked = multiProfileManager.isPrivateSpaceLocked()
        val privateUsers = if (isPrivateLocked) {
            multiProfileManager.profilesState.value
                .filter { it.profileType == com.pixel.intelligentsearch.core.data.ProfileType.PRIVATE }
                .mapTo(HashSet()) { it.userHandle }
        } else emptySet()
        val scoredShortcuts = ArrayList<ScoredCandidate<AppShortcutItem>>(candidates.size)
        for (item in candidates) {
            if (isPrivateSpaceItemBlocked(item, isPrivateLocked, privateUsers)) continue
            val score = SearchScoringEngine.evaluateScore(
                query = q,
                targetTitle = item.title,
                metadata = item.metadata,
                currentTimeMs = now,
                domainWeightMultiplier = 1.0f,
                precomputedQueryMetaphone = queryMetaphone,
                precomputedTargetMetaphone = item.precomputedMetaphone,
                targetSubtitle = item.subtitle
            )
            if (score.totalScore > 0f) {
                scoredShortcuts.add(ScoredCandidate(item.payload as AppShortcutItem, score.totalScore))
            }
        }
        scoredShortcuts.sortByDescending { it.score }
        val takeCount = minOf(limit, scoredShortcuts.size)
        val resultShortcuts = ArrayList<AppShortcutItem>(takeCount)
        for (i in 0 until takeCount) {
            resultShortcuts.add(scoredShortcuts[i].item)
        }
        return resultShortcuts
    }

    /**
     * Queries indexed files from memory (< 1ms).
     */
    fun searchFiles(query: String, limit: Int = 10): List<FileItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val candidates = mutableSetOf<SearchItem>()
        fileRadixTree.searchPrefix(q, limit = limit * 2).filterTo(candidates) { it.domain == SearchScoringEngine.EntityDomain.FILE }

        val normalized = QueryNormalizer.normalize(q)
        if (normalized.isNotEmpty() && !normalized.equals(q, ignoreCase = true)) {
            fileRadixTree.searchPrefix(normalized, limit = limit * 2).filterTo(candidates) { it.domain == SearchScoringEngine.EntityDomain.FILE }
        }

        val now = System.currentTimeMillis()
        val queryMetaphone = DoubleMetaphone.encode(q)
        val scoredFiles = ArrayList<ScoredCandidate<FileItem>>(candidates.size)
        for (item in candidates) {
            val score = SearchScoringEngine.evaluateScore(
                query = q,
                targetTitle = item.title,
                metadata = item.metadata,
                currentTimeMs = now,
                domainWeightMultiplier = cachedFileWeightMul,
                precomputedQueryMetaphone = queryMetaphone,
                precomputedTargetMetaphone = item.precomputedMetaphone,
                targetSubtitle = item.subtitle
            )
            if (score.totalScore > 0f) {
                scoredFiles.add(ScoredCandidate(item.payload as FileItem, score.totalScore))
            }
        }
        scoredFiles.sortByDescending { it.score }
        val takeCount = minOf(limit, scoredFiles.size)
        val resultFiles = ArrayList<FileItem>(takeCount)
        for (i in 0 until takeCount) {
            resultFiles.add(scoredFiles[i].item)
        }
        return resultFiles
    }

    fun removePackage(packageName: String) {
        appRadixTree.removeIf { (it.payload as? AppItem)?.packageName == packageName }
        appPackageRadixTree.removeIf { (it.payload as? AppItem)?.packageName == packageName }
        shortcutRadixTree.removeIf { (it.payload as? AppShortcutItem)?.packageName == packageName }
        removeFromPhoneticIndex {
            (it.payload as? AppItem)?.packageName == packageName ||
                (it.payload as? AppShortcutItem)?.packageName == packageName
        }
    }

    /**
     * Unregisters preference listeners and purges in-memory indices for zero memory leaks.
     */
    fun cleanup() {
        try {
            prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        } catch (_: Exception) {}
        clearIndex()
    }

    suspend fun executeSearch(rawQuery: String): UnifiedSearchResults = withContext(Dispatchers.Default) {
        executeSearchInternal(rawQuery) { ensureActive() }
    }

    fun executeSearchSync(rawQuery: String): UnifiedSearchResults {
        return executeSearchInternal(rawQuery, cancellationCheck = null)
    }

    private fun executeSearchInternal(rawQuery: String, cancellationCheck: (() -> Unit)? = null): UnifiedSearchResults {
        val startTime = System.nanoTime()
        val query = rawQuery.trim()

        if (query.isEmpty()) {
            return UnifiedSearchResults(query = query)
        }

        // 1. Instant System Action / Slider Router
        val systemAction = systemActionRouter.matchAction(query)

        // 2. Instant Math, Scientific, & Unit Evaluator
        val mathResult = MathematicalExpressionEngine.evaluate(query)

        // 3. Natural Language Intent Parser
        // "Call X" / "Message X" only resolve names from contacts when contact search is turned on.
        val contactLookup: ((String) -> ContactItem?)? = if (cachedContactSearchEnabled) {
            { target -> searchContacts(target, limit = 5).firstOrNull { it.phoneNumber.isNotBlank() } }
        } else null
        val parsedIntent = NaturalLanguageIntentParser.parse(query, contactLookup = contactLookup)

        cancellationCheck?.invoke()

        // 4. Candidate Retrieval via Partitioned Radix Tree Prefix Search (< 0.5ms)
        val candidates = mutableSetOf<SearchItem>()
        candidates.addAll(appPrefixMatches(query, limit = 40))
        candidates.addAll(contactRadixTree.searchPrefix(query, limit = 20))
        candidates.addAll(shortcutRadixTree.searchPrefix(query, limit = 20))
        candidates.addAll(fileRadixTree.searchPrefix(query, limit = 20))

        val normalized = QueryNormalizer.normalize(query)
        if (normalized.isNotEmpty() && !normalized.equals(query, ignoreCase = true)) {
            candidates.addAll(appPrefixMatches(normalized, limit = 40))
            candidates.addAll(contactRadixTree.searchPrefix(normalized, limit = 20))
            candidates.addAll(shortcutRadixTree.searchPrefix(normalized, limit = 20))
            candidates.addAll(fileRadixTree.searchPrefix(normalized, limit = 20))
        }

        // Multi-token lookup for multi-word queries (e.g. "goo chr")
        val tokens = QueryNormalizer.tokenize(query)
        if (tokens.size > 1) {
            for (token in tokens) {
                candidates.addAll(appRadixTree.searchPrefix(token, limit = 20))
                candidates.addAll(contactRadixTree.searchPrefix(token, limit = 10))
                candidates.addAll(shortcutRadixTree.searchPrefix(token, limit = 10))
                candidates.addAll(fileRadixTree.searchPrefix(token, limit = 10))
            }
            // Conjunctive filter: ensure matched candidates contain all query tokens across title + subtitle
            candidates.retainAll { item ->
                QueryNormalizer.containsAllTokens("${item.title} ${item.subtitle.orEmpty()}", tokens)
            }
        }

        // 5. Phonetic Candidate Retrieval via Double Metaphone (< 0.2ms)
        cancellationCheck?.invoke()
        val queryMetaphone = DoubleMetaphone.encode(query)
        if (queryMetaphone.primary.isNotEmpty()) {
            phoneticIndex[queryMetaphone.primary]?.let { candidates.addAll(it) }
        }
        if (queryMetaphone.alternate.isNotEmpty()) {
            phoneticIndex[queryMetaphone.alternate]?.let { candidates.addAll(it) }
        }

        // Phone number search normalization for contact lookups
        val queryDigits = query.filter { it.isDigit() }
        if (queryDigits.length >= 3) {
            candidates.addAll(contactRadixTree.searchPrefix(queryDigits, limit = 20))
        }

        // 6. Typo-Tolerant Fuzzy Search if App Candidate Pool is Small (< 1.5ms)
        val appCandidateCount = candidates.count { it.domain == SearchScoringEngine.EntityDomain.APPLICATION }
        if (cachedFuzzySearchEnabled && appCandidateCount < 8 && query.length >= 3) {
            cancellationCheck?.invoke()
            val fuzzyResults = appRadixTree.searchFuzzy(query, maxDistance = 2, limit = 20)
            for (f in fuzzyResults) {
                candidates.add(f.value)
            }
        }

        // 7. Multi-Factor Relevance Scoring Matrix
        cancellationCheck?.invoke()
        val now = System.currentTimeMillis()
        val appWeightMul = cachedAppWeightMul
        val contactWeightMul = cachedContactWeightMul
        val fileWeightMul = cachedFileWeightMul
        val isPrivateLocked = multiProfileManager.isPrivateSpaceLocked()
        val privateUsers = if (isPrivateLocked) {
            multiProfileManager.profilesState.value
                .filter { it.profileType == com.pixel.intelligentsearch.core.data.ProfileType.PRIVATE }
                .mapTo(HashSet()) { it.userHandle }
        } else emptySet()

        val rankedResults = ArrayList<RankedSearchResult>(candidates.size)
        var scoredCount = 0
        for (item in candidates) {
            if ((++scoredCount and 15) == 0) {
                cancellationCheck?.invoke()
            }
            if (isPrivateSpaceItemBlocked(item, isPrivateLocked, privateUsers)) continue
            val domainMul = when (item.domain) {
                SearchScoringEngine.EntityDomain.APPLICATION, SearchScoringEngine.EntityDomain.APP_SHORTCUT -> appWeightMul
                SearchScoringEngine.EntityDomain.CONTACT -> contactWeightMul
                SearchScoringEngine.EntityDomain.FILE -> fileWeightMul
                else -> 1.0f
            }
            val scoreBreakdown = SearchScoringEngine.evaluateScore(
                query = query,
                targetTitle = item.title,
                metadata = item.metadata,
                currentTimeMs = now,
                domainWeightMultiplier = domainMul,
                precomputedQueryMetaphone = queryMetaphone,
                precomputedTargetMetaphone = item.precomputedMetaphone,
                targetSubtitle = item.subtitle
            )
            if (scoreBreakdown.totalScore > 0f) {
                rankedResults.add(RankedSearchResult(item, scoreBreakdown))
            }
        }
        cancellationCheck?.invoke()
        rankedResults.sortByDescending { it.scoreBreakdown.totalScore }

        // 8. Domain Separation & Output Filtering
        val matchedApps = mutableListOf<AppItem>()
        val matchedContacts = mutableListOf<ContactItem>()
        val matchedShortcuts = mutableListOf<AppShortcutItem>()
        val matchedFiles = mutableListOf<FileItem>()

        for (ranked in rankedResults) {
            when (ranked.item.domain) {
                SearchScoringEngine.EntityDomain.APPLICATION -> {
                    (ranked.item.payload as? AppItem)?.let { if (matchedApps.size < 12) matchedApps.add(it) }
                }
                SearchScoringEngine.EntityDomain.CONTACT -> {
                    (ranked.item.payload as? ContactItem)?.let { if (matchedContacts.size < 8) matchedContacts.add(it) }
                }
                SearchScoringEngine.EntityDomain.APP_SHORTCUT -> {
                    (ranked.item.payload as? AppShortcutItem)?.let { if (matchedShortcuts.size < 6) matchedShortcuts.add(it) }
                }
                SearchScoringEngine.EntityDomain.FILE -> {
                    (ranked.item.payload as? FileItem)?.let { if (matchedFiles.size < 8) matchedFiles.add(it) }
                }
                else -> {}
            }
        }

        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

        return UnifiedSearchResults(
            query = query,
            mathResult = mathResult,
            parsedIntent = parsedIntent,
            systemAction = systemAction,
            apps = matchedApps,
            contacts = matchedContacts,
            shortcuts = matchedShortcuts,
            files = matchedFiles,
            totalExecutionTimeMs = elapsedMs
        )
    }
}
