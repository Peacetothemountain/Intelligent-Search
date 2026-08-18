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
    val systemToggle: com.pixel.intelligentsearch.core.ui.SystemToggleUiState? = null,
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
    private val systemToggleManager = com.pixel.intelligentsearch.core.system.SystemToggleManager(context)

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
                    instantAnswer = null,
                    systemToggle = null
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
                    systemToggle = null
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
            
            val cachedSuggestions = if (settingsState.value.searchWeb) WebSearchProvider.getCachedSuggestions(newQuery) else null
            
            if (forceSearchError) {
                _uiState.update { it.copy(
                    webSuggestions = listOf("Error: Unable to connect to search API"),
                    isLoading = false
                ) }
                return@launch
            }
            
            _uiState.update { it.copy(
                isLoading = cachedSuggestions == null && settingsState.value.searchWeb,
                webSuggestions = cachedSuggestions ?: it.webSuggestions
            ) }
            
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

                val systemToggleDeferred = async {
                    val qClean = newQuery.lowercase().trim()
                    when {
                        qClean in listOf("flashlight", "torch", "flash light", "light", "flash") -> {
                            val isTorch = systemToggleManager.isTorchEnabled()
                            com.pixel.intelligentsearch.core.ui.SystemToggleUiState(
                                id = "flashlight",
                                title = "Flashlight",
                                subtitle = systemToggleManager.getTorchStatusText(isTorch),
                                iconType = "flashlight",
                                isEnabled = isTorch,
                                onToggle = { systemToggleManager.toggleTorch(it) },
                                onOpenSettings = { systemToggleManager.openTorchSettings() }
                            )
                        }
                        qClean in listOf("bluetooth", "bt", "blue tooth") -> {
                            val isBt = systemToggleManager.isBluetoothEnabled()
                            com.pixel.intelligentsearch.core.ui.SystemToggleUiState(
                                id = "bluetooth",
                                title = "Bluetooth",
                                subtitle = systemToggleManager.getBluetoothStatusText(isBt),
                                iconType = "bluetooth",
                                isEnabled = isBt,
                                onToggle = { systemToggleManager.toggleBluetooth(it) },
                                onOpenSettings = { systemToggleManager.openBluetoothSettings() }
                            )
                        }
                        qClean in listOf("wifi", "wi-fi", "internet", "wireless", "wlan") -> {
                            val isWifi = systemToggleManager.isWifiEnabled()
                            com.pixel.intelligentsearch.core.ui.SystemToggleUiState(
                                id = "wifi",
                                title = "Wi-Fi",
                                subtitle = systemToggleManager.getWifiStatusText(isWifi),
                                iconType = "wifi",
                                isEnabled = isWifi,
                                onToggle = { systemToggleManager.toggleWifiDirect(it) },
                                onOpenSettings = { systemToggleManager.openWifiSettings() }
                            )
                        }
                        qClean in listOf("mobile data", "cellular", "cellular data", "data", "lte", "5g") -> {
                            val isData = systemToggleManager.isMobileDataEnabled()
                            com.pixel.intelligentsearch.core.ui.SystemToggleUiState(
                                id = "cellular",
                                title = "Mobile Data",
                                subtitle = systemToggleManager.getMobileDataStatusText(),
                                iconType = "cellular",
                                isEnabled = isData,
                                isActionOnly = true,
                                onToggle = { systemToggleManager.openMobileDataSettings() },
                                onOpenSettings = { systemToggleManager.openMobileDataSettings() }
                            )
                        }
                        qClean in listOf("airplane", "airplane mode", "aeroplane mode", "flight mode") -> {
                            val isAir = systemToggleManager.isAirplaneModeEnabled()
                            com.pixel.intelligentsearch.core.ui.SystemToggleUiState(
                                id = "airplane",
                                title = "Airplane Mode",
                                subtitle = systemToggleManager.getAirplaneModeStatusText(isAir),
                                iconType = "airplane",
                                isEnabled = isAir,
                                isActionOnly = true,
                                onToggle = { systemToggleManager.openAirplaneModeSettings() },
                                onOpenSettings = { systemToggleManager.openAirplaneModeSettings() }
                            )
                        }
                        qClean in listOf("auto rotate", "autorotate", "rotation", "screen rotation", "rotate", "portrait", "landscape") -> {
                            val isRotate = systemToggleManager.isAutoRotateEnabled()
                            com.pixel.intelligentsearch.core.ui.SystemToggleUiState(
                                id = "autorotate",
                                title = "Auto-Rotate",
                                subtitle = systemToggleManager.getAutoRotateStatusText(isRotate),
                                iconType = "autorotate",
                                isEnabled = isRotate,
                                onToggle = { systemToggleManager.toggleAutoRotate(it) },
                                onOpenSettings = { systemToggleManager.openAutoRotateSettings() }
                            )
                        }
                        qClean in listOf("dnd", "do not disturb", "silence", "mute phone", "priority only") -> {
                            val isDnd = systemToggleManager.isDndEnabled()
                            com.pixel.intelligentsearch.core.ui.SystemToggleUiState(
                                id = "dnd",
                                title = "Do Not Disturb",
                                subtitle = systemToggleManager.getDndStatusText(isDnd),
                                iconType = "dnd",
                                isEnabled = isDnd,
                                onToggle = { systemToggleManager.toggleDnd(it) },
                                onOpenSettings = { systemToggleManager.openDndSettings() }
                            )
                        }
                        qClean in listOf("battery saver", "power saver", "low power mode", "battery", "saver") -> {
                            val isBat = systemToggleManager.isBatterySaverEnabled()
                            com.pixel.intelligentsearch.core.ui.SystemToggleUiState(
                                id = "battery",
                                title = "Battery Saver",
                                subtitle = systemToggleManager.getBatterySaverStatusText(isBat),
                                iconType = "battery",
                                isEnabled = isBat,
                                isActionOnly = true,
                                onToggle = { systemToggleManager.openBatterySaverSettings() },
                                onOpenSettings = { systemToggleManager.openBatterySaverSettings() }
                            )
                        }
                        qClean in listOf("location", "gps", "locate") -> {
                            val isLoc = systemToggleManager.isLocationEnabled()
                            com.pixel.intelligentsearch.core.ui.SystemToggleUiState(
                                id = "location",
                                title = "Location",
                                subtitle = systemToggleManager.getLocationStatusText(isLoc),
                                iconType = "location",
                                isEnabled = isLoc,
                                isActionOnly = true,
                                onToggle = { systemToggleManager.openLocationSettings() },
                                onOpenSettings = { systemToggleManager.openLocationSettings() }
                            )
                        }
                        qClean in listOf("hotspot", "tethering", "portable hotspot", "wifi hotspot", "personal hotspot") -> {
                            com.pixel.intelligentsearch.core.ui.SystemToggleUiState(
                                id = "hotspot",
                                title = "Hotspot & Tethering",
                                subtitle = systemToggleManager.getHotspotStatusText(),
                                iconType = "hotspot",
                                isEnabled = false,
                                isActionOnly = true,
                                onToggle = { systemToggleManager.openHotspotSettings() },
                                onOpenSettings = { systemToggleManager.openHotspotSettings() }
                            )
                        }
                        qClean in listOf("dark mode", "dark theme", "night mode", "light mode", "theme") -> {
                            val isDark = systemToggleManager.isDarkModeEnabled()
                            com.pixel.intelligentsearch.core.ui.SystemToggleUiState(
                                id = "darkmode",
                                title = "Dark Theme",
                                subtitle = systemToggleManager.getDarkModeStatusText(isDark),
                                iconType = "darkmode",
                                isEnabled = isDark,
                                onToggle = { systemToggleManager.toggleDarkMode(it) },
                                onOpenSettings = { systemToggleManager.openDarkModeSettings() }
                            )
                        }
                        qClean in listOf("night light", "blue light", "reading mode", "eye comfort") -> {
                            com.pixel.intelligentsearch.core.ui.SystemToggleUiState(
                                id = "nightlight",
                                title = "Night Light",
                                subtitle = "Warm screen tint for low light",
                                iconType = "nightlight",
                                isEnabled = false,
                                isActionOnly = true,
                                onToggle = { systemToggleManager.openNightLightSettings() },
                                onOpenSettings = { systemToggleManager.openNightLightSettings() }
                            )
                        }
                        qClean in listOf("nfc", "contactless", "google pay") -> {
                            val isNfc = systemToggleManager.isNfcEnabled()
                            com.pixel.intelligentsearch.core.ui.SystemToggleUiState(
                                id = "nfc",
                                title = "NFC",
                                subtitle = systemToggleManager.getNfcStatusText(isNfc),
                                iconType = "nfc",
                                isEnabled = isNfc,
                                isActionOnly = true,
                                onToggle = { systemToggleManager.openNfcSettings() },
                                onOpenSettings = { systemToggleManager.openNfcSettings() }
                            )
                        }
                        qClean in listOf("volume", "sound", "vibrate", "silent", "ringtone", "ringer") -> {
                            val isVib = systemToggleManager.isSilentOrVibrate()
                            com.pixel.intelligentsearch.core.ui.SystemToggleUiState(
                                id = "sound",
                                title = "Sound & Vibration",
                                subtitle = systemToggleManager.getSoundStatusText(isVib),
                                iconType = "sound",
                                isEnabled = isVib,
                                onToggle = { systemToggleManager.toggleSoundMode(it) },
                                onOpenSettings = { systemToggleManager.openSoundSettings() }
                            )
                        }
                        qClean in listOf("cast", "screen cast", "screen mirroring", "chromecast") -> {
                            com.pixel.intelligentsearch.core.ui.SystemToggleUiState(
                                id = "cast",
                                title = "Screen Cast",
                                subtitle = "Mirror screen to TV / Displays",
                                iconType = "cast",
                                isEnabled = false,
                                isActionOnly = true,
                                onToggle = { systemToggleManager.openCastSettings() },
                                onOpenSettings = { systemToggleManager.openCastSettings() }
                            )
                        }
                        qClean in listOf("brightness", "auto brightness", "screen brightness") -> {
                            com.pixel.intelligentsearch.core.ui.SystemToggleUiState(
                                id = "brightness",
                                title = "Display Brightness",
                                subtitle = "Screen & adaptive brightness",
                                iconType = "brightness",
                                isEnabled = false,
                                isActionOnly = true,
                                onToggle = { systemToggleManager.openDisplayBrightnessSettings() },
                                onOpenSettings = { systemToggleManager.openDisplayBrightnessSettings() }
                            )
                        }
                        qClean in listOf("privacy", "camera access", "mic access", "sensor privacy") -> {
                            com.pixel.intelligentsearch.core.ui.SystemToggleUiState(
                                id = "privacy",
                                title = "Privacy & Sensors",
                                subtitle = "Microphone & Camera Permissions",
                                iconType = "privacy",
                                isEnabled = true,
                                isActionOnly = true,
                                onToggle = { systemToggleManager.openPrivacySettings() },
                                onOpenSettings = { systemToggleManager.openPrivacySettings() }
                            )
                        }
                        else -> null
                    }
                }

                val localContacts = contactsDeferred.await()
                val localFiles = filesDeferred.await()
                val localShortcuts = shortcutsDeferred.await()
                val localMathResult = mathResultDeferred.await()
                val localInstantAnswer = instantAnswerDeferred.await()
                val localSystemToggle = systemToggleDeferred.await()
                
                _uiState.update {
                    it.copy(
                        filteredApps = filteredApps,
                        contacts = localContacts,
                        files = localFiles,
                        shortcuts = localShortcuts,
                        mathResult = localMathResult,
                        instantAnswer = localInstantAnswer,
                        systemToggle = localSystemToggle,
                        lastQueryLatency = System.currentTimeMillis() - startTime
                    )
                }

                if (settings.searchWeb) {
                    val webSuggestions = webSuggestionsDeferred.await()
                    _uiState.update {
                        it.copy(
                            webSuggestions = if (webSuggestions.isNotEmpty()) webSuggestions else it.webSuggestions,
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




