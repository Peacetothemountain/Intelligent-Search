package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.system.openPendingOrApp
import com.pixel.intelligentsearch.core.glance.IndexedNotification
import com.pixel.intelligentsearch.core.glance.GlanceItem
import com.pixel.intelligentsearch.core.system.launchSafeIntent
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.pixel.intelligentsearch.core.ai.AiAnswerState
import com.pixel.intelligentsearch.core.ai.AiAnswers
import com.pixel.intelligentsearch.core.ai.AiProvider
import com.pixel.intelligentsearch.core.answers.LiveAnswer
import com.pixel.intelligentsearch.core.bangs.AiAssistants
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.FileTypeGroup
import com.pixel.intelligentsearch.core.data.SavedLink
import com.pixel.intelligentsearch.core.data.WebSuggestion
import com.pixel.intelligentsearch.core.search.AndroidSettingResult
import com.pixel.intelligentsearch.core.search.ResultKeys
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveCornerTokens
import com.pixel.intelligentsearch.core.util.findActivity
import com.pixel.intelligentsearch.feature.settings.focusRing

/*
 * Arrow-key navigation of the overlay's results with a physical keyboard (tablets, foldables, Chromebooks, Bluetooth
 * keyboards). Focus stays in the search field the whole time, as in Google's and Pixel's search boxes: the arrow keys
 * move a highlight over the results, drawn as the same focus ring a focused control shows, and Enter opens it. TalkBack
 * and touch never see the highlight; it appears only once an arrow key or Tab is pressed.
 */

/** What a highlighted result opens. */
internal sealed interface NavTarget {
    /**
     * An app, contact, file, Android Settings page or Saved Link ([AppItem], [ContactItem], [FileItem],
     * [AndroidSettingResult], [SavedLink]), opened as its own row opens it; an app opens out of the icon under [iconKey].
     */
    data class Result(val item: Any, val iconKey: String? = null) : NavTarget

    /** A past search or a web suggestion: searches the web for [query]. [suggestion] has the entity's picture and description. */
    data class WebQuery(val query: String, val suggestion: WebSuggestion? = null, val isRecent: Boolean = false) : NavTarget

    /** "Did you mean": fills [corrected] into the search bar. */
    data class Correction(val corrected: String) : NavTarget

    /** A suggested action (a timer, a call, something on the clipboard). */
    data class Action(val action: DirectAction) : NavTarget

    /** An instant answer, which opens its main action. */
    data class Answer(val action: AnswerAction) : NavTarget

    /** AI Answers' "Ask" row: sends [question] to [provider], as tapping the row does. */
    data class AskAi(val provider: AiProvider, val question: String) : NavTarget

    /** The file-type chip above the files ("PDFs Only"): removes the limit to [group] for this query. */
    data class RemoveFileTypeFilter(val group: FileTypeGroup) : NavTarget

    /** A matching notification: does what tapping it does, or opens its app. */
    data class Notification(val entry: IndexedNotification) : NavTarget

    /** An At a Glance card: opens what it is about ([openGlanceItem]). */
    data class Glance(val item: GlanceItem) : NavTarget
}

/** The main action of an instant answer's card. */
internal sealed interface AnswerAction {
    data class WebSearch(val query: String) : AnswerAction
    data class OpenAddress(val address: String) : AnswerAction
    data object OpenCalculator : AnswerAction
    data object ShowAlarms : AnswerAction
    /** The weather app, else a web search for [query]. */
    data class OpenWeather(val query: String) : AnswerAction
    /** A translation's "Translate in Google Translate": [text] from [source] to [target]. */
    data class Translate(val text: String, val source: String?, val target: String) : AnswerAction
    /** An AI answer's "Continue in": [question] in [provider]'s assistant app or site. */
    data class ContinueInAssistant(val provider: AiProvider, val question: String) : AnswerAction
}

/**
 * One place the highlight can stop: [key] is unique among the stops, [listKey] is the results list item it sits in
 * (several stops share the app row's), and [target] is what Enter opens.
 */
internal data class NavStop(val key: String, val listKey: String, val target: NavTarget)

