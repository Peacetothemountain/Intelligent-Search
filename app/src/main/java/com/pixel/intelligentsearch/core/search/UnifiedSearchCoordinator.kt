package com.pixel.intelligentsearch.core.search

import android.content.Context
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.AppShortcutItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.FileItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
    val systemActionRouter: SystemActionRouter
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
    private val contactRadixTree = RadixTreeIndex<SearchItem>()
    private val shortcutRadixTree = RadixTreeIndex<SearchItem>()
    private val fileRadixTree = RadixTreeIndex<SearchItem>()
    private val phoneticIndex = ConcurrentHashMap<String, MutableSet<SearchItem>>()
    private val itemRegistry = ConcurrentHashMap<String, SearchItem>()

    private val prefs = context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)
    @Volatile private var cachedAppWeightMul: Float = 0.5f
    @Volatile private var cachedContactWeightMul: Float = 0.5f
    @Volatile private var cachedFileWeightMul: Float = 0.5f

    private val prefChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "search_weight_apps" -> updateAppWeight()
            "search_weight_contacts" -> updateContactWeight()
            "search_weight_files" -> updateFileWeight()
        }
    }

    init {
        updateAppWeight()
        updateContactWeight()
        updateFileWeight()
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
    }

    private fun updateAppWeight() {
        cachedAppWeightMul = (prefs.getInt("search_weight_apps", 50) / 100f).coerceAtLeast(0.01f)
    }

    private fun updateContactWeight() {
        cachedContactWeightMul = (prefs.getInt("search_weight_contacts", 50) / 100f).coerceAtLeast(0.01f)
    }

    private fun updateFileWeight() {
        cachedFileWeightMul = (prefs.getInt("search_weight_files", 50) / 100f).coerceAtLeast(0.01f)
    }

    // ---------------------------------------------------------------------------------------------
    // INGESTION & INDEXING API
    // ---------------------------------------------------------------------------------------------

    /**
     * Indexes an installed application into the Radix Tree and phonetic registry.
     */
    fun indexApp(app: AppItem, launchCount: Int = 0, lastUsedMs: Long = 0L, isPinned: Boolean = false) {
        val entityId = "app:${app.packageName}:${app.userHandle?.hashCode() ?: 0}:${app.profileType}"
        val metadata = SearchScoringEngine.ScoringMetadata(
            launchCount = launchCount,
            lastUsedTimestampMs = lastUsedMs,
            isPinned = isPinned,
            domain = SearchScoringEngine.EntityDomain.APPLICATION
        )
        val metaphone = DoubleMetaphone.encode(app.name)
        val searchItem = SearchItem(
            id = entityId,
            title = app.name,
            subtitle = app.packageName,
            domain = SearchScoringEngine.EntityDomain.APPLICATION,
            payload = app,
            metadata = metadata,
            precomputedMetaphone = metaphone
        )
        itemRegistry[searchItem.id] = searchItem

        // Index full title, normalized title, and package name
        appRadixTree.insert(app.name, searchItem)
        val normalizedTitle = QueryNormalizer.normalize(app.name)
        if (normalizedTitle.isNotEmpty() && !normalizedTitle.equals(app.name, ignoreCase = true)) {
            appRadixTree.insert(normalizedTitle, searchItem)
        }
        appRadixTree.insert(app.packageName, searchItem)

        // Index acronym / initials (e.g. "yt" -> "YouTube", "gpm" -> "Google Play Music")
        val initials = QueryNormalizer.extractInitials(app.name)
        if (initials.isNotEmpty()) {
            appRadixTree.insert(initials, searchItem)
        }
        val words = QueryNormalizer.tokenize(app.name)
        for (w in words) {
            appRadixTree.insert(w, searchItem)
        }

        // Index phonetic codes
        if (metaphone.primary.isNotEmpty()) {
            phoneticIndex.getOrPut(metaphone.primary) { ConcurrentHashMap.newKeySet() }.add(searchItem)
        }
        if (metaphone.alternate.isNotEmpty()) {
            phoneticIndex.getOrPut(metaphone.alternate) { ConcurrentHashMap.newKeySet() }.add(searchItem)
        }
    }

    /**
     * Indexes a contact into the Radix Tree and phonetic inverted index.
     */
    fun indexContact(contact: ContactItem, lastUsedMs: Long = 0L) {
        val metadata = SearchScoringEngine.ScoringMetadata(
            launchCount = 0,
            lastUsedTimestampMs = lastUsedMs,
            isPinned = false,
            domain = SearchScoringEngine.EntityDomain.CONTACT
        )
        val nameMetaphone = DoubleMetaphone.encode(contact.name)
        val searchItem = SearchItem(
            id = "contact:${contact.lookupUri}",
            title = contact.name,
            subtitle = contact.phoneNumber,
            domain = SearchScoringEngine.EntityDomain.CONTACT,
            payload = contact,
            metadata = metadata,
            precomputedMetaphone = nameMetaphone
        )
        itemRegistry[searchItem.id] = searchItem

        // Index full name, normalized name, and phone number
        contactRadixTree.insert(contact.name, searchItem)
        val normalizedContact = QueryNormalizer.normalize(contact.name)
        if (normalizedContact.isNotEmpty() && !normalizedContact.equals(contact.name, ignoreCase = true)) {
            contactRadixTree.insert(normalizedContact, searchItem)
        }
        contactRadixTree.insert(contact.phoneNumber.filter { it.isDigit() }, searchItem)

        // Index individual name tokens (First name, Last name)
        val tokens = QueryNormalizer.tokenize(contact.name)
        for (token in tokens) {
            contactRadixTree.insert(token, searchItem)
            val tokenMetaphone = DoubleMetaphone.encode(token)
            if (tokenMetaphone.primary.isNotEmpty()) {
                phoneticIndex.getOrPut(tokenMetaphone.primary) { ConcurrentHashMap.newKeySet() }.add(searchItem)
            }
            if (tokenMetaphone.alternate.isNotEmpty()) {
                phoneticIndex.getOrPut(tokenMetaphone.alternate) { ConcurrentHashMap.newKeySet() }.add(searchItem)
            }
        }

        // Index full name phonetics
        if (nameMetaphone.primary.isNotEmpty()) {
            phoneticIndex.getOrPut(nameMetaphone.primary) { ConcurrentHashMap.newKeySet() }.add(searchItem)
        }
    }

    /**
     * Indexes an app shortcut.
     */
    fun indexShortcut(shortcut: AppShortcutItem) {
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
        itemRegistry[searchItem.id] = searchItem

        shortcutRadixTree.insert(shortcut.shortLabel, searchItem)
        if (shortcut.longLabel.isNotBlank()) {
            shortcutRadixTree.insert(shortcut.longLabel, searchItem)
        }
    }

    /**
     * Indexes a local file.
     */
    fun indexFile(file: FileItem, modifiedMs: Long = 0L) {
        val metadata = SearchScoringEngine.ScoringMetadata(
            lastUsedTimestampMs = modifiedMs,
            domain = SearchScoringEngine.EntityDomain.FILE
        )
        val metaphone = DoubleMetaphone.encode(file.name)
        val searchItem = SearchItem(
            id = "file:${file.path}",
            title = file.name,
            subtitle = file.path,
            domain = SearchScoringEngine.EntityDomain.FILE,
            payload = file,
            metadata = metadata,
            precomputedMetaphone = metaphone
        )
        itemRegistry[searchItem.id] = searchItem

        fileRadixTree.insert(file.name, searchItem)
    }

    /**
     * Batch indexes an entire list of apps atomically using insertBatch.
     */
    fun bulkIndexApps(apps: List<AppItem>) {
        val pairs = ArrayList<Pair<String, SearchItem>>(apps.size * 5)
        for (app in apps) {
            val entityId = "app:${app.packageName}:${app.userHandle?.hashCode() ?: 0}:${app.profileType}"
            val metadata = SearchScoringEngine.ScoringMetadata(
                domain = SearchScoringEngine.EntityDomain.APPLICATION
            )
            val metaphone = DoubleMetaphone.encode(app.name)
            val searchItem = SearchItem(
                id = entityId,
                title = app.name,
                subtitle = app.packageName,
                domain = SearchScoringEngine.EntityDomain.APPLICATION,
                payload = app,
                metadata = metadata,
                precomputedMetaphone = metaphone
            )
            itemRegistry[searchItem.id] = searchItem

            pairs.add(app.name to searchItem)
            val normalizedTitle = QueryNormalizer.normalize(app.name)
            if (normalizedTitle.isNotEmpty() && !normalizedTitle.equals(app.name, ignoreCase = true)) {
                pairs.add(normalizedTitle to searchItem)
            }
            pairs.add(app.packageName to searchItem)

            val initials = QueryNormalizer.extractInitials(app.name)
            if (initials.isNotEmpty()) {
                pairs.add(initials to searchItem)
            }
            val words = QueryNormalizer.tokenize(app.name)
            for (w in words) {
                pairs.add(w to searchItem)
            }

            if (metaphone.primary.isNotEmpty()) {
                phoneticIndex.getOrPut(metaphone.primary) { ConcurrentHashMap.newKeySet() }.add(searchItem)
            }
            if (metaphone.alternate.isNotEmpty()) {
                phoneticIndex.getOrPut(metaphone.alternate) { ConcurrentHashMap.newKeySet() }.add(searchItem)
            }
        }
        appRadixTree.insertBatch(pairs)
    }

    /**
     * Clears all in-memory indices.
     */
    fun clearIndex() {
        appRadixTree.clear()
        contactRadixTree.clear()
        shortcutRadixTree.clear()
        fileRadixTree.clear()
        phoneticIndex.clear()
        itemRegistry.clear()
    }

    /**
     * Clears only contact entities from in-memory indices before re-indexing.
     */
    fun clearContacts() {
        contactRadixTree.clear()
        phoneticIndex.values.forEach { set ->
            set.removeIf { it.domain == SearchScoringEngine.EntityDomain.CONTACT }
        }
        itemRegistry.entries.removeIf { it.value.domain == SearchScoringEngine.EntityDomain.CONTACT }
    }

    // ---------------------------------------------------------------------------------------------
    // HIGH-PERFORMANCE SEARCH PIPELINES (< 2ms Tier 0 / < 5ms Full)
    // ---------------------------------------------------------------------------------------------

    /**
     * Tier 0 Instant Search (< 2ms) executed synchronously on keystrokes.
     * Evaluates instant calculators, system toggles/actions, and Radix Tree app prefix lookups.
     */
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
        val directMatches = appRadixTree.searchPrefix(query, limit = 20)
        candidates.addAll(directMatches)

        val normalized = QueryNormalizer.normalize(query)
        if (normalized.isNotEmpty() && !normalized.equals(query, ignoreCase = true)) {
            val normMatches = appRadixTree.searchPrefix(normalized, limit = 20)
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
        val scoredApps = ArrayList<ScoredCandidate<AppItem>>(candidates.size)
        for (item in candidates) {
            val score = SearchScoringEngine.evaluateScore(
                query = query,
                targetTitle = item.title,
                metadata = item.metadata,
                currentTimeMs = now,
                domainWeightMultiplier = 1.0f,
                precomputedQueryMetaphone = queryMetaphone,
                precomputedTargetMetaphone = item.precomputedMetaphone
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
        appRadixTree.searchPrefix(q, limit = limit * 3).filterTo(candidates) { it.domain == SearchScoringEngine.EntityDomain.APPLICATION }

        val normalized = QueryNormalizer.normalize(q)
        if (normalized.isNotEmpty() && !normalized.equals(q, ignoreCase = true)) {
            appRadixTree.searchPrefix(normalized, limit = limit * 3).filterTo(candidates) { it.domain == SearchScoringEngine.EntityDomain.APPLICATION }
        }

        val now = System.currentTimeMillis()
        val queryMetaphone = DoubleMetaphone.encode(q)
        val scoredApps = ArrayList<ScoredCandidate<AppItem>>(candidates.size)
        for (item in candidates) {
            val score = SearchScoringEngine.evaluateScore(
                query = q,
                targetTitle = item.title,
                metadata = item.metadata,
                currentTimeMs = now,
                domainWeightMultiplier = 1.0f,
                precomputedQueryMetaphone = queryMetaphone,
                precomputedTargetMetaphone = item.precomputedMetaphone
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
                domainWeightMultiplier = 1.0f,
                precomputedQueryMetaphone = queryMetaphone,
                precomputedTargetMetaphone = item.precomputedMetaphone
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
        val scoredShortcuts = ArrayList<ScoredCandidate<AppShortcutItem>>(candidates.size)
        for (item in candidates) {
            val score = SearchScoringEngine.evaluateScore(
                query = q,
                targetTitle = item.title,
                metadata = item.metadata,
                currentTimeMs = now,
                domainWeightMultiplier = 1.0f,
                precomputedQueryMetaphone = queryMetaphone,
                precomputedTargetMetaphone = item.precomputedMetaphone
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
                domainWeightMultiplier = 1.0f,
                precomputedQueryMetaphone = queryMetaphone,
                precomputedTargetMetaphone = item.precomputedMetaphone
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
        shortcutRadixTree.removeIf { (it.payload as? AppShortcutItem)?.packageName == packageName }
        itemRegistry.entries.removeIf { it.value.id.startsWith("app:$packageName") || it.value.id.startsWith("shortcut:$packageName") }
    }

    suspend fun executeSearch(rawQuery: String): UnifiedSearchResults = withContext(Dispatchers.Default) {
        executeSearchInternal(rawQuery)
    }

    fun executeSearchSync(rawQuery: String): UnifiedSearchResults {
        return executeSearchInternal(rawQuery)
    }

    private fun executeSearchInternal(rawQuery: String): UnifiedSearchResults {
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
        val parsedIntent = NaturalLanguageIntentParser.parse(query)

        // 4. Candidate Retrieval via Partitioned Radix Tree Prefix Search (< 0.5ms)
        val candidates = mutableSetOf<SearchItem>()
        candidates.addAll(appRadixTree.searchPrefix(query, limit = 40))
        candidates.addAll(contactRadixTree.searchPrefix(query, limit = 20))
        candidates.addAll(shortcutRadixTree.searchPrefix(query, limit = 20))
        candidates.addAll(fileRadixTree.searchPrefix(query, limit = 20))

        val normalized = QueryNormalizer.normalize(query)
        if (normalized.isNotEmpty() && !normalized.equals(query, ignoreCase = true)) {
            candidates.addAll(appRadixTree.searchPrefix(normalized, limit = 40))
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
            // Conjunctive filter: ensure matched candidates contain all query tokens
            candidates.retainAll { item ->
                QueryNormalizer.containsAllTokens(item.title, tokens) ||
                (item.subtitle != null && QueryNormalizer.containsAllTokens(item.subtitle, tokens))
            }
        }

        // 5. Phonetic Candidate Retrieval via Double Metaphone (< 0.2ms)
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

        // 6. Typo-Tolerant Fuzzy Search if Candidate Pool is Small (< 1.5ms)
        if (candidates.size < 8 && query.length >= 3) {
            val fuzzyResults = appRadixTree.searchFuzzy(query, maxDistance = 2, limit = 20)
            for (f in fuzzyResults) {
                candidates.add(f.value)
            }
        }

        // 7. Multi-Factor Relevance Scoring Matrix
        val now = System.currentTimeMillis()
        val appWeightMul = cachedAppWeightMul
        val contactWeightMul = cachedContactWeightMul
        val fileWeightMul = cachedFileWeightMul

        val rankedResults = ArrayList<RankedSearchResult>(candidates.size)
        for (item in candidates) {
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
                precomputedTargetMetaphone = item.precomputedMetaphone
            )
            if (scoreBreakdown.totalScore > 0f) {
                rankedResults.add(RankedSearchResult(item, scoreBreakdown))
            }
        }
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
