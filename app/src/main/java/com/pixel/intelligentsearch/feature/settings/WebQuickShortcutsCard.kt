package com.pixel.intelligentsearch.feature.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.data.settings.get
import android.content.Intent
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixel.intelligentsearch.core.data.SettingsManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import com.pixel.intelligentsearch.core.bangs.SearchBang
import com.pixel.intelligentsearch.core.bangs.SearchBangManager
import com.pixel.intelligentsearch.core.data.IntelligentSearchSettings
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine

/**
 * The trigger (without its symbol) and search address the shortcut editor suggests for an app picked from the
 * list. Either is null for an app it doesn't know: an address made up from the app's name could send the user's
 * queries to a site that has nothing to do with the app.
 */
internal fun suggestedWebShortcut(pkg: String): Pair<String?, String?> = when (pkg) {
    "com.google.android.youtube" -> "yt" to "https://www.youtube.com/results?search_query=%s"
    "com.spotify.music" -> "spot" to "https://open.spotify.com/search/%s"
    "org.wikipedia" -> "w" to "https://en.wikipedia.org/wiki/%s"
    "com.reddit.frontpage" -> "r" to "https://www.reddit.com/search/?q=%s"
    "com.github.android" -> "gh" to "https://github.com/search?q=%s"
    "com.google.android.apps.maps" -> "maps" to "https://www.google.com/maps/search/%s"
    "com.amazon.mShop.android.shopping" -> "az" to "https://www.amazon.com/s?k=%s"
    "com.twitter.android" -> "x" to "https://x.com/search?q=%s"
    "tv.twitch.android.app" -> "tw" to "https://www.twitch.tv/search?term=%s"
    "com.instagram.android" -> "ig" to "https://www.instagram.com/explore/tags/%s"
    "com.pinterest" -> "pin" to "https://www.pinterest.com/search/pins/?q=%s"
    "com.imdb.mobile" -> "imdb" to "https://www.imdb.com/find/?q=%s"
    "com.google.android.googlequicksearchbox" -> "g" to "https://www.google.com/search?q=%s"
    "com.duckduckgo.mobile.android" -> "ddg" to "https://duckduckgo.com/?q=%s"
    else -> null to null
}

/**
 * The trigger and search address after [appName] ([pkg]) is picked for a shortcut. Each is replaced only while
 * it's blank (the trigger also while it's just the symbol) or still holds the picker's own earlier suggestion
 * ([lastSuggestedPrefix], [lastSuggestedUrl]), so picking an app never overwrites what the user typed.
 */
internal fun fieldsForPickedApp(
    pkg: String,
    appName: String,
    trigger: String,
    prefix: String,
    url: String,
    lastSuggestedPrefix: String?,
    lastSuggestedUrl: String?
): Pair<String, String> {
    val (slug, suggestedUrl) = suggestedWebShortcut(pkg)
    val newPrefix = if (prefix.isBlank() || prefix == trigger || prefix == lastSuggestedPrefix) {
        trigger + (slug ?: appName.lowercase().filter { it.isLetterOrDigit() }.take(4))
    } else {
        prefix
    }
    val newUrl = if (suggestedUrl != null && (url.isBlank() || url == lastSuggestedUrl)) suggestedUrl else url
    return newPrefix to newUrl
}

/**
 * Whether [prefix] already triggers another active shortcut. Saving replaces whatever has the trigger, so the
 * editor refuses a clash instead of silently deleting that shortcut; an edited shortcut may keep its own.
 */
internal fun webShortcutTriggerTaken(prefix: String, editing: SearchBang?, active: List<SearchBang>): Boolean =
    prefix != editing?.displayPrefix && active.any { it.displayPrefix == prefix }

/**
 * The Quick Web Shortcuts card of Web Search settings: the on/off switch and trigger symbol, and the editor for
 * the shortcuts themselves (built-in ones to hide or restore, custom ones to add, edit and remove).
 */