/** The keys that move the highlight; anything else (and any key with Ctrl, Alt or Meta) is the search field's. */
internal enum class NavKey { Up, Down, Left, Right, Tab, ShiftTab, Enter, Escape }

/** The highlighted stop: its row (a result row, or the whole app row) and its place in that row. */
internal data class NavPosition(val row: Int, val column: Int)

/** What a [NavKey] does. */
internal sealed interface NavOutcome {
    /** Highlights [to], or with null takes the highlight off and back to the search field. Uses the key up. */
    data class Move(val to: NavPosition?) : NavOutcome

    /** Opens the highlighted result. */
    data object Open : NavOutcome

    /** Escape with something typed: clears it. */
    data object ClearQuery : NavOutcome

    /** Escape with nothing typed: closes search. */
    data object Close : NavOutcome

    /** Takes the highlight off and lets the key go on: Tab past the last result moves focus to what follows. */
    data object Leave : NavOutcome

    /** Not for the results: the search field (or Android's focus) handles the key as it always has. */
    data object PassThrough : NavOutcome
}

/** The rules for the keyboard highlight; pure, so every sequence can be tested. */
internal object OverlayKeyboardNav {

    /**
     * What [key] does with the highlight at [at] (null: none, the field alone has focus) over rows of [rowSizes] stops.
     * Down from the field highlights the first result and stops at the last; Up from the first goes back to the field.
     * Left and Right move along a row of several (the app row). Tab and Shift+Tab step through every stop in order,
     * Shift+Tab from the first back to the field and Tab past the last on out of the results. Enter opens the
     * highlighted result, and without one is the field's (Enter Key Action). Escape clears what was typed, then closes.
     */
    fun reduce(at: NavPosition?, key: NavKey, rowSizes: List<Int>, queryEmpty: Boolean): NavOutcome {
        val rows = rowSizes
        val current = at?.takeIf { it.row in rows.indices && it.column in 0 until rows[it.row] }
        return when (key) {
            NavKey.Escape -> if (queryEmpty) NavOutcome.Close else NavOutcome.ClearQuery
            NavKey.Enter -> if (current == null) NavOutcome.PassThrough else NavOutcome.Open
            NavKey.Down -> when {
                rows.isEmpty() -> NavOutcome.PassThrough
                current == null -> NavOutcome.Move(NavPosition(0, 0))
                current.row == rows.lastIndex -> NavOutcome.Move(current)
                else -> NavOutcome.Move(NavPosition(current.row + 1, 0))
            }
            NavKey.Up -> when {
                current == null -> NavOutcome.PassThrough
                current.row == 0 -> NavOutcome.Move(null)
                else -> NavOutcome.Move(NavPosition(current.row - 1, 0))
            }
            NavKey.Left, NavKey.Right -> when {
                current == null || rows[current.row] < 2 -> NavOutcome.PassThrough
                key == NavKey.Right -> NavOutcome.Move(current.copy(column = (current.column + 1).coerceAtMost(rows[current.row] - 1)))
                else -> NavOutcome.Move(current.copy(column = (current.column - 1).coerceAtLeast(0)))
            }
            NavKey.Tab -> when {
                current == null -> if (rows.isEmpty()) NavOutcome.PassThrough else NavOutcome.Move(NavPosition(0, 0))
                current.column < rows[current.row] - 1 -> NavOutcome.Move(current.copy(column = current.column + 1))
                current.row < rows.lastIndex -> NavOutcome.Move(NavPosition(current.row + 1, 0))
                else -> NavOutcome.Leave
            }
            NavKey.ShiftTab -> when {
                current == null -> NavOutcome.PassThrough
                current.column > 0 -> NavOutcome.Move(current.copy(column = current.column - 1))
                current.row > 0 -> NavOutcome.Move(NavPosition(current.row - 1, rows[current.row - 1] - 1))
                else -> NavOutcome.Move(null)
            }
        }
    }

    /** Where the stop under [key] is in [rows], or null when it isn't there (the results changed under it). */
    fun positionOf(key: String?, rows: List<List<NavStop>>): NavPosition? {
        if (key == null) return null
        rows.forEachIndexed { r, row ->
            val c = row.indexOfFirst { it.key == key }
            if (c >= 0) return NavPosition(r, c)
        }
        return null
    }

