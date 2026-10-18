package com.pixel.intelligentsearch.feature.search
import com.pixel.intelligentsearch.core.search.SpellingSuggester
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.CalendarEvent
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.AppShortcutItem
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

/** Endings far more often meant as file names than web domains, even where they are also real TLDs (.zip, .mov). */
private val FILE_EXTENSIONS = setOf(
    "txt", "pdf", "jpg", "jpeg", "png", "gif", "webp", "heic", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv",
    "zip", "rar", "7z", "apk", "mp3", "mp4", "mov", "mkv", "wav", "json", "xml", "kt", "java", "py", "js", "md", "log"
)

/** Whether [text] should be offered as "Go to website": a scheme, or a real domain that isn't a file name. */
private fun looksLikeWebAddress(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) return true
    if (' ' in trimmed || !android.util.Patterns.WEB_URL.matcher(trimmed).matches()) return false
    val host = trimmed.substringBefore('/').substringBefore('?').substringBefore(':')
    return '.' in host && host.substringAfterLast('.').lowercase() !in FILE_EXTENSIONS
}

/** Common places whose time zone is not named after them in the tz database (e.g. Asia/Kolkata). */
private val TIME_ZONE_ALIASES = mapOf(
    "utc" to "UTC", "gmt" to "GMT",
    "san francisco" to "America/Los_Angeles", "seattle" to "America/Los_Angeles", "la" to "America/Los_Angeles",
    "california" to "America/Los_Angeles", "las vegas" to "America/Los_Angeles", "portland" to "America/Los_Angeles",
    "boston" to "America/New_York", "miami" to "America/New_York", "washington" to "America/New_York",
    "atlanta" to "America/New_York", "philadelphia" to "America/New_York", "nyc" to "America/New_York",
    "dallas" to "America/Chicago", "houston" to "America/Chicago", "austin" to "America/Chicago",
    "salt lake city" to "America/Denver", "montreal" to "America/Toronto",
    "beijing" to "Asia/Shanghai", "china" to "Asia/Shanghai", "japan" to "Asia/Tokyo",
    "india" to "Asia/Kolkata", "delhi" to "Asia/Kolkata", "new delhi" to "Asia/Kolkata",
    "mumbai" to "Asia/Kolkata", "bangalore" to "Asia/Kolkata", "bengaluru" to "Asia/Kolkata",
    "uk" to "Europe/London", "england" to "Europe/London", "france" to "Europe/Paris",
    "germany" to "Europe/Berlin", "spain" to "Europe/Madrid", "italy" to "Europe/Rome",
    "korea" to "Asia/Seoul", "south korea" to "Asia/Seoul", "vietnam" to "Asia/Ho_Chi_Minh",
    "hanoi" to "Asia/Bangkok", "uae" to "Asia/Dubai", "abu dhabi" to "Asia/Dubai",
    "melbourne" to "Australia/Melbourne", "canberra" to "Australia/Sydney", "new zealand" to "Pacific/Auckland",
    "hawaii" to "Pacific/Honolulu", "alaska" to "America/Anchorage"
)

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
    val volumeSlider: com.pixel.intelligentsearch.core.search.SystemActionRouter.ActionResult.VolumeSlider? = null,
    val directActions: List<DirectAction> = emptyList(),
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val hasLockedPrivateSpace: Boolean = false,
    val lastQueryLatency: Long = 0,
    val bangSuggestions: List<com.pixel.intelligentsearch.core.bangs.SearchBang> = emptyList(),
    val detectedBangQuery: com.pixel.intelligentsearch.core.bangs.ParsedBangQuery? = null,
    /** The query with an obvious typo fixed, offered as "Did you mean"; null when nothing is misspelled. */
    val spellingSuggestion: String? = null
)

/** How long typing must pause before a query is spell checked for "Did you mean". */
private const val SPELLING_PAUSE_MS = 450L

/** The debug menu's Simulate Web Latency delay before web suggestions are fetched. */
private const val SIMULATED_LATENCY_MS = 2_000L

