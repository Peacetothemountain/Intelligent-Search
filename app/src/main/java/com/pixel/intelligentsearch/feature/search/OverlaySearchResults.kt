package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.feature.settings.IconButton
import com.pixel.intelligentsearch.core.system.launchSafeIntent
import com.pixel.intelligentsearch.core.ui.uniqueItems
import com.pixel.intelligentsearch.core.search.ResultKeys
import com.pixel.intelligentsearch.feature.widget.getLensSearchIntent
import com.pixel.intelligentsearch.feature.widget.getVoiceActionIntent
import com.pixel.intelligentsearch.feature.widget.getVoiceSearchIntent
import com.pixel.intelligentsearch.core.ui.ClickDebouncer
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.feature.settings.bouncyClickable
import com.pixel.intelligentsearch.feature.settings.expressiveRowClickable
import com.pixel.intelligentsearch.core.haptics.rememberTactileSonicEngine
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.*
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import com.pixel.intelligentsearch.core.ui.MathResultOneBox
import com.pixel.intelligentsearch.core.ui.ConversionOneBox
import com.pixel.intelligentsearch.core.ui.DictionaryOneBox
import com.pixel.intelligentsearch.core.ui.UrlNavigationOneBox
import com.pixel.intelligentsearch.core.ui.TimeWeatherOneBox

/**
 * The result sections in display order: heaviest user weight first (apps, web, contacts, files on a tie), with
 * Android Settings pages placed among them without moving the others. Pages come after the apps, the web and the
 * contacts, since a name or a search shares words with some setting ("tim", "pat") far more often than it means
 * one; when the query names a page outright ([settingsStrongMatch]: "battery", "wifi settings") they come straight
 * after the apps. Either way a settings page never sits above an app, whatever the weights.
 */
internal fun resultSectionOrder(
    appWeight: Int,
    webWeight: Int,
    contactWeight: Int,
    fileWeight: Int,
    settingsStrongMatch: Boolean = false
): List<String> {
    val sections = listOf(
        "apps" to appWeight,
        "web" to webWeight,
        "contacts" to contactWeight,
        "files" to fileWeight
    ).sortedByDescending { it.second }.map { it.first }
    val after = if (settingsStrongMatch) listOf("apps") else listOf("apps", "web", "contacts")
    return sections.toMutableList().apply { add(after.maxOf { sections.indexOf(it) } + 1, "android_settings") }
}

/** The icon on a suggested action, by its [DirectAction.iconType]; a type with no icon of its own falls back to the clipboard. */
internal fun directActionIcon(type: String): androidx.compose.ui.graphics.vector.ImageVector = when (type) {
    "link" -> Icons.Default.Link
    "phone" -> Icons.Default.Call
    "search" -> Icons.Default.Search
    "calendar" -> Icons.Default.Event
    "message" -> Icons.AutoMirrored.Filled.Message
    "timer" -> Icons.Default.Timer
    "alarm" -> Icons.Default.Alarm
    "navigation" -> Icons.Default.Directions
    "media" -> Icons.Default.PlayArrow
    "private_space" -> Icons.Default.Lock
    ClipboardLens.ICON_TYPE -> Icons.Default.ImageSearch
    else -> Icons.Default.ContentPaste
}