    /**
     * The [NavKey] for a key press, or null when it isn't one: key releases, Shift+arrows (selecting text), and keys
     * held with Ctrl, Alt or Meta (Ctrl+Backspace, Ctrl+A, Alt+arrows) stay the search field's, as everywhere on
     * Android. With [rtl], Left and Right swap, so Right always moves the way the app row reads.
     */
    fun keyOf(event: KeyEvent, rtl: Boolean): NavKey? {
        if (event.type != KeyEventType.KeyDown) return null
        return navKeyOf(event.key, event.isShiftPressed, event.isCtrlPressed || event.isAltPressed || event.isMetaPressed, rtl)
    }

    /** [keyOf] for a [key] with the given modifiers held. */
    fun navKeyOf(key: Key, shift: Boolean, otherModifier: Boolean, rtl: Boolean): NavKey? {
        if (otherModifier) return null
        return when (key) {
            Key.DirectionDown -> NavKey.Down.takeUnless { shift }
            Key.DirectionUp -> NavKey.Up.takeUnless { shift }
            Key.DirectionRight -> (if (rtl) NavKey.Left else NavKey.Right).takeUnless { shift }
            Key.DirectionLeft -> (if (rtl) NavKey.Right else NavKey.Left).takeUnless { shift }
            Key.Tab -> if (shift) NavKey.ShiftTab else NavKey.Tab
            Key.Enter, Key.NumPadEnter -> NavKey.Enter.takeUnless { shift }
            Key.Escape -> NavKey.Escape
            else -> null
        }
    }
}

/** The results list's item keys, shared by the list and the keyboard stops so they always agree. */
internal object ResultListKeys {
    const val DID_YOU_MEAN = "did_you_mean"
    const val BEST_MATCH = "best_match_hero_card"
    const val PINNED = "pinned_results"
    const val MATH = "math_result"
    const val LIVE_ANSWER = "live_answer"
    const val INSTANT_ANSWER = "instant_answer"
    const val APPS_ROW = "apps_row"
    const val ASK_AI = "ask_ai"
    const val AI_ANSWER = "ai_answer"
    const val FILE_TYPE_FILTER = "files_type_filter"

    fun directAction(action: DirectAction) = "direct_action_${action.title}_${action.subtitle}"
    fun recent(query: String) = "recent_$query"
    fun app(app: AppItem) = LaunchIconKeys.app(app)
    fun setting(setting: AndroidSettingResult) = "android_setting_${setting.id}"
    fun suggestion(query: String) = "sugg_$query"
    fun contact(contact: ContactItem) = "contact_${contact.lookupUri.ifBlank { "${contact.name}_${contact.phoneNumber}" }}"
    fun file(file: FileItem) = "file_${file.uri}"
    fun savedLink(link: SavedLink) = "saved_link_${link.url}"
    fun notification(entry: IndexedNotification) = "notification_${entry.key}"

    /** A stop inside a row of several: the row's key and the item's. */
    fun inRow(rowKey: String, itemKey: String) = "$rowKey/$itemKey"

    /** A pinned result's key in the Pinned row. */
    fun pinned(result: Any): String = inRow(PINNED, when (result) {
        is AppItem -> ResultKeys.app(result)
        is ContactItem -> ResultKeys.contact(result) ?: contact(result)
        is FileItem -> ResultKeys.file(result)
        is AndroidSettingResult -> ResultKeys.setting(result) ?: setting(result)
        is SavedLink -> ResultKeys.link(result)
        else -> result.hashCode().toString()
    })
}

/**
 * Everything the keyboard can reach in the results, in the order the list shows it. [answers] are the answer cards'
 * list keys and main actions; [sectionOrder] is [resultSectionOrder]'s; each section's list is what it shows.
 */
