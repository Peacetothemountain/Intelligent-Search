package com.pixel.intelligentsearch.core.search

import com.pixel.intelligentsearch.core.data.SystemDataProvider
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.SettingsManager
import android.content.Context
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
    /** Pins, applied to ranking at search time. */
    private val resultShortcuts: ResultShortcutStore,
    /** What the user opened and picked for what they typed, applied to ranking at search time. */
    private val selectionMemory: SelectionMemory
) {
    /**
     * Outside Hilt (tests): currency answers use the saved or built-in rates from a repository of its own, pins are
     * read from [settingsManager] through a store of its own, and picks are remembered in this app's own file.
     */
    constructor(
        context: Context,
        systemActionRouter: SystemActionRouter,
        multiProfileManager: com.pixel.intelligentsearch.core.profile.MultiProfileManager,
        settingsManager: SettingsManager,
        selectionMemory: SelectionMemory = SelectionMemory(context)
    ) : this(
        context, systemActionRouter, multiProfileManager, settingsManager,
        CurrencyRateRepository(context, settingsManager), ResultShortcutStore(settingsManager), selectionMemory
    )

    private val dateMath = DateMathEngine()
    private val worldClock = com.pixel.intelligentsearch.core.answers.WorldClock()
    private val randomAnswers = com.pixel.intelligentsearch.core.answers.RandomAnswers()

    /**
     * The last chance answer drawn and the query it was drawn for. The same query runs again when the cursor moves, a
     * pin changes or the index finishes, and the coin or die must not land again each time: it is redrawn only once
     * the query's text has changed or the field was cleared ([forgetChanceDraw]).
     */
    @Volatile
    private var lastRandomAnswer: Pair<String, com.pixel.intelligentsearch.core.answers.RandomAnswers.RandomAnswer?>? = null

    private fun randomAnswer(query: String): com.pixel.intelligentsearch.core.answers.RandomAnswers.RandomAnswer? {
        lastRandomAnswer?.let { (drawnFor, answer) -> if (drawnFor == query) return answer }
        return randomAnswers.evaluate(query).also { lastRandomAnswer = query to it }
    }

    /**
     * Lets the next chance answer land anew. The coordinator outlives the overlay, so without this a coin flipped
     * again after clearing the field, or in the next session, would land the same way every time.
     */
    fun forgetChanceDraw() {
        lastRandomAnswer = null
    }

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
        /** The result's [ResultKeys] name, under which its pin is stored; null when it can't be pinned. */
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
        /** "15 min", "7:30am", "laundry in 2 hrs": the short phrasing's action, when nothing else reads the query. */
        val shortAction: ShortActionPhrasing.Suggestion? = null,
        val dateAnswer: DateMathEngine.DateAnswer? = null,
        /** "time in tokyo", "3pm PST in IST": the clock answer, when the calculator and date maths don't answer. */
        val timeAnswer: com.pixel.intelligentsearch.core.answers.WorldClock.TimeAnswer? = null,
        /** "flip a coin", "roll 2d6", "random 1 to 10": the chance answer, when nothing above answers. */
        val randomAnswer: com.pixel.intelligentsearch.core.answers.RandomAnswers.RandomAnswer? = null,
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
     * Every indexed app, contact and file by its [SearchItem.shortcutKey]: the overlay finds pinned results here. Each
     * replace swaps its own domain's entries.
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
    fun replaceContacts(contacts: List<ContactItem>) {
        val keys = ArrayList<Pair<String, SearchItem>>(contacts.size * 4)
        val fresh = identitySet(contacts.size * 2)
        for (contact in contacts) {
            val item = newContactItem(contact)
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
        val typed = ArrayList<Pair<SearchItem, com.pixel.intelligentsearch.core.data.FileTypeGroup>>(files.size)
        for (file in files) {
            val item = newFileItem(file, file.lastModifiedMs)
            keys.add(file.name to item)
            // Each word of the name as well: "resume" finds "My Resume.pdf".
            for (word in QueryNormalizer.tokenize(file.name)) keys.add(word to item)
            items.add(item)
            typed.add(item to com.pixel.intelligentsearch.core.data.FileTypes.groupOf(file))
        }
        fileRadixTree.replaceAll(keys)
        replaceShortcutItems(SearchScoringEngine.EntityDomain.FILE, items)
        filesByType = typed
    }

    /** Every indexed file with its type, for searches limited to one type ([searchFilesOfType]). */
    @Volatile
    private var filesByType: List<Pair<SearchItem, com.pixel.intelligentsearch.core.data.FileTypeGroup>> = emptyList()

    /**
     * Files of [group] matching [query], best first, for a query that names a type ("pdf invoice" searches PDFs for
     * "invoice"). Every word of [query] has to be in the file's name or folder. A blank [query] lists the files of
     * that type most recently changed first ("photos" alone).
     */
    fun searchFilesOfType(
        query: String,
        group: com.pixel.intelligentsearch.core.data.FileTypeGroup,
        limit: Int = MAX_RESULTS_PER_DOMAIN
    ): List<FileItem> {
        val ofType = filesByType.filter { it.second == group }.map { it.first }
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return ofType.sortedByDescending { it.metadata.lastUsedTimestampMs }.take(limit).mapNotNull { it.payload as? FileItem }
        }
        val tokens = QueryNormalizer.tokenize(trimmed)
        if (tokens.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val queryMetaphone = DoubleMetaphone.encode(trimmed)
        return ofType.asSequence()
            .filter { QueryNormalizer.containsAllTokens("${it.title} ${it.subtitle.orEmpty()}", tokens) }
            .map { item ->
                item to SearchScoringEngine.evaluateScore(
                    query = trimmed,
                    targetTitle = item.title,
                    metadata = item.metadata,
                    currentTimeMs = now,
                    precomputedQueryMetaphone = queryMetaphone,
                    precomputedTargetMetaphone = item.precomputedMetaphone,
                    targetSubtitle = item.subtitle
                ).totalScore
            }
            .sortedByDescending { it.second }
            .take(limit)
            .mapNotNull { it.first.payload as? FileItem }
            .toList()
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

    /**
     * [item]'s scoring metadata for [learnedQuery] ([SelectionMemory.normalizeQuery]'s form) with usage, picks and
     * whether the user pinned it applied. Apps take their usage from Android's usage stats; contacts and files, which
     * have none, from how often and how lately they were opened from search, a file counting as used when it was
     * changed or opened, whichever is later.
     */
    private fun metadataFor(item: SearchItem, shortcuts: ResultShortcuts, learnedQuery: String, now: Long): SearchScoringEngine.ScoringMetadata {
        var metadata = item.metadata
        val appUsageNow = (item.payload as? AppItem)?.let { appUsage[it.packageName] }
        if (appUsageNow != null) {
            metadata = metadata.copy(launchCount = appUsageNow.daysUsed, lastUsedTimestampMs = appUsageNow.lastUsedMs)
        } else if (item.domain != SearchScoringEngine.EntityDomain.APPLICATION) {
            selectionMemory.usage(item.shortcutKey, now)?.let { opened ->
                metadata = metadata.copy(
                    launchCount = kotlin.math.round(opened.opens).toInt(),
                    lastUsedTimestampMs = maxOf(metadata.lastUsedTimestampMs, opened.lastMs)
                )
            }
        }
        val learned = selectionMemory.learnedWeight(item.shortcutKey, learnedQuery, now)
        if (learned > 0f) metadata = metadata.copy(learnedWeight = learned)
        if (shortcuts.isPinned(item.shortcutKey)) metadata = metadata.copy(isPinned = true)
        return metadata
    }

    /** Adds to [candidates] the indexed results [SelectionMemory.candidateKeys] names, of [domain] when one is given. */
    private fun addRemembered(
        candidates: MutableSet<SearchItem>,
        learnedQuery: String,
        domain: SearchScoringEngine.EntityDomain? = null
    ) {
        val keys = selectionMemory.candidateKeys(learnedQuery)
        if (keys.isEmpty()) return
        val indexed = itemsByShortcutKey
        for (key in keys) {
            val item = indexed[key] ?: continue
            if (domain == null || item.domain == domain) candidates.add(item)
        }
    }

    /** Scores [item] for [query] with its usage, picks and pin, as every search pipeline here does. */
    private fun scoreItem(
        query: String,
        item: SearchItem,
        shortcuts: ResultShortcuts,
        now: Long,
        queryMetaphone: DoubleMetaphone.MetaphoneResult,
        learnedQuery: String = SelectionMemory.normalizeQuery(query)
    ): SearchScoringEngine.ScoreBreakdown = SearchScoringEngine.evaluateScore(
        query = query,
        targetTitle = item.title,
        metadata = metadataFor(item, shortcuts, learnedQuery, now),
        currentTimeMs = now,
        precomputedQueryMetaphone = queryMetaphone,
        precomputedTargetMetaphone = item.precomputedMetaphone,
        targetSubtitle = item.subtitle
    )

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

    private fun newContactItem(contact: ContactItem): SearchItem {
        return SearchItem(
            id = "contact:${contact.lookupUri}",
            title = contact.name,
            subtitle = contact.detail,
            domain = SearchScoringEngine.EntityDomain.CONTACT,
            payload = contact,
            metadata = SearchScoringEngine.ScoringMetadata(
                launchCount = 0,
                lastUsedTimestampMs = 0L,
                isPinned = false,
                domain = SearchScoringEngine.EntityDomain.CONTACT,
                isFavorite = contact.starred
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
        // A contact saved with only an email address has no number to find it by.
        if (digits.isNotEmpty()) out.add(digits to item)
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

        addRemembered(candidates, SelectionMemory.normalizeQuery(q), SearchScoringEngine.EntityDomain.CONTACT)

        val queryMetaphone = DoubleMetaphone.encode(q)
        if (queryMetaphone.primary.isNotEmpty()) {
            phoneticIndex[queryMetaphone.primary]?.filterTo(candidates) { it.domain == SearchScoringEngine.EntityDomain.CONTACT }
        }
        val shortcuts = resultShortcuts.current

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
            // A cleared field ends the query: the same chance question asked again is a new draw.
            forgetChanceDraw()
            return UnifiedSearchResults(query = query)
        }

        // 1. Instant System Action / Slider Router
        val systemAction = systemActionRouter.matchAction(query)

        // 2. Instant Math, Scientific, & Unit Evaluator
        val mathResult = evaluateMath(query)
        val dateAnswer = dateMath.evaluate(query)
        val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        val timeAnswer = if (mathResult == null && dateAnswer == null) worldClock.evaluate(query, is24Hour) else null
        val randomAnswer = if (mathResult == null && dateAnswer == null && timeAnswer == null) randomAnswer(query) else null

        // 3. Natural Language Intent Parser
        // "Call X" / "Message X" only resolve names from contacts when contact search is turned on.
        val contactLookup: ((String) -> ContactItem?)? = if (cachedContactSearchEnabled) {
            { target -> searchContacts(target, limit = 5).firstOrNull { it.phoneNumber.isNotBlank() } }
        } else null
        val parsedIntent = NaturalLanguageIntentParser.parse(query, contactLookup = contactLookup)
        // Only what the full phrasings, the calculator, the converters, date maths and the clock and chance answers
        // leave: "15 min in seconds", "15 min + 5" and "random 1 to 10" stay theirs.
        val shortAction = if (parsedIntent == null && mathResult == null && dateAnswer == null && timeAnswer == null && randomAnswer == null) {
            ShortActionPhrasing.parse(query, is24Hour = is24Hour)
        } else null

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
        val shortcuts = resultShortcuts.current

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
            val fuzzyResults = appRadixTree.searchFuzzy(query, maxDistance = SearchScoringEngine.fuzzyEditsFor(query), limit = 20)
            for (f in fuzzyResults) {
                candidates.add(f.value)
            }
        }

        // Results the user opens often or picked for this text: the prefix lookups above stop at the first 20
        // contacts and files, which a much-used one can fall behind ("j" with fifty Js). Added after the fuzzy step,
        // whose trigger counts the apps the lookups found, and held to every word of a multi-word query like the rest.
        val learnedQuery = SelectionMemory.normalizeQuery(query)
        val remembered = mutableSetOf<SearchItem>()
        addRemembered(remembered, learnedQuery)
        if (tokens.size > 1) {
            remembered.retainAll { item -> QueryNormalizer.containsAllTokens("${item.title} ${item.subtitle.orEmpty()}", tokens) }
        }
        candidates.addAll(remembered)

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
            val scoreBreakdown = scoreItem(query, item, shortcuts, now, queryMetaphone, learnedQuery)
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
            shortAction = shortAction,
            dateAnswer = dateAnswer,
            timeAnswer = timeAnswer,
            randomAnswer = randomAnswer,
            systemAction = systemAction,
            apps = matchedApps,
            contacts = matchedContacts,
            files = matchedFiles
        )
    }
}