/** The overlay's results list: suggested actions, At a Glance, best match, recents, calendar, shortcuts, one-boxes, and the app, Android Settings, web, people, file and notification sections. */
@Composable
internal fun OverlaySearchResults(s: OverlayScope) {
    val onOpenSettings = s.onOpenSettings
    val viewModel = s.viewModel
    val uiState = s.uiState
    val settingsState = s.settingsState
    val appWeight = s.appWeight
    val webWeight = s.webWeight
    val contactWeight = s.contactWeight
    val fileWeight = s.fileWeight
    val suggestionsEnabled = s.suggestionsEnabled
    val performAppLaunch = s.performAppLaunch
    val launchIcons = s.launchIcons
    val launchWebSearch = s.launchWebSearch
    val visibleApps = s.visibleApps
    val bestMatch = s.bestMatch
    val filteredContacts = s.filteredContacts
    val filteredApps = s.filteredApps
    val filteredFiles = s.filteredFiles
    val distinctRecents = s.distinctRecents
    var hasStartedTyping by s.ui::hasStartedTyping
    var textFieldValue by s.ui::textFieldValue
    val mockTrendingUnlocked by rememberSetting(SettingsManager.DEBUG_UNLOCKED)
    val mockTrendingSwitch by rememberSetting(SettingsManager.DEBUG_MOCK_TRENDING)
    val mockTrendingOn = mockTrendingUnlocked && mockTrendingSwitch
    val aiModel by rememberSetting(SettingsManager.AI_MODEL)
    var showHistoryClearedSnackbar by s.ui::showHistoryClearedSnackbar
    val context = LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sensoryEngine = rememberTactileSonicEngine()
    val view = androidx.compose.ui.platform.LocalView.current
    val searchResultsListState = s.listState
    val verticalPad = 14.dp

    // These are plain values from the scope, not snapshot state, so they are keyed rather than derived: a
    // derivedStateOf over them would compute once and never again.
    val settingsStrongMatch = uiState.androidSettings.any { it.isStrongMatch }
    val sortedResultSections = remember(appWeight, webWeight, contactWeight, fileWeight, settingsStrongMatch) {
        resultSectionOrder(appWeight, webWeight, contactWeight, fileWeight, settingsStrongMatch)
    }
    // Past searches first, then the engine's suggestions, as Google lists them.
    val allDisplaySuggestions = remember(uiState.query, uiState.recentSearches, settingsState.searchPreviousSearches, uiState.webSuggestions, settingsState.webResultsCount) {
        displayedSuggestions(uiState.query, uiState.recentSearches, settingsState.searchPreviousSearches, uiState.webSuggestions, settingsState.webResultsCount)
    }

    val isBottomResults = settingsState.bottomSearch && settingsState.bottomSearchResult
    // The keyboard's highlight is scrolled into view as it moves.
    val highlightedKey = s.ui.highlightedKey
    val animationsOn by com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled()
    LaunchedEffect(highlightedKey) {
        val rows = s.navRows
        val stop = rows.flatten().firstOrNull { it.key == highlightedKey } ?: return@LaunchedEffect
        val order = rows.map { it.first().listKey }
        searchResultsListState.scrollIntoView(stop.listKey, isTowardEnd(searchResultsListState, order, stop.listKey), animationsOn)
    }
    val glanceItems = s.glanceItems
    val notificationHits = s.notificationHits

    LazyColumn(
        state = searchResultsListState,
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (settingsState.bottomSearch) Modifier.statusBarsPadding() else Modifier
            ),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 8.dp
        ),
        reverseLayout = false,
        verticalArrangement = if (isBottomResults) Arrangement.Bottom else Arrangement.Top
    ) {
        val renderInlineShortcuts = {
            if (settingsState.shortcutInline && uiState.query.isNotEmpty()) {
                item(key = "inline_shortcuts_divider", contentType = "divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                item(key = "lens_shortcut", contentType = "shortcut") {
                    ShortcutRow(
                        iconRes = R.drawable.ic_camera,
                        title = "Search with Google Lens",
                        onClick = {
                            hasStartedTyping = false
                            val intent = getLensSearchIntent(context)
                            launchSafeIntent(context, intent)
                        }
                    )
                }
                item(key = "voice_shortcut", contentType = "shortcut") {
                    ShortcutRow(
                        iconRes = R.drawable.ic_mic,
                        title = "Search with Voice",
                        onClick = {
                            hasStartedTyping = false
                            val intent = getVoiceSearchIntent(context)
                            launchSafeIntent(context, intent)
                        }
                    )
                }
                item(key = "assistant_shortcut", contentType = "shortcut") {
                    ShortcutRow(
                        iconRes = R.drawable.ic_lens_action,
                        title = "Digital Assistant",
                        onClick = {
                            hasStartedTyping = false
                            val intent = getVoiceActionIntent(context)
                            launchSafeIntent(context, intent)
                        }
                    )
                }
            }
        }

        if (isBottomResults) {
            renderInlineShortcuts()
        }

        val systemToggle = uiState.systemToggle
        if (systemToggle != null) {
            item(key = "system_toggle_card_${systemToggle.id}", contentType = "system_toggle") {
                com.pixel.intelligentsearch.core.ui.SystemToggleCard(
                    toggleState = systemToggle,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }

        val volumeSlider = uiState.volumeSlider
        if (volumeSlider != null) {
            item(key = "volume_slider_${volumeSlider.streamType}", contentType = "volume_slider") {
                com.pixel.intelligentsearch.core.ui.VolumeSliderCard(
                    title = volumeSlider.title,
                    streamType = volumeSlider.streamType,
                    currentLevel = volumeSlider.currentLevel,
                    maxLevel = volumeSlider.maxLevel,
                    onVolumeChange = volumeSlider.onVolumeChange
                )
            }
        }

        // "Did you mean": an obvious typo in a query that matched nothing on the phone. Tapping fills the fix in.
        uiState.spellingSuggestion?.let { corrected ->
            item(key = ResultListKeys.DID_YOU_MEAN, contentType = "did_you_mean") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .keyboardHighlight(s, ResultListKeys.DID_YOU_MEAN, RoundedCornerShape(28.dp))
                        .expressiveRowClickable {
                            textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                                text = corrected,
                                selection = androidx.compose.ui.text.TextRange(corrected.length)
                            )
                            viewModel.onQueryChanged(corrected)
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Spellcheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = androidx.compose.ui.text.buildAnnotatedString {
                            append("Did you mean ")
                            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                            append(corrected)
                            pop()
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontFamily = GoogleSansFlex
                    )
                }
            }
        }

        // Parsed intents (timers, alarms, events, messages, calls, navigation) always show; clipboard
        // actions are only added to this list when smart clipboard suggestions are enabled.
        if (uiState.directActions.isNotEmpty()) {
            uniqueItems(uiState.directActions, key = ResultListKeys::directAction, contentType = { "direct_action" }) { action ->
                val swipe = rememberSwipeToDismiss(sensoryEngine, view) { viewModel.dismissDirectAction(action) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(
                            placementSpec = ExpressiveMotionTokens.Spatial.ExpressiveDefault(),
                            fadeInSpec = ExpressiveMotionTokens.Effects.FastSpatial(),
                            fadeOutSpec = ExpressiveMotionTokens.Effects.FastSpatial()
                        )
                        .swipeOffset(swipe)
                ) {
                    DirectActionRow(
                        title = action.title,
                        subtitle = action.subtitle,
                        icon = directActionIcon(action.iconType),
                        gestures = Modifier
                            .keyboardHighlight(s, ResultListKeys.directAction(action), DirectActionRowShape)
                            .swipeToDismiss(swipe, action)
                    ) {
                        hasStartedTyping = false
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        runDirectAction(context, viewModel, action)
                    }
                }
            }
            item(key = "direct_actions_divider", contentType = "divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
        }

        // AI Answers: "Ask Claude" for a question, sending it only when tapped; then its answer, for this query only.
        val aiOffer = uiState.askAi
        val aiAnswer = uiState.aiAnswer
        val question = com.pixel.intelligentsearch.core.ai.AiAnswers.questionIn(uiState.query)
        if (aiAnswer != null) {
            item(key = ResultListKeys.AI_ANSWER, contentType = "ai_answer") {
                HighlightedCard(s, ResultListKeys.AI_ANSWER) {
                    AiAnswerCard(
                        aiAnswer,
                        model = aiAnswer.provider.model(aiModel),
                        onOpenSettings = { page -> onOpenSettings(page) },
                        onRetry = { viewModel.askAi() }
                    ) {
                        hasStartedTyping = false
                        launchSafeIntent(context, com.pixel.intelligentsearch.core.bangs.AiAssistants.launchIntent(context, aiAnswer.provider.assistant, aiAnswer.question))
                    }
                }
            }
        } else if (aiOffer != null && question != null) {
            item(key = ResultListKeys.ASK_AI, contentType = "direct_action") {
                AskAiRow(aiOffer, question, modifier = Modifier.keyboardHighlight(s, ResultListKeys.ASK_AI, DirectActionRowShape)) {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    viewModel.askAi()
                }
            }
        }

        // At a Glance, one card in the top start corner: timers, the next alarm, what's playing, birthdays and the battery.
        if (uiState.query.isEmpty()) {
            atAGlanceItems(glanceItems, highlight = { key -> Modifier.keyboardHighlight(s, key, GlanceRowShape) }) { hasStartedTyping = false }
        }

        if (bestMatch != null) {
            item(key = ResultListKeys.BEST_MATCH, contentType = "best_match") {
                OverlayBestMatchCard(s, bestMatch)
            }
        }

        // What the user pinned, on the empty overlay; with a query, pinned results lead their own sections instead.
        if (uiState.query.isEmpty() && uiState.pinned.isNotEmpty()) {
            item(key = ResultListKeys.PINNED, contentType = "pinned_results") { OverlayPinnedRow(s) }
        }

        // Debug menu: Mock Trending Queries fills the empty search state with stand-in topics.
        if (mockTrendingOn && uiState.query.isEmpty()) {
            item(key = "header_trending", contentType = "recent_label") {
                OverlaySectionLabel("Trending")
            }
            uniqueItems(DebugMocks.trendingQueries, key = { topic -> "trending_$topic" }, contentType = { "recent_query" }) { topic ->
                val pillShape = RoundedCornerShape(28.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f), pillShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f), pillShape)
                        .clip(pillShape)
                        .bouncyClickable { launchWebSearch(topic) }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = topic,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontFamily = GoogleSansFlex,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (settingsState.searchPreviousSearches && uiState.query.isEmpty() && distinctRecents.isNotEmpty()) {
            item(key = "header_recent_searches", contentType = "recent_label") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(
                            placementSpec = ExpressiveMotionTokens.Spatial.ExpressiveDefault(),
                            fadeInSpec = ExpressiveMotionTokens.Effects.FastSpatial(),
                            fadeOutSpec = ExpressiveMotionTokens.Effects.FastSpatial()
                        )
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OverlaySectionLabel("Recent", inset = false)
                    if (distinctRecents.size > 7) {
                        Surface(
                            onClick = {
                                sensoryEngine.deleteThud(view)
                                viewModel.clearSearchHistory()
                                showHistoryClearedSnackbar = true
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteSweep,
                                    contentDescription = "Delete All",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Delete All",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.5.sp,
                                    fontFamily = GoogleSansFlex,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
            uniqueItems(distinctRecents, key = ResultListKeys::recent, contentType = { "recent_query" }) { recentQuery ->
                OverlayRecentSearchRow(s, recentQuery)
            }
        }

        if (uiState.calendarEvents.isNotEmpty()) {
            // Title + time can repeat (same meeting on two calendars); the instance id and begin time cannot.
            uniqueItems(uiState.calendarEvents, key = { event -> "event_${event.eventId}_${event.beginMillis}_${event.title}" }, contentType = { "calendar_event" }) { event ->
                val swipe = rememberSwipeToDismiss(sensoryEngine, view) { viewModel.dismissCalendarEvent(event) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(
                            placementSpec = ExpressiveMotionTokens.Spatial.ExpressiveDefault(),
                            fadeInSpec = ExpressiveMotionTokens.Effects.FastSpatial(),
                            fadeOutSpec = ExpressiveMotionTokens.Effects.FastSpatial()
                        )
                        .swipeOffset(swipe)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = event.eventId > 0L) {
                                hasStartedTyping = false
                                val eventIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    android.content.ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, event.eventId)
                                ).putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.beginMillis)
                                launchSafeIntent(context, eventIntent)
                            }
                            .swipeToDismiss(swipe, event)
                            .padding(horizontal = 16.dp, vertical = verticalPad),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(24.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                            // The badge holds the hour; an all-day event has none, so it gets the event icon.
                            if (event.allDay) {
                                Icon(imageVector = Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            } else {
                                Text(event.startTime.split(":").first(), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(event.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = GoogleSansFlex)
                            Text(event.startTime, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = GoogleSansFlex)
                        }
                    }
                }
            }
            item(key = "calendar_divider", contentType = "divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
        }

        if (uiState.mathResult != null) {
            item(key = ResultListKeys.MATH, contentType = "math_result") {
                HighlightedCard(s, ResultListKeys.MATH) {
                    MathResultOneBox(
                        expression = uiState.query,
                        result = uiState.mathResult ?: "",
                        onOpenCalculator = {
                            hasStartedTyping = false
                            try {
                                val calcIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)
                                launchSafeIntent(context, calcIntent)
                            } catch (_: Exception) {}
                        }
                    )
                }
            }
        }

        // A live answer (definition, forecast, translation, screen time) takes the tap-through card's place.
        val liveAnswer = uiState.liveAnswer?.takeIf { it.query == uiState.query }
        val instantAnswer = uiState.instantAnswer
        if (liveAnswer != null) {
            item(key = ResultListKeys.LIVE_ANSWER, contentType = "live_answer") {
                HighlightedCard(s, ResultListKeys.LIVE_ANSWER) { OverlayLiveAnswer(s, liveAnswer) }
            }
        } else if (instantAnswer != null) {
            item(key = ResultListKeys.INSTANT_ANSWER, contentType = "instant_answer") {
                HighlightedCard(s, ResultListKeys.INSTANT_ANSWER) { OverlayInstantAnswer(s, instantAnswer) }
            }
        }

        for (sec in sortedResultSections) {
            when (sec) {
                "apps" -> {
                    val isAppsEnabled = settingsState.searchApps
                    val displayApps = if (filteredApps.isNotEmpty()) filteredApps else visibleApps
                    if (isAppsEnabled && displayApps.isNotEmpty()) {
                        item(key = "apps_divider", contentType = "divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                        item(key = ResultListKeys.APPS_ROW, contentType = "apps_row") {
                            val appRowState = androidx.compose.foundation.lazy.rememberLazyListState()
                            // A highlighted app is scrolled into view along the row.
                            LaunchedEffect(highlightedKey) {
                                val appKey = highlightedKey?.removePrefix(ResultListKeys.inRow(ResultListKeys.APPS_ROW, ""))
                                    ?.takeIf { highlightedKey.startsWith(ResultListKeys.APPS_ROW + "/") } ?: return@LaunchedEffect
                                val order = displayApps.map(ResultListKeys::app)
                                appRowState.scrollIntoView(appKey, isTowardEnd(appRowState, order, appKey), animationsOn)
                            }
                            LazyRow(state = appRowState, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                if (uiState.query.isEmpty()) {
                                    item(key = "search_settings_shortcut", contentType = "settings_shortcut") {
                                        SearchSettingsItem {
                                            if (ClickDebouncer.canClick()) {
                                                onOpenSettings("main")
                                            }
                                        }
                                    }
                                }
                                uniqueItems(displayApps, key = ResultListKeys::app, contentType = { "app_item" }) { app ->
                                    val iconKey = LaunchIconKeys.app(app)
                                    ResultMenuBox(s, ResultMenuTarget.App(app)) { openMenu ->
                                        AppGridItem(
                                            app,
                                            settingsState.activeIconPack,
                                            launchIcon = LaunchIconTarget(launchIcons, iconKey),
                                            onLongClick = openMenu,
                                            modifier = Modifier.keyboardHighlight(s, ResultListKeys.inRow(ResultListKeys.APPS_ROW, ResultListKeys.app(app)), ResultHighlightTileShape)
                                        ) { performAppLaunch(app, LaunchSource.tap(iconKey)) }
                                    }
                                }
                            }
                        }
                    }
                }
                "android_settings" -> {
                    if (uiState.query.isNotEmpty() && uiState.androidSettings.isNotEmpty()) {
                        item(key = "android_settings_divider", contentType = "divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                        uniqueItems(uiState.androidSettings, key = ResultListKeys::setting, contentType = { "android_setting" }) { setting ->
                            // App Info rows stand for an app, so they have no menu of their own.
                            val menuTarget = if (ResultKeys.setting(setting) != null) ResultMenuTarget.Setting(setting) else null
                            ResultMenuBox(s, menuTarget) { openMenu ->
                                AndroidSettingRow(setting, onLongClick = openMenu, modifier = Modifier.keyboardHighlight(s, ResultListKeys.setting(setting), ResultHighlightRowShape)) {
                                    hasStartedTyping = false
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                    // The app's own pages open in its settings, with the overlay's own transition.
                                    setting.appRoute?.let(onOpenSettings) ?: openAndroidSetting(context, setting)
                                }
                            }
                        }
                    }
                }
                "web" -> {
                    if ((settingsState.searchWeb || suggestionsEnabled) && uiState.query.isNotEmpty()) {
                        if (allDisplaySuggestions.isNotEmpty()) {
                            uniqueItems(allDisplaySuggestions, key = { suggestion -> ResultListKeys.suggestion(suggestion.query) }, contentType = { "web_suggestion" }) { suggestion ->
                                val query = suggestion.query
                                val isRecent = uiState.recentSearches.any { it.equals(query, ignoreCase = true) }
                                // Past searches have a menu (Remove from History); the engine's suggestions keep none.
                                ResultMenuBox(
                                    s,
                                    if (isRecent) ResultMenuTarget.History(query) else null,
                                    modifier = Modifier.animateItem(
                                        placementSpec = ExpressiveMotionTokens.Spatial.ExpressiveDefault(),
                                        fadeInSpec = ExpressiveMotionTokens.Effects.FastSpatial(),
                                        fadeOutSpec = ExpressiveMotionTokens.Effects.FastSpatial()
                                    )
                                ) { openMenu ->
                                    OverlayWebSuggestionRow(
                                        suggestion = suggestion,
                                        typed = uiState.query,
                                        isRecent = isRecent,
                                        // The Show Thumbnails switch covers pictures of people, songs and videos too.
                                        showThumbnail = settingsState.filesThumbnails,
                                        onLongClick = openMenu,
                                        highlighted = { s.ui.highlightedKey == ResultListKeys.suggestion(query) },
                                        onSearch = {
                                            viewModel.onQueryChanged(query)
                                            viewModel.addSearchHistory(query)
                                            launchWebSearch(query)
                                        },
                                        onRemove = { viewModel.removeSearchHistory(query) },
                                        onFill = {
                                            hasStartedTyping = true
                                            textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                                                text = query,
                                                selection = androidx.compose.ui.text.TextRange(query.length)
                                            )
                                            viewModel.onQueryChanged(query)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                "contacts" -> {
                    val isContactsEnabled = settingsState.searchContacts
                    val displayContacts = if (filteredContacts.isNotEmpty()) filteredContacts else uiState.contacts
                    if (isContactsEnabled && displayContacts.isNotEmpty()) {
                        item(key = "contacts_divider", contentType = "divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                        uniqueItems(displayContacts, key = ResultListKeys::contact, contentType = { "contact" }) { contact ->
                            val isDirectCall = settingsState.contactDirectCall
                            val menuTarget = if (ResultKeys.contact(contact) != null) ResultMenuTarget.Contact(contact) else null
                            ResultMenuBox(s, menuTarget) { openMenu ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .keyboardHighlight(s, ResultListKeys.contact(contact), ResultHighlightRowShape)
                                        .moreOptions(openMenu)
                                        .expressiveRowClickable(onLongClick = openMenu) {
                                            hasStartedTyping = false
                                            viewModel.recordOpened(contact)
                                            launchSafeIntent(context, ContactActions.openIntent(contact, isDirectCall))
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ContactAvatar(contact, 40.dp) {
                                        Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    val contactActions = remember(contact, s.contactActionTargets) {
                                        ContactActions.forContact(contact, s.contactActionTargets)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(contact.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = GoogleSansFlex)
                                        Text(contact.phoneNumber, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = GoogleSansFlex)
                                        if (contactActions.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            // Pulled back by the touch target's margin so the first circle lines up with the name.
                                            ContactActionButtons(contactActions, settingsState.activeIconPack, Modifier.offset(x = (-4).dp)) {
                                                hasStartedTyping = false
                                                viewModel.recordOpened(contact)
                                            }
                                        }
                                    }
                                    // The action buttons include Call; without them the row keeps its own call button.
                                    if (contactActions.isEmpty()) IconButton(
                                        onClick = {
                                            if (contact.phoneNumber.isNotBlank()) {
                                                hasStartedTyping = false
                                                viewModel.recordOpened(contact)
                                                launchSafeIntent(context, ContactActions.callIntent(contact.phoneNumber))
                                            }
                                        }
                                    ) {
                                        Icon(imageVector = Icons.Default.Call, contentDescription = "Call", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                "files" -> {
                    val isFilesEnabled = settingsState.searchFiles
                    val displayFiles = if (filteredFiles.isNotEmpty()) filteredFiles else uiState.files
                    val fileTypeFilter = uiState.fileTypeFilter
                    // A type named in the query shows its chip even before (or without) any file of that type.
                    if (isFilesEnabled && (displayFiles.isNotEmpty() || fileTypeFilter != null)) {
                        item(key = "files_divider", contentType = "divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                        if (fileTypeFilter != null) {
                            item(key = ResultListKeys.FILE_TYPE_FILTER, contentType = "file_type_filter") {
                                FileTypeFilterChip(
                                    fileTypeFilter,
                                    onRemove = viewModel::clearFileTypeFilter,
                                    highlight = Modifier.keyboardHighlight(s, ResultListKeys.FILE_TYPE_FILTER, FileTypeFilterChipShape)
                                )
                            }
                        }
                        uniqueItems(displayFiles, key = ResultListKeys::file, contentType = { "file" }) { file ->
                            ResultMenuBox(s, ResultMenuTarget.File(file)) { openMenu ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .keyboardHighlight(s, ResultListKeys.file(file), ResultHighlightRowShape)
                                        .moreOptions(openMenu)
                                        .expressiveRowClickable(onLongClick = openMenu) {
                                            hasStartedTyping = false
                                            viewModel.recordOpened(file)
                                            openFileResult(context, s.prefs, file)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (settingsState.filesThumbnails) {
                                        FileIconThumbnail(file.uri, file.mimeType)
                                    } else {
                                        Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                                            Icon(imageVector = Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = GoogleSansFlex, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(fileLocationLabel(file), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = GoogleSansFlex, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Search Your Notifications: matching notifications after the phone's other results.
        if (uiState.query.isNotEmpty()) notificationResultItems(
            notificationHits,
            settingsState.activeIconPack,
            highlight = { entry -> Modifier.keyboardHighlight(s, ResultListKeys.notification(entry), ResultHighlightRowShape) }
        ) { hasStartedTyping = false }

        if (!isBottomResults) {
            renderInlineShortcuts()
        }
    }
}

/** The tap-through answer card for [instantAnswer] (a conversion, a word, an address, the date, time or weather). */
@Composable
internal fun OverlayInstantAnswer(s: OverlayScope, instantAnswer: InstantAnswer) {
    val context = LocalContext.current
    val query = s.uiState.query
    when (instantAnswer.iconType) {
        "conversion" -> {
            ConversionOneBox(conversionText = instantAnswer.title)
        }
        "currency" -> {
            ConversionOneBox(
                conversionText = instantAnswer.title,
                ratesLabel = instantAnswer.subtitle,
                header = "CURRENCY CONVERSION",
                clipLabel = "Currency Conversion"
            )
        }
        "dictionary" -> {
            DictionaryOneBox(
                word = instantAnswer.title,
                providerName = s.searchProviderName,
                onClick = { s.launchWebSearch("define ${instantAnswer.title}") }
            )
        }
        "url" -> {
            UrlNavigationOneBox(
                url = instantAnswer.title,
                onClick = {
                    s.ui.hasStartedTyping = false
                    s.openWebAddress(instantAnswer.title)
                }
            )
        }
        "date" -> {
            TimeWeatherOneBox(
                title = instantAnswer.title,
                subtitle = instantAnswer.subtitle,
                iconType = instantAnswer.iconType,
                onClick = { s.launchWebSearch(query) }
            )
        }
        "weather", "time" -> {
            TimeWeatherOneBox(
                title = instantAnswer.title,
                subtitle = instantAnswer.subtitle,
                iconType = instantAnswer.iconType,
                onClick = {
                    s.ui.hasStartedTyping = false
                    if (instantAnswer.iconType == "time") {
                        launchSafeIntent(context, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))
                    } else {
                        s.launchWebSearch(query)
                    }
                }
            )
        }
        else -> {
            TimeWeatherOneBox(
                title = instantAnswer.title,
                subtitle = instantAnswer.subtitle,
                iconType = "info",
                onClick = { s.launchWebSearch(query) }
            )
        }
    }
}