internal data class NavSources(
    val spellingSuggestion: String? = null,
    val directActions: List<DirectAction> = emptyList(),
    /** AI Answers' offer for the query's question, shown as the "Ask" row while there is no [aiAnswer]. */
    val aiAsk: Pair<AiProvider, String>? = null,
    /** The AI answer for this query, whose card's main action is "Continue in". */
    val aiAnswer: AiAnswerState? = null,
    val savedLinks: List<SavedLink> = emptyList(),
    /** At a Glance's cards, above the empty search. */
    val glance: List<GlanceItem> = emptyList(),
    val bestMatch: Any? = null,
    val pinned: List<Any> = emptyList(),
    val recents: List<String> = emptyList(),
    val answers: List<Pair<String, AnswerAction>> = emptyList(),
    val sectionOrder: List<String> = emptyList(),
    val apps: List<AppItem> = emptyList(),
    val androidSettings: List<AndroidSettingResult> = emptyList(),
    val suggestions: List<WebSuggestion> = emptyList(),
    /** The user's past searches, so a suggestion that is one is marked as such. */
    val history: List<String> = emptyList(),
    val contacts: List<ContactItem> = emptyList(),
    /** The file type the query limits [files] to; its chip leads the files. */
    val fileTypeFilter: FileTypeGroup? = null,
    val files: List<FileItem> = emptyList(),
    /** Matching notifications, after the phone's other results. */
    val notifications: List<IndexedNotification> = emptyList()
) {
    /** The stops, a row per result (the app and Pinned rows are one row of several). */
    fun rows(): List<List<NavStop>> = buildList {
        fun single(key: String, target: NavTarget) = add(listOf(NavStop(key, key, target)))
        spellingSuggestion?.let { single(ResultListKeys.DID_YOU_MEAN, NavTarget.Correction(it)) }
        directActions.distinctBy(ResultListKeys::directAction).forEach { single(ResultListKeys.directAction(it), NavTarget.Action(it)) }
        val answer = aiAnswer
        if (answer != null) single(ResultListKeys.AI_ANSWER, NavTarget.Answer(AnswerAction.ContinueInAssistant(answer.provider, answer.question)))
        else aiAsk?.let { (provider, question) -> single(ResultListKeys.ASK_AI, NavTarget.AskAi(provider, question)) }
        savedLinks.distinctBy(ResultListKeys::savedLink).forEach { single(ResultListKeys.savedLink(it), NavTarget.Result(it)) }
        glance.distinctBy { it.key }.forEach { single(it.key, NavTarget.Glance(it)) }
        bestMatch?.let { single(ResultListKeys.BEST_MATCH, NavTarget.Result(it, (it as? AppItem)?.let { LaunchIconKeys.BEST_MATCH })) }
        if (pinned.isNotEmpty()) {
            add(pinned.distinctBy(ResultListKeys::pinned).map { item ->
                NavStop(ResultListKeys.pinned(item), ResultListKeys.PINNED, NavTarget.Result(item, (item as? AppItem)?.let(::pinnedIconKey)))
            })
        }
        recents.distinct().forEach { single(ResultListKeys.recent(it), NavTarget.WebQuery(it, isRecent = true)) }
        answers.forEach { (key, action) -> single(key, NavTarget.Answer(action)) }
        for (section in sectionOrder) when (section) {
            "apps" -> if (apps.isNotEmpty()) add(apps.distinctBy(ResultListKeys::app).map { app ->
                NavStop(ResultListKeys.inRow(ResultListKeys.APPS_ROW, ResultListKeys.app(app)), ResultListKeys.APPS_ROW, NavTarget.Result(app, LaunchIconKeys.app(app)))
            })
            "android_settings" -> androidSettings.distinctBy(ResultListKeys::setting).forEach { single(ResultListKeys.setting(it), NavTarget.Result(it)) }
            "web" -> suggestions.distinctBy { it.query }.forEach { suggestion ->
                val isRecent = history.any { it.equals(suggestion.query, ignoreCase = true) }
                single(ResultListKeys.suggestion(suggestion.query), NavTarget.WebQuery(suggestion.query, suggestion, isRecent))
            }
            "contacts" -> contacts.distinctBy(ResultListKeys::contact).forEach { single(ResultListKeys.contact(it), NavTarget.Result(it)) }
            "files" -> {
                fileTypeFilter?.let { single(ResultListKeys.FILE_TYPE_FILTER, NavTarget.RemoveFileTypeFilter(it)) }
                files.distinctBy(ResultListKeys::file).forEach { single(ResultListKeys.file(it), NavTarget.Result(it)) }
            }
        }
        notifications.distinctBy { it.key }.forEach { single(ResultListKeys.notification(it), NavTarget.Notification(it)) }
    }
}

