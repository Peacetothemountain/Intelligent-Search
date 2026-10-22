package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.ui.uniqueItems
import com.pixel.intelligentsearch.feature.widget.getLensSearchIntent
import com.pixel.intelligentsearch.feature.widget.getVoiceActionIntent
import com.pixel.intelligentsearch.feature.widget.getVoiceSearchIntent
import com.pixel.intelligentsearch.core.ui.AppIconResult
import com.pixel.intelligentsearch.core.ui.peekThemedAppIcon
import com.pixel.intelligentsearch.core.ui.getThemedAppIcon
import com.pixel.intelligentsearch.core.ui.ClickDebouncer
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.util.findActivity
import com.pixel.intelligentsearch.feature.settings.bouncyClickable
import com.pixel.intelligentsearch.feature.settings.expressiveRowClickable
import com.pixel.intelligentsearch.core.haptics.rememberTactileSonicEngine
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.AppItem
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.*
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import com.pixel.intelligentsearch.core.ui.MathResultOneBox
import com.pixel.intelligentsearch.core.ui.ConversionOneBox
import com.pixel.intelligentsearch.core.ui.DictionaryOneBox
import com.pixel.intelligentsearch.core.ui.UrlNavigationOneBox
import com.pixel.intelligentsearch.core.ui.TimeWeatherOneBox
import androidx.compose.ui.graphics.Brush

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
    "private_space" -> Icons.Default.Lock
    else -> Icons.Default.ContentPaste
}