@Composable
internal fun WebQuickShortcutsCard() {
    val context = LocalContext.current

    SettingsCard {
        var quickShortcutsEnabled by rememberSetting(SettingsManager.SEARCH_QUICK_SHORTCUTS)
        var shortcutTriggerSymbol by rememberSetting(SettingsManager.WEB_SHORTCUT_TRIGGER_SYMBOL)
        SettingsRowToggle(
            title = "Enable Quick Web Shortcuts",
            subtitle = "Prefix Queries with User Selected Symbol to Open Specific Web Applications.",
            icon = Icons.Outlined.TravelExplore,
            isChecked = quickShortcutsEnabled,
            onCheckedChange = { quickShortcutsEnabled = it },
            showDivider = quickShortcutsEnabled
        )
        if (quickShortcutsEnabled) {
            val triggerOptions = listOf("!", "@", "#", "/", "?", ":", "~", "Custom")
            val isPredefined = triggerOptions.dropLast(1).contains(shortcutTriggerSymbol)
            // Kept apart from the stored symbol, so typing a preset symbol ("#" on the way to "##") into the custom
            // field doesn't switch the field away.
            var customMode by rememberSaveable { mutableStateOf(!isPredefined) }

            SettingsDropdownRow(
                title = "Shortcut Trigger Symbol",
                subtitle = "Prefix Queries with '$shortcutTriggerSymbol'.",
                icon = Icons.Outlined.Tag,
                options = triggerOptions,
                selectedOption = if (customMode) "Custom" else shortcutTriggerSymbol,
                onOptionSelected = { selected ->
                    customMode = selected == "Custom"
                    if (!customMode) {
                        shortcutTriggerSymbol = selected
                    }
                },
                showDivider = true
            )

            if (customMode) {
                var customInputText by remember(customMode) {
                    mutableStateOf(if (isPredefined) "" else shortcutTriggerSymbol)
                }
                OutlinedTextField(
                    value = customInputText,
                    onValueChange = { input ->
                        // Symbols only: the query parser skips triggers that start with a letter or digit, so a
                        // shortcut behind one would never fire.
                        val clean = input.filter { !it.isWhitespace() && !it.isLetterOrDigit() }
                        customInputText = clean
                        if (clean.isNotBlank()) {
                            shortcutTriggerSymbol = clean
                        }
                    },
                    label = { Text("Custom Trigger Symbol") },
                    placeholder = { Text("e.g. @ or ?") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            val viewModel = LocalSettingsViewModel.current
            val localSettings = LocalSettingsState.current
            val allBangs by (viewModel?.bangsFlow ?: remember { kotlinx.coroutines.flow.emptyFlow() })
                .collectAsStateWithLifecycle(initialValue = viewModel?.bangsFlow?.value ?: emptyList())
            val settings = localSettings ?: (viewModel?.settingsState ?: remember { kotlinx.coroutines.flow.emptyFlow() })
                .collectAsStateWithLifecycle(initialValue = IntelligentSearchSettings()).value
            val disabledPrefixes = settings.disabledWebShortcuts

            var showAddDialog by remember { mutableStateOf(false) }
            var editingBang by remember { mutableStateOf<com.pixel.intelligentsearch.core.bangs.SearchBang?>(null) }
            var localDismissedPrefixes by remember { mutableStateOf(setOf<String>()) }

            val expressiveShapes = remember {
                listOf(
                    // 1. Smooth Squircle
                    RoundedPolygon(numVertices = 4, rounding = CornerRounding(radius = 0.55f)),
                    // 2. 4-Corner Expressive Clover
                    RoundedPolygon.star(numVerticesPerRadius = 4, innerRadius = 0.60f, rounding = CornerRounding(radius = 0.35f)),
                    // 3. Rounded Triangle
                    RoundedPolygon(numVertices = 3, rounding = CornerRounding(radius = 0.40f)),
                    // 4. 6-Point Flower
                    RoundedPolygon.star(numVerticesPerRadius = 6, innerRadius = 0.70f, rounding = CornerRounding(radius = 0.35f)),
                    // 5. Rounded Pentagon
                    RoundedPolygon(numVertices = 5, rounding = CornerRounding(radius = 0.35f)),
                    // 6. 8-Point Starburst
                    RoundedPolygon.star(numVerticesPerRadius = 8, innerRadius = 0.80f, rounding = CornerRounding(radius = 0.35f)),
                    // 7. Rounded Hexagon
                    RoundedPolygon(numVertices = 6, rounding = CornerRounding(radius = 0.30f)),
                    // 8. 12-Point Scalloped Blossom
                    RoundedPolygon.star(numVerticesPerRadius = 12, innerRadius = 0.85f, rounding = CornerRounding(radius = 0.40f)),
                    // 9. Diamond Sparkle
                    RoundedPolygon.star(numVerticesPerRadius = 4, innerRadius = 0.42f, rounding = CornerRounding(radius = 0.20f))
                )
            }

            val morphSequence = remember(expressiveShapes) {
                List(expressiveShapes.size) { i ->
                    Morph(expressiveShapes[i], expressiveShapes[(i + 1) % expressiveShapes.size])
                }
            }

            val infiniteTransition = rememberInfiniteTransition(label = "morphingShortcutsSync")
            // Kept as State and read only in the badges' draw phase (see ShortcutBadgeFrame); reading
            // them here recomposed this whole card on every animation frame.
            val totalCycleProgress = infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = morphSequence.size.toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = morphSequence.size * 2200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "shortcutBadgeMorphCycle"
            )

            val rotationAngle = infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 16000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "shortcutBadgeRotation"
            )

            val badgeFrame: () -> ShortcutBadgeFrame = remember(morphSequence, totalCycleProgress, rotationAngle) {
                {
                    val currentCycle = (totalCycleProgress.value % morphSequence.size.toFloat()).coerceIn(0f, morphSequence.size - 0.0001f)
                    val morphIndex = currentCycle.toInt().coerceIn(0, morphSequence.size - 1)
                    val rawProgress = (currentCycle - morphIndex).coerceIn(0f, 1f)
                    ShortcutBadgeFrame(
                        morph = morphSequence[morphIndex],
                        rotationAngle = rotationAngle.value,
                        morphProgress = FastOutSlowInEasing.transform(rawProgress)
                    )
                }
            }

            val view = LocalView.current
            val hapticEngine = remember(context) { com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context) }

            val displayedActiveBangs = remember(allBangs, localDismissedPrefixes) {
                allBangs.filter { it.displayPrefix !in localDismissedPrefixes }
            }

            val effectiveDisabledPrefixes = remember(disabledPrefixes, localDismissedPrefixes) {
                disabledPrefixes + localDismissedPrefixes
            }

            val availableDirectBangs = remember(effectiveDisabledPrefixes) {
                SearchBangManager.availableBuiltIns(effectiveDisabledPrefixes)
            }

            // The swipe and TalkBack's Remove action both end here.
            fun removeShortcut(bang: SearchBang) {
                localDismissedPrefixes = localDismissedPrefixes + bang.displayPrefix
                viewModel?.removeBangs(listOf(bang))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header row for Active Shortcuts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Active Shortcuts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            "Web Shortcuts. Swipe to Remove.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            editingBang = null
                            showAddDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (displayedActiveBangs.isEmpty()) {
                    com.pixel.intelligentsearch.core.ui.expressive.MatteShell(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = null
                    ) {
                        Text(
                            text = "No Active Shortcuts. Tap Add or Restore Direct Shortcuts Below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    SlideToRemoveAllBar(
                        onRemoveAll = {
                            localDismissedPrefixes = localDismissedPrefixes + displayedActiveBangs.map { it.displayPrefix }
                            viewModel?.removeBangs(displayedActiveBangs)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        displayedActiveBangs.forEach { bang ->
                            key(bang.displayPrefix) {
                                val dismissState = rememberSwipeToDismissBoxState(
                                    positionalThreshold = { it * 0.4f }
                                )

                                LaunchedEffect(dismissState.currentValue) {
                                    if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart ||
                                        dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd
                                    ) {
                                        hapticEngine.performPredictiveBackHaptic(view)
                                        removeShortcut(bang)
                                    }
                                }

                                SwipeToDismissBox(
                                    state = dismissState,
                                    backgroundContent = {
                                        val color by androidx.compose.animation.animateColorAsState(
                                            targetValue = when (dismissState.targetValue) {
                                                SwipeToDismissBoxValue.Settled -> Color.Transparent
                                                else -> MaterialTheme.colorScheme.errorContainer
                                            },
                                            label = "dismissBackground"
                                        )
                                        val iconTint by androidx.compose.animation.animateColorAsState(
                                            targetValue = when (dismissState.targetValue) {
                                                SwipeToDismissBoxValue.Settled -> Color.Transparent
                                                else -> MaterialTheme.colorScheme.onErrorContainer
                                            },
                                            label = "dismissIconTint"
                                        )
                                        val alignment = when (dismissState.dismissDirection) {
                                            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                            SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                            SwipeToDismissBoxValue.Settled -> Alignment.CenterEnd
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(24.dp))
                                                .background(color)
                                                .padding(horizontal = 24.dp),
                                            contentAlignment = alignment
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = "Delete shortcut",
                                                tint = iconTint,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                ) {
                                    com.pixel.intelligentsearch.core.ui.expressive.MatteShell(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            // TalkBack can't swipe the card away, so removing it is an action on it.
                                            .semantics {
                                                customActions = listOf(CustomAccessibilityAction("Remove") { removeShortcut(bang); true })
                                            }
                                            // The card's own shape, so the ripple doesn't spill past its corners.
                                            .bouncyClickable(shape = RoundedCornerShape(24.dp)) {
                                                editingBang = bang
                                                showAddDialog = true
                                            },
                                        shape = RoundedCornerShape(24.dp),
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                val badgeLabel = remember(bang.prefix, shortcutTriggerSymbol) {
                                                    val bare = bang.prefix.trimStart { !it.isLetterOrDigit() }
                                                    if (bang.isBuiltIn || bang.prefix.startsWith("!")) {
                                                        "$shortcutTriggerSymbol$bare"
                                                    } else {
                                                        bang.displayPrefix
                                                    }
                                                }
                                                SynchronizedMorphingShortcutBadge(
                                                    shortcut = badgeLabel,
                                                    frame = badgeFrame
                                                )
                                                Spacer(modifier = Modifier.width(16.dp))
                                                // The app's label is looked up off the main thread; the bang's own name shows until then.
                                                val appLabel by produceState(initialValue = bang.name, bang.targetPackage, bang.name) {
                                                    val target = bang.targetPackage
                                                    if (!target.isNullOrBlank()) {
                                                        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                            try {
                                                                val pm = context.packageManager
                                                                pm.getApplicationLabel(pm.getApplicationInfo(target, 0)).toString()
                                                            } catch (_: Exception) {
                                                                bang.name.ifBlank { target }
                                                            }
                                                        }
                                                    }
                                                }
                                                Column {
                                                    Text(
                                                        text = appLabel.ifBlank { bang.targetPackage ?: badgeLabel },
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    val subtext = if (!bang.targetPackage.isNullOrBlank()) {
                                                        bang.targetPackage
                                                    } else {
                                                        bang.urlTemplate
                                                    }
                                                    Text(
                                                        text = subtext,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                        fontSize = 12.sp,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Icon(
                                                imageVector = Icons.Outlined.Edit,
                                                contentDescription = "Edit Shortcut",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Available Direct Shortcuts section
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                var showAvailableDirectShortcuts by rememberSetting(SettingsManager.SHOW_AVAILABLE_DIRECT_SHORTCUTS)
                SettingsRowToggle(
                    title = "Available Direct Shortcuts",
                    subtitle = "Tap Add to Restore Any Direct Shortcut.",
                    icon = Icons.Outlined.BookmarkBorder,
                    isChecked = showAvailableDirectShortcuts,
                    onCheckedChange = { showAvailableDirectShortcuts = it },
                    showDivider = showAvailableDirectShortcuts && availableDirectBangs.isNotEmpty()
                )

                if (showAvailableDirectShortcuts && availableDirectBangs.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableDirectBangs.forEach { bang ->
                            key("avail_${bang.displayPrefix}") {
                                com.pixel.intelligentsearch.core.ui.expressive.MatteShell(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(24.dp),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            val directBadge = remember(bang.prefix, shortcutTriggerSymbol) {
                                                val bare = bang.prefix.trimStart { !it.isLetterOrDigit() }
                                                "$shortcutTriggerSymbol$bare"
                                            }
                                            SynchronizedMorphingShortcutBadge(
                                                shortcut = directBadge,
                                                frame = badgeFrame
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column {
                                                Text(
                                                    text = bang.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = bang.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                hapticEngine.performPredictiveBackHaptic(view)
                                                // It may have been removed under another trigger symbol; drop every form.
                                                val canonical = SearchBangManager.canonicalBuiltInPrefix(bang.displayPrefix)
                                                localDismissedPrefixes = localDismissedPrefixes
                                                    .filterNot { SearchBangManager.canonicalBuiltInPrefix(it) == canonical }.toSet()
                                                viewModel?.restoreBuiltInBang(bang.displayPrefix)
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showAddDialog) {
                var prefixInput by remember(editingBang) { mutableStateOf(editingBang?.displayPrefix ?: shortcutTriggerSymbol) }
                var targetPackageInput by remember(editingBang) { mutableStateOf(editingBang?.targetPackage.orEmpty()) }
                var urlInput by remember(editingBang) { mutableStateOf(editingBang?.urlTemplate ?: "") }
                // What the app picker last filled in: a later pick may replace that, never what the user typed.
                var lastSuggestedPrefix by remember(editingBang) { mutableStateOf<String?>(null) }
                var lastSuggestedUrl by remember(editingBang) { mutableStateOf<String?>(null) }
                var errorMsg by remember { mutableStateOf<String?>(null) }
                var showAppPicker by remember { mutableStateOf(false) }

                // Loading every launcher app's label is slow; do it off the main thread. Null until it's done.
                val installedApps by produceState<List<Pair<String, String>>?>(initialValue = null, context) {
                    value = withContext(Dispatchers.IO) {
                        try {
                            val pm = context.packageManager
                            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                                addCategory(Intent.CATEGORY_LAUNCHER)
                            }
                            pm.queryIntentActivities(intent, 0).map { resolveInfo ->
                                resolveInfo.loadLabel(pm).toString() to resolveInfo.activityInfo.packageName
                            }.distinctBy { it.second }.sortedBy { it.first.lowercase() }
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }

                if (showAppPicker) {
                    var pickerQuery by remember { mutableStateOf("") }
                    val displayedApps = remember(pickerQuery, installedApps) {
                        val apps = installedApps
                        if (apps == null || pickerQuery.isBlank()) {
                            apps
                        } else {
                            val q = pickerQuery.trim().lowercase()
                            apps.filter {
                                it.first.lowercase().contains(q) || it.second.lowercase().contains(q)
                            }
                        }
                    }

                    AlertDialog(
                        onDismissRequest = { showAppPicker = false },
                        title = {
                            Text(
                                "Select Installed App",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 380.dp)
                            ) {
                                OutlinedTextField(
                                    value = pickerQuery,
                                    onValueChange = { pickerQuery = it },
                                    placeholder = { Text("Search installed apps") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Search,
                                            contentDescription = null
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                )

                                InstalledAppList(displayedApps) { appName, pkgName ->
                                    targetPackageInput = pkgName
                                    val (prefix, url) = fieldsForPickedApp(
                                        pkg = pkgName,
                                        appName = appName,
                                        trigger = shortcutTriggerSymbol,
                                        prefix = prefixInput,
                                        url = urlInput,
                                        lastSuggestedPrefix = lastSuggestedPrefix,
                                        lastSuggestedUrl = lastSuggestedUrl
                                    )
                                    if (prefix != prefixInput) {
                                        prefixInput = prefix
                                        lastSuggestedPrefix = prefix
                                    }
                                    if (url != urlInput) {
                                        urlInput = url
                                        lastSuggestedUrl = url
                                    }
                                    showAppPicker = false
                                    errorMsg = null
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showAppPicker = false }) {
                                Text("Close")
                            }
                        }
                    )
                }

                AlertDialog(
                    onDismissRequest = {
                        editingBang = null
                        showAddDialog = false
                    },
                    title = {
                        Text(
                            if (editingBang != null) "Edit Web Shortcut" else "Add Web Shortcut",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = prefixInput,
                                onValueChange = {
                                    prefixInput = it.filter { c -> !c.isWhitespace() }
                                    errorMsg = null
                                },
                                label = { Text("Shortcut Trigger") },
                                placeholder = { Text("${shortcutTriggerSymbol}wiki") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = targetPackageInput,
                                onValueChange = {
                                    targetPackageInput = it
                                    errorMsg = null
                                },
                                label = { Text("Package Name") },
                                placeholder = { Text("com.example.app") },
                                trailingIcon = {
                                    IconButton(onClick = { showAppPicker = true }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Apps,
                                            contentDescription = "Select installed app",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = {
                                    urlInput = it
                                    errorMsg = null
                                },
                                label = { Text("Search URL (use %s for query)") },
                                placeholder = { Text("https://en.wikipedia.org/wiki/%s") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (errorMsg != null) {
                                Text(
                                    text = errorMsg!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val trimmedPrefix = prefixInput.trim()
                                val trimmedPackage = targetPackageInput.trim()
                                var trimmedUrl = urlInput.trim()

                                if (trimmedPrefix.length < 2) {
                                    errorMsg = "Trigger must be at least 2 characters (e.g. ${shortcutTriggerSymbol}wiki)"
                                    return@TextButton
                                }
                                if (trimmedPrefix.length > 24 || trimmedPrefix.any { it.isWhitespace() || it.isISOControl() }) {
                                    errorMsg = "Trigger must be one word of at most 24 characters"
                                    return@TextButton
                                }
                                if (webShortcutTriggerTaken(trimmedPrefix.lowercase(), editingBang, displayedActiveBangs)) {
                                    errorMsg = "Another shortcut already uses ${trimmedPrefix.lowercase()}"
                                    return@TextButton
                                }
                                if (trimmedPackage.isNotBlank() && !PACKAGE_NAME_REGEX.matches(trimmedPackage)) {
                                    errorMsg = "That isn't a valid app package name"
                                    return@TextButton
                                }
                                if (trimmedUrl.isBlank()) {
                                    errorMsg = "Enter a search address"
                                    return@TextButton
                                }
                                if (!trimmedUrl.contains("%s")) {
                                    trimmedUrl = if (trimmedUrl.endsWith("/") || trimmedUrl.endsWith("=")) {
                                        "${trimmedUrl}%s"
                                    } else if (trimmedUrl.contains("?")) {
                                        "${trimmedUrl}&q=%s"
                                    } else {
                                        "${trimmedUrl}/?q=%s"
                                    }
                                }
                                // Same checks as the custom search engine: https only, no credentials, no script or
                                // other schemes. The canonical form is what gets stored.
                                when (val check = com.pixel.intelligentsearch.core.security.CustomSearchUrl.validate(trimmedUrl)) {
                                    is com.pixel.intelligentsearch.core.security.CustomSearchUrl.Validation.Valid -> trimmedUrl = check.template
                                    is com.pixel.intelligentsearch.core.security.CustomSearchUrl.Validation.Invalid -> {
                                        errorMsg = check.reason
                                        return@TextButton
                                    }
                                }

                                val derivedName = if (trimmedPackage.isNotBlank()) {
                                    try {
                                        val appInfo = context.packageManager.getApplicationInfo(trimmedPackage, 0)
                                        context.packageManager.getApplicationLabel(appInfo).toString()
                                    } catch (_: Exception) {
                                        trimmedPackage.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                                    }
                                } else {
                                    trimmedPrefix.trimStart { !it.isLetterOrDigit() }.replaceFirstChar { it.uppercase() }.ifBlank { "Shortcut" }
                                }

                                val newBang = com.pixel.intelligentsearch.core.bangs.SearchBang(
                                    prefix = trimmedPrefix.lowercase(),
                                    name = derivedName,
                                    urlTemplate = trimmedUrl,
                                    targetPackage = trimmedPackage.ifBlank { null },
                                    isBuiltIn = false,
                                    description = "Custom shortcut for $derivedName"
                                )

                                localDismissedPrefixes = localDismissedPrefixes - newBang.displayPrefix
                                viewModel?.saveBang(newBang, replacing = editingBang)
                                editingBang = null
                                showAddDialog = false
                            }
                        ) {
                            Text(if (editingBang != null) "Save" else "Add")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            editingBang = null
                            showAddDialog = false
                        }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

/**
 * The shortcut editor's list of installed apps as (label, package): progress while [apps] is still loading (null),
 * then the apps or a note that none match. Each row's icon comes from the shared icon cache as the row is shown.
 */
@Composable
internal fun InstalledAppList(apps: List<Pair<String, String>>?, onPick: (appName: String, packageName: String) -> Unit) {
    val context = LocalContext.current
    when {
        apps == null -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.testTag(com.pixel.intelligentsearch.core.ui.LOADING_INDICATOR_TAG))
        }
        apps.isEmpty() -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No matching apps found",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(apps, key = { it.second }) { (appName, pkgName) ->
                Surface(
                    onClick = { onPick(appName, pkgName) },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val appBitmap by produceState(peekNativeAppIcon(pkgName), pkgName) {
                            value = withContext(Dispatchers.IO) { getNativeAppIcon(context, pkgName) }
                        }
                        val loadedBitmap = appBitmap
                        if (loadedBitmap != null) {
                            Image(
                                bitmap = loadedBitmap,
                                contentDescription = appName,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Android,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                appName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                pkgName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