/**
 * The suggestions the web section lists for [query]: past searches containing it first, then the engine's, as
 * Google lists them, up to [maxCount].
 */
internal fun displayedSuggestions(
    query: String,
    recentSearches: List<String>,
    searchPreviousSearches: Boolean,
    webSuggestions: List<WebSuggestion>,
    maxCount: Int
): List<WebSuggestion> {
    val trimmed = query.trim()
    val matchingRecent = if (!searchPreviousSearches || trimmed.isEmpty()) emptyList()
    else recentSearches.filter { it.contains(trimmed, ignoreCase = true) }.distinct()
    val nonRecentWeb = webSuggestions.filter { web -> matchingRecent.none { it.equals(web.query, ignoreCase = true) } }
    return (matchingRecent.map { WebSuggestion(it) } + nonRecentWeb).distinctBy { it.query }.take(maxCount.coerceAtLeast(1))
}

/** The main action of [answer]'s card, or null for a card without one (a prompt, a conversion to copy). */
internal fun liveAnswerAction(answer: LiveAnswer): AnswerAction? = when (answer) {
    is LiveAnswer.Definition -> AnswerAction.WebSearch("define ${answer.entry.word}")
    is LiveAnswer.Weather -> AnswerAction.OpenWeather(answer.query)
    is LiveAnswer.Translation -> AnswerAction.Translate(answer.text, answer.source, answer.target)
    is LiveAnswer.WeatherNeedsLocation, is LiveAnswer.ScreenTime -> null
}

/** The main action of the tap-through answer card [answer] for [query], or null when it has none. */
internal fun instantAnswerAction(answer: InstantAnswer, query: String): AnswerAction? = when (answer.iconType) {
    "conversion", "currency" -> null
    "dictionary" -> AnswerAction.WebSearch("define ${answer.title}")
    "url" -> AnswerAction.OpenAddress(answer.title)
    "time" -> AnswerAction.ShowAlarms
    else -> AnswerAction.WebSearch(query)
}

/** What the keyboard can reach in the overlay [s] shows now, mirroring OverlaySearchResults' sections. */
internal fun navSources(s: OverlayScope): NavSources {
    val uiState = s.uiState
    val settings = s.settingsState
    val query = uiState.query
    val answers = buildList {
        if (uiState.mathResult != null) add(ResultListKeys.MATH to AnswerAction.OpenCalculator)
        val live = uiState.liveAnswer?.takeIf { it.query == query }
        val instant = uiState.instantAnswer
        if (live != null) liveAnswerAction(live)?.let { add(ResultListKeys.LIVE_ANSWER to it) }
        else if (instant != null) instantAnswerAction(instant, query)?.let { add(ResultListKeys.INSTANT_ANSWER to it) }
    }
    return NavSources(
        spellingSuggestion = uiState.spellingSuggestion,
        directActions = uiState.directActions,
        aiAsk = uiState.askAi?.let { provider -> AiAnswers.questionIn(query)?.let { provider to it } },
        aiAnswer = uiState.aiAnswer,
        savedLinks = uiState.savedLinks,
        glance = if (query.isEmpty()) s.glanceItems else emptyList(),
        bestMatch = s.bestMatch,
        pinned = if (query.isEmpty()) uiState.pinned else emptyList(),
        recents = if (settings.searchPreviousSearches && query.isEmpty()) s.distinctRecents else emptyList(),
        answers = answers,
        sectionOrder = resultSectionOrder(s.appWeight, s.webWeight, s.contactWeight, s.fileWeight, uiState.androidSettings.any { it.isStrongMatch }),
        apps = if (settings.searchApps) s.filteredApps.ifEmpty { s.visibleApps } else emptyList(),
        androidSettings = if (settings.searchAndroidSettings && query.isNotEmpty()) uiState.androidSettings else emptyList(),
        suggestions = if ((settings.searchWeb || s.suggestionsEnabled) && query.isNotEmpty()) {
            displayedSuggestions(query, uiState.recentSearches, settings.searchPreviousSearches, uiState.webSuggestions, settings.webResultsCount)
        } else emptyList(),
        history = uiState.recentSearches,
        contacts = if (settings.searchContacts) s.filteredContacts.ifEmpty { uiState.contacts } else emptyList(),
        fileTypeFilter = uiState.fileTypeFilter.takeIf { settings.searchFiles },
        files = if (settings.searchFiles) s.filteredFiles.ifEmpty { uiState.files } else emptyList(),
        notifications = if (query.isNotEmpty()) s.notificationHits else emptyList()
    )
}

