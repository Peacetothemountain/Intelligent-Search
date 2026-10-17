package com.ngdesigns.intelligentsearch.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ngdesigns.intelligentsearch.AppGraph
import com.ngdesigns.intelligentsearch.data.SettingEntry
import com.ngdesigns.intelligentsearch.data.Snapshot
import com.ngdesigns.intelligentsearch.data.device.AppEntry
import com.ngdesigns.intelligentsearch.data.device.Suggestions
import com.ngdesigns.intelligentsearch.data.history.Memory
import com.ngdesigns.intelligentsearch.data.history.PastSearch
import com.ngdesigns.intelligentsearch.data.settings.Settings
import com.ngdesigns.intelligentsearch.engine.Kind
import com.ngdesigns.intelligentsearch.engine.Search
import com.ngdesigns.intelligentsearch.engine.SearchPreferences
import com.ngdesigns.intelligentsearch.engine.SearchResult
import com.ngdesigns.intelligentsearch.engine.web.SearchEngine
import com.ngdesigns.intelligentsearch.engine.web.WebShortcut
import com.ngdesigns.intelligentsearch.engine.web.WebShortcuts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which results the scope buttons show. */
enum class Scope { All, Apps, People, Files, Web }

data class SearchUiState(
    val settings: Settings = Settings(),
    val query: String = "",
    val scope: Scope = Scope.All,
    val result: SearchResult? = null,
    val webSuggestions: List<String> = emptyList(),
    val recents: List<PastSearch> = emptyList(),
    val suggestedApps: List<AppEntry> = emptyList(),
    val snapshot: Snapshot = Snapshot(),
) {
    val engine: SearchEngine get() = SearchEngine.named(settings.web.engine, settings.web.customTemplate)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(private val graph: AppGraph, private val settingsIndex: List<SettingEntry>) : ViewModel() {

    private val query = MutableStateFlow("")
    private val scope = MutableStateFlow(Scope.All)
    private val refresh = MutableSharedFlow<Unit>(replay = 1).also { it.tryEmit(Unit) }

    private val settings: StateFlow<Settings> = graph.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    private val memory: StateFlow<Memory> = graph.history.memory.stateIn(viewModelScope, SharingStarted.Eagerly, Memory())

    /** Everything searchable, rebuilt when what it depends on changes. */
    private val snapshot: StateFlow<Snapshot> = combine(
        settings.map { SnapshotInputs(it) }.distinctUntilChanged(),
        memory,
        merge(refresh, graph.device.apps.changes().onStart { emit(Unit) }),
    ) { inputs, mem, _ -> inputs to mem }
        .debounce(80)
        .mapLatest { (inputs, mem) -> graph.snapshots.build(inputs.settings, mem, settingsIndex) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Snapshot())

    private val result = combine(query, snapshot, settings) { q, snap, s -> Triple(q, snap, s) }
        .mapLatest { (q, snap, s) -> Search.run(q, snap.catalogs, preferences(s)) }
        .flowOn(Dispatchers.Default)

    private val webSuggestions = combine(query, settings) { q, s -> q to s }
        .debounce(140)
        .mapLatest { (q, s) ->
            val engine = SearchEngine.named(s.web.engine, s.web.customTemplate)
            if (!s.sources.webSuggestions || q.isBlank() || s.ranking.webResults == 0 || q.trimStart().startsWith(s.web.shortcutSymbol)) emptyList()
            else graph.suggestions.suggestions(q, engine, s.ranking.webResults)
        }

    private val suggested = combine(snapshot, memory) { snap, mem ->
        Suggestions.apps(snap.apps, graph.predictions.predictedPackages(), snap.usage, mem.launches, System.currentTimeMillis())
    }.flowOn(Dispatchers.Default)

    val state: StateFlow<SearchUiState> = combine(
        combine(settings, query, scope, snapshot) { s, q, sc, snap -> Quad(s, q, sc, snap) },
        result, webSuggestions.onStart { emit(emptyList()) }, memory, suggested.onStart { emit(emptyList()) },
    ) { (s, q, sc, snap), r, web, mem, apps ->
        SearchUiState(
            settings = s, query = q, scope = sc, result = r.takeIf { it.query == q }, webSuggestions = web,
            recents = if (s.sources.history) mem.searches.take(8) else emptyList(), suggestedApps = apps, snapshot = snap,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchUiState())

    fun onQuery(text: String) { query.value = text }

    fun onScope(value: Scope) { scope.value = value }

    /** Rebuild the snapshot, e.g. when the screen comes back (contacts or files may have changed meanwhile). */
    fun refresh() { refresh.tryEmit(Unit) }

    fun remember(query: String) = viewModelScope.launch { if (settings.value.sources.history) graph.history.record(query) }

    fun forget(query: String) = viewModelScope.launch { graph.history.remove(query) }

    fun clearRecents() = viewModelScope.launch { graph.history.clear() }

    fun launched(packageName: String) = viewModelScope.launch { graph.history.recordLaunch(packageName) }

    fun hideApp(packageName: String) = viewModelScope.launch {
        graph.settings.update { it.copy(privacy = it.privacy.copy(hiddenApps = it.privacy.hiddenApps + packageName)) }
    }

    fun unhideApp(packageName: String) = viewModelScope.launch {
        graph.settings.update { it.copy(privacy = it.privacy.copy(hiddenApps = it.privacy.hiddenApps - packageName)) }
    }

    /** Only the parts of the settings the snapshot depends on, so unrelated changes don't rebuild it. */
    private data class SnapshotInputs(val settings: Settings) {
        private val key = listOf(settings.sources, settings.privacy.hiddenApps, settings.files)
        override fun equals(other: Any?) = other is SnapshotInputs && other.key == key
        override fun hashCode() = key.hashCode()
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    companion object {
        /** How the user's settings shape a search. */
        fun preferences(s: Settings): SearchPreferences = SearchPreferences(
            priority = mapOf(
                Kind.App to s.ranking.apps, Kind.Shortcut to s.ranking.apps, Kind.Contact to s.ranking.contacts,
                Kind.File to s.ranking.files, Kind.Web to s.ranking.web, Kind.History to s.ranking.web,
                // Settings are a help, not the main thing people search for: below an equally good app or contact.
                Kind.Setting to -1,
            ),
            limits = mapOf(
                Kind.Contact to s.ranking.contactResults, Kind.File to s.ranking.fileResults,
                Kind.Shortcut to s.ranking.shortcutResults, Kind.History to 4, Kind.Setting to 3, Kind.Event to 3, Kind.App to 8,
            ),
            typos = s.behaviour.typos,
            calculator = s.sources.calculator,
            shortcutSymbol = s.web.shortcutSymbol,
            shortcuts = if (s.web.shortcutsEnabled) shortcuts(s) else emptyList(),
        )

        fun shortcuts(s: Settings): List<WebShortcut> =
            s.web.customShortcuts.mapNotNull { WebShortcuts.custom(it.key, it.name, it.template, it.appPackage) } +
                WebShortcuts.builtIn.filter { it.key !in s.web.disabledBuiltIns }
    }
}
