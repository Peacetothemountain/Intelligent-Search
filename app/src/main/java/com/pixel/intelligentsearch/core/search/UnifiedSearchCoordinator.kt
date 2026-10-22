package com.pixel.intelligentsearch.core.search

import com.pixel.intelligentsearch.core.data.SystemDataProvider
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.SettingsManager
import android.content.Context
import android.content.Intent
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.ResultShortcutStore
import com.pixel.intelligentsearch.core.currency.CurrencyRateRepository
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
 * 8. Date Arithmetic Answers ("days until Dec 25", "90 days from now")
 *
 * Guarantees sub-5ms local search response across thousands of indexed entities.
 */
@Singleton
class UnifiedSearchCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    val systemActionRouter: SystemActionRouter,
    private val multiProfileManager: com.pixel.intelligentsearch.core.profile.MultiProfileManager,
    settingsManager: SettingsManager,
    private val currencyRates: CurrencyRateRepository,
    /** Pins and nicknames, applied to ranking at search time. */
    private val resultShortcuts: ResultShortcutStore
) {
    /**
     * Outside Hilt (tests): currency answers use the saved or built-in rates from a repository of its own, and pins and
     * nicknames are read from [settingsManager] through a store of its own.
     */
    constructor(
        context: Context,
        systemActionRouter: SystemActionRouter,
        multiProfileManager: com.pixel.intelligentsearch.core.profile.MultiProfileManager,
        settingsManager: SettingsManager
    ) : this(
        context, systemActionRouter, multiProfileManager, settingsManager,
        CurrencyRateRepository(context, settingsManager), ResultShortcutStore(settingsManager)
    )

    private val dateMath = DateMathEngine()

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
        val precomputedMetaphone: DoubleMetaphone.MetaphoneResult? = null,
        /** The result's [ResultKeys] name, under which pins and nicknames are stored; null when it can't have any. */
        val shortcutKey: String? = null
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
        val apps: List<AppItem> = emptyList()
    )

    data class UnifiedSearchResults(
        val query: String,
        val mathResult: MathematicalExpressionEngine.MathEvaluationResult? = null,
        val parsedIntent: NaturalLanguageIntentParser.ParsedIntent? = null,
        val dateAnswer: DateMathEngine.DateAnswer? = null,
        val systemAction: SystemActionRouter.ActionResult? = null,
        val apps: List<AppItem> = emptyList(),
        val contacts: List<ContactItem> = emptyList(),
        val files: List<FileItem> = emptyList()
    )

    /** The highest each result-count setting goes; the view model trims to the user's setting. */
    private val MAX_RESULTS_PER_DOMAIN = 20

    private val appRadixTree = RadixTreeIndex<SearchItem>()
    // Package names live in their own tree: nearly every app shares the "com." prefix, so in the name tree
    // they used up the prefix-collection limit for "c"/"co" before names like "Camera" were reached.
    private val appPackageRadixTree = RadixTreeIndex<SearchItem>()
    private val contactRadixTree = RadixTreeIndex<SearchItem>()
    private val fileRadixTree = RadixTreeIndex<SearchItem>()
    private val phoneticIndex = ConcurrentHashMap<String, MutableSet<SearchItem>>()
    /**
     * Every indexed app, contact and file by its [SearchItem.shortcutKey]: nickname matches are looked up here, and the
     * overlay finds pinned results and trigger words' targets here. Each replace swaps its own domain's entries.
     */
    @Volatile private var itemsByShortcutKey: Map<String, SearchItem> = emptyMap()

    private val prefs = settingsManager.prefs
    @Volatile private var appUsage: Map<String, SystemDataProvider.AppUsage> = emptyMap()
    @Volatile private var cachedFuzzySearchEnabled: Boolean = true
    @Volatile private var cachedContactSearchEnabled: Boolean = false

    // Settings read on every keystroke are cached and refreshed when any of them changes. The per-domain search
    // weights aren't among them: results are ranked within each domain, where one multiplier for every item
    // changes no order, so the overlay applies them when it orders the sections instead.
    private val cachedSettingNames = listOf(
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
        cachedFuzzySearchEnabled = prefs[SettingsManager.APP_FUZZY_SEARCH]
        cachedContactSearchEnabled = prefs[SettingsManager.SEARCH_CONTACTS]
    }

    /**
     * The calculator and converter answer, with the current currency rates. A currency answer may start a fetch, which
     * the repository allows only while the Calculator and Live Currency Rates are on and Tor isn't the engine.
     */
    private fun evaluateMath(query: String): MathematicalExpressionEngine.MathEvaluationResult? {
        val result = MathematicalExpressionEngine.evaluate(query, currencyRates::current)
        (result as? MathematicalExpressionEngine.MathEvaluationResult.UnitConversion)?.currencyRates
            ?.let(currencyRates::onCurrencyConverted)
        return result
    }

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
        val fresh = identitySet(contacts.size * 2)
        for (contact in contacts) {
            val item = newContactItem(contact, lastUsedMs)
            collectContactKeys(contact, item, keys)
            fresh.add(item)
        }
        contactRadixTree.replaceAll(keys)
        replaceShortcutItems(SearchScoringEngine.EntityDomain.CONTACT, fresh)
        removeFromPhoneticIndex { it.domain == SearchScoringEngine.EntityDomain.CONTACT && it !in fresh }
    }

    /**
     * Atomically replaces every indexed file with [files], so deleted or renamed files no longer
     * linger in the index.
     */
    fun replaceFiles(files: List<FileItem>) {
        val keys = ArrayList<Pair<String, SearchItem>>(files.size * 3)
        val items = ArrayList<SearchItem>(files.size)
        for (file in files) {
            val item = newFileItem(file, file.lastModifiedMs)
            keys.add(file.name to item)
            // Each word of the name as well: "resume" finds "My Resume.pdf".
            for (word in QueryNormalizer.tokenize(file.name)) keys.add(word to item)
            items.add(item)
        }
        fileRadixTree.replaceAll(keys)
        replaceShortcutItems(SearchScoringEngine.EntityDomain.FILE, items)
    }

    private val shortcutItemsLock = Any()

    /** Swaps [domain]'s entries in [itemsByShortcutKey] for [items], leaving the other domains' as they are. */
    private fun replaceShortcutItems(domain: SearchScoringEngine.EntityDomain, items: Collection<SearchItem>) {
        synchronized(shortcutItemsLock) {
            val next = HashMap<String, SearchItem>(itemsByShortcutKey.size + items.size)
            for ((key, item) in itemsByShortcutKey) if (item.domain != domain) next[key] = item
            for (item in items) item.shortcutKey?.let { next[it] = item }
            itemsByShortcutKey = next
        }
    }

    /**
     * The indexed app ([AppItem]), contact ([ContactItem]) or file ([FileItem]) a [ResultKeys] key names, if it is
     * indexed now; a locked Private Space's apps are never handed out.
     */
    fun indexedResult(key: String): Any? {
        val item = itemsByShortcutKey[key] ?: return null
        if (isPrivateSpaceItemBlocked(item, multiProfileManager.isPrivateSpaceLocked())) return null
        return item.payload
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
            precomputedMetaphone = DoubleMetaphone.encode(file.name),
            shortcutKey = ResultKeys.file(file)
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
        val fresh = identitySet(apps.size * 2)
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
        replaceShortcutItems(SearchScoringEngine.EntityDomain.APPLICATION, fresh)
        removeFromPhoneticIndex { it.domain == SearchScoringEngine.EntityDomain.APPLICATION && it !in fresh }
    }

    /**
     * The items just indexed, by identity. Items compare equal by id, so an id-based set would keep a renamed
     * contact's or app's old phonetic entry (old name, old number); only the instances indexed now survive cleanup.
     */
    private fun identitySet(capacity: Int): MutableSet<SearchItem> =
        java.util.Collections.newSetFromMap(java.util.IdentityHashMap(capacity))

    /** Replaces the app usage that feeds frequency and recency into app ranking. */
    fun updateAppUsage(usage: Map<String, SystemDataProvider.AppUsage>) {
        appUsage = usage
    }

    /** [item]'s scoring metadata with current app usage (apps only) and whether the user pinned it applied. */
    private fun metadataFor(item: SearchItem, shortcuts: ResultShortcuts): SearchScoringEngine.ScoringMetadata {
        var metadata = item.metadata
        val usage = (item.payload as? AppItem)?.let { appUsage[it.packageName] }
        if (usage != null) metadata = metadata.copy(launchCount = usage.daysUsed, lastUsedTimestampMs = usage.lastUsedMs)
        if (shortcuts.isPinned(item.shortcutKey)) metadata = metadata.copy(isPinned = true)
        return metadata
    }

    /** Scores [item] for [query] with its usage, pin and nicknames, as every search pipeline here does. */
    private fun scoreItem(
        query: String,
        item: SearchItem,
        shortcuts: ResultShortcuts,
        now: Long,
        queryMetaphone: DoubleMetaphone.MetaphoneResult
    ): SearchScoringEngine.ScoreBreakdown = SearchScoringEngine.evaluateScore(
        query = query,
        targetTitle = item.title,
        metadata = metadataFor(item, shortcuts),
        currentTimeMs = now,
        domainWeightMultiplier = 1.0f,
        precomputedQueryMetaphone = queryMetaphone,
        precomputedTargetMetaphone = item.precomputedMetaphone,
        targetSubtitle = item.subtitle,
        nicknames = shortcuts.nicknamesOf(item.shortcutKey)
    )

    /** Indexed items of [domain] with a nickname that is [query] or starts with it. */
    private fun nicknameMatches(query: String, shortcuts: ResultShortcuts, domain: SearchScoringEngine.EntityDomain? = null): List<SearchItem> {
        if (!shortcuts.hasNicknames) return emptyList()
        val items = itemsByShortcutKey
        return shortcuts.keysWithNicknameFor(query).mapNotNull { items[it] }.filter { domain == null || it.domain == domain }
    }

    private fun newAppItem(app: AppItem, metadata: SearchScoringEngine.ScoringMetadata): SearchItem {
        return SearchItem(
            id = "app:${app.packageName}:${app.userHandle?.hashCode() ?: 0}:${app.profileType}",
            title = app.name,
            subtitle = app.packageName,
            domain = SearchScoringEngine.EntityDomain.APPLICATION,
            payload = app,
            metadata = metadata,
            precomputedMetaphone = DoubleMetaphone.encode(app.name),
            shortcutKey = ResultKeys.app(app)
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
            precomputedMetaphone = DoubleMetaphone.encode(contact.name),
            shortcutKey = ResultKeys.contact(contact)
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
     * Immediately purges all Private Space apps from in-memory indices.
     */
    fun purgePrivateProfileItems() {
        val isPrivateItem: (SearchItem) -> Boolean = { item ->
            when (val payload = item.payload) {
                is AppItem -> payload.isPrivateProfile || payload.profileType == com.pixel.intelligentsearch.core.data.ProfileType.PRIVATE
                else -> false
            }
        }
        appRadixTree.removeIf(isPrivateItem)
        appPackageRadixTree.removeIf(isPrivateItem)
        synchronized(shortcutItemsLock) { itemsByShortcutKey = itemsByShortcutKey.filterValues { !isPrivateItem(it) } }
        removeFromPhoneticIndex(isPrivateItem)
    }

    private fun isPrivateSpaceItemBlocked(item: SearchItem, isPrivateLocked: Boolean): Boolean {
        if (!isPrivateLocked) return false
        return when (val payload = item.payload) {
            is AppItem -> payload.isPrivateProfile || payload.profileType == com.pixel.intelligentsearch.core.data.ProfileType.PRIVATE
            else -> false
        }
    }

    // ---------------------------------------------------------------------------------------------
    // HIGH-PERFORMANCE SEARCH PIPELINES (< 2ms Tier 0 / < 5ms Full)
    // ---------------------------------------------------------------------------------------------

    /**
     * Every app with a name key starting with [query]. Not capped: the tree hands matches back in indexing order,
     * so a cap dropped apps before usage ranking saw them ("s" with many "Sa..." apps lost a much-used Spotify).
     */
    private fun appNameMatches(query: String): List<SearchItem> = appRadixTree.searchPrefix(query, limit = Int.MAX_VALUE)

    /**
     * App prefix matches by name, plus up to [packageLimit] package-name matches once the query looks like one
     * (4+ characters or containing a dot), so "com" alone never pulls in every installed app.
     */
    private fun appPrefixMatches(query: String, packageLimit: Int): List<SearchItem> {
        val byName = appNameMatches(query)
        if (query.length < 4 && '.' !in query) return byName
        return byName + appPackageRadixTree.searchPrefix(query, packageLimit)
    }

    /**
     * Tier 0 Instant Search (< 2ms) executed synchronously on keystrokes.
     * Evaluates instant calculators, system toggles/actions, and Radix Tree app prefix lookups.
     */
    fun executeTier0Search(rawQuery: String): Tier0SearchResults {
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            return Tier0SearchResults(query = query)
        }

        // 1. Instant System Action / Slider Router
        val systemAction = systemActionRouter.matchAction(query)

        // 2. Instant Math, Scientific, & Unit Evaluator
        val mathResult = evaluateMath(query)

        // 3. Fast Radix prefix search for apps only
        val candidates = mutableSetOf<SearchItem>()
        val directMatches = appPrefixMatches(query, packageLimit = 20)
        candidates.addAll(directMatches)

        val normalized = QueryNormalizer.normalize(query)
        if (normalized.isNotEmpty() && !normalized.equals(query, ignoreCase = true)) {
            val normMatches = appPrefixMatches(normalized, packageLimit = 20)
            candidates.addAll(normMatches)
        }

        // If candidates are small and query has multiple words, search first token or initials
        if (candidates.size < 3) {
            val tokens = QueryNormalizer.tokenize(query)
            if (tokens.isNotEmpty()) {
                val tokenMatches = appNameMatches(tokens[0])
                candidates.addAll(tokenMatches)
            }
            val initials = QueryNormalizer.extractInitials(query)
            if (initials.isNotEmpty()) {
                val initialMatches = appNameMatches(initials)
                candidates.addAll(initialMatches)
            }
        }
        val shortcuts = resultShortcuts.current
        candidates.addAll(nicknameMatches(query, shortcuts, SearchScoringEngine.EntityDomain.APPLICATION))

        val now = System.currentTimeMillis()
        val queryMetaphone = DoubleMetaphone.encode(query)
        val isPrivateLocked = multiProfileManager.isPrivateSpaceLocked()
        val scoredApps = ArrayList<ScoredCandidate<AppItem>>(candidates.size)
        for (item in candidates) {
            if (isPrivateSpaceItemBlocked(item, isPrivateLocked)) continue
            val score = scoreItem(query, item, shortcuts, now, queryMetaphone)
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

        return Tier0SearchResults(
            query = query,
            mathResult = mathResult,
            systemAction = systemAction,
            apps = matchedApps
        )
    }

    /**
     * In-memory search for apps (< 1ms).
     */
    fun searchApps(query: String, limit: Int = 12): List<AppItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val candidates = mutableSetOf<SearchItem>()
        appPrefixMatches(q, packageLimit = limit * 3).filterTo(candidates) { it.domain == SearchScoringEngine.EntityDomain.APPLICATION }

        val normalized = QueryNormalizer.normalize(q)
        if (normalized.isNotEmpty() && !normalized.equals(q, ignoreCase = true)) {
            appPrefixMatches(normalized, packageLimit = limit * 3).filterTo(candidates) { it.domain == SearchScoringEngine.EntityDomain.APPLICATION }
        }
        val shortcuts = resultShortcuts.current
        candidates.addAll(nicknameMatches(q, shortcuts, SearchScoringEngine.EntityDomain.APPLICATION))

        val now = System.currentTimeMillis()
        val queryMetaphone = DoubleMetaphone.encode(q)
        val isPrivateLocked = multiProfileManager.isPrivateSpaceLocked()
        val scoredApps = ArrayList<ScoredCandidate<AppItem>>(candidates.size)
        for (item in candidates) {
            if (isPrivateSpaceItemBlocked(item, isPrivateLocked)) continue
            val score = scoreItem(q, item, shortcuts, now, queryMetaphone)
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
        // "Call dad" reaches the contact nicknamed "dad" through here.
        val shortcuts = resultShortcuts.current
        candidates.addAll(nicknameMatches(q, shortcuts, SearchScoringEngine.EntityDomain.CONTACT))

        val now = System.currentTimeMillis()
        val scoredContacts = ArrayList<ScoredCandidate<ContactItem>>(candidates.size)
        for (item in candidates) {
            val score = scoreItem(q, item, shortcuts, now, queryMetaphone)
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

    suspend fun executeSearch(rawQuery: String): UnifiedSearchResults = withContext(Dispatchers.Default) {
        executeSearchInternal(rawQuery) { ensureActive() }
    }

    fun executeSearchSync(rawQuery: String): UnifiedSearchResults {
        return executeSearchInternal(rawQuery, cancellationCheck = null)
    }

    private fun executeSearchInternal(rawQuery: String, cancellationCheck: (() -> Unit)? = null): UnifiedSearchResults {
        val query = rawQuery.trim()

        if (query.isEmpty()) {
            return UnifiedSearchResults(query = query)
        }

        // 1. Instant System Action / Slider Router
        val systemAction = systemActionRouter.matchAction(query)

        // 2. Instant Math, Scientific, & Unit Evaluator
        val mathResult = evaluateMath(query)
        val dateAnswer = dateMath.evaluate(query)

        // 3. Natural Language Intent Parser
        // "Call X" / "Message X" only resolve names from contacts when contact search is turned on.
        val contactLookup: ((String) -> ContactItem?)? = if (cachedContactSearchEnabled) {
            { target -> searchContacts(target, limit = 5).firstOrNull { it.phoneNumber.isNotBlank() } }
        } else null
        val parsedIntent = NaturalLanguageIntentParser.parse(query, contactLookup = contactLookup)

        cancellationCheck?.invoke()

        // 4. Candidate Retrieval via Partitioned Radix Tree Prefix Search (< 0.5ms)
        val candidates = mutableSetOf<SearchItem>()
        candidates.addAll(appPrefixMatches(query, packageLimit = 40))
        candidates.addAll(contactRadixTree.searchPrefix(query, limit = 20))
        candidates.addAll(fileRadixTree.searchPrefix(query, limit = 20))

        val normalized = QueryNormalizer.normalize(query)
        if (normalized.isNotEmpty() && !normalized.equals(query, ignoreCase = true)) {
            candidates.addAll(appPrefixMatches(normalized, packageLimit = 40))
            candidates.addAll(contactRadixTree.searchPrefix(normalized, limit = 20))
            candidates.addAll(fileRadixTree.searchPrefix(normalized, limit = 20))
        }

        // Multi-token lookup for multi-word queries (e.g. "goo chr")
        val tokens = QueryNormalizer.tokenize(query)
        if (tokens.size > 1) {
            for (token in tokens) {
                candidates.addAll(appNameMatches(token))
                candidates.addAll(contactRadixTree.searchPrefix(token, limit = 10))
                candidates.addAll(fileRadixTree.searchPrefix(token, limit = 10))
            }
            // Conjunctive filter: ensure matched candidates contain all query tokens across title + subtitle
            candidates.retainAll { item ->
                QueryNormalizer.containsAllTokens("${item.title} ${item.subtitle.orEmpty()}", tokens)
            }
        }
        // The user's own names for results ("dad", "my bank"), after the filter above, which only reads titles.
        val shortcuts = resultShortcuts.current
        candidates.addAll(nicknameMatches(query, shortcuts))

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
            // A number typed with its country code ("+1 415 555 2671") must find one saved without it
            // ("(415) 555-2671"). Contacts are also indexed under their last 10 and 7 digits, so look those up.
            if (queryDigits.length > 10) {
                for (localLength in intArrayOf(10, 7)) {
                    candidates.addAll(contactRadixTree.searchPrefix(queryDigits.takeLast(localLength), limit = 20))
                }
            }
        }

        // 6. Typo-Tolerant Fuzzy Search if App Candidate Pool is Small (< 1.5ms)
        val appCandidateCount = candidates.count { it.domain == SearchScoringEngine.EntityDomain.APPLICATION }
        if (cachedFuzzySearchEnabled && appCandidateCount < 8 && query.length >= 3) {
            cancellationCheck?.invoke()
            val fuzzyResults = appRadixTree.searchFuzzy(query, maxDistance = SearchScoringEngine.fuzzyEditsFor(query.length), limit = 20)
            for (f in fuzzyResults) {
                candidates.add(f.value)
            }
        }

        // 7. Multi-Factor Relevance Scoring Matrix
        cancellationCheck?.invoke()
        val now = System.currentTimeMillis()
        val isPrivateLocked = multiProfileManager.isPrivateSpaceLocked()

        val rankedResults = ArrayList<RankedSearchResult>(candidates.size)
        var scoredCount = 0
        for (item in candidates) {
            if ((++scoredCount and 15) == 0) {
                cancellationCheck?.invoke()
            }
            if (isPrivateSpaceItemBlocked(item, isPrivateLocked)) continue
            val scoreBreakdown = scoreItem(query, item, shortcuts, now, queryMetaphone)
            if (scoreBreakdown.totalScore > 0f) {
                rankedResults.add(RankedSearchResult(item, scoreBreakdown))
            }
        }
        cancellationCheck?.invoke()
        rankedResults.sortByDescending { it.scoreBreakdown.totalScore }

        // 8. Domain Separation & Output Filtering
        val matchedApps = mutableListOf<AppItem>()
        val matchedContacts = mutableListOf<ContactItem>()
        val matchedFiles = mutableListOf<FileItem>()

        for (ranked in rankedResults) {
            when (ranked.item.domain) {
                SearchScoringEngine.EntityDomain.APPLICATION -> {
                    (ranked.item.payload as? AppItem)?.let { if (matchedApps.size < 12) matchedApps.add(it) }
                }
                SearchScoringEngine.EntityDomain.CONTACT -> {
                    (ranked.item.payload as? ContactItem)?.let { if (matchedContacts.size < MAX_RESULTS_PER_DOMAIN) matchedContacts.add(it) }
                }
                SearchScoringEngine.EntityDomain.FILE -> {
                    (ranked.item.payload as? FileItem)?.let { if (matchedFiles.size < MAX_RESULTS_PER_DOMAIN) matchedFiles.add(it) }
                }
                else -> {}
            }
        }

        return UnifiedSearchResults(
            query = query,
            mathResult = mathResult,
            parsedIntent = parsedIntent,
            dateAnswer = dateAnswer,
            systemAction = systemAction,
            apps = matchedApps,
            contacts = matchedContacts,
            files = matchedFiles
        )
    }
}
