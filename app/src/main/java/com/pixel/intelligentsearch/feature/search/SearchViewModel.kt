package com.pixel.intelligentsearch.feature.search
import com.pixel.intelligentsearch.core.search.SpellingSuggester
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.CalendarEvent
import com.pixel.intelligentsearch.core.data.AppItem
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

@androidx.compose.runtime.Immutable
data class DirectAction(
    val title: String,
    val subtitle: String,
    val iconType: String,
    val intent: android.content.Intent?,
    /** Opened instead when no app takes [intent]: any maps app when Google Maps is missing, a text without WhatsApp. */
    val fallbackIntent: android.content.Intent? = null
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
    val webSuggestions: List<WebSuggestion> = emptyList(),
    /** Android Settings pages (and App Info pages) matching the query; see [resultSectionOrder] for where they show. */
    val androidSettings: List<com.pixel.intelligentsearch.core.search.AndroidSettingResult> = emptyList(),
    val mathResult: String? = null,
    val instantAnswer: InstantAnswer? = null,
    val systemToggle: com.pixel.intelligentsearch.core.ui.SystemToggleUiState? = null,
    val volumeSlider: com.pixel.intelligentsearch.core.search.SystemActionRouter.ActionResult.VolumeSlider? = null,
    val directActions: List<DirectAction> = emptyList(),
    /**
     * The action Enter runs instead of searching: a timer, alarm or reminder the query plainly asks for ("15 min",
     * "7:30am", "set a timer for 10 minutes"). Null when there is none or it may be meant as a search ("7:30").
     */
    val enterAction: DirectAction? = null,
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val hasLockedPrivateSpace: Boolean = false,
    val lastQueryLatency: Long = 0,
    val bangSuggestions: List<com.pixel.intelligentsearch.core.bangs.SearchBang> = emptyList(),
    val detectedBangQuery: com.pixel.intelligentsearch.core.bangs.ParsedBangQuery? = null,
    /** The query with an obvious typo fixed, offered as "Did you mean"; null when nothing is misspelled. */
    val spellingSuggestion: String? = null,
    /** What the user pinned, for the results' long-press menus. */
    val shortcuts: com.pixel.intelligentsearch.core.search.ResultShortcuts = com.pixel.intelligentsearch.core.search.ResultShortcuts.EMPTY,
    /**
     * The pinned results that can be shown now, in pin order, for the empty overlay's Pinned row: [AppItem],
     * [ContactItem], [FileItem] or [com.pixel.intelligentsearch.core.search.AndroidSettingResult].
     */
    val pinned: List<Any> = emptyList(),
    /**
     * A definition, forecast, translation or screen time worked out for the query; shown in place of [instantAnswer]'s
     * tap-through card while its [com.pixel.intelligentsearch.core.answers.LiveAnswer.query] is the query shown.
     */
    val liveAnswer: com.pixel.intelligentsearch.core.answers.LiveAnswer? = null,
    /** The file type the query names ("pdf invoice"), which [files] are limited to; null when there is none. */
    val fileTypeFilter: com.pixel.intelligentsearch.core.data.FileTypeGroup? = null,
    /** The provider the "Ask" row offers for a question; null when there is no row (see AiAnswers.offerFor). */
    val aiOffer: com.pixel.intelligentsearch.core.ai.AiProvider? = null,
    /** The answer to the question the user tapped Ask on, for this query only. */
    val aiAnswer: com.pixel.intelligentsearch.core.ai.AiAnswerState? = null,
    /**
     * The [com.pixel.intelligentsearch.core.search.ResultKeys] key of the result the user keeps picking for exactly
     * this query, which the Best Match puts first while it is among the results; null when there is none.
     */
    val learnedPick: String? = null
) {
    /**
     * The provider the Ask row shows for, or null when there is no row: besides [aiOffer], the query must not already
     * have an answer that would contradict it at the top, an action Enter runs ("ask sarah at 6pm" is a reminder) or
     * a definition ("what does serendipity mean?" shows its meaning).
     */
    val askAi: com.pixel.intelligentsearch.core.ai.AiProvider?
        get() = aiOffer?.takeIf {
            enterAction == null &&
                (liveAnswer as? com.pixel.intelligentsearch.core.answers.LiveAnswer.Definition)?.query != query
        }
}

/**
 * This state without anything from the Private Space: its apps and the App Info rows for them in the settings
 * results. What stays on screen once the Private Space locks, until the next search.
 */
internal fun SearchUiState.withoutPrivateProfile(): SearchUiState {
    fun AppItem.isPrivate() = isPrivateProfile || profileType == ProfileType.PRIVATE
    return copy(
        allApps = allApps.filterNot { it.isPrivate() },
        recentApps = recentApps.filterNot { it.isPrivate() },
        filteredApps = filteredApps.filterNot { it.isPrivate() },
        androidSettings = androidSettings.filterNot { it.app?.isPrivate() == true }
    )
}

/** How long typing must pause before a query is spell checked for "Did you mean". */
private const val SPELLING_PAUSE_MS = 450L

/**
 * The least time between two web suggestion requests: Chrome's omnibox default
 * (`kDefaultMinimumTimeBetweenSuggestQueriesMs`), measured from the last request rather than the last keystroke.
 */
internal const val SUGGEST_REQUEST_SPACING_MS = 100L

/** How long to hold a suggestion request whose predecessor went out [sinceLastRequestMs] ago: none once spaced out. */
internal fun suggestRequestDelay(sinceLastRequestMs: Long): Long =
    (SUGGEST_REQUEST_SPACING_MS - sinceLastRequestMs).coerceIn(0L, SUGGEST_REQUEST_SPACING_MS)

