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

private val URL_PATTERN = Regex("""^([a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}(/.*)?$""")

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
    val isLoading: Boolean = false,
    val isDirectBootLocked: Boolean = false,
    val hasLockedPrivateSpace: Boolean = false,
    val lastQueryLatency: Long = 0,
    val bangSuggestions: List<com.pixel.intelligentsearch.core.bangs.SearchBang> = emptyList(),
    val detectedBangQuery: com.pixel.intelligentsearch.core.bangs.ParsedBangQuery? = null
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
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

    private val _remoteSearchQueryFlow = MutableStateFlow("")
    private var searchJob: Job? = null
    private var initialDataJob: Job? = null

    private val adpfThermalManager = com.pixel.intelligentsearch.core.performance.ADPFThermalManager.getInstance(context)
    private val nexusLauncherBridge = com.pixel.intelligentsearch.core.data.NexusLauncherBridge(context)
    private val customPrefs by lazy { context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE) }
    private var initialClipboardActions: List<DirectAction> = emptyList()

    init {
        adpfThermalManager.applyTopAppThreadPriority()
        corpusIndexManager.initialize()
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            corpusIndexManager.reindexContacts()
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
                _uiState.update { it.copy(isDirectBootLocked = !isUnlocked) }
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
            multiProfileManager.profilesState.collect { profiles ->
                val hasLocked = profiles.any { it.profileType == ProfileType.PRIVATE && it.isLocked }
                _uiState.update { it.copy(hasLockedPrivateSpace = hasLocked) }
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
            corpusIndexManager.reindexContacts()
        }
    }

    fun loadInitialData() {
        refreshContacts()
        initialDataJob?.cancel()
        initialDataJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val appsDeferred = async { SystemDataProvider.getAllApps(context) }
                val launcherPredictedDeferred = async {
                    val nativePredicted = nativeAppPredictionProvider.getPredictedApps()
                    if (nativePredicted.isNotEmpty()) {
                        nativePredicted
                    } else {
                        nexusLauncherBridge.getPredictedApps().mapNotNull { pred ->
                            try {
                                val iconDrawable = context.packageManager.getApplicationIcon(pred.packageName)
                                AppItem(name = pred.displayName, packageName = pred.packageName, icon = iconDrawable)
                            } catch (e: Exception) {
                                null
                            }
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

                val hiddenApps = settingsState.value.hiddenApps
                val recentApps = if (settingsState.value.contextAwareQuickApps) {
                    val quickApps = SystemDataProvider.getContextAwareQuickApps(context, settingsState.value.hiddenApps).distinctBy { it.packageName }
                    if (launcherPredictedApps.isNotEmpty()) (launcherPredictedApps + quickApps).distinctBy { it.packageName } else quickApps
                } else {
                    val standardRecents = SystemDataProvider.getRecentApps(context, settingsState.value.hiddenApps).distinctBy { it.packageName }
                    if (launcherPredictedApps.isNotEmpty()) (launcherPredictedApps + standardRecents).distinctBy { it.packageName } else standardRecents
                }.filter { it.packageName !in hiddenApps } // launcher predictions know nothing about hidden apps

                val events = eventsDeferred.await()

                val clipboardActions = mutableListOf<DirectAction>()
                if (settingsState.value.smartClipboardSuggestions && !isAdvancedProtectionOn()) {
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
                    } catch (e: Exception) { android.util.Log.w("IntelligentSearch", "Unexpected error", e) }
                }
                initialClipboardActions = clipboardActions
                _uiState.update { it.copy(
                    allApps = allApps,
                    recentApps = recentApps,
                    calendarEvents = events,
                    directActions = clipboardActions
                ) }
            } catch (e: Exception) {
                android.util.Log.w("IntelligentSearch", "Unexpected error", e)
            }
        }
    }

    fun onQueryChanged(newQuery: String) {
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
                isLoading = false
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
        executeLocalSearch(newQuery)

        // TIER 2: Debounced Web Suggestions (80ms human-pause debounce)
        _remoteSearchQueryFlow.value = newQuery
    }

    private fun executeLocalSearch(newQuery: String) {
        val prefs = customPrefs
        val verboseLogging = prefs.getBoolean("debug.verbose_logging", false)
        val forceSearchError = prefs.getBoolean("debug.force_search_error", false)

        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (isDebuggable && verboseLogging) {
                android.util.Log.d("SearchDebug", "Local query started (len: ${newQuery.length})")
            }
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
                            !intent.contactNameOrNumber.all { it.isDigit() || it in "+- ()" }
                        ) {
                            try {
                                val contacts = com.pixel.intelligentsearch.core.data.SystemDataProvider.getContacts(context, intent.contactNameOrNumber)
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
                if (unifiedResults.contacts.isNotEmpty()) {
                    unifiedResults.contacts.take(settings.contactResultsCount)
                } else if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    SystemDataProvider.getContacts(context, newQuery).take(settings.contactResultsCount)
                } else {
                    emptyList()
                }
            } else emptyList()

            currentCoroutineContext().ensureActive()
            val localFiles = if (settings.searchFiles) {
                unifiedResults.files.take(settings.fileResultsCount)
            } else emptyList()

            val localShortcuts = if (settings.searchShortcuts) {
                unifiedResults.shortcuts.take(settings.shortcutResultsCount)
            } else emptyList()

            val suggestionsEnabled = customPrefs.getBoolean("search.web.suggestions", true) && !isAdvancedProtectionOn()
            val engine = settings.searchEngine
            val cachedWebSuggestions = if (suggestionsEnabled) {
                WebSearchProvider.getCachedSuggestions(newQuery, engine)?.take(settings.webResultsCount.coerceAtLeast(5))
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
            } else if (q.matches(URL_PATTERN) || q.startsWith("http://") || q.startsWith("https://")) {
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
                val resolvedSuggestions = cachedWebSuggestions ?: if (current.query.isNotBlank() && newQuery.startsWith(current.query, ignoreCase = true)) {
                    current.webSuggestions.filter { it.contains(newQuery, ignoreCase = true) }
                } else {
                    emptyList()
                }
                current.copy(
                    filteredApps = resolvedApps,
                    contacts = localContacts,
                    files = localFiles,
                    shortcuts = localShortcuts,
                    webSuggestions = resolvedSuggestions,
                    mathResult = mathStr,
                    instantAnswer = localInstantAnswer,
                    systemToggle = systemToggle,
                    volumeSlider = unifiedResults.systemAction as? com.pixel.intelligentsearch.core.search.SystemActionRouter.ActionResult.VolumeSlider,
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

    private fun isAdvancedProtectionOn(): Boolean =
        com.pixel.intelligentsearch.core.security.AdvancedProtectionMonitor.isEnabled(context)

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
        val engine = settings.searchEngine
        if (engine == "Tor Project" || engine == "Tor Browser") {
            _uiState.update { it.copy(webSuggestions = emptyList()) }
            return
        }

        val suggestionsEnabled = customPrefs.getBoolean("search.web.suggestions", true)
        if (!suggestionsEnabled || isAdvancedProtectionOn()) return

        val maxResults = settings.webResultsCount.coerceAtLeast(5)

        withContext(Dispatchers.IO) {
            try {
                val suggestions = WebSearchProvider.getWebSuggestions(query, engine).take(maxResults)
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

    private var lastClearedHistory: List<HistoryEntity> = emptyList()

    fun addSearchHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
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
        _uiState.update { state -> state.copy(recentSearches = emptyList()) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (directBootManager.isUserUnlocked.value) {
                    val dbHistory = historyDao.getSearchHistory()
                    lastClearedHistory = if (dbHistory.isNotEmpty()) {
                        dbHistory
                    } else {
                        currentSearches.map { HistoryEntity(it, System.currentTimeMillis()) }
                    }
                    historyDao.clearHistory()
                } else {
                    lastClearedHistory = currentSearches.map { HistoryEntity(it, System.currentTimeMillis()) }
                }
            } catch (_: Exception) {
                lastClearedHistory = currentSearches.map { HistoryEntity(it, System.currentTimeMillis()) }
            }
        }
    }

    fun undoClearSearchHistory() {
        val toRestore = lastClearedHistory
        if (toRestore.isNotEmpty()) {
            _uiState.update { state -> state.copy(recentSearches = toRestore.map { it.query }) }
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    if (directBootManager.isUserUnlocked.value) {
                        historyDao.insertSearches(toRestore)
                        lastClearedHistory = emptyList()
                    }
                } catch (_: Exception) {}
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

    fun notifyAppLaunch(packageName: String) {
        nativeAppPredictionProvider.notifyAppLaunch(packageName)
    }

    fun unlockPrivateSpace(activity: android.app.Activity) {
        privateSpaceManager.requestUnlockPrivateSpace(activity)
    }

    override fun onCleared() {
        searchJob?.cancel()
        searchJob = null
        initialDataJob?.cancel()
        initialDataJob = null
    }
}