/** The overlay's results list: suggested actions, best match, recents, calendar, shortcuts, one-boxes, and the app, Android Settings, web, people and file sections. */
@Composable
internal fun OverlaySearchResults(s: OverlayScope) {
    val onOpenSettings = s.onOpenSettings
    val viewModel = s.viewModel
    val uiState = s.uiState
    val settingsState = s.settingsState
    val searchProviderName = s.searchProviderName
    val appWeight = s.appWeight
    val webWeight = s.webWeight
    val contactWeight = s.contactWeight
    val fileWeight = s.fileWeight
    val suggestionsEnabled = s.suggestionsEnabled
    val performAppLaunch = s.performAppLaunch
    val launchIcons = s.launchIcons
    val appLaunch = s.appLaunch
    val launchWebSearch = s.launchWebSearch
    val openWebAddress = s.openWebAddress
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
    val matchingRecent = remember(uiState.query, uiState.recentSearches, settingsState.searchPreviousSearches) {
        val trimmed = uiState.query.trim()
        if (!settingsState.searchPreviousSearches || trimmed.isEmpty()) {
            emptyList()
        } else {
            uiState.recentSearches
                .filter { it.contains(trimmed, ignoreCase = true) }
                .distinct()
        }
    }
    val allDisplaySuggestions = remember(matchingRecent, uiState.webSuggestions, settingsState.webResultsCount) {
        val maxCount = settingsState.webResultsCount.coerceAtLeast(1)
        val nonRecentWeb = uiState.webSuggestions.filter { webSugg ->
            matchingRecent.none { it.equals(webSugg, ignoreCase = true) }
        }
        (matchingRecent + nonRecentWeb).distinct().take(maxCount)
    }

    val isBottomResults = settingsState.bottomSearch && settingsState.bottomSearchResult

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
        val showApps = true
        val showWeb = true
        val showPeople = true
        val showFiles = true

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
            item(key = "did_you_mean", contentType = "did_you_mean") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(28.dp))
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
            uniqueItems(uiState.directActions, key = { action -> "direct_action_${action.title}_${action.subtitle}" }, contentType = { "direct_action" }) { action ->
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
                    val actionIcon = directActionIcon(action.iconType)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), RoundedCornerShape(32.dp))
                            .clip(RoundedCornerShape(32.dp))
                            .swipeToDismiss(swipe, action)
                            .bouncyClickable {
                                hasStartedTyping = false
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                if (action.iconType == "private_space") {
                                    val act = context.findActivity()
                                    if (act != null) {
                                        viewModel.unlockPrivateSpace(act)
                                    }
                                } else {
                                    action.intent?.let { intent ->
                                        launchSafeIntent(context, intent)
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = action.title,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = GoogleSansFlex
                            )
                            Text(
                                text = action.subtitle,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                fontFamily = GoogleSansFlex,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            item(key = "direct_actions_divider", contentType = "divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
        }

        if (bestMatch != null) {
            item(key = "best_match_hero_card", contentType = "best_match") {
                OverlayBestMatchCard(s, bestMatch)
            }
        }

        // Debug menu: Mock Trending Queries fills the empty search state with stand-in topics.
        if (mockTrendingOn && uiState.query.isEmpty()) {
            item(key = "header_trending", contentType = "recent_label") {
                Text(
                    text = "Trending",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)
                )
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
                    Text(
                        text = "Recent",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontFamily = GoogleSansFlex,
                        fontWeight = FontWeight.SemiBold
                    )
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
            uniqueItems(distinctRecents, key = { query -> "recent_$query" }, contentType = { "recent_query" }) { recentQuery ->
                OverlayRecentSearchRow(s, recentQuery)
            }
        }

        if (settingsState.searchCalendar && uiState.calendarEvents.isNotEmpty()) {
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
            item(key = "math_result", contentType = "math_result") {
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

        val instantAnswer = uiState.instantAnswer
        if (instantAnswer != null) {
            item(key = "instant_answer", contentType = "instant_answer") {
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
                            providerName = searchProviderName,
                            onClick = { launchWebSearch("define ${instantAnswer.title}") }
                        )
                    }
                    "url" -> {
                        UrlNavigationOneBox(
                            url = instantAnswer.title,
                            onClick = {
                                hasStartedTyping = false
                                openWebAddress(instantAnswer.title)
                            }
                        )
                    }
                    "date" -> {
                        TimeWeatherOneBox(
                            title = instantAnswer.title,
                            subtitle = instantAnswer.subtitle,
                            iconType = instantAnswer.iconType,
                            onClick = { launchWebSearch(uiState.query) }
                        )
                    }
                    "weather", "time" -> {
                        TimeWeatherOneBox(
                            title = instantAnswer.title,
                            subtitle = instantAnswer.subtitle,
                            iconType = instantAnswer.iconType,
                            onClick = {
                                hasStartedTyping = false
                                if (instantAnswer.iconType == "time") {
                                    launchSafeIntent(context, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))
                                } else {
                                    launchWebSearch(uiState.query)
                                }
                            }
                        )
                    }
                    else -> {
                        TimeWeatherOneBox(
                            title = instantAnswer.title,
                            subtitle = instantAnswer.subtitle,
                            iconType = "info",
                            onClick = { launchWebSearch(uiState.query) }
                        )
                    }
                }
            }
        }

        for (sec in sortedResultSections) {
            when (sec) {
                "apps" -> {
                    val isAppsEnabled = settingsState.searchApps
                    val displayApps = if (filteredApps.isNotEmpty()) filteredApps else visibleApps
                    if (showApps && isAppsEnabled && displayApps.isNotEmpty()) {
                        item(key = "apps_divider", contentType = "divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                        item(key = "apps_row", contentType = "apps_row") {
                            LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                if (uiState.query.isEmpty()) {
                                    item(key = "search_settings_shortcut", contentType = "settings_shortcut") {
                                        SearchSettingsItem {
                                            if (ClickDebouncer.canClick()) {
                                                onOpenSettings("main")
                                            }
                                        }
                                    }
                                }
                                uniqueItems(displayApps, key = { app -> "app_${app.packageName}_${app.userHandle?.hashCode() ?: 0}_${app.profileType}" }, contentType = { "app_item" }) { app ->
                                    val iconKey = LaunchIconKeys.app(app)
                                    AppGridItem(app, launchIcon = LaunchIconTarget(launchIcons, iconKey)) { performAppLaunch(app, LaunchSource.tap(iconKey)) }
                                }
                            }
                        }
                    }
                }
                "android_settings" -> {
                    if (settingsState.searchAndroidSettings && uiState.query.isNotEmpty() && uiState.androidSettings.isNotEmpty()) {
                        item(key = "android_settings_divider", contentType = "divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                        uniqueItems(uiState.androidSettings, key = { setting -> "android_setting_${setting.id}" }, contentType = { "android_setting" }) { setting ->
                            AndroidSettingRow(setting) {
                                hasStartedTyping = false
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                openAndroidSetting(context, setting)
                            }
                        }
                    }
                }
                "web" -> {
                    if (showWeb && (settingsState.searchWeb || suggestionsEnabled) && uiState.query.isNotEmpty()) {
                        if (allDisplaySuggestions.isNotEmpty()) {
                            uniqueItems(allDisplaySuggestions, key = { suggestion -> "sugg_$suggestion" }, contentType = { "web_suggestion" }) { suggestion ->
                                val isRecent = uiState.recentSearches.any { it.equals(suggestion, ignoreCase = true) }
                                val trimmed = uiState.query.trim()
                                val annotatedSuggestion = remember(suggestion, trimmed) {
                                    androidx.compose.ui.text.buildAnnotatedString {
                                        if (trimmed.isNotEmpty() && suggestion.startsWith(trimmed, ignoreCase = true)) {
                                            append(suggestion.substring(0, trimmed.length))
                                            withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) {
                                                append(suggestion.substring(trimmed.length))
                                            }
                                        } else {
                                            append(suggestion)
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem(
                                            placementSpec = ExpressiveMotionTokens.Spatial.ExpressiveDefault(),
                                            fadeInSpec = ExpressiveMotionTokens.Effects.FastSpatial(),
                                            fadeOutSpec = ExpressiveMotionTokens.Effects.FastSpatial()
                                        )
                                        .padding(horizontal = 16.dp, vertical = 3.dp)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(28.dp))
                                        .clip(RoundedCornerShape(28.dp))
                                        .expressiveRowClickable {
                                            viewModel.onQueryChanged(suggestion)
                                            viewModel.addSearchHistory(suggestion)
                                            launchWebSearch(suggestion)
                                        }
                                        .padding(
                                            start = if (isRecent) 16.dp else 20.dp,
                                            end = 8.dp,
                                            top = 10.dp,
                                            bottom = 10.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isRecent) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                    }
                                    Text(
                                        text = annotatedSuggestion,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 16.sp,
                                        fontFamily = GoogleSansFlex,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isRecent) {
                                        IconButton(
                                            onClick = { viewModel.removeSearchHistory(suggestion) },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            hasStartedTyping = true
                                            textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                                                text = suggestion,
                                                selection = androidx.compose.ui.text.TextRange(suggestion.length)
                                            )
                                            viewModel.onQueryChanged(suggestion)
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.NorthWest,
                                            contentDescription = "Insert query",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                "contacts" -> {
                    val isContactsEnabled = settingsState.searchContacts
                    val displayContacts = if (filteredContacts.isNotEmpty()) filteredContacts else uiState.contacts
                    if (showPeople && isContactsEnabled && displayContacts.isNotEmpty()) {
                        item(key = "contacts_divider", contentType = "divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                        uniqueItems(displayContacts, key = { contact -> "contact_${contact.lookupUri.ifBlank { "${contact.name}_${contact.phoneNumber}" }}" }, contentType = { "contact" }) { contact ->
                            val isDirectCall = settingsState.contactDirectCall
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .expressiveRowClickable {
                                        hasStartedTyping = false
                                        launchSafeIntent(context, ContactActions.openIntent(contact, isDirectCall))
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                val contactActions = remember(contact, s.contactActionTargets, settingsState.contactActions) {
                                    ContactActions.forContact(contact, s.contactActionTargets, settingsState.contactActions)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(contact.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = GoogleSansFlex)
                                    Text(contact.phoneNumber, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = GoogleSansFlex)
                                    if (contactActions.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        // Pulled back by the touch target's margin so the first circle lines up with the name.
                                        ContactActionButtons(contactActions, settingsState.activeIconPack, Modifier.offset(x = (-4).dp)) {
                                            hasStartedTyping = false
                                        }
                                    }
                                }
                                // The action buttons include Call; without them the row keeps its own call button.
                                if (contactActions.isEmpty()) IconButton(
                                    onClick = {
                                        if (contact.phoneNumber.isNotBlank()) {
                                            hasStartedTyping = false
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
                "files" -> {
                    val isFilesEnabled = settingsState.searchFiles
                    val displayFiles = if (filteredFiles.isNotEmpty()) filteredFiles else uiState.files
                    if (showFiles && isFilesEnabled && displayFiles.isNotEmpty()) {
                        item(key = "files_divider", contentType = "divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                        uniqueItems(displayFiles, key = { file -> "file_${file.uri}" }, contentType = { "file" }) { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .expressiveRowClickable {
                                        hasStartedTyping = false
                                        val intent = buildOpenFileIntent(file.uri, file.mimeType)
                                        if (!launchSafeIntent(context, intent)) {
                                            intent.setPackage(null)
                                            launchSafeIntent(context, intent)
                                        }
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

        if (!isBottomResults) {
            renderInlineShortcuts()
        }
    }
}