/** Runs a suggested action as its row does: unlocks the Private Space, searches the clipboard's image, or opens its intent. */
internal fun runDirectAction(context: Context, viewModel: SearchViewModel, action: DirectAction) {
    when (action.iconType) {
        "private_space" -> context.findActivity()?.let { viewModel.unlockPrivateSpace(it) }
        ClipboardLens.ICON_TYPE -> ClipboardLens.searchClipboardImage(context)
        else -> action.intent?.let { launchSafeIntent(context, it) }
    }
}

/** Opens [target] as tapping its row (or its card's main button) would. */
internal fun openNavTarget(context: Context, s: OverlayScope, target: NavTarget) {
    s.ui.highlightedKey = null
    when (target) {
        is NavTarget.Result -> openResult(context, s, target.item, LaunchSource.keyboard(*listOfNotNull(target.iconKey).toTypedArray()))
        is NavTarget.WebQuery -> s.launchWebSearch(target.query)
        is NavTarget.Correction -> {
            s.ui.textFieldValue = TextFieldValue(target.corrected, TextRange(target.corrected.length))
            s.viewModel.onQueryChanged(target.corrected)
        }
        is NavTarget.Action -> {
            s.ui.hasStartedTyping = false
            runDirectAction(context, s.viewModel, target.action)
        }
        is NavTarget.Answer -> {
            s.ui.hasStartedTyping = false
            when (val action = target.action) {
                is AnswerAction.WebSearch -> s.launchWebSearch(action.query)
                is AnswerAction.OpenAddress -> s.openWebAddress(action.address)
                AnswerAction.OpenCalculator -> launchSafeIntent(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR))
                AnswerAction.ShowAlarms -> launchSafeIntent(context, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))
                is AnswerAction.OpenWeather -> {
                    val app = com.pixel.intelligentsearch.core.system.GoogleApps.weatherIntent(context)
                    if (app == null || !launchSafeIntent(context, app)) s.launchWebSearch(action.query)
                }
                is AnswerAction.Translate -> openInGoogleTranslate(context, s, action.text, action.source, action.target)
                is AnswerAction.ContinueInAssistant ->
                    launchSafeIntent(context, AiAssistants.launchIntent(context, action.provider.assistant, action.question))
            }
        }
        // Nothing is sent until the row is chosen; then its answer card takes the row's place.
        is NavTarget.AskAi -> s.viewModel.askAi()
        is NavTarget.RemoveFileTypeFilter -> s.viewModel.clearFileTypeFilter()
        is NavTarget.Notification -> {
            s.ui.hasStartedTyping = false
            openPendingOrApp(context, target.entry.contentIntent, target.entry.packageName)
        }
        is NavTarget.Glance -> {
            s.ui.hasStartedTyping = false
            openGlanceItem(context, target.item)
        }
    }
}

/**
 * Scrolls the item under [key] fully into view, [animate]d unless animations are off. An item off screen isn't laid
 * out, so the list steps toward it ([towardEnd] says which way) until it appears.
 */
