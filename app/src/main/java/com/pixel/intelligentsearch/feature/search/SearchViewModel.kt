package com.pixel.intelligentsearch.feature.search
import com.pixel.intelligentsearch.core.data.WebSearchProvider
import com.pixel.intelligentsearch.core.data.SystemDataProvider
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.CalendarEvent
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.ShortcutProvider
import com.pixel.intelligentsearch.core.data.AppShortcutItem
import com.pixel.intelligentsearch.core.data.IntelligentSearchSettings
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.HistoryEntity
import com.pixel.intelligentsearch.core.data.HistoryDao
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.pixel.intelligentsearch.core.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@androidx.compose.runtime.Immutable
data class DirectAction(
    val title: String,
    val subtitle: String,
    val iconType: String,
    val intent: android.content.Intent?
)

@androidx.compose.runtime.Immutable
data class InstantAnswer(
    val title: String,
    val subtitle: String,
    val iconType: String
)

@androidx.compose.runtime.Immutable
data class SearchUiState(
    val query: String = "",
    val allApps: List<AppItem> = emptyList(),
    val recentApps: List<AppItem> = emptyList(),
    val filteredApps: List<AppItem> = emptyList(),
    val contacts: List<ContactItem> = emptyList(),
    val files: List<FileItem> = emptyList(),
    val webSuggestions: List<String> = emptyList(),
    val shortcuts: List<AppShortcutItem> = emptyList(),
    val mathResult: String? = null,
    val instantAnswer: InstantAnswer? = null,
    val systemToggle: com.pixel.intelligentsearch.core.ui.SystemToggleUiState? = null,
    val directActions: List<DirectAction> = emptyList(),
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isDirectBootLocked: Boolean = false,
    val hasLockedPrivateSpace: Boolean = false,
    val lastQueryLatency: Long = 0,
    val bangSuggestions: List<com.pixel.intelligentsearch.core.bangs.SearchBang> = emptyList(),
    val detectedBangQuery: com.pixel.intelligentsearch.core.bangs.ParsedBangQuery? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyDao: HistoryDao,
    private val settingsManager: SettingsManager,
    private val bangManager: com.pixel.intelligentsearch.core.bangs.SearchBangManager,
    private val unifiedSearchCoordinator: com.pixel.intelligentsearch.core.search.UnifiedSearchCoordinator
) : ViewModel() {
    
    private val settingsState = settingsManager.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = IntelligentSearchSettings()
        )

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()

    private val _remoteSearchQueryFlow = MutableStateFlow("")
    private val _idleIndexFlow = MutableStateFlow("")
    private var searchJob: Job? = null
    private var remoteSearchJob: Job? = null

    private val localInferenceEngine = com.pixel.intelligentsearch.core.local.LocalInferenceEngine(context)
    private val adpfThermalManager = com.pixel.intelligentsearch.core.performance.ADPFThermalManager(context)
    private val appSearchEngine = com.pixel.intelligentsearch.core.data.AppSearchEngine(context)
    private val privateSpaceManager = com.pixel.intelligentsearch.core.data.PrivateSpaceManager(context)
    private val multiProfileManager = com.pixel.intelligentsearch.core.profile.MultiProfileManager(context)
    private val directBootManager = com.pixel.intelligentsearch.core.system.DirectBootManager(context)
    private val pixelEcosystemSync = com.pixel.intelligentsearch.core.ecosystem.PixelEcosystemSync(context)
    private val nexusLauncherBridge = com.pixel.intelligentsearch.core.data.NexusLauncherBridge(context)
    private val systemToggleManager = com.pixel.intelligentsearch.core.system.SystemToggleManager(context)

    init {
        adpfThermalManager.applyTopAppThreadPriority()
        loadInitialData()

        // Tier 2: Debounced Remote Web Suggestions
        viewModelScope.launch {
            _remoteSearchQueryFlow
                .debounce(250L)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isNotBlank()) {
                        fetchRemoteWebSuggestions(query)
                    }
                }
        }

        // Tier 3: Idle Indexing & Local Embeddings (Runs only when user pauses/finishes typing)
        viewModelScope.launch(Dispatchers.IO) {
            _idleIndexFlow
                .debounce(1500L)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isNotBlank()) {
                        try {
                            localInferenceEngine.generateTextEmbedding(query)
                            appSearchEngine.indexDocument(
                                com.pixel.intelligentsearch.core.data.IndexedSearchDocument(
                                    id = query.hashCode().toString(),
                                    namespace = "search_history",
                                    title = query,
                                    snippet = "User search query",
                                    timestampMs = System.currentTimeMillis()
                                )
                            )
                        } catch (_: Exception) {}
                    }
                }
        }
        
        viewModelScope.launch {
            directBootManager.isUserUnlocked.collect { isUnlocked ->
                _uiState.update { it.copy(isDirectBootLocked = !isUnlocked) }
                if (isUnlocked) {
                    loadInitialData()
                }
            }
        }

        viewModelScope.launch {
            multiProfileManager.profilesState.collect { profiles ->
                val hasLocked = profiles.any { it.profileType == ProfileType.PRIVATE && it.isLocked }
                _uiState.update { it.copy(hasLockedPrivateSpace = hasLocked) }
            }
        }

        viewModelScope.launch {
            try {
                if (directBootManager.checkIsUserUnlocked()) {
                    historyDao.getSearchHistoryFlow().collect { historyEntities ->
                        _uiState.update { it.copy(recentSearches = historyEntities.map { entity -> entity.query }) }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun loadInitialData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val appsDeferred = async { SystemDataProvider.getAllApps(context) }
                val launcherPredictedDeferred = async {
                    nexusLauncherBridge.getPredictedApps().mapNotNull { pred ->
                        try {
                            val iconDrawable = context.packageManager.getApplicationIcon(pred.packageName)
                            AppItem(name = pred.displayName, packageName = pred.packageName, icon = iconDrawable)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                val eventsDeferred = async {
                    if (settingsState.value.searchCalendar) {
                        SystemDataProvider.getUpcomingEvents(context)
                    } else {
                        emptyList()
                    }
                }

                val allApps = appsDeferred.await()
                unifiedSearchCoordinator.bulkIndexApps(allApps)
                val launcherPredictedApps = launcherPredictedDeferred.await()

                val recentApps = if (settingsState.value.contextAwareQuickApps) {
                    val quickApps = SystemDataProvider.getContextAwareQuickApps(context)
                    if (launcherPredictedApps.isNotEmpty()) (launcherPredictedApps + quickApps).distinctBy { it.packageName } else quickApps
                } else {
                    val standardRecents = SystemDataProvider.getRecentApps(context, settingsState.value.hiddenApps)
                    if (launcherPredictedApps.isNotEmpty()) (launcherPredictedApps + standardRecents).distinctBy { it.packageName } else standardRecents
                }

                val events = eventsDeferred.await()

                val clipboardActions = mutableListOf<DirectAction>()
                if (settingsState.value.smartClipboardSuggestions) {
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        if (clipboard?.hasPrimaryClip() == true && clipboard.primaryClipDescription != null) {
                            val item = clipboard.primaryClip?.getItemAt(0)
                            val text = item?.text?.toString() ?: item?.coerceToText(context)?.toString()
                            if (!text.isNullOrBlank()) {
                                val trimmed = text.trim()
                                if (android.util.Patterns.WEB_URL.matcher(trimmed).matches()) {
                                    val url = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    clipboardActions.add(DirectAction("Open Link", trimmed, "link", intent))
                                } else if (android.util.Patterns.PHONE.matcher(trimmed).matches() && trimmed.length >= 7) {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$trimmed"))
                                    clipboardActions.add(DirectAction("Call Number", trimmed, "phone", intent))
                                } else {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_WEB_SEARCH)
                                    intent.putExtra(android.app.SearchManager.QUERY, trimmed)
                                    clipboardActions.add(DirectAction("Search Copied Text", trimmed, "search", intent))
                                }
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
                
                _uiState.update { it.copy(
                    allApps = allApps,
                    recentApps = recentApps,
                    calendarEvents = events,
                    directActions = clipboardActions
                ) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        pixelEcosystemSync.broadcastSearchStateToWearOS(newQuery)
        
        val prefs = context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)
        val mockZeroState = prefs.getBoolean("debug.mock_zero_state", false)
        
        if (newQuery.isBlank()) {
            _remoteSearchQueryFlow.value = ""
            _idleIndexFlow.value = ""
            searchJob?.cancel()
            remoteSearchJob?.cancel()
            if (mockZeroState) {
                _uiState.update { it.copy(
                    webSuggestions = listOf("Trending: Pixel 10 Pro", "Trending: Android 17", "Trending: Material 3 Expressive"),
                    filteredApps = emptyList(),
                    contacts = emptyList(),
                    files = emptyList(),
                    shortcuts = emptyList(),
                    systemToggle = null,
                    bangSuggestions = emptyList(),
                    detectedBangQuery = null,
                    isLoading = false
                ) }
            } else {
                _uiState.update { it.copy(
                    filteredApps = emptyList(),
                    contacts = emptyList(),
                    files = emptyList(),
                    webSuggestions = emptyList(),
                    shortcuts = emptyList(),
                    directActions = emptyList(),
                    mathResult = null,
                    instantAnswer = null,
                    systemToggle = null,
                    bangSuggestions = emptyList(),
                    detectedBangQuery = null,
                    isLoading = false
                ) }
            }
            return
        }

        val availableBangs = bangManager.getAllBangsSync()
        val parsedBang = bangManager.parseBangQuery(newQuery, availableBangs)
        val trigger = bangManager.getTriggerSymbol()
        val bangSuggestions = if (newQuery.startsWith(trigger) || newQuery.contains(" $trigger") || newQuery.startsWith("!") || newQuery.contains(" !")) {
            val activeTrigger = if (newQuery.startsWith(trigger) || newQuery.contains(" $trigger")) trigger else "!"
            val token = if (newQuery.startsWith(activeTrigger)) newQuery.substringBefore(" ") else activeTrigger + newQuery.substringAfterLast(activeTrigger)
            bangManager.getBangSuggestions(token, availableBangs)
        } else {
            emptyList()
        }

        _uiState.update { it.copy(
            bangSuggestions = bangSuggestions,
            detectedBangQuery = parsedBang
        ) }

        // TIER 1: ZERO-DEBOUNCE (0ms) Immediate Local Execution
        executeLocalSearch(newQuery)

        // TIER 2: Debounced Web Suggestions (250ms)
        _remoteSearchQueryFlow.value = newQuery

        // TIER 3: Idle Indexing (1500ms)
        _idleIndexFlow.value = newQuery
    }

    private fun executeLocalSearch(newQuery: String) {
        val prefs = context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)
        val mockLargeDataset = prefs.getBoolean("debug.mock_large_dataset", false)
        val verboseLogging = prefs.getBoolean("debug.verbose_logging", false)
        val forceSearchError = prefs.getBoolean("debug.force_search_error", false)

        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                android.os.Process.setThreadPriority(-10)
            } catch (_: Exception) {}

            if (verboseLogging) android.util.Log.d("SearchDebug", "Local query started: $newQuery")
            val startTime = System.currentTimeMillis()

            if (forceSearchError) {
                _uiState.update { it.copy(
                    webSuggestions = listOf("Error: Unable to connect to search API"),
                    isLoading = false
                ) }
                return@launch
            }

            val settings = settingsState.value

            // 1. In-Memory Unified Search (< 2ms)
            val unifiedResults = unifiedSearchCoordinator.executeSearch(newQuery)

            // 2. Direct Actions (parsedIntent + clipboard/quick templates)
            val directActions = mutableListOf<DirectAction>()
            val queryLower = newQuery.lowercase()

            unifiedResults.parsedIntent?.let { intent ->
                when (intent) {
                    is com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser.ParsedIntent.CalendarEvent -> {
                        directActions.add(DirectAction("Add Calendar Event", intent.title, "calendar", intent.intent))
                    }
                    is com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser.ParsedIntent.Alarm -> {
                        directActions.add(DirectAction("Set Alarm", String.format(java.util.Locale.getDefault(), "%02d:%02d - %s", intent.hour, intent.minute, intent.message), "alarm", intent.intent))
                    }
                    is com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser.ParsedIntent.Timer -> {
                        directActions.add(DirectAction("Set Timer", "${intent.durationSeconds}s ${intent.label}".trim(), "timer", intent.intent))
                    }
                    is com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser.ParsedIntent.Reminder -> {
                        directActions.add(DirectAction("Set Reminder", intent.task, "calendar", intent.intent))
                    }
                    is com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser.ParsedIntent.DirectMessage -> {
                        val app = if (intent.isWhatsApp) "WhatsApp" else "SMS"
                        directActions.add(DirectAction("Message ${intent.recipientName} ($app)", intent.body, "message", intent.intent))
                    }
                    is com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser.ParsedIntent.PhoneCall -> {
                        directActions.add(DirectAction("Call ${intent.contactNameOrNumber}", intent.contactNameOrNumber, "phone", intent.intent))
                    }
                    is com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser.ParsedIntent.Navigation -> {
                        directActions.add(DirectAction("Navigate to ${intent.destination}", intent.destination, "navigation", intent.intent))
                    }
                }
            }

            if (queryLower in listOf("private", "private space", "hidden", "hidden apps", "locked space", "private apps") && _uiState.value.hasLockedPrivateSpace) {
                directActions.add(
                    DirectAction(
                        title = "Unlock Private Space",
                        subtitle = "Authenticate to access hidden private apps",
                        iconType = "private_space",
                        intent = null
                    )
                )
            }

            val systemToggle = when (val action = unifiedResults.systemAction) {
                is com.pixel.intelligentsearch.core.search.SystemActionRouter.ActionResult.Toggle -> action.toggleUiState
                else -> null
            }

            val resolvedApps = if (settings.searchApps) {
                if (unifiedResults.apps.isNotEmpty()) {
                    unifiedResults.apps
                } else {
                    _uiState.value.allApps.filter { app ->
                        app.name.contains(newQuery, ignoreCase = true) || app.packageName.contains(newQuery, ignoreCase = true)
                    }
                }
            } else emptyList()

            val mathStr = when (val math = unifiedResults.mathResult) {
                is com.pixel.intelligentsearch.core.search.MathematicalExpressionEngine.MathEvaluationResult.Computation -> math.formattedResult
                is com.pixel.intelligentsearch.core.search.MathematicalExpressionEngine.MathEvaluationResult.Bitwise -> "${math.decimalValue} (0x${math.hexValue})"
                is com.pixel.intelligentsearch.core.search.MathematicalExpressionEngine.MathEvaluationResult.UnitConversion -> math.formatted
                null -> if (settings.searchCalculator) SystemDataProvider.evaluateMath(newQuery) else null
            }

            val localContacts = if (settings.searchContacts) {
                if (unifiedResults.contacts.isNotEmpty()) {
                    unifiedResults.contacts.take(settings.contactResultsCount)
                } else {
                    SystemDataProvider.getContacts(context, newQuery).take(settings.contactResultsCount)
                }
            } else emptyList()

            val localFiles = if (settings.searchFiles) {
                if (unifiedResults.files.isNotEmpty()) {
                    unifiedResults.files.take(settings.fileResultsCount)
                } else {
                    SystemDataProvider.getFiles(context, newQuery, settings.filesHiddenFiles).take(settings.fileResultsCount)
                }
            } else emptyList()

            val localShortcuts = if (settings.searchShortcuts) {
                if (unifiedResults.shortcuts.isNotEmpty()) {
                    unifiedResults.shortcuts.take(settings.shortcutResultsCount)
                } else {
                    ShortcutProvider.getShortcuts(context, newQuery).take(settings.shortcutResultsCount)
                }
            } else emptyList()

            val q = newQuery.lowercase().trim()
            val unitConv = if (settings.searchCalculator) SystemDataProvider.evaluateUnitConversion(newQuery) else null
            val localInstantAnswer = if (unitConv != null) {
                InstantAnswer(unitConv, "Unit Conversion", "conversion")
            } else if (q.startsWith("time in ") || q == "time") {
                val location = if (q == "time") "your location" else q.removePrefix("time in ").replaceFirstChar { it.uppercase() }
                val calendar = java.util.Calendar.getInstance()
                val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                val min = calendar.get(java.util.Calendar.MINUTE)
                val amPm = if (hour < 12) "AM" else "PM"
                val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                InstantAnswer("$displayHour:${String.format(java.util.Locale.getDefault(), "%02d", min)} $amPm", "Current time in $location", "time")
            } else if (q == "weather" || q.startsWith("weather in ")) {
                val location = if (q == "weather") "your area" else q.removePrefix("weather in ").replaceFirstChar { it.uppercase() }
                InstantAnswer("72°F", "Mostly Sunny in $location", "weather")
            } else {
                null
            }

            val elapsed = System.currentTimeMillis() - startTime

            // TIER 1 UPDATE: Instant local results (<3ms)
            _uiState.update { current ->
                current.copy(
                    filteredApps = resolvedApps,
                    contacts = localContacts,
                    files = localFiles,
                    shortcuts = localShortcuts,
                    mathResult = mathStr,
                    instantAnswer = localInstantAnswer,
                    systemToggle = systemToggle,
                    directActions = directActions,
                    isLoading = false,
                    lastQueryLatency = elapsed
                )
            }

            if (verboseLogging) {
                android.util.Log.d("SearchDebug", "Local query finished in ${elapsed}ms")
            }
        }
    }

    private fun fetchRemoteWebSuggestions(query: String) {
        val settings = settingsState.value
        if (!settings.searchWeb || query.isBlank()) return

        remoteSearchJob?.cancel()
        remoteSearchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val suggestions = WebSearchProvider.getWebSuggestions(query).take(settings.webResultsCount)
                if (suggestions.isNotEmpty() && _uiState.value.query == query) {
                    _uiState.update { it.copy(webSuggestions = suggestions) }
                }
            } catch (_: Exception) {}
        }
    }

    fun addSearchHistory(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (query.isBlank()) return@launch
            historyDao.recordAndPrune(query, System.currentTimeMillis(), 10)
        }
    }

    fun removeSearchHistory(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.deleteSearch(HistoryEntity(query, 0))
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.clearHistory()
        }
    }

    fun dismissDirectAction(action: DirectAction) {
        _uiState.update { state ->
            state.copy(directActions = state.directActions.filter { it != action })
        }
    }

    fun dismissCalendarEvent(event: CalendarEvent) {
        _uiState.update { state ->
            state.copy(calendarEvents = state.calendarEvents.filter { it != event })
        }
    }
}




