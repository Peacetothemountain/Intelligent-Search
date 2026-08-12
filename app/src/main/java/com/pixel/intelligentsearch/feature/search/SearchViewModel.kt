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

data class DirectAction(
    val title: String,
    val subtitle: String,
    val iconType: String,
    val intent: android.content.Intent?
)

data class InstantAnswer(
    val title: String,
    val subtitle: String,
    val iconType: String
)

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
    val directActions: List<DirectAction> = emptyList(),
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val lastQueryLatency: Long = 0
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyDao: HistoryDao,
    private val settingsManager: SettingsManager
) : ViewModel() {
    
    private val settingsState = settingsManager.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = IntelligentSearchSettings()
        )

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()

    private var searchJob: Job? = null

    private val liteRtEngine = com.pixel.intelligentsearch.core.ai.LiteRtEngine(context)
    private val adpfThermalManager = com.pixel.intelligentsearch.core.performance.ADPFThermalManager(context)
    private val appSearchEngine = com.pixel.intelligentsearch.core.data.AppSearchEngine(context)
    private val privateSpaceManager = com.pixel.intelligentsearch.core.data.PrivateSpaceManager(context)
    private val pixelEcosystemSync = com.pixel.intelligentsearch.core.ecosystem.PixelEcosystemSync(context)
    private val nexusLauncherBridge = com.pixel.intelligentsearch.core.data.NexusLauncherBridge(context)

    init {
        adpfThermalManager.applyTopAppThreadPriority()
        loadInitialData()
        
        viewModelScope.launch {
            historyDao.getSearchHistoryFlow().collect { historyEntities ->
                _uiState.update { it.copy(recentSearches = historyEntities.map { entity -> entity.query }) }
            }
        }
    }

        fun loadInitialData() {
        viewModelScope.launch {
            try {
                kotlinx.coroutines.delay(400) // Delay to let enter transition animation complete
                val allApps = SystemDataProvider.getAllApps(context)
                
                val launcherPredictedApps = nexusLauncherBridge.getPredictedApps().mapNotNull { pred ->
                    try {
                        val iconDrawable = context.packageManager.getApplicationIcon(pred.packageName)
                        AppItem(name = pred.displayName, packageName = pred.packageName, icon = iconDrawable)
                    } catch (e: Exception) {
                        null
                    }
                }

                val recentApps = if (settingsState.value.contextAwareQuickApps) {
                    val quickApps = SystemDataProvider.getContextAwareQuickApps(context)
                    if (launcherPredictedApps.isNotEmpty()) (launcherPredictedApps + quickApps).distinctBy { it.packageName } else quickApps
                } else {
                    val standardRecents = SystemDataProvider.getRecentApps(context, settingsState.value.hiddenApps)
                    if (launcherPredictedApps.isNotEmpty()) (launcherPredictedApps + standardRecents).distinctBy { it.packageName } else standardRecents
                }

                val events = if (settingsState.value.searchCalendar) {
                    SystemDataProvider.getUpcomingEvents(context)
                } else {
                    emptyList()
                }

                val dockState = pixelEcosystemSync.getDeviceDockState()
                if (dockState.isDocked) {
                    android.util.Log.i("SearchViewModel", "Pixel Ecosystem Docked state active (Desk: ${dockState.isDeskDock}, Car: ${dockState.isCarDock})")
                }

                val profileState = privateSpaceManager.getProfileContainerState()
                if (profileState.hasPrivateSpace && profileState.isPrivateSpaceLocked) {
                    android.util.Log.i("SearchViewModel", "Android 15/16 Private Space container is locked.")
                }
                
                val clipboardActions = mutableListOf<DirectAction>()
                if (settingsState.value.smartClipboardSuggestions) {
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        if (clipboard.hasPrimaryClip() && clipboard.primaryClipDescription != null) {
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
        val simulateLatency = prefs.getBoolean("debug.simulate_latency", false)
        val mockLargeDataset = prefs.getBoolean("debug.mock_large_dataset", false)
        val verboseLogging = prefs.getBoolean("debug.verbose_logging", false)
        val mockZeroState = prefs.getBoolean("debug.mock_zero_state", false)
        val forceSearchError = prefs.getBoolean("debug.force_search_error", false)
        
        searchJob?.cancel()
        if (newQuery.isBlank()) {
            if (mockZeroState) {
                _uiState.update { it.copy(
                    webSuggestions = listOf("Trending: Pixel 10 Leaks", "Trending: Android 17", "Trending: AI Overviews"),
                    filteredApps = emptyList(),
                    contacts = emptyList(),
                    files = emptyList(),
                    shortcuts = emptyList(),
                    directActions = emptyList(),
                    mathResult = null,
                    instantAnswer = null
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
                    instantAnswer = null
                ) }
            }
            return
        }

        searchJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            if (verboseLogging) android.util.Log.d("SearchDebug", "Query started: $newQuery")
            val startTime = System.currentTimeMillis()

            val queryEmbedding = liteRtEngine.generateTextEmbedding(newQuery)
            appSearchEngine.indexDocument(
                com.pixel.intelligentsearch.core.data.IndexedSearchDocument(
                    id = newQuery.hashCode().toString(),
                    namespace = "search_history",
                    title = newQuery,
                    snippet = "User search query",
                    timestampMs = System.currentTimeMillis()
                )
            )
            
            delay(60)
            if (simulateLatency) {
                delay(2000)
            }
            
            if (forceSearchError) {
                _uiState.update { it.copy(
                    webSuggestions = listOf("Error: Unable to connect to search API"),
                    isLoading = false
                ) }
                return@launch
            }
            
            _uiState.update { it.copy(isLoading = true) }
            
            val settings = settingsState.value

            // Natural Language Parsing for Direct Actions
            val directActions = mutableListOf<DirectAction>()
            val queryLower = newQuery.lowercase()
            
            // 1. Task/Calendar Injection
            if (queryLower.startsWith("remind me to ")) {
                val task = newQuery.substring(13)
                val intent = android.content.Intent(android.content.Intent.ACTION_INSERT)
                    .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                    .putExtra(android.provider.CalendarContract.Events.TITLE, task)
                directActions.add(DirectAction("Set Reminder", task, "calendar", intent))
            } else if (queryLower.startsWith("add meeting ")) {
                val title = newQuery.substring(12)
                val intent = android.content.Intent(android.content.Intent.ACTION_INSERT)
                    .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                    .putExtra(android.provider.CalendarContract.Events.TITLE, title)
                directActions.add(DirectAction("Add Meeting", title, "calendar", intent))
            }
            
            // 2. Direct Messaging
            if (queryLower.startsWith("message ") || queryLower.startsWith("text ")) {
                val match = Regex("(?:message|text)\\s+(.+?)\\s*(?:on|:|,)\\s*(.+)").find(queryLower)
                if (match != null) {
                    val contactName = match.groupValues[1].trim()
                    val messageBody = match.groupValues[2].trim()
                    val matchingContact = SystemDataProvider.getContacts(context, contactName).firstOrNull()
                    val recipientNumber = matchingContact?.phoneNumber ?: ""
                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("smsto:$recipientNumber")
                        putExtra("sms_body", messageBody)
                    }
                    val label = if (matchingContact != null) "Message ${matchingContact.name}" else "Message $contactName"
                    directActions.add(DirectAction(label, messageBody, "message", intent))
                }
            }
            
                        val fuzzyKeywords = if (settings.appFuzzySearch) {
                SystemDataProvider.semanticTaxonomy.entries
                    .filter { it.key.contains(newQuery.lowercase()) || newQuery.lowercase().contains(it.key) }
                    .flatMap { it.value }
            } else emptyList()

            val filteredApps = if (settings.searchApps) {
                _uiState.value.allApps.filter { app ->
                    app.name.contains(newQuery, ignoreCase = true) || 
                    app.packageName.contains(newQuery, ignoreCase = true) ||
                    fuzzyKeywords.any { app.packageName.contains(it, ignoreCase = true) || app.name.contains(it, ignoreCase = true) }
                }
            } else emptyList()
            
            coroutineScope {
                val contactsDeferred = async {
                    val realContacts = if (settings.searchContacts) {
                        SystemDataProvider.getContacts(context, newQuery).take(settings.contactResultsCount)
                    } else emptyList()
                    
                    if (mockLargeDataset) {
                        realContacts + (1..settings.contactResultsCount).map { ContactItem("Mock Contact $it", "555-01$it", "mock_uri_$it") }
                    } else realContacts
                }
                
                val filesDeferred = async {
                    val realFiles = if (settings.searchFiles) {
                        SystemDataProvider.getFiles(context, newQuery, settings.filesHiddenFiles).take(settings.fileResultsCount)
                    } else emptyList()
                    
                    if (mockLargeDataset) {
                        realFiles + (1..settings.fileResultsCount).map { FileItem("Mock File $it.pdf", "/mock/path/$it", "application/pdf", "mock_uri_$it") }
                    } else realFiles
                }

                val webSuggestionsDeferred = async {
                    if (settings.searchWeb) {
                        WebSearchProvider.getWebSuggestions(newQuery).take(settings.webResultsCount)
                    } else {
                        emptyList()
                    }
                }

                val shortcutsDeferred = async {
                    if (settings.searchShortcuts) {
                        ShortcutProvider.getShortcuts(context, newQuery).take(settings.shortcutResultsCount)
                    } else {
                        emptyList()
                    }
                }

                val mathResultDeferred = async {
                    if (settings.searchCalculator) {
                        SystemDataProvider.evaluateMath(newQuery)
                    } else {
                        null
                    }
                }

                val instantAnswerDeferred = async {
                    val q = newQuery.lowercase().trim()
                    val unitConv = if (settings.searchCalculator) SystemDataProvider.evaluateUnitConversion(newQuery) else null
                    if (unitConv != null) {
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
                }

                val localContacts = contactsDeferred.await()
                val localFiles = filesDeferred.await()
                val localShortcuts = shortcutsDeferred.await()
                val localMathResult = mathResultDeferred.await()
                val localInstantAnswer = instantAnswerDeferred.await()
                
                _uiState.update {
                    it.copy(
                        filteredApps = filteredApps,
                        contacts = localContacts,
                        files = localFiles,
                        webSuggestions = emptyList(), // clear old suggestions while waiting for new ones
                        shortcuts = localShortcuts,
                        mathResult = localMathResult,
                        instantAnswer = localInstantAnswer,
                        isLoading = settings.searchWeb,
                        lastQueryLatency = System.currentTimeMillis() - startTime
                    )
                }

                if (settings.searchWeb) {
                    val webSuggestions = webSuggestionsDeferred.await()
                    _uiState.update {
                        it.copy(
                            webSuggestions = webSuggestions,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }

                val endTime = System.currentTimeMillis()
                val latency = endTime - startTime
                
                if (verboseLogging) {
                    android.util.Log.d("SearchDebug", "Query finished in ${latency}ms")
                }
            }
        }
    }

    fun addSearchHistory(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) return@launch
            historyDao.insertSearch(HistoryEntity(query, System.currentTimeMillis()))
            historyDao.pruneHistory(10)
        }
    }

    fun removeSearchHistory(query: String) {
        viewModelScope.launch {
            historyDao.deleteSearch(HistoryEntity(query, 0))
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
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