@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sources: com.pixel.intelligentsearch.core.data.DeviceSearchSources,
    private val historyDao: HistoryDao,
    private val settingsManager: SettingsManager,
    private val bangManager: com.pixel.intelligentsearch.core.bangs.SearchBangManager,
    private val unifiedSearchCoordinator: com.pixel.intelligentsearch.core.search.UnifiedSearchCoordinator,
    private val corpusIndexManager: com.pixel.intelligentsearch.core.search.CorpusIndexManager,
    private val nativeAppPredictionProvider: com.pixel.intelligentsearch.core.system.NativeAppPredictionProvider,
    private val multiProfileManager: com.pixel.intelligentsearch.core.profile.MultiProfileManager,
    private val privateSpaceManager: com.pixel.intelligentsearch.core.data.PrivateSpaceManager,
    private val systemToggleManager: com.pixel.intelligentsearch.core.system.SystemToggleManager,
    private val directBootManager: com.pixel.intelligentsearch.core.system.DirectBootManager
) : ViewModel() {
    
    private val settingsState = settingsManager.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = settingsManager.getInitialSettings()
        )

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()

    private val _voiceRequests = MutableStateFlow(0)
    /**
     * Bumps for each request to start voice search as the screen opens (the assistant gesture with Start Assistant
     * with Voice on). The screen claims each with [takeVoiceRequest], which answers true once per request, so a
     * recreated screen can't repeat one, and a fresh view model after process death can't swallow the next.
     */
    val voiceRequests: StateFlow<Int> = _voiceRequests.asStateFlow()
    private var voiceRequestPending = false
    fun requestVoice() {
        voiceRequestPending = true
        _voiceRequests.value++
    }
    fun takeVoiceRequest(): Boolean = voiceRequestPending.also { voiceRequestPending = false }

    private val _remoteSearchQueryFlow = MutableStateFlow("")
    private var searchJob: Job? = null

    /** Completed once the latest search's local results are published (before its "did you mean" pause). */
    private var localResultsReady: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    /** Waits for the local results of the latest [onQueryChanged], so a caller can act on the best match for it. */
    suspend fun awaitSearch() {
        val job = searchJob ?: return
        val ready = localResultsReady ?: return job.join()
        kotlinx.coroutines.selects.select<Unit> {
            ready.onAwait {}
            job.onJoin {}
        }
    }
    private var initialDataJob: Job? = null

    private val customPrefs get() = settingsManager.prefs
    private var initialClipboardActions: List<DirectAction> = emptyList()

    init {
        // initialize() already indexes contacts (when permitted); loadInitialData() refreshes them if stale.
        corpusIndexManager.initialize()

        // A query handed in with the launch (another app's search, a widget, the assistant) may arrive before the
        // index is built; search it again the moment the index is ready.
        viewModelScope.launch {
            corpusIndexManager.isIndexReady.first { it }
            val query = _uiState.value.query
            if (query.isNotBlank()) executeLocalSearch(query)
        }

        // Tier 2: Debounced Remote Web Suggestions (80ms human-pause debounce)
        viewModelScope.launch {
            _remoteSearchQueryFlow
                .debounce(80L)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isBlank()) {
                        _uiState.update { it.copy(webSuggestions = emptyList()) }
                    } else {
                        fetchRemoteWebSuggestions(query)
                    }
                }
        }

        viewModelScope.launch {
            directBootManager.isUserUnlocked.collectLatest { isUnlocked ->
                if (isUnlocked) {
                    loadInitialData()
                    try {
                        historyDao.getSearchHistoryFlow().collect { historyEntities ->
                            _uiState.update { it.copy(recentSearches = historyEntities.map { entity -> entity.query }) }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w("SearchViewModel", "Error collecting search history", e)
                    }
                }
            }
        }

        viewModelScope.launch {
            // Seed from the current state: the StateFlow replays it immediately, and starting from false made
            // every overlay open look like a fresh lock and force a full app + shortcut re-index.
            var previousLocked = multiProfileManager.profilesState.value
                .any { it.profileType == ProfileType.PRIVATE && it.isLocked }
            multiProfileManager.profilesState.collect { profiles ->
                val hasLocked = profiles.any { it.profileType == ProfileType.PRIVATE && it.isLocked }
                if (hasLocked) {
                    unifiedSearchCoordinator.purgePrivateProfileItems()
                    _uiState.update { current ->
                        current.copy(
                            hasLockedPrivateSpace = true,
                            allApps = current.allApps.filterNot { it.isPrivateProfile || it.profileType == ProfileType.PRIVATE },
                            recentApps = current.recentApps.filterNot { it.isPrivateProfile || it.profileType == ProfileType.PRIVATE },
                            filteredApps = current.filteredApps.filterNot { it.isPrivateProfile || it.profileType == ProfileType.PRIVATE }
                        )
                    }
                    if (!previousLocked) {
                        corpusIndexManager.reindexApps(forceRefresh = true)
                        corpusIndexManager.reindexShortcuts()
                    }
                } else {
                    _uiState.update { it.copy(hasLockedPrivateSpace = false) }
                    if (previousLocked) {
                        corpusIndexManager.reindexApps(forceRefresh = true)
                        corpusIndexManager.reindexShortcuts()
                    }
                }
                previousLocked = hasLocked
            }
        }

        // Live Hardware State Synchronization with System Toggles (Flashlight, Bluetooth, Wi-Fi, DND, Location, etc.)
        observeLiveSystemToggles()
    }

    private fun observeLiveSystemToggles() {
        viewModelScope.launch {
            systemToggleManager.isFlashlightOn.collect { torch ->
                _uiState.update { current ->
                    if (current.systemToggle?.id == "flashlight" && current.systemToggle.isEnabled != torch) {
                        current.copy(systemToggle = current.systemToggle.copy(
                            isEnabled = torch,
                            subtitle = systemToggleManager.getTorchStatusText(torch)
                        ))
                    } else current
                }
            }
        }

        viewModelScope.launch {
            systemToggleManager.isBluetoothEnabled.collect { bt ->
                _uiState.update { current ->
                    if (current.systemToggle?.id == "bluetooth" && current.systemToggle.isEnabled != bt) {
                        current.copy(systemToggle = current.systemToggle.copy(
                            isEnabled = bt,
                            subtitle = systemToggleManager.getBluetoothStatusText(bt)
                        ))
                    } else current
                }
            }
        }

        viewModelScope.launch {
            combine(systemToggleManager.isWifiConnected, systemToggleManager.wifiSsid) { wifi, _ ->
                _uiState.update { current ->
                    if (current.systemToggle?.id == "wifi") {
                        val newSubtitle = systemToggleManager.getWifiStatusText(wifi)
                        if (current.systemToggle.isEnabled != wifi || current.systemToggle.subtitle != newSubtitle) {
                            current.copy(systemToggle = current.systemToggle.copy(
                                isEnabled = wifi,
                                subtitle = newSubtitle
                            ))
                        } else current
                    } else current
                }
            }.collect()
        }

        viewModelScope.launch {
            systemToggleManager.isDndEnabled.collect { dnd ->
                _uiState.update { current ->
                    if (current.systemToggle?.id == "dnd" && current.systemToggle.isEnabled != dnd) {
                        current.copy(systemToggle = current.systemToggle.copy(
                            isEnabled = dnd,
                            subtitle = systemToggleManager.getDndStatusText(dnd)
                        ))
                    } else current
                }
            }
        }

        viewModelScope.launch {
            systemToggleManager.isLocationEnabled.collect { loc ->
                _uiState.update { current ->
                    if (current.systemToggle?.id == "location" && current.systemToggle.isEnabled != loc) {
                        current.copy(systemToggle = current.systemToggle.copy(
                            isEnabled = loc,
                            subtitle = systemToggleManager.getLocationStatusText(loc)
                        ))
                    } else current
                }
            }
        }

        viewModelScope.launch {
            systemToggleManager.isBatterySaverEnabled.collect { bat ->
                _uiState.update { current ->
                    if (current.systemToggle?.id == "battery" && current.systemToggle.isEnabled != bat) {
                        current.copy(systemToggle = current.systemToggle.copy(
                            isEnabled = bat,
                            subtitle = systemToggleManager.getBatterySaverStatusText(bat)
                        ))
                    } else current
                }
            }
        }

        viewModelScope.launch {
            systemToggleManager.isAirplaneModeEnabled.collect { air ->
                _uiState.update { current ->
                    if (current.systemToggle?.id == "airplane" && current.systemToggle.isEnabled != air) {
                        current.copy(systemToggle = current.systemToggle.copy(
                            isEnabled = air,
                            subtitle = systemToggleManager.getAirplaneModeStatusText(air)
                        ))
                    } else current
                }
            }
        }
    }

    fun refreshContacts() {
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            corpusIndexManager.reindexContactsIfStale()
        }
    }

    /** On each overlay open: picks up new files in the chosen folders and fresh app usage; both are throttled. */
    fun refreshStaleIndexes() {
        corpusIndexManager.reindexFilesIfStale()
        corpusIndexManager.refreshAppUsageIfStale()
    }

    fun loadInitialData() {
        refreshContacts()
        initialDataJob?.cancel()
        initialDataJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val appsDeferred = async { sources.allApps() }
                // The provider already tries Pixel Launcher predictions first, then usage stats. (Re-querying the
                // launcher here returned apps outside the indexed list, which skipped the hidden-app filtering.)
                val launcherPredictedDeferred = async { nativeAppPredictionProvider.getPredictedApps() }
                val eventsDeferred = async {
                    if (settingsState.value.searchCalendar) {
                        sources.upcomingEvents()
                    } else {
                        emptyList()
                    }
                }

                // Indexing belongs to CorpusIndexManager (serialized there); indexing this list here too raced it
                // and could re-add an app uninstalled in between.
                val allApps = appsDeferred.await()
                val launcherPredictedApps = launcherPredictedDeferred.await()

                val hiddenApps = settingsState.value.hiddenApps
                val recentApps = if (settingsState.value.contextAwareQuickApps) {
                    val quickApps = sources.contextAwareQuickApps(settingsState.value.hiddenApps).distinctBy { it.packageName }
                    if (launcherPredictedApps.isNotEmpty()) (launcherPredictedApps + quickApps).distinctBy { it.packageName } else quickApps
                } else {
                    val standardRecents = sources.recentApps(settingsState.value.hiddenApps).distinctBy { it.packageName }
                    if (launcherPredictedApps.isNotEmpty()) (launcherPredictedApps + standardRecents).distinctBy { it.packageName } else standardRecents
                }.filter { it.packageName !in hiddenApps } // launcher predictions know nothing about hidden apps

                val events = eventsDeferred.await()

                val clipboardActions = mutableListOf<DirectAction>()
                if (settingsState.value.smartClipboardSuggestions) {
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        // Password managers mark copied secrets sensitive; never show or search those.
                        val isSensitive = clipboard?.primaryClipDescription?.extras
                            ?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
                        val engine = settingsState.value.searchEngine
                        val isTor = com.pixel.intelligentsearch.core.security.SafeWebLaunch.isTorEngine(engine)
                        if (!isSensitive && clipboard?.hasPrimaryClip() == true && clipboard.primaryClipDescription != null) {
                            val item = clipboard.primaryClip?.getItemAt(0)
                            val text = item?.text?.toString() ?: item?.coerceToText(context)?.toString()
                            if (!text.isNullOrBlank()) {
                                val trimmed = text.trim()
                                if (android.util.Patterns.WEB_URL.matcher(trimmed).matches()) {
                                    // Same rules as a typed address: http(s) only, browsable apps only, Tor Browser under Tor.
                                    val launch = com.pixel.intelligentsearch.core.security.SafeWebLaunch.forAddress(context, trimmed, engine)
                                    if (launch is com.pixel.intelligentsearch.core.security.SafeWebLaunch.Launch.Open) {
                                        clipboardActions.add(DirectAction("Open Link", trimmed, "link", launch.intent))
                                    }
                                } else if (android.util.Patterns.PHONE.matcher(trimmed).matches() && trimmed.length >= 7) {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$trimmed"))
                                    clipboardActions.add(DirectAction("Call Number", trimmed, "phone", intent))
                                } else if (isTor) {
                                    // The generic web-search intent would reach a normal browser; search through Tor instead.
                                    val launch = com.pixel.intelligentsearch.core.security.SafeWebLaunch.torSearch(context, trimmed)
                                    if (launch is com.pixel.intelligentsearch.core.security.SafeWebLaunch.Launch.Open) {
                                        clipboardActions.add(DirectAction("Search Copied Text", trimmed, "search", launch.intent))
                                    }
                                } else {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_WEB_SEARCH)
                                    intent.putExtra(android.app.SearchManager.QUERY, trimmed)
                                    clipboardActions.add(DirectAction("Search Copied Text", trimmed, "search", intent))
                                }
                            }
                        }
                    } catch (e: Exception) { android.util.Log.w("IntelligentSearch", "Unexpected error", e) }
                }
                initialClipboardActions = clipboardActions
                _uiState.update { it.copy(
                    allApps = allApps,
                    recentApps = recentApps,
                    calendarEvents = events,
                    // Only the empty screen shows clipboard actions; a query's own actions ("call mom") stay.
                    directActions = if (it.query.isBlank()) clipboardActions else it.directActions
                ) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.w("IntelligentSearch", "Unexpected error", e)
            }
        }
    }

    fun onQueryChanged(newQuery: String) {
        val previousQuery = _uiState.value.query
        _uiState.update { it.copy(query = newQuery) }
        
        if (newQuery.isBlank()) {
            _remoteSearchQueryFlow.value = ""
            searchJob?.cancel()
            _uiState.update { it.copy(
                filteredApps = emptyList(),
                contacts = emptyList(),
                files = emptyList(),
                webSuggestions = emptyList(),
                shortcuts = emptyList(),
                directActions = if (settingsState.value.smartClipboardSuggestions) initialClipboardActions else emptyList(),
                mathResult = null,
                instantAnswer = null,
                systemToggle = null,
                volumeSlider = null,
                bangSuggestions = emptyList(),
                detectedBangQuery = null,
                spellingSuggestion = null
            ) }
            return
        }

        // TIER 0: Sub-2ms Synchronous Direct Prefix / Calculation / System Toggle Execution
        val tier0 = unifiedSearchCoordinator.executeTier0Search(newQuery)
        val tier0Toggle = when (val action = tier0.systemAction) {
            is com.pixel.intelligentsearch.core.search.SystemActionRouter.ActionResult.Toggle -> action.toggleUiState
            else -> null
        }
        val tier0Math = tier0.mathResult?.takeIf { settingsState.value.searchCalculator }?.let(::formatMathResult)

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

        _uiState.update { current ->
            current.copy(
                spellingSuggestion = null,
                bangSuggestions = bangSuggestions,
                detectedBangQuery = parsedBang,
                filteredApps = if (tier0.apps.isNotEmpty() && settingsState.value.searchApps) {
                    tier0.apps.filter { it.packageName !in settingsState.value.hiddenApps }
                } else current.filteredApps,
                mathResult = tier0Math,
                systemToggle = tier0Toggle,
                volumeSlider = tier0.systemAction as? com.pixel.intelligentsearch.core.search.SystemActionRouter.ActionResult.VolumeSlider
            )
        }

        // TIER 1: ZERO-DEBOUNCE (0ms) Immediate Local Execution across all corpuses
        executeLocalSearch(newQuery, previousQuery)

        // TIER 2: Debounced Web Suggestions (80ms human-pause debounce)
        if (settingsState.value.searchWeb) {
            _remoteSearchQueryFlow.value = newQuery
        } else {
            _remoteSearchQueryFlow.value = ""
            _uiState.update { it.copy(webSuggestions = emptyList()) }
        }
    }

    private fun executeLocalSearch(newQuery: String, previousQuery: String = "") {
        val prefs = customPrefs
        val verboseLogging = debugSwitch(SettingsManager.DEBUG_VERBOSE_LOGGING)
        val forceSearchError = debugSwitch(SettingsManager.DEBUG_FORCE_SEARCH_ERROR)
        val mockLargeDataset = debugSwitch(SettingsManager.DEBUG_MOCK_LARGE_DATASET)

        searchJob?.cancel()
        val ready = kotlinx.coroutines.CompletableDeferred<Unit>()
        localResultsReady = ready
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            if (verboseLogging) debugLog("Local query started (length ${newQuery.length})")
            val startTime = System.currentTimeMillis()

            if (forceSearchError) {
                _uiState.update { it.copy(
                    webSuggestions = listOf("Error: Unable to connect to search API")
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
                        directActions.add(DirectAction("Set Timer", intent.label, "timer", intent.intent))
                    }
                    is com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser.ParsedIntent.Reminder -> {
                        directActions.add(DirectAction("Set Reminder", intent.task, "calendar", intent.intent))
                    }
                    is com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser.ParsedIntent.DirectMessage -> {
                        val app = if (intent.isWhatsApp) "WhatsApp" else "SMS"
                        directActions.add(DirectAction("Message ${intent.recipientName} ($app)", intent.body, "message", intent.intent))
                    }
                    is com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser.ParsedIntent.PhoneCall -> {
                        var callIntent = intent.intent
                        var displayName = intent.contactNameOrNumber
                        var displaySubtitle = intent.resolvedPhoneNumber ?: intent.contactNameOrNumber

                        // The coordinator resolves names from the in-memory index; this provider lookup only
                        // covers the window before that index has loaded, and only if contact search is on.
                        if (intent.resolvedPhoneNumber == null && settings.searchContacts &&
                            !corpusIndexManager.isIndexReady.value &&
                            !intent.contactNameOrNumber.all { it.isDigit() || it in "+- ()" }
                        ) {
                            try {
                                val contacts = sources.contacts(intent.contactNameOrNumber)
                                val matched = contacts.firstOrNull { it.phoneNumber.isNotBlank() }
                                if (matched != null) {
                                    displayName = matched.name
                                    displaySubtitle = matched.phoneNumber
                                    val clean = matched.phoneNumber.filter { it.isDigit() || it == '+' }
                                    callIntent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$clean"))
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                android.util.Log.w("SearchViewModel", "Contact lookup for call action failed", e)
                            }
                        }

                        directActions.add(DirectAction("Call $displayName", displaySubtitle, "phone", callIntent))
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

            // settingsState already merges DataStore with the SharedPreferences mirror, so it is the
            // single source of truth; OR-ing in raw prefs made a stale "true" impossible to switch off.
            val resolvedApps = if (settings.searchApps) {
                unifiedResults.apps.filter { it.packageName !in settings.hiddenApps }.take(12)
            } else emptyList()

            val mathResult = unifiedResults.mathResult.takeIf { settings.searchCalculator }
            val mathStr = mathResult?.let(::formatMathResult)

            currentCoroutineContext().ensureActive()
            val localContacts = if (settings.searchContacts) {
                if (unifiedResults.contacts.isNotEmpty() || corpusIndexManager.isIndexReady.value) {
                    unifiedResults.contacts.take(settings.contactResultsCount)
                } else if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    sources.contacts(newQuery).take(settings.contactResultsCount)
                } else {
                    emptyList()
                }
            } else emptyList()

            currentCoroutineContext().ensureActive()
            val localFiles = if (settings.searchFiles) {
                unifiedResults.files.take(settings.fileResultsCount)
            } else emptyList()
            // Debug menu: pad the lists past their usual caps to exercise long results.
            val shownContacts = if (mockLargeDataset) localContacts + DebugMocks.contacts else localContacts
            val shownFiles = if (mockLargeDataset) localFiles + DebugMocks.files else localFiles

            val localShortcuts = if (settings.searchShortcuts) {
                unifiedResults.shortcuts.filter { it.packageName !in settings.hiddenApps }.take(settings.shortcutResultsCount)
            } else emptyList()

            val suggestionsEnabled = settings.searchWeb && customPrefs[SettingsManager.SEARCH_WEB_SUGGESTIONS]
            val engine = settings.searchEngine
            val cachedWebSuggestions = if (suggestionsEnabled) {
                sources.cachedWebSuggestions(newQuery, engine)?.take(settings.webResultsCount.coerceAtLeast(1))
            } else null

            val q = newQuery.lowercase().trim()
            val unitConv = mathResult as? com.pixel.intelligentsearch.core.search.MathematicalExpressionEngine.MathEvaluationResult.UnitConversion
            val localInstantAnswer = if (unitConv != null) {
                InstantAnswer(unitConv.formatted, "Unit Conversion", "conversion")
            } else if (q == "time" || q.startsWith("time in ")) {
                buildTimeAnswer(q.removePrefix("time").removePrefix(" in ").trim())
            } else if (q == "weather" || q.startsWith("weather in ")) {
                // No weather data source is available offline, so offer the lookup rather than a made-up reading.
                val place = q.removePrefix("weather").removePrefix(" in ").trim()
                val title = if (place.isEmpty()) "Weather" else "Weather in ${place.toDisplayPlaceName()}"
                InstantAnswer(title, "Tap for the current forecast", "weather")
            } else if (q.startsWith("define ") || q.startsWith("meaning of ") || q.startsWith("definition of ")) {
                val word = q.removePrefix("define ").removePrefix("meaning of ").removePrefix("definition of ").trim()
                val providerName = when (engine) { "DuckDuckGo" -> "DuckDuckGo"; "Bing" -> "Bing"; else -> "Dictionary" }
                InstantAnswer(word.replaceFirstChar { it.uppercase() }, "$providerName • Tap for full definition", "dictionary")
            } else if (looksLikeWebAddress(q)) {
                InstantAnswer(newQuery.trim(), "Go to Website • Tap to open", "url")
            } else {
                null
            }

            val elapsed = System.currentTimeMillis() - startTime

            currentCoroutineContext().ensureActive()
            if (_uiState.value.query != newQuery) return@launch

            // TIER 1 UPDATE: Instant local results (<3ms) + Instant cached suggestions
            _uiState.update { current ->
                if (current.query != newQuery) return@update current
                val resolvedSuggestions = if (!suggestionsEnabled) {
                    emptyList()
                } else {
                    cachedWebSuggestions ?: if (previousQuery.isNotBlank() && newQuery.startsWith(previousQuery, ignoreCase = true)) {
                        current.webSuggestions.filter { it.contains(newQuery, ignoreCase = true) }
                    } else {
                        emptyList()
                    }
                }
                current.copy(
                    filteredApps = resolvedApps,
                    contacts = shownContacts,
                    files = shownFiles,
                    shortcuts = localShortcuts,
                    webSuggestions = resolvedSuggestions,
                    mathResult = mathStr,
                    instantAnswer = localInstantAnswer,
                    systemToggle = systemToggle,
                    volumeSlider = unifiedResults.systemAction as? com.pixel.intelligentsearch.core.search.SystemActionRouter.ActionResult.VolumeSlider,
                    directActions = directActions,
                    lastQueryLatency = elapsed
                )
            }
            ready.complete(Unit)

            if (verboseLogging) {
                debugLog("Local query finished in ${elapsed} ms: ${resolvedApps.size} apps, ${shownContacts.size} contacts, " +
                    "${shownFiles.size} files, ${localShortcuts.size} shortcuts, ${directActions.size} actions")
            }

            // "Did you mean": once typing pauses (the next keystroke cancels this job, so it never runs mid-word),
            // unless the query is plainly the start of an app or contact name, where a dictionary would "fix" it.
            val typed = newQuery.trim()
            val namingSomething = resolvedApps.any { it.name.startsWith(typed, ignoreCase = true) } ||
                shownContacts.any { it.name.startsWith(typed, ignoreCase = true) }
            val checkSpelling = !namingSomething &&
                SpellingSuggester.shouldCheck(newQuery, bangManager.getTriggerSymbol(), looksLikeWebAddress(typed))
            if (checkSpelling) {
                delay(SPELLING_PAUSE_MS)
                withContext(Dispatchers.Main) {
                    val suggester = spellingSuggester ?: SpellingSuggester(context).also { spellingSuggester = it }
                    suggester.suggest(newQuery) { corrected ->
                        _uiState.update { if (it.query == newQuery) it.copy(spellingSuggestion = corrected) else it }
                    }
                }
            }
        }
    }

    /** Opened on first use, on the main thread (the spell checker answers on the thread that opened it). */
    private var spellingSuggester: SpellingSuggester? = null

    /**
     * Text for the calculator card. Unit conversions return null because they render in the dedicated
     * conversion card; showing them in both places duplicated the answer.
     */
    private fun formatMathResult(
        math: com.pixel.intelligentsearch.core.search.MathematicalExpressionEngine.MathEvaluationResult
    ): String? = when (math) {
        is com.pixel.intelligentsearch.core.search.MathematicalExpressionEngine.MathEvaluationResult.Computation -> math.formattedResult
        is com.pixel.intelligentsearch.core.search.MathematicalExpressionEngine.MathEvaluationResult.Bitwise -> {
            // The expression ends with the requested representation, e.g. "255 to bin = 0b11111111".
            val value = math.expression.substringAfterLast(" = ")
            if (value.startsWith("0x") || value.startsWith("0b") || value.startsWith("0o")) value else "$value (${math.hexValue})"
        }
        is com.pixel.intelligentsearch.core.search.MathematicalExpressionEngine.MathEvaluationResult.UnitConversion -> null
    }

    /**
     * Answers "time" with the local time and "time in <place>" with that place's time when the place maps
     * to a known time zone; unknown places return null so the query falls through to web search instead
     * of mislabelling the local time.
     */
    private fun buildTimeAnswer(place: String): InstantAnswer? {
        val zone = if (place.isEmpty()) java.util.TimeZone.getDefault() else resolveTimeZone(place) ?: return null
        val format = android.text.format.DateFormat.getTimeFormat(context).apply { timeZone = zone }
        val label = if (place.isEmpty()) "your location" else place.toDisplayPlaceName()
        return InstantAnswer(format.format(java.util.Date()), "Current time in $label", "time")
    }

    private fun String.toDisplayPlaceName(): String =
        split(' ').filter { it.isNotEmpty() }.joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }

    private fun resolveTimeZone(place: String): java.util.TimeZone? {
        val normalized = place.lowercase().replace(Regex("[^a-z ]"), "").trim().replace(Regex("\\s+"), " ")
        if (normalized.isEmpty()) return null
        val zoneId = TIME_ZONE_ALIASES[normalized]
            ?: java.time.ZoneId.getAvailableZoneIds().firstOrNull { id ->
                id.contains('/') && id.substringAfterLast('/').replace('_', ' ').lowercase() == normalized
            }
            ?: return null
        return java.util.TimeZone.getTimeZone(zoneId)
    }

    private suspend fun fetchRemoteWebSuggestions(query: String) {
        if (query.isBlank()) return
        val settings = settingsState.value
        if (!settings.searchWeb) {
            _uiState.update { it.copy(webSuggestions = emptyList()) }
            return
        }
        val engine = settings.searchEngine
        if (engine == "Tor Project" || engine == "Tor Browser") {
            _uiState.update { it.copy(webSuggestions = emptyList()) }
            return
        }

        val suggestionsEnabled = customPrefs[SettingsManager.SEARCH_WEB_SUGGESTIONS]
        if (!suggestionsEnabled) {
            _uiState.update { it.copy(webSuggestions = emptyList()) }
            return
        }

        val maxResults = settings.webResultsCount.coerceAtLeast(1)

        withContext(Dispatchers.IO) {
            try {
                val started = System.currentTimeMillis()
                if (debugSwitch(SettingsManager.DEBUG_SIMULATE_LATENCY)) delay(SIMULATED_LATENCY_MS)
                val suggestions = sources.webSuggestions(query, engine).take(maxResults)
                if (debugSwitch(SettingsManager.DEBUG_VERBOSE_LOGGING)) {
                    debugLog("Web suggestions: ${suggestions.size} in ${System.currentTimeMillis() - started} ms")
                }
                currentCoroutineContext().ensureActive()
                _uiState.update { current ->
                    if (current.query == query) {
                        current.copy(webSuggestions = suggestions)
                    } else current
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    /** A debug menu switch, which applies only while debug mode is on. */
    private fun debugSwitch(key: com.pixel.intelligentsearch.core.data.settings.BooleanSetting): Boolean =
        customPrefs[SettingsManager.DEBUG_UNLOCKED] && customPrefs[key]

    /**
     * Verbose debug logging. Log.println, unlike Log.d, survives release builds (R8 strips v, d, i and w), and
     * it records lengths, counts and timings only, never what the user typed.
     */
    private fun debugLog(message: String) {
        android.util.Log.println(android.util.Log.INFO, "SearchDebug", message)
    }

    @Volatile
    private var lastClearedHistory: List<HistoryEntity> = emptyList()
    private var historyClearJob: Job? = null

    /**
     * Records a search, unless the user turned Search History off or searches through Tor: a Tor user expects
     * no local trace, so nothing is kept while Tor is the search engine.
     */
    fun addSearchHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val settings = settingsState.value
        if (!settings.searchPreviousSearches || isTorSearchEngine(settings.searchEngine)) return
        _uiState.update { state ->
            val updated = (listOf(trimmed) + state.recentSearches.filterNot { it.equals(trimmed, ignoreCase = true) }).take(25)
            state.copy(recentSearches = updated)
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (directBootManager.isUserUnlocked.value) {
                historyDao.recordAndPrune(trimmed, System.currentTimeMillis(), 25)
            }
        }
    }

    fun removeSearchHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        _uiState.update { state ->
            state.copy(recentSearches = state.recentSearches.filterNot { it.equals(trimmed, ignoreCase = true) })
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (directBootManager.isUserUnlocked.value) {
                historyDao.deleteByQuery(trimmed)
            }
        }
    }

    fun clearSearchHistory() {
        val currentSearches = _uiState.value.recentSearches
        // Snapshot before going async so an immediate Undo restores this clear, not an older one.
        // Descending timestamps keep the original most-recent-first order on restore.
        val now = System.currentTimeMillis()
        lastClearedHistory = currentSearches.mapIndexed { index, query -> HistoryEntity(query, now - index) }
        _uiState.update { state -> state.copy(recentSearches = emptyList()) }
        historyClearJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (directBootManager.isUserUnlocked.value) {
                    val dbHistory = historyDao.getSearchHistory()
                    if (dbHistory.isNotEmpty()) lastClearedHistory = dbHistory
                    historyDao.clearHistory()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.w("IntelligentSearch", "Failed to clear search history", e)
            }
        }
    }

    fun undoClearSearchHistory() {
        val toRestore = lastClearedHistory
        if (toRestore.isEmpty()) return
        lastClearedHistory = emptyList()
        _uiState.update { state -> state.copy(recentSearches = toRestore.map { it.query }) }
        val pendingClear = historyClearJob
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // The clear may still be running; restoring first would let it wipe the restored rows.
                pendingClear?.join()
                if (directBootManager.isUserUnlocked.value) {
                    historyDao.insertSearches(toRestore)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.w("IntelligentSearch", "Failed to restore search history", e)
            }
        }
    }

    fun dismissDirectAction(action: DirectAction) {
        initialClipboardActions = initialClipboardActions.filter { it != action }
        _uiState.update { state ->
            state.copy(directActions = state.directActions.filter { it != action })
        }
    }

    fun dismissCalendarEvent(event: CalendarEvent) {
        _uiState.update { state ->
            state.copy(calendarEvents = state.calendarEvents.filter { it != event })
        }
    }

    /** Intent for a bang query such as "!yt cats", or null when [query] contains no known bang. */
    fun resolveBangIntent(query: String): android.content.Intent? =
        bangManager.parseBangQuery(query)?.let(bangManager::dispatchBangSearch)

    /** The web address a bang would open (never an app), or null if [query] isn't a bang. */
    fun resolveBangWebAddress(query: String): String? =
        bangManager.parseBangQuery(query)?.let(bangManager::bangWebUri)?.toString()

    fun notifyAppLaunch(packageName: String) {
        nativeAppPredictionProvider.notifyAppLaunch(packageName)
    }

    fun unlockPrivateSpace(activity: android.app.Activity) {
        privateSpaceManager.requestUnlockPrivateSpace(activity)
    }

    override fun onCleared() {
        spellingSuggester?.close()
        spellingSuggester = null
        searchJob?.cancel()
        searchJob = null
        initialDataJob?.cancel()
        initialDataJob = null
    }
}





/** Whether [engine] (the stored search-engine choice) routes searches through Tor Browser. */
internal fun isTorSearchEngine(engine: String): Boolean = com.pixel.intelligentsearch.core.security.SafeWebLaunch.isTorEngine(engine)