internal suspend fun LazyListState.scrollIntoView(key: Any, towardEnd: Boolean, animate: Boolean) {
    repeat(MAX_SCROLL_STEPS) {
        val info = layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == key }
        if (item != null) {
            val start = info.viewportStartOffset
            val end = info.viewportEndOffset
            val delta = when {
                item.offset < start -> (item.offset - start).toFloat()
                item.offset + item.size > end -> minOf(item.offset + item.size - end, item.offset - start).toFloat()
                else -> 0f
            }
            if (delta != 0f) { if (animate) animateScrollBy(delta) else scrollBy(delta) }
            return
        }
        val step = (info.viewportEndOffset - info.viewportStartOffset) / 2f
        if (towardEnd && !canScrollForward || !towardEnd && !canScrollBackward || step <= 0f) return
        scrollBy(if (towardEnd) step else -step)
    }
}

/** How far [scrollIntoView] steps before giving up on an item that never appears. */
private const val MAX_SCROLL_STEPS = 40

/** The highlight's outline on a full-width result row (contacts, files, settings pages). */
internal val ResultHighlightRowShape: Shape get() = ExpressiveCornerTokens.ShapeLarge

/** The highlight's outline on an app (or pinned) tile. */
internal val ResultHighlightTileShape: Shape get() = ExpressiveCornerTokens.ShapeLarge

/** Draws the keyboard focus ring along [shape] while the stop under [key] is highlighted; read only while drawing. */
internal fun Modifier.keyboardHighlight(s: OverlayScope, key: String, shape: Shape): Modifier {
    val ui = s.ui
    return focusRing(shape) { ui.highlightedKey == key }
}

/**
 * An answer card under the stop [key], with the keyboard's ring drawn on the card itself: answer cards sit 16 dp in
 * from the sides and 6 dp from the next, in the OneBox squircle.
 */
@Composable
internal fun HighlightedCard(s: OverlayScope, key: String, content: @Composable () -> Unit) {
    Box {
        content()
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .keyboardHighlight(s, key, ExpressiveCornerTokens.ShapeExtraLarge)
        )
    }
}

/**
 * Handles a key pressed in the search field for the results: moves the highlight, opens it, clears or closes. Returns
 * whether the key was used; a key's release is used with its press, so the field never sees half of one.
 */
internal fun OverlayScope.onResultsKey(event: KeyEvent, context: Context, rtl: Boolean, usedPresses: MutableSet<Key>): Boolean {
    if (event.type == KeyEventType.KeyUp) return usedPresses.remove(event.key)
    val navKey = OverlayKeyboardNav.keyOf(event, rtl) ?: return false
    val rows = navRows
    val outcome = OverlayKeyboardNav.reduce(
        at = OverlayKeyboardNav.positionOf(ui.highlightedKey, rows),
        key = navKey,
        rowSizes = rows.map { it.size },
        queryEmpty = ui.textFieldValue.text.isEmpty()
    )
    val used = when (outcome) {
        is NavOutcome.Move -> {
            ui.highlightedKey = outcome.to?.let { rows[it.row][it.column].key }
            true
        }
        NavOutcome.Open -> {
            // Through Enter's rules, so a trigger word typed in full still comes before the highlight.
            onEnter(context, OverlayKeyboardNav.positionOf(ui.highlightedKey, rows)?.let { rows[it.row][it.column].target })
            true
        }
        NavOutcome.ClearQuery -> {
            ui.highlightedKey = null
            ui.textFieldValue = TextFieldValue("")
            viewModel.onQueryChanged("")
            true
        }
        NavOutcome.Close -> {
            closeOverlay()
            true
        }
        NavOutcome.Leave -> {
            ui.highlightedKey = null
            false
        }
        NavOutcome.PassThrough -> false
    }
    if (used) usedPresses += event.key
    return used
}

/**
 * Whether the item under [targetKey] comes after what [state] shows: [order] is every key in the list's order. When
 * nothing on screen is in [order], the target is taken to be further on.
 */
internal fun isTowardEnd(state: LazyListState, order: List<Any>, targetKey: Any): Boolean {
    val targetIndex = order.indexOf(targetKey)
    val visibleIndex = state.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { info -> order.indexOf(info.key).takeIf { it >= 0 } }
    return visibleIndex == null || targetIndex >= visibleIndex
}