/** The debug menu's Simulate Web Latency delay before web suggestions are fetched. */
private const val SIMULATED_LATENCY_MS = 2_000L

/** The longest clipboard text read for its suggestion, as for every other way text reaches the search. */
internal const val MAX_CLIP_CHARS = 512

/** Whether answering [question] asks a server (definitions, forecasts); the rest are worked out on the phone. */
private fun asksServer(question: com.pixel.intelligentsearch.core.answers.LiveAnswerQuery): Boolean =
    question is com.pixel.intelligentsearch.core.answers.LiveAnswerQuery.Define ||
        question is com.pixel.intelligentsearch.core.answers.LiveAnswerQuery.Weather

/** A load of quick apps and events younger than this counts as fresh when the overlay comes back into view. */
private const val RELOAD_GRACE_MS = 1_000L

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
    private val systemToggleManager: dagger.Lazy<com.pixel.intelligentsearch.core.system.SystemToggleManager>,
    private val directBootManager: com.pixel.intelligentsearch.core.system.DirectBootManager,
    private val androidSettingsSearch: com.pixel.intelligentsearch.core.search.AndroidSettingsSearch,
    private val resultShortcutStore: com.pixel.intelligentsearch.core.data.ResultShortcutStore,
    private val instantAnswers: com.pixel.intelligentsearch.core.answers.LiveAnswerEngine,
    private val aiAnswers: com.pixel.intelligentsearch.core.ai.AiAnswers,
    private val selectionMemory: com.pixel.intelligentsearch.core.search.SelectionMemory
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

    private val _calculatorKeyboard = MutableStateFlow(false)
    /**
     * Whether the search field asks for the number pad: the widget's Calculator shortcut opened the screen. A plain
     * open puts the letters back ([setCalculatorKeyboard] with false).
     */
    val calculatorKeyboard: StateFlow<Boolean> = _calculatorKeyboard.asStateFlow()
    fun setCalculatorKeyboard(numberPad: Boolean) {
        _calculatorKeyboard.value = numberPad
    }

    private val _remoteSearchQueryFlow = MutableStateFlow("")

    private val _queryConfirmed = MutableStateFlow(true)
    /**
     * Whether the query is the user's own. False while it is text another app handed in ([seedQuery]: selected or
     * shared text, another app's search), which is searched on the phone only: web suggestions and the instant answers
     * that ask a server wait until the user edits it or presses Enter ([confirmQuery]).
     */
    val queryConfirmed: StateFlow<Boolean> = _queryConfirmed.asStateFlow()

    /** A query's instant-answer question; [attempt] changes to ask again (after a permission grant). */
    private data class LiveAnswerRequest(
        val query: String,
        val question: com.pixel.intelligentsearch.core.answers.LiveAnswerQuery,
        val attempt: Int = 0
    )
    private val liveAnswerRequests = MutableStateFlow<LiveAnswerRequest?>(null)
    /** When the last web suggestion request went out ([android.os.SystemClock.elapsedRealtime]). */
    private var lastSuggestRequestAtMs = Long.MIN_VALUE / 2
    /**
     * The query the shown web suggestions were fetched for. A request can now answer before the local search for the
     * same keystroke publishes, and that publish must keep them rather than fall back to the cache or a filtered list.
     */
    @Volatile private var webSuggestionsQuery: String? = null
    @Volatile private var searchJob: Job? = null
    private var pinnedJob: Job? = null

    /** Completed once the latest search's local results are published (before its "did you mean" pause). */
    @Volatile private var localResultsReady: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    /**
     * Waits for the local results of the latest [onQueryChanged], so a caller can act on the best match for it. A
     * search that is cancelled by a re-run (a removed file type chip, the next keystroke) hands over to that re-run:
     * this only returns once the search it waited for is still the current one.
     */
    suspend fun awaitSearch() {
        while (true) {
            val job = searchJob ?: return
            val ready = localResultsReady
            if (ready == null) {
                job.join()
            } else {
                kotlinx.coroutines.selects.select<Unit> {
                    ready.onAwait {}
                    job.onJoin {}
                }
            }
            if (job === searchJob) return
        }
    }
    private var initialDataJob: Job? = null

    private val customPrefs get() = settingsManager.prefs
    private var initialClipboardActions: List<DirectAction> = emptyList()

    init {
        // initialize() already indexes contacts (when permitted); loadInitialData() refreshes them if stale.
        corpusIndexManager.initialize()
        // A new overlay is a new session: the last one's coin or die doesn't carry over.
        unifiedSearchCoordinator.forgetChanceDraw()

        // A query handed in with the launch (another app's search, a widget, the assistant) may arrive before the
        // index is built; search it again the moment the index is ready.
        viewModelScope.launch {
            corpusIndexManager.isIndexReady.first { it }
            val query = _uiState.value.query
            if (query.isNotBlank()) executeLocalSearch(query)
            refreshPinned()
        }

        // Pins, from the menus here or a restore. A change ranks the
        // current results again at once, so a result pinned from its menu moves to the top.
        viewModelScope.launch {
            var first = true
            resultShortcutStore.flow.collect { shortcuts ->
                _uiState.update { it.copy(shortcuts = shortcuts) }
                refreshPinned()
                val query = _uiState.value.query
                if (!first && query.isNotBlank()) executeLocalSearch(query)
                first = false
            }
        }

        // The key vault's small preferences file is read once here, off the main thread, so typing never waits on it.
        viewModelScope.launch(Dispatchers.IO) { aiAnswers.unavailableReason() }


        // Tier 2: remote web suggestions, paced like Chrome's omnibox: a request goes out at once unless the last one
        // left under SUGGEST_REQUEST_SPACING_MS ago, and each keystroke cancels the request still in flight.
        viewModelScope.launch {
            // A StateFlow only emits changes, so a repeated query never re-sends.
            _remoteSearchQueryFlow
                .collectLatest { query ->
                    if (query.isBlank()) {
                        _uiState.update { it.copy(webSuggestions = emptyList()) }
                    } else {
                        delay(suggestRequestDelay(android.os.SystemClock.elapsedRealtime() - lastSuggestRequestAtMs))
                        lastSuggestRequestAtMs = android.os.SystemClock.elapsedRealtime()
                        fetchRemoteWebSuggestions(query)
                    }
                }
        }

        // Instant answers: each keystroke's question cancels the last one's work (and its pending request).
        viewModelScope.launch {
            liveAnswerRequests.collectLatest { request ->
                if (request == null) return@collectLatest
                try {
                    instantAnswers.answer(request.question, request.query).collect { answer ->
                        _uiState.update { if (it.query == request.query) it.copy(liveAnswer = answer) else it }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("SearchViewModel", "Instant answer failed", e)
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
            // every overlay open look like a fresh lock and force a full app re-index.
            var previousLocked = multiProfileManager.profilesState.value
                .any { it.profileType == ProfileType.PRIVATE && it.isLocked }
            multiProfileManager.profilesState.collect { profiles ->
                val hasLocked = profiles.any { it.profileType == ProfileType.PRIVATE && it.isLocked }
                if (hasLocked) {
                    unifiedSearchCoordinator.purgePrivateProfileItems()
                    _uiState.update { it.withoutPrivateProfile().copy(hasLockedPrivateSpace = true) }
                    if (!previousLocked) {
                        corpusIndexManager.reindexApps()
                    }
                } else {
                    _uiState.update { it.copy(hasLockedPrivateSpace = false) }
                    if (previousLocked) {
                        corpusIndexManager.reindexApps()
                    }
                }
                previousLocked = hasLocked
            }
        }

        // Live Hardware State Synchronization with System Toggles (Flashlight, Bluetooth, Wi-Fi, DND, Location, etc.)
        observeLiveSystemToggles()
    }

    private fun observeLiveSystemToggles() {
        // The toggle manager registers camera, network and broadcast listeners as it is built; build it off the main
        // thread so opening the overlay never waits for that.
        viewModelScope.launch(Dispatchers.Default) {
            val toggles = systemToggleManager.get()

            launch {
                toggles.isFlashlightOn.collect { torch ->
                    _uiState.update { current ->
                        if (current.systemToggle?.id == "flashlight" && current.systemToggle.isEnabled != torch) {
                            current.copy(systemToggle = current.systemToggle.copy(
                                isEnabled = torch,
                                subtitle = toggles.getTorchStatusText(torch)
                            ))
                        } else current
                    }
                }
            }

            launch {
                toggles.isBluetoothEnabled.collect { bt ->
                    _uiState.update { current ->
                        if (current.systemToggle?.id == "bluetooth" && current.systemToggle.isEnabled != bt) {
                            current.copy(systemToggle = current.systemToggle.copy(
                                isEnabled = bt,
                                subtitle = toggles.getBluetoothStatusText(bt)
                            ))
                        } else current
                    }
                }
            }

            launch {
                toggles.isWifiConnected.collect { connected ->
                    // A connection means Wi-Fi is on, whatever the radio state read a moment ago said.
                    val enabled = toggles.isWifiEnabled() || connected
                    val subtitle = com.pixel.intelligentsearch.core.system.SystemToggleManager.wifiStatusText(enabled, connected)
                    _uiState.update { current ->
                        if (current.systemToggle?.id == "wifi" &&
                            (current.systemToggle.isEnabled != enabled || current.systemToggle.subtitle != subtitle)
                        ) {
                            current.copy(systemToggle = current.systemToggle.copy(isEnabled = enabled, subtitle = subtitle))
                        } else current
                    }
                }
            }

            launch {
                toggles.isDndEnabled.collect { dnd ->
                    _uiState.update { current ->
                        if (current.systemToggle?.id == "dnd" && current.systemToggle.isEnabled != dnd) {
                            current.copy(systemToggle = current.systemToggle.copy(
                                isEnabled = dnd,
                                subtitle = toggles.getDndStatusText(dnd)
                            ))
                        } else current
                    }
                }
            }

            launch {
                toggles.isLocationEnabled.collect { loc ->
                    _uiState.update { current ->
                        if (current.systemToggle?.id == "location" && current.systemToggle.isEnabled != loc) {
                            current.copy(systemToggle = current.systemToggle.copy(
                                isEnabled = loc,
                                subtitle = toggles.getLocationStatusText(loc)
                            ))
                        } else current
                    }
                }
            }

            launch {
                toggles.isBatterySaverEnabled.collect { bat ->
                    _uiState.update { current ->
                        if (current.systemToggle?.id == "battery" && current.systemToggle.isEnabled != bat) {
                            current.copy(systemToggle = current.systemToggle.copy(
                                isEnabled = bat,
                                subtitle = toggles.getBatterySaverStatusText(bat)
                            ))
                        } else current
                    }
                }
            }

            launch {
                toggles.isAirplaneModeEnabled.collect { air ->
                    _uiState.update { current ->
                        if (current.systemToggle?.id == "airplane" && current.systemToggle.isEnabled != air) {
                            current.copy(systemToggle = current.systemToggle.copy(
                                isEnabled = air,
                                subtitle = toggles.getAirplaneModeStatusText(air)
                            ))
                        } else current
                    }
                }
            }
        }
    }

    fun refreshContacts() {
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            corpusIndexManager.reindexContactsIfStale()
        }
    }

    /** On each overlay open with file search on: picks up new files in the chosen folders; throttled. */
    fun refreshStaleIndexes() {
        corpusIndexManager.reindexFilesIfStale()
    }

    /** On each overlay open: re-reads app usage for ranking apps, whatever sources are on; throttled. */
    fun refreshAppUsage() = corpusIndexManager.refreshAppUsageIfStale()

    /** When the last [loadInitialData] started ([android.os.SystemClock.elapsedRealtime]); null before the first. */
    private var lastInitialLoadAtMs: Long? = null

    /**
     * Brings quick-app predictions, upcoming events and contacts up to date each time the overlay comes back into
     * view; loaded once, they went stale on every reopen. The first open's load, started as this view model was
     * created, is only moments old then, so a load younger than [RELOAD_GRACE_MS] isn't repeated.
     */
    fun reloadOnResume() {
        if (!directBootManager.isUserUnlocked.value) return
        val last = lastInitialLoadAtMs
        if (last != null && android.os.SystemClock.elapsedRealtime() - last < RELOAD_GRACE_MS) return
        loadInitialData()
    }

    fun loadInitialData() {
        lastInitialLoadAtMs = android.os.SystemClock.elapsedRealtime()
        refreshContacts()
        initialDataJob?.cancel()
        initialDataJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val appsDeferred = async { sources.allApps() }
                // The provider already tries Pixel Launcher predictions first, then usage stats. (Re-querying the
                // launcher here returned apps outside the indexed list, which skipped the hidden-app filtering.)
                val launcherPredictedDeferred = async { nativeAppPredictionProvider.getPredictedApps() }
                // Read whenever Android allows the calendar; without the permission the source returns nothing.
                val eventsDeferred = async { sources.upcomingEvents() }

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

                _uiState.update { it.copy(
                    allApps = allApps,
                    recentApps = recentApps,
                    calendarEvents = events
                ) }
                refreshPinned()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.w("IntelligentSearch", "Unexpected error", e)
            }
        }
    }

    /** Clipboard texts whose suggestion the user dismissed; reading the same clip again doesn't bring it back. */
    private val dismissedClipTexts: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /**
     * Offers what is on the clipboard: open a link, call a number, search the text, or search an image with Lens
     * ([ClipboardLens]). Android hands the clipboard
     * only to the app in focus, so the screen calls this each time its window gains focus, which also keeps it
     * current on every reopen.
     */
    suspend fun refreshClipboardActions() {
        val settings = settingsState.value
        val actions = if (settings.smartClipboardSuggestions) {
            withContext(Dispatchers.IO) { readClipboardActions(settings) }.filterNot { it.subtitle in dismissedClipTexts }
        } else emptyList()
        initialClipboardActions = actions
        // Only the empty screen shows clipboard actions; a query's own actions ("call mom") stay.
        _uiState.update { if (it.query.isBlank()) it.copy(directActions = actions) else it }
    }

    private fun readClipboardActions(settings: IntelligentSearchSettings): List<DirectAction> {
        val clipboardActions = mutableListOf<DirectAction>()
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            // Password managers mark copied secrets sensitive; never show or search those.
            val isSensitive = ClipboardLens.isSensitive(clipboard?.primaryClipDescription)
            val engine = settings.searchEngine
            if (ClipboardLens.isImage(clipboard?.primaryClipDescription)) {
                // An image is offered to Lens from its description alone; the clip itself is read only when the
                // suggestion is tapped. Lens is an app hand-off, not a request of ours, so it is offered under Tor as
                // well, like the search bar's Lens button.
                ClipboardLens.suggestionFor(context, clipboard?.primaryClipDescription)?.let(clipboardActions::add)
                return clipboardActions
            }
            if (!isSensitive && clipboard?.hasPrimaryClip() == true && clipboard.primaryClipDescription != null) {
                // Only the text the clip itself carries. Another app's content: URI would be read with this app's
                // permissions (a contact's card) or could stream without end; a long clip is cut like any query.
                val clip: CharSequence = clipboard.primaryClip?.getItemAt(0)?.text ?: ""
                var end = minOf(clip.length, MAX_CLIP_CHARS)
                if (end < clip.length && Character.isHighSurrogate(clip[end - 1])) end--
                val text = clip.subSequence(0, end).toString()
                if (text.isNotBlank()) {
                    val trimmed = text.trim()
                    // A link cut short would open some other page.
                    if (end == clip.length && android.util.Patterns.WEB_URL.matcher(trimmed).matches()) {
                        // Same rules as a typed address: http(s) only, browsable apps only, Tor Browser under Tor.
                        val launch = com.pixel.intelligentsearch.core.security.SafeWebLaunch.forAddress(context, trimmed, engine)
                        if (launch is com.pixel.intelligentsearch.core.security.SafeWebLaunch.Launch.Open) {
                            clipboardActions.add(DirectAction("Open Link", trimmed, "link", launch.intent))
                        }
                    } else if (android.util.Patterns.PHONE.matcher(trimmed).matches() && trimmed.length >= 7) {
                        clipboardActions.add(DirectAction("Call Number", trimmed, "phone", ContactActions.callIntent(trimmed)))
                    } else {
                        // The chosen engine, as for a typed search: the system web search would hand the copied text
                        // to Google, and under Tor this opens Tor Browser or nothing.
                        val launch = webSearchLaunch(context, engine, settings.customSearchEngineUrl, trimmed)
                        if (launch is com.pixel.intelligentsearch.core.security.SafeWebLaunch.Launch.Open) {
                            clipboardActions.add(DirectAction("Search Copied Text", trimmed, "search", launch.intent))
                        }
                    }
                }
            }
        } catch (e: Exception) { android.util.Log.w("IntelligentSearch", "Unexpected error", e) }
        return clipboardActions
    }

    /** The user typed or picked [newQuery]. An edit makes the query theirs; the same text again (a cursor move) doesn't. */
    fun onQueryChanged(newQuery: String) {
        if (newQuery != _uiState.value.query || newQuery.isBlank()) _queryConfirmed.value = true
        updateQuery(newQuery)
    }

    /**
     * Types in [query] handed in from outside the overlay (selected or shared text, another app's search, a widget),
     * unconfirmed: it is searched on the phone only until the user edits it or presses Enter ([queryConfirmed]).
     */
    fun seedQuery(query: String) {
        _queryConfirmed.value = query.isBlank()
        updateQuery(query)
    }

    /** The user accepts the query as it stands (Enter): what waited for [queryConfirmed] goes out now. */
    fun confirmQuery() {
        if (_queryConfirmed.value) return
        _queryConfirmed.value = true
        val query = _uiState.value.query
        if (query.isBlank()) return
        liveAnswerRequests.value = liveAnswerRequestFor(query)
        if (settingsState.value.searchWeb) _remoteSearchQueryFlow.value = query
    }

    /** [query]'s instant-answer question; one that asks a server only once the query is the user's own. */
    private fun liveAnswerRequestFor(query: String): LiveAnswerRequest? =
        com.pixel.intelligentsearch.core.answers.LiveAnswerParser.parse(query)
            ?.takeIf { _queryConfirmed.value || !asksServer(it) }
            ?.let { LiveAnswerRequest(query, it) }

    private fun updateQuery(newQuery: String) {
        val previousQuery = _uiState.value.query
        // Suggestions fetched for another query no longer count as this one's answer.
        if (newQuery != previousQuery) {
            webSuggestionsQuery = null
            // An answer belongs to the question it was asked for; typing on stops it.
            aiJob?.cancel()
            aiJob = null
        }
        _uiState.update {
            val liveAnswer = it.liveAnswer?.takeIf { answer -> answer.query == newQuery }
            if (newQuery != previousQuery) it.copy(query = newQuery, liveAnswer = liveAnswer, aiAnswer = null)
            else it.copy(query = newQuery, liveAnswer = liveAnswer)
        }
        liveAnswerRequests.value = liveAnswerRequestFor(newQuery)

        if (newQuery.isBlank()) {
            _remoteSearchQueryFlow.value = ""
            liveAnswerRequests.value = null
            searchJob?.cancel()
            // Clearing the field (or closing the overlay) ends the query: a coin flipped again lands anew.
            unifiedSearchCoordinator.forgetChanceDraw()
            _uiState.update { it.copy(
                filteredApps = emptyList(),
                contacts = emptyList(),
                files = emptyList(),
                webSuggestions = emptyList(),
                androidSettings = emptyList(),
                directActions = if (settingsState.value.smartClipboardSuggestions) initialClipboardActions else emptyList(),
                enterAction = null,
                mathResult = null,
                instantAnswer = null,
                systemToggle = null,
                volumeSlider = null,
                bangSuggestions = emptyList(),
                detectedBangQuery = null,
                spellingSuggestion = null,
                liveAnswer = null,
                aiOffer = null,
                learnedPick = null
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
                // Offered only; nothing is sent until the row is tapped (askAi).
                aiOffer = if (parsedBang == null) aiAnswers.offerFor(newQuery) else null,
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

        // TIER 2: Web suggestions, paced in init. A query handed in waits for the user (confirmQuery).
        if (settingsState.value.searchWeb && _queryConfirmed.value) {
            _remoteSearchQueryFlow.value = newQuery
        } else {
            _remoteSearchQueryFlow.value = ""
            _uiState.update { it.copy(webSuggestions = emptyList()) }
        }
    }

    /** The file type word whose chip the user removed; the query searches all files until it starts with another. */
    @Volatile
    private var dismissedFileTypeWord: String? = null

    /** The file type [query] starts with, unless its chip was removed. */
    private fun activeFileTypeQuery(query: String): com.pixel.intelligentsearch.core.data.FileTypeQuery? {
        val parsed = com.pixel.intelligentsearch.core.data.FileTypeQuery.parse(query)
        if (parsed?.word != dismissedFileTypeWord) dismissedFileTypeWord = null
        return parsed?.takeIf { it.word != dismissedFileTypeWord }
    }

    /** Removes the file type filter chip: the current query searches files of every type again. */
    fun clearFileTypeFilter() {
        val query = _uiState.value.query
        dismissedFileTypeWord = com.pixel.intelligentsearch.core.data.FileTypeQuery.parse(query)?.word ?: return
        executeLocalSearch(query)
    }

    private fun executeLocalSearch(newQuery: String, previousQuery: String = "") {
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
                    webSuggestions = listOf(WebSuggestion("Error: Unable to connect to search API"))
                ) }
                return@launch
            }

            val settings = settingsState.value

            // 1. In-Memory Unified Search (< 2ms)
            val unifiedResults = unifiedSearchCoordinator.executeSearch(newQuery)
            val learnedPick = selectionMemory.topPick(newQuery)

            // 2. Direct Actions (parsedIntent + clipboard/quick templates)
            val directActions = mutableListOf<DirectAction>()
            val queryLower = newQuery.lowercase()

            unifiedResults.parsedIntent?.let { intent ->
                when (intent) {
                    is com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser.ParsedIntent.CalendarEvent -> {
                        directActions.add(DirectAction("Add Calendar Event", intent.title, "calendar", intent.intent))
                    }
                    is com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser.ParsedIntent.Alarm -> {
                        directActions.add(DirectAction("Set Alarm", com.pixel.intelligentsearch.core.util.Durations.timeOfDay(intent.hour, intent.minute, android.text.format.DateFormat.is24HourFormat(context)), "alarm", intent.intent))
                    }
                    is com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser.ParsedIntent.Timer -> {
                        directActions.add(DirectAction("Set Timer", intent.label, "timer", intent.intent))
                    }
                    is com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser.ParsedIntent.Reminder -> {
                        directActions.add(DirectAction("Set Reminder", intent.task, "calendar", intent.intent))
                    }
                    is com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser.ParsedIntent.DirectMessage -> {
                        val app = if (intent.isWhatsApp) "WhatsApp" else "SMS"
                        directActions.add(DirectAction("Message ${intent.recipientName} ($app)", intent.body, "message", intent.intent, intent.fallbackIntent))
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
                                    callIntent = ContactActions.callIntent(matched.phoneNumber)
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
                        directActions.add(DirectAction("Navigate to ${intent.destination}", intent.destination, "navigation", intent.intent, intent.fallbackIntent))
                    }
                    is com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser.ParsedIntent.MediaPlay -> {
                        directActions.add(DirectAction("Play ${intent.query}", intent.appName ?: "Media app", "media", intent.intent, intent.fallbackIntent))
                    }
                }
            }

            // Enter runs a timer, alarm or reminder the query asks for; calls, messages and the rest stay a tap away.
            var enterAction: DirectAction? = when (unifiedResults.parsedIntent) {
                is com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser.ParsedIntent.Timer,
                is com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser.ParsedIntent.Alarm,
                is com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser.ParsedIntent.Reminder -> directActions.firstOrNull()
                else -> null
            }
            // The short phrasing ("15 min", "7:30am", "laundry in 2 hrs") is the top suggestion, above the results.
            unifiedResults.shortAction?.let { action ->
                val row = DirectAction(action.title, action.subtitle, action.iconType, action.intent)
                directActions.add(0, row)
                if (action.runsOnEnter) enterAction = row
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
            // A query starting with a file type ("pdf invoice") limits files to that type, until its chip is removed.
            val fileTypeQuery = activeFileTypeQuery(newQuery).takeIf { settings.searchFiles }
            val localFiles = if (!settings.searchFiles) {
                emptyList()
            } else if (fileTypeQuery != null) {
                unifiedSearchCoordinator.searchFilesOfType(fileTypeQuery.rest, fileTypeQuery.group).take(settings.fileResultsCount)
            } else {
                unifiedResults.files.take(settings.fileResultsCount)
            }
            // Debug menu: pad the lists past their usual caps to exercise long results.
            val shownContacts = if (mockLargeDataset) localContacts + DebugMocks.contacts else localContacts
            val shownFiles = if (mockLargeDataset) localFiles + DebugMocks.files else localFiles

            // Settings pages complement the quick toggles above ("wifi" offers both). App Info looks apps up
            // through the same app index, leaving out hidden apps; it is an app result too, so it also needs the
            // Apps source. The contacts shown keep a name ("carrie") from turning into a setting ("carrier").
            val androidSettings = run {
                androidSettingsSearch.search(
                    newQuery,
                    findApps = { name ->
                        if (settings.searchApps) {
                            unifiedSearchCoordinator.searchApps(name, limit = 3).filter { it.packageName !in settings.hiddenApps }
                        } else emptyList()
                    },
                    peopleNames = localContacts.map { it.name }
                ).let { pages ->
                    // Pinned pages first, the rest in their own order (the sort is stable).
                    val shortcuts = resultShortcutStore.current
                    pages.sortedByDescending { shortcuts.isPinned(com.pixel.intelligentsearch.core.search.ResultKeys.setting(it)) }
                }
            }

            val suggestionsEnabled = com.pixel.intelligentsearch.core.security.NetworkPolicy.webSuggestionsAllowed(customPrefs)
            val engine = settings.searchEngine
            val cachedWebSuggestions = if (suggestionsEnabled) {
                sources.cachedWebSuggestions(newQuery, engine)?.take(settings.webResultsCount.coerceAtLeast(1))
            } else null

            val q = newQuery.lowercase().trim()
            val question = com.pixel.intelligentsearch.core.answers.LiveAnswerParser.parse(newQuery)
            val unitConv = mathResult as? com.pixel.intelligentsearch.core.search.MathematicalExpressionEngine.MathEvaluationResult.UnitConversion
            // Date arithmetic is calculation too, so it follows the Calculator switch.
            val dateAnswer = unifiedResults.dateAnswer.takeIf { settings.searchCalculator }
            val localInstantAnswer = if (unitConv != null) {
                // A currency answer says where its rates come from: "Rates from Oct 19, 2026" or "Built-in rates".
                val rates = unitConv.currencyRates
                if (rates != null) InstantAnswer(unitConv.formatted, rates.sourceLabel(), "currency")
                else InstantAnswer(unitConv.formatted, "Unit Conversion", "conversion")
            } else if (dateAnswer != null) {
                InstantAnswer(dateAnswer.title, dateAnswer.subtitle, "date")
            } else if (unifiedResults.timeAnswer != null) {
                // "time", "tokyo time", "time in PST", "3pm PST in IST": unknown places get no answer, not the local time.
                unifiedResults.timeAnswer.let { InstantAnswer(it.title, it.subtitle, "time") }
            } else if (unifiedResults.randomAnswer != null && settings.searchCalculator) {
                // A coin, dice or a random number: drawn like a calculation, so it follows the Calculator switch too.
                unifiedResults.randomAnswer.let { InstantAnswer(it.title, it.subtitle, "random") }
            } else if (question is com.pixel.intelligentsearch.core.answers.LiveAnswerQuery.Weather) {
                // The tap-through stands until (or unless) a live forecast replaces it: Web Suggestions off, Tor, offline.
                val title = question.place?.let { "Weather in ${it.toDisplayPlaceName()}" } ?: "Weather"
                val subtitle = if (question.day == com.pixel.intelligentsearch.core.answers.ForecastDay.TOMORROW) {
                    "Tap for tomorrow's forecast"
                } else "Tap for the current forecast"
                InstantAnswer(title, subtitle, "weather")
            } else if (question is com.pixel.intelligentsearch.core.answers.LiveAnswerQuery.Define && question.explicit) {
                val providerName = when (engine) { "DuckDuckGo" -> "DuckDuckGo"; "Bing" -> "Bing"; else -> "Dictionary" }
                InstantAnswer(question.word.replaceFirstChar { it.uppercase() }, "$providerName • Tap for full definition", "dictionary")
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
                } else if (webSuggestionsQuery == newQuery) {
                    // The engine has already answered this very query.
                    current.webSuggestions
                } else {
                    cachedWebSuggestions ?: if (previousQuery.isNotBlank() && newQuery.startsWith(previousQuery, ignoreCase = true)) {
                        // The engine's bold marks were for the shorter query; the row works them out again.
                        current.webSuggestions.filter { it.query.contains(newQuery, ignoreCase = true) }
                            .map { it.copy(boldRanges = emptyList()) }
                    } else {
                        emptyList()
                    }
                }
                current.copy(
                    filteredApps = resolvedApps,
                    contacts = shownContacts,
                    files = shownFiles,
                    fileTypeFilter = fileTypeQuery?.group,
                    androidSettings = androidSettings,
                    webSuggestions = resolvedSuggestions,
                    mathResult = mathStr,
                    instantAnswer = localInstantAnswer,
                    systemToggle = systemToggle,
                    volumeSlider = unifiedResults.systemAction as? com.pixel.intelligentsearch.core.search.SystemActionRouter.ActionResult.VolumeSlider,
                    directActions = directActions,
                    enterAction = enterAction,
                    lastQueryLatency = elapsed,
                    learnedPick = learnedPick
                )
            }
            ready.complete(Unit)

            if (verboseLogging) {
                debugLog("Local query finished in ${elapsed} ms: ${resolvedApps.size} apps, ${shownContacts.size} contacts, " +
                    "${shownFiles.size} files, ${directActions.size} actions")
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

    /** Works the current query's instant answer out again: after a permission was granted. */
    fun retryLiveAnswer() {
        liveAnswerRequests.update { it?.copy(attempt = it.attempt + 1) }
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

    private fun String.toDisplayPlaceName(): String =
        split(' ').filter { it.isNotEmpty() }.joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }

    private suspend fun fetchRemoteWebSuggestions(query: String) {
        if (query.isBlank()) return
        val settings = settingsState.value
        if (!settings.searchWeb) {
            _uiState.update { it.copy(webSuggestions = emptyList()) }
            return
        }
        val engine = settings.searchEngine
        // Tor, Web Suggestions off or a custom engine: nothing is asked of any suggestion service.
        if (!com.pixel.intelligentsearch.core.security.NetworkPolicy.webSuggestionsAllowed(customPrefs)) {
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
                        // Marked inside the update, so a local publish that loses the race to it re-reads the mark.
                        webSuggestionsQuery = query
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
    /** What was typed for each result before the history was cleared, for Undo. */
    @Volatile private var lastClearedPicks: Map<String, com.pixel.intelligentsearch.core.search.SelectionMemory.Entry> = emptyMap()
    private var historyClearJob: Job? = null

    /**
     * Records a search, unless the user turned Search History off or searches through Tor: a Tor user expects
     * no local trace, so nothing is kept while Tor is the search engine.
     */
    fun addSearchHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val settings = settingsState.value
        if (!settings.searchPreviousSearches || com.pixel.intelligentsearch.core.security.NetworkPolicy.isTorEngine(settings.searchEngine)) return
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
                selectionMemory.forgetQuery(trimmed)
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
                    // What was typed before each pick is search history too; how often results were opened stays.
                    lastClearedPicks = selectionMemory.clearPicks()
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
                    selectionMemory.restore(lastClearedPicks)
                    lastClearedPicks = emptyMap()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.w("IntelligentSearch", "Failed to restore search history", e)
            }
        }
    }

    fun dismissDirectAction(action: DirectAction) {
        // The clipboard is read again on every reopen; a dismissed suggestion for the same clip stays dismissed.
        if (action in initialClipboardActions) dismissedClipTexts += action.subtitle
        initialClipboardActions = initialClipboardActions.filter { it != action }
        _uiState.update { state ->
            // A dismissed action is no longer what Enter runs.
            state.copy(directActions = state.directActions.filter { it != action }, enterAction = state.enterAction.takeIf { it != action })
        }
    }

    fun dismissCalendarEvent(event: CalendarEvent) {
        _uiState.update { state ->
            state.copy(calendarEvents = state.calendarEvents.filter { it != event })
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Pins (the results' long-press menus and the Pinned row)
    // ---------------------------------------------------------------------------------------------


    /** Finds the pinned results that can be shown now (indexed, their source on, not hidden) for the empty overlay. */
    private fun refreshPinned() {
        pinnedJob?.cancel()
        pinnedJob = viewModelScope.launch(Dispatchers.Default) {
            val pinned = resultShortcutStore.current.pinned.mapNotNull { resolveShortcut(it.key) }
            ensureActive()
            _uiState.update { if (it.pinned == pinned) it else it.copy(pinned = pinned) }
        }
    }

    /**
     * The result a [com.pixel.intelligentsearch.core.search.ResultKeys] key names, if it can be offered now: an
     * [AppItem], [ContactItem], [FileItem] or [com.pixel.intelligentsearch.core.search.AndroidSettingResult]. Its
     * source must be on, and a hidden app stays hidden.
     */
    private fun resolveShortcut(key: String): Any? {
        val settings = settingsState.value
        return when (com.pixel.intelligentsearch.core.search.ResultKeys.kindOf(key)) {
            com.pixel.intelligentsearch.core.search.ResultKind.APP -> if (!settings.searchApps) null else {
                val app = unifiedSearchCoordinator.indexedResult(key) as? AppItem
                    ?: _uiState.value.allApps.firstOrNull { com.pixel.intelligentsearch.core.search.ResultKeys.app(it) == key }
                app?.takeIf { it.packageName !in settings.hiddenApps }
            }
            com.pixel.intelligentsearch.core.search.ResultKind.CONTACT ->
                if (settings.searchContacts) unifiedSearchCoordinator.indexedResult(key) as? ContactItem else null
            com.pixel.intelligentsearch.core.search.ResultKind.FILE ->
                if (settings.searchFiles) unifiedSearchCoordinator.indexedResult(key) as? FileItem else null
            com.pixel.intelligentsearch.core.search.ResultKind.SETTING ->
                com.pixel.intelligentsearch.core.search.ResultKeys.settingId(key)?.let(androidSettingsSearch::page)
            null -> null
        }
    }

    private var aiJob: Job? = null

    /**
     * Ask: sends the current query's question to the chosen AI provider (with the user's key) and streams its answer
     * into [SearchUiState.aiAnswer]. Only the Ask row's tap calls this; typing on cancels it.
     */
    fun askAi() {
        val query = _uiState.value.query
        if (_uiState.value.askAi == null) return
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            aiAnswers.ask(query).collect { state ->
                _uiState.update { if (it.query == query) it.copy(aiAnswer = state) else it }
            }
        }
    }

    fun setPinned(key: String, title: String, pinned: Boolean) =
        resultShortcutStore.update { it.withPinned(key, title, pinned) }

    /** Hide from Results: the app joins the hidden apps (Manage Hidden Apps lists it) and leaves the results now. */
    fun hideApp(packageName: String) {
        val hidden = settingsManager[SettingsManager.HIDDEN_APPS]
        if (packageName in hidden) return
        settingsManager.set(SettingsManager.HIDDEN_APPS, hidden + packageName)
        _uiState.update { state ->
            state.copy(
                filteredApps = state.filteredApps.filterNot { it.packageName == packageName },
                pinned = state.pinned.filterNot { it is AppItem && it.packageName == packageName }
            )
        }
    }

    /** Intent for a bang query such as "!yt cats", or null when [query] contains no known bang. */
    fun resolveBangIntent(query: String): android.content.Intent? =
        bangManager.parseBangQuery(query)?.let(bangManager::dispatchBangSearch)

    /** The web address a bang would open (never an app), or null if [query] isn't a bang. */
    fun resolveBangWebAddress(query: String): String? =
        bangManager.parseBangQuery(query)?.let(bangManager::bangWebUri)?.toString()

    /**
     * Remembers that [result] (an [AppItem], [ContactItem] or [FileItem]) was opened from search, and what was typed
     * for it, so ranking learns from it. What was typed is kept only while searches are remembered (Previous Searches
     * on, and not with Tor as the engine); Private Space apps leave no trace.
     */
    fun recordOpened(result: Any) {
        val key = when (result) {
            is AppItem -> if (result.isPrivateProfile || result.profileType == ProfileType.PRIVATE) null else com.pixel.intelligentsearch.core.search.ResultKeys.app(result)
            is ContactItem -> com.pixel.intelligentsearch.core.search.ResultKeys.contact(result)
            is FileItem -> com.pixel.intelligentsearch.core.search.ResultKeys.file(result)
            else -> null
        } ?: return
        val settings = settingsState.value
        val keepsSearches = settings.searchPreviousSearches &&
            !com.pixel.intelligentsearch.core.security.NetworkPolicy.isTorEngine(settings.searchEngine)
        val query = _uiState.value.query.takeIf { keepsSearches }
        viewModelScope.launch(Dispatchers.IO) { selectionMemory.record(key, query) }
    }

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
