package com.ngdesigns.intelligentsearch.ui.search

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ngdesigns.intelligentsearch.data.device.AppEntry
import com.ngdesigns.intelligentsearch.data.device.Target
import com.ngdesigns.intelligentsearch.engine.Instant
import com.ngdesigns.intelligentsearch.engine.Kind
import com.ngdesigns.intelligentsearch.engine.Ranked
import com.ngdesigns.intelligentsearch.engine.actions.Action
import com.ngdesigns.intelligentsearch.engine.toggles.Toggle
import com.ngdesigns.intelligentsearch.engine.web.WebShortcuts
import com.ngdesigns.intelligentsearch.graph
import com.ngdesigns.intelligentsearch.launch.Torch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The search screen with its view model: keeps the text field and the model in step and turns taps into launches.
 * [onLeave] runs after something opened outside the app, so the screen gets out of the way.
 */
@Composable
fun SearchRoute(viewModel: SearchViewModel, onOpenSetting: (String) -> Unit, onVoice: () -> Unit, onLeave: () -> Unit, initialQuery: String = "") {
    val context = LocalContext.current
    val launcher = remember { context.graph.launcher }
    val torch = remember { Torch(context) }
    val torchOn by torch.state.collectAsState(initial = false)
    val ui by viewModel.state.collectAsState()
    val field = rememberTextFieldState(initialQuery)
    val focus = remember { FocusRequester() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var createOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collectLatest(viewModel::onQuery) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun leave(ok: Boolean) { if (ok) onLeave() else scope.launch { snackbar.showSnackbar("Nothing on this phone can open that") } }

    val results = object : ResultActions {
        override fun open(item: Ranked) {
            val target = ui.snapshot.target(item.candidate) ?: return
            when (target) {
                is Target.Setting -> onOpenSetting(target.key)
                is Target.Query -> field.fill(target.text)
                is Target.App -> { viewModel.launched(target.packageName); remember(); leave(launcher.open(target)) }
                is Target.Contact -> {
                    remember()
                    val direct = ui.settings.behaviour.contactDirectCall
                    leave(if (direct && target.phone != null) launcher.call(target.phone, direct = true) else launcher.open(target))
                }
                else -> { remember(); leave(launcher.open(target)) }
            }
        }
        override fun insert(text: String) = field.fill(text)
        override fun openApp(app: AppEntry) { viewModel.launched(app.target.packageName); leave(launcher.open(app.target)) }
        override fun call(item: Ranked) {
            val phone = (ui.snapshot.target(item.candidate) as? Target.Contact)?.phone ?: return
            leave(launcher.call(phone, direct = ui.settings.behaviour.contactDirectCall))
        }
        override fun message(item: Ranked) {
            val phone = (ui.snapshot.target(item.candidate) as? Target.Contact)?.phone ?: return
            leave(launcher.message(phone))
        }
        override fun appInfo(item: Ranked) { (ui.snapshot.target(item.candidate) as? Target.App)?.let { leave(launcher.appInfo(it.packageName)) } }
        override fun hide(item: Ranked) {
            val app = ui.snapshot.target(item.candidate) as? Target.App ?: return
            viewModel.hideApp(app.packageName)
            scope.launch {
                if (snackbar.showSnackbar("${item.candidate.title} hidden from search", "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                    viewModel.unhideApp(app.packageName)
                }
            }
        }
        override fun uninstall(item: Ranked) { (ui.snapshot.target(item.candidate) as? Target.App)?.let { launcher.uninstall(it.packageName) } }
        override fun forget(query: String) {
            viewModel.forget(query)
            scope.launch {
                if (snackbar.showSnackbar("Removed “$query” from history", "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                    viewModel.remember(query)
                }
            }
        }
        override fun searchWeb(text: String) { viewModel.remember(text); leave(launcher.search(text, ui.engine)) }
        private fun remember() { field.text.toString().takeIf { it.isNotBlank() }?.let(viewModel::remember) }
    }

    val answers = object : AnswerActions {
        override fun copy(text: String) {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Result", text))
        }
        override fun openCalculator() { leave(launcher.calculator()) }
        override fun run(action: Action) {
            viewModel.remember(field.text.toString())
            leave(launcher.run(action) { name -> resolvePhone(ui, name, launcher) })
        }
        override fun toggle(toggle: Toggle, on: Boolean) {
            if (toggle == Toggle.Flashlight && torch.exists) torch.set(on) else leave(launcher.toggle(toggle))
        }
        override fun shortcut(use: WebShortcuts.Use) {
            viewModel.remember(field.text.toString())
            val app = use.shortcut.appUrl(use.terms)?.takeIf { use.terms.isNotBlank() }
            val opened = app?.let { launcher.openUri(it) } ?: false
            leave(opened || launcher.web(use.shortcut.url(use.terms)))
        }
        override fun address(url: String) { viewModel.remember(field.text.toString()); leave(launcher.web(url)) }
    }

    fun submit() {
        val r = ui.result ?: return
        val first = r.instants.firstOrNull()
        when {
            first is Instant.Address -> answers.address(first.url)
            first is Instant.Shortcut -> answers.shortcut(first.use)
            r.best != null -> results.open(r.best!!)
            field.text.isNotBlank() -> results.searchWeb(field.text.toString())
        }
    }

    SearchScreen(
        ui = ui, field = field, focus = focus, torchOn = torchOn, snackbar = snackbar,
        createOpen = createOpen, onCreateOpen = { createOpen = it },
        onStarter = { s -> if (s.label == "Note") leave(launcher.newNote()) else field.fill(s.text) },
        onScope = viewModel::onScope, onSubmit = ::submit, onVoice = onVoice, onLens = { leave(launcher.lens()) },
        results = results, answers = answers,
    )
}

private fun resolvePhone(ui: SearchUiState, name: String, launcher: com.ngdesigns.intelligentsearch.launch.Launcher): String? {
    val contacts = ui.snapshot.catalogs[Kind.Contact] ?: return null
    val match = contacts.search(name, 1).firstOrNull() ?: return null
    if (match.match.quality < 0.8f) return null
    val target = ui.snapshot.target(match.candidate) as? Target.Contact ?: return null
    return target.phone ?: launcher.contactPhone(target.lookupUri)
}

private fun Scope.shows(kind: Kind) = when (this) {
    Scope.All -> true
    Scope.Apps -> kind == Kind.App || kind == Kind.Shortcut
    Scope.People -> kind == Kind.Contact
    Scope.Files -> kind == Kind.File
    Scope.Web -> kind == Kind.Web || kind == Kind.History
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    ui: SearchUiState,
    field: TextFieldState,
    focus: FocusRequester,
    torchOn: Boolean,
    snackbar: SnackbarHostState,
    createOpen: Boolean,
    onCreateOpen: (Boolean) -> Unit,
    onStarter: (Starter) -> Unit,
    onScope: (Scope) -> Unit,
    onSubmit: () -> Unit,
    onVoice: () -> Unit,
    onLens: () -> Unit,
    results: ResultActions,
    answers: AnswerActions,
    modifier: Modifier = Modifier,
) {
    val s = ui.settings
    val atBottom = s.behaviour.barAtBottom
    val bottomUp = atBottom && s.behaviour.resultsBottomUp
    val empty = ui.query.isBlank()
    val working = !empty && ui.result == null

    val bar: @Composable () -> Unit = {
        Column {
            AnimatedVisibility(visible = !empty) {
                ScopeButtons(ui.scope, onScope, Modifier.padding(bottom = 8.dp))
            }
            SearchInput(
                state = field, completion = ui.result?.completion, working = working, animate = s.look.animations,
                focusRequester = focus, onSubmit = onSubmit, onVoice = onVoice, onLens = onLens,
            )
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            if (!atBottom) { Spacer(Modifier.height(8.dp)); bar() }
            LazyColumn(
                Modifier.weight(1f),
                reverseLayout = bottomUp,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = if (empty && atBottom) 72.dp else 0.dp),
            ) {
                if (empty) home(ui, results, bottomUp) else results(ui, torchOn, results, answers, bottomUp)
            }
            if (atBottom) { bar(); Spacer(Modifier.height(12.dp)) }
        }
        if (empty) {
            CreateMenu(
                expanded = createOpen, onExpanded = onCreateOpen, onStart = onStarter,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().imePadding().padding(bottom = if (atBottom) 84.dp else 16.dp),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding())
    }
}

private fun LazyListScope.home(ui: SearchUiState, actions: ResultActions, bottomUp: Boolean) {
    val blocks = listOf<Pair<String, @Composable () -> Unit>>(
        "greeting" to { Greeting(Modifier.padding(top = 24.dp, bottom = 16.dp)) },
        "recents" to { RecentChips(ui.recents, onSearch = actions::insert) },
        "apps-label" to { if (ui.suggestedApps.isNotEmpty()) SectionHeader("Suggested", null) },
        "apps" to {
            SuggestedApps(ui.suggestedApps, ui.settings.look.iconShape, columns = 4, onOpen = actions::openApp)
        },
    )
    (if (bottomUp) blocks.reversed() else blocks).forEach { (key, content) -> item(key) { content() } }
}

private fun LazyListScope.results(ui: SearchUiState, torchOn: Boolean, actions: ResultActions, answers: AnswerActions, bottomUp: Boolean) {
    val r = ui.result ?: return
    val bestId = r.best?.candidate?.id
    val gap: @Composable () -> Unit = { Spacer(Modifier.height(8.dp)) }
    // Nearest the search bar first; reverseLayout puts the first item at the bottom when the bar is there.
    r.instants.forEachIndexed { i, instant ->
        item("instant-$i") { Column { InstantAnswer(instant, torchOn, answers); gap() } }
    }
    if (ui.scope == Scope.All) r.best?.let { best ->
        item("best") { Column { BestMatchCard(best, ui.snapshot.target(best.candidate), actions); gap() } }
    }
    r.sections.filter { ui.scope.shows(it.kind) }.forEach { section ->
        val items = section.items.filter { it.candidate.id != bestId || ui.scope != Scope.All }
        if (items.isEmpty()) return@forEach
        // In a bottom-up list the first item sits lowest, so the header goes last (on top) and the corner shapes count
        // from the bottom; the best-ranked row is always the one nearest the search bar.
        val header: LazyListScope.() -> Unit = { item("head-${section.kind}") { SectionHeader(sectionTitle(section.kind), items.size) } }
        if (!bottomUp) header()
        itemsIndexed(items, key = { _, it -> it.candidate.id }) { i, item ->
            Column {
                ResultRow(item, ui.snapshot.target(item.candidate), if (bottomUp) items.size - 1 - i else i, items.size, ui.settings.look.iconShape, actions)
                Spacer(Modifier.height(2.dp))
            }
        }
        if (bottomUp) header()
        item("gap-${section.kind}") { gap() }
    }
    if (ui.scope.shows(Kind.Web) && ui.webSuggestions.isNotEmpty()) {
        if (!bottomUp) item("head-web") { SectionHeader("Web", ui.webSuggestions.size) }
        itemsIndexed(ui.webSuggestions, key = { _, s -> "web:$s" }) { i, s -> WebRow(s, if (bottomUp) ui.webSuggestions.size - 1 - i else i, ui.webSuggestions.size, actions) }
        if (bottomUp) item("head-web") { SectionHeader("Web", ui.webSuggestions.size) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WebRow(text: String, index: Int, count: Int, actions: ResultActions) {
    Column(Modifier.padding(horizontal = 12.dp)) {
        SegmentedListItem(
            onClick = { actions.searchWeb(text) },
            shapes = ListItemDefaults.segmentedShapes(index, count),
            colors = rowColors(),
            leadingContent = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingContent = { IconButton(onClick = { actions.insert(text) }) { Icon(Icons.Rounded.NorthWest, contentDescription = "Put in search bar") } },
        ) { Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        Spacer(Modifier.height(2.dp))
    }
}
