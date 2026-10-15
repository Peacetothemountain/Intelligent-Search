package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.data.settings.get
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.ui.TorBrowserInstallDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.pixel.intelligentsearch.core.bangs.SearchBang
import com.pixel.intelligentsearch.core.bangs.SearchBangManager
import com.pixel.intelligentsearch.core.data.IntelligentSearchSettings
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine

/** One animation frame shared by every shortcut badge. */
class ShortcutBadgeFrame(val morph: Morph, val rotationAngle: Float, val morphProgress: Float)

/**
 * [frame] is read only inside the Canvas draw lambda, so the running animation redraws the badges without
 * recomposing them or the card around them.
 */
@Composable
fun SynchronizedMorphingShortcutBadge(
    shortcut: String,
    frame: () -> ShortcutBadgeFrame,
    modifier: Modifier = Modifier
) {
    val containerColor = MaterialTheme.colorScheme.secondaryContainer
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer

    val nativePath = remember { android.graphics.Path() }
    val composePath = remember(nativePath) { nativePath.asComposePath() }

    Box(
        modifier = modifier.size(46.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val scaleFactor = size.minDimension * 0.44f
            val current = frame()

            translate(left = center.x, top = center.y) {
                rotate(degrees = current.rotationAngle, pivot = Offset.Zero) {
                    nativePath.rewind()
                    current.morph.toPath(progress = current.morphProgress, path = nativePath)
                    scale(scale = scaleFactor, pivot = Offset.Zero) {
                        drawPath(
                            path = composePath,
                            color = containerColor
                        )
                    }
                }
            }
        }

        Text(
            text = shortcut,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = if (shortcut.length > 3) 10.sp else 12.sp,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
fun SlideToRemoveAllBar(
    onRemoveAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val context = LocalContext.current
    val hapticEngine = remember(context) { com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context) }

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val thumbSizeDp = 40.dp
    val thumbSizePx = with(LocalDensity.current) { thumbSizeDp.toPx() }
    val maxDragPx = (trackWidthPx - thumbSizePx - with(LocalDensity.current) { 8.dp.toPx() }).coerceAtLeast(0f)

    val dragFraction = if (maxDragPx > 0f) (dragOffsetX / maxDragPx).coerceIn(0f, 1f) else 0f
    val isThresholdReached = dragFraction >= 0.82f

    val animDragOffsetX by androidx.compose.animation.core.animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "removeSliderSpring"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .clip(RoundedCornerShape(24.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f + 0.35f * dragFraction),
                RoundedCornerShape(24.dp)
            ),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart
        ) {
            // Fill background behind dragging thumb
            if (trackWidthPx > 0f && animDragOffsetX > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(with(LocalDensity.current) { (animDragOffsetX + thumbSizePx + 8.dp.toPx()).toDp() })
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f + 0.35f * dragFraction))
                )
            }

            // Central Prompt label
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isThresholdReached) "Release to Remove All" else "Slide to Remove All",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isThresholdReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = (1f - dragFraction * 0.7f).coerceIn(0.2f, 1f))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (isThresholdReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = (1f - dragFraction * 0.7f).coerceIn(0.2f, 1f)),
                    modifier = Modifier.size(14.dp)
                )
            }

            // Draggable Thumb Handle
            Box(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset((animDragOffsetX + 4.dp.toPx()).toInt(), 0) }
                    .size(thumbSizeDp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        if (isThresholdReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)
                    )
                    .pointerInput(maxDragPx) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragOffsetX >= maxDragPx * 0.82f) {
                                    hapticEngine.performPredictiveBackHaptic(view)
                                    onRemoveAll()
                                }
                                dragOffsetX = 0f
                            },
                            onDragCancel = {
                                dragOffsetX = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetX = (dragOffsetX + dragAmount).coerceIn(0f, maxDragPx)
                                if (dragOffsetX >= maxDragPx * 0.82f && !isThresholdReached) {
                                    hapticEngine.performPredictiveBackHaptic(view)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
                    contentDescription = "Slide to remove all",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebSearchScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsViewModel = LocalSettingsViewModel.current
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
        topBar = {
            TopAppBar(
                title = { Text("Web Search", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsCard {
                var searchEngine by rememberSetting(SettingsManager.SEARCH_ENGINE)
                var customEngineName by rememberSetting(SettingsManager.CUSTOM_SEARCH_ENGINE_NAME)
                val effectiveSearchEngineName = if (searchEngine == "Custom") customEngineName else searchEngine
                var showTorDialog by remember { mutableStateOf(false) }

                SettingsDropdownRow(
                    title = "Primary Search App",
                    subtitle = effectiveSearchEngineName,
                    icon = Icons.Outlined.Search,
                    options = listOf("Google", "DuckDuckGo", "Bing", "Tor Project", "Custom"),
                    selectedOption = searchEngine,
                    onOptionSelected = { selected ->
                        if (selected == "Tor Project") {
                            val pm = context.packageManager
                            val isTorInstalled = pm.getLaunchIntentForPackage("org.torproject.torbrowser") != null ||
                                pm.getLaunchIntentForPackage("org.torproject.torbrowser_alpha") != null
                            if (!isTorInstalled) {
                                showTorDialog = true
                                return@SettingsDropdownRow
                            }
                        }
                        if (selected == "Custom" && settingsViewModel != null &&
                            prefs[SettingsManager.CUSTOM_SEARCH_ENGINE_URL].isNullOrBlank()
                        ) {
                            // The URL field shows this default; persist it so searches use what is displayed
                            // instead of silently falling back to the generic web-search intent.
                            val defaultUrl = "https://duckduckgo.com/?q=%s"
                            prefs.edit().put(SettingsManager.CUSTOM_SEARCH_ENGINE_URL, defaultUrl).apply()
                            settingsViewModel.updateSetting(SettingsManager.CUSTOM_SEARCH_ENGINE_URL, defaultUrl)
                        }
                        searchEngine = selected
                    },
                    showDivider = searchEngine != "Custom"
                )

                if (showTorDialog) {
                    TorBrowserInstallDialog(
                        onDismiss = { showTorDialog = false }
                    )
                }
                if (searchEngine == "Custom") {
                    var customUrl by rememberSetting(SettingsManager.CUSTOM_SEARCH_ENGINE_URL)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SearchAppColorfulIcon(appName = customEngineName, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = customEngineName,
                            onValueChange = { customEngineName = it },
                            label = { Text("Search App Name") },
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    androidx.compose.material3.OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text("Custom Search URL (use %s for query)") },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
                var webSuggestions by rememberSetting(SettingsManager.SEARCH_WEB_SUGGESTIONS)
                val advancedProtectionOn by remember(context) {
                    com.pixel.intelligentsearch.core.security.AdvancedProtectionMonitor.ensureRegistered(context)
                    com.pixel.intelligentsearch.core.security.AdvancedProtectionMonitor.enabled
                }.collectAsState()
                SettingsRowToggle(
                    title = "Web Suggestions",
                    subtitle = if (advancedProtectionOn) {
                        "Paused While Android Advanced Protection Is On."
                    } else {
                        "Sends What You Type to Your Search Engine for Live Suggestions."
                    },
                    icon = Icons.Outlined.ChatBubbleOutline,
                    isChecked = webSuggestions && !advancedProtectionOn,
                    onCheckedChange = { if (!advancedProtectionOn) webSuggestions = it },
                    showDivider = true
                )

                var searchPreviousSearches by rememberSetting(SettingsManager.SEARCH_PREVIOUS_SEARCHES)
                SettingsRowToggle(
                    title = "Search History",
                    subtitle = "Previously Searched Web Queries.",
                    icon = Icons.Outlined.History,
                    isChecked = searchPreviousSearches,
                    onCheckedChange = { searchPreviousSearches = it },
                    showDivider = true
                )
                
                var webResultsCount by rememberSetting(SettingsManager.WEB_RESULTS_COUNT)
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Web Results: $webResultsCount",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(
                            onClick = {
                                performClickHaptic(context)
                                webResultsCount = 5
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = "Reset to Default",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Android17Slider(
                        label = "Web results",
                        value = webResultsCount.toFloat(),
                        onValueChange = { webResultsCount = it.toInt() },
                        valueRange = 1f..20f,
                        steps = 18,
                        isSquiggly = false,
                        modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                var webWeight by rememberSetting(SettingsManager.SEARCH_WEIGHT_WEB)
                val webLevelIndex = remember(webWeight) { PriorityWeightHelper.weightToLevelIndex(webWeight) }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Web Priority Weight: ${PriorityWeightHelper.levelLabel(webLevelIndex)}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(
                            onClick = {
                                performClickHaptic(context)
                                webWeight = PriorityWeightHelper.DEFAULT_WEIGHT
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = "Reset to Default",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = "Adjust Ranking Priority of Web Search.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Android17Slider(
                        label = "Web priority weight",
                        value = webLevelIndex.toFloat(),
                        onValueChange = { newValue ->
                            val idx = Math.round(newValue).coerceIn(0, PriorityWeightHelper.LEVELS.size - 1)
                            webWeight = PriorityWeightHelper.levelIndexToWeight(idx)
                        },
                        valueRange = 0f..(PriorityWeightHelper.LEVELS.size - 1).toFloat(),
                        steps = PriorityWeightHelper.LEVELS.size - 2,
                        isSquiggly = false,
                        modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
                    )
                }
            }

            Text(
                "Quick Web Shortcuts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            )
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
                    var triggerDropdownSelection by remember(shortcutTriggerSymbol) {
                        mutableStateOf(if (isPredefined) shortcutTriggerSymbol else "Custom")
                    }

                    SettingsDropdownRow(
                        title = "Shortcut Trigger Symbol",
                        subtitle = "Prefix Queries with '$shortcutTriggerSymbol'.",
                        icon = Icons.Outlined.Tag,
                        options = triggerOptions,
                        selectedOption = triggerDropdownSelection,
                        onOptionSelected = { selected ->
                            triggerDropdownSelection = selected
                            if (selected != "Custom") {
                                shortcutTriggerSymbol = selected
                            }
                        },
                        showDivider = true
                    )

                    if (triggerDropdownSelection == "Custom") {
                        var customInputText by remember(triggerDropdownSelection) {
                            mutableStateOf(if (isPredefined) "" else shortcutTriggerSymbol)
                        }
                        OutlinedTextField(
                            value = customInputText,
                            onValueChange = { input ->
                                val clean = input.filter { !it.isWhitespace() }
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
                        SearchBangManager.BUILT_IN_BANGS.filter { it.displayPrefix in effectiveDisabledPrefixes }
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
                                    color = MaterialTheme.colorScheme.onSurface
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
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                                    displayedActiveBangs.forEach { bang ->
                                        if (bang.isBuiltIn) {
                                            viewModel?.disableBuiltInBang(bang.displayPrefix)
                                        } else {
                                            viewModel?.deleteCustomBang(bang.displayPrefix)
                                        }
                                    }
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
                                                localDismissedPrefixes = localDismissedPrefixes + bang.displayPrefix
                                                if (bang.isBuiltIn) {
                                                    viewModel?.disableBuiltInBang(bang.displayPrefix)
                                                } else {
                                                    viewModel?.deleteCustomBang(bang.displayPrefix)
                                                }
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
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .bouncyClickable {
                                                        editingBang = bang
                                                        showAddDialog = true
                                                    },
                                                shape = RoundedCornerShape(24.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
                                                        val appLabel = remember(bang.targetPackage, bang.name) {
                                                            if (!bang.targetPackage.isNullOrBlank()) {
                                                                try {
                                                                    val appInfo = context.packageManager.getApplicationInfo(bang.targetPackage, 0)
                                                                    context.packageManager.getApplicationLabel(appInfo).toString()
                                                                } catch (_: Exception) {
                                                                    bang.name.ifBlank { bang.targetPackage }
                                                                }
                                                            } else {
                                                                bang.name
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
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(24.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
                                                        localDismissedPrefixes = localDismissedPrefixes - bang.displayPrefix
                                                        viewModel?.enableBuiltInBang(bang.displayPrefix)
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
                        var errorMsg by remember { mutableStateOf<String?>(null) }
                        var showAppPicker by remember { mutableStateOf(false) }

                        val installedApps = remember(context) {
                            try {
                                val pm = context.packageManager
                                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                                    addCategory(Intent.CATEGORY_LAUNCHER)
                                }
                                val resolveInfos = pm.queryIntentActivities(intent, 0)
                                resolveInfos.map { resolveInfo ->
                                    val label = resolveInfo.loadLabel(pm).toString()
                                    val packageName = resolveInfo.activityInfo.packageName
                                    label to packageName
                                }.distinctBy { it.second }.sortedBy { it.first.lowercase() }
                            } catch (_: Exception) {
                                emptyList<Pair<String, String>>()
                            }
                        }

                        if (showAppPicker) {
                            var pickerQuery by remember { mutableStateOf("") }
                            val displayedApps = remember(pickerQuery, installedApps) {
                                if (pickerQuery.isBlank()) {
                                    installedApps
                                } else {
                                    val q = pickerQuery.trim().lowercase()
                                    installedApps.filter {
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

                                        if (displayedApps.isEmpty()) {
                                            Box(
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
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                items(displayedApps, key = { it.second }) { (appName, pkgName) ->
                                                    Surface(
                                                        onClick = {
                                                            targetPackageInput = pkgName

                                                            // Automatically adjust shortcut trigger with user's selected trigger symbol
                                                            val clean = appName.lowercase().filter { it.isLetterOrDigit() }
                                                            val slug = when (pkgName) {
                                                                "com.google.android.youtube" -> "yt"
                                                                "com.spotify.music" -> "spot"
                                                                "org.wikipedia" -> "w"
                                                                "com.reddit.frontpage" -> "r"
                                                                "com.github.android" -> "gh"
                                                                "com.google.android.apps.maps" -> "maps"
                                                                "com.amazon.mShop.android.shopping" -> "az"
                                                                "com.twitter.android" -> "x"
                                                                "tv.twitch.android.app" -> "tw"
                                                                "com.instagram.android" -> "ig"
                                                                "com.pinterest" -> "pin"
                                                                "com.imdb.mobile" -> "imdb"
                                                                "com.google.android.googlequicksearchbox" -> "g"
                                                                "com.duckduckgo.mobile.android" -> "ddg"
                                                                else -> if (clean.length <= 4) clean else clean.take(4)
                                                            }
                                                            prefixInput = "$shortcutTriggerSymbol$slug"

                                                            // Suggest URL if empty or generic
                                                            if (urlInput.isBlank() || urlInput.contains("/search?q=%s")) {
                                                                urlInput = when (pkgName) {
                                                                    "com.google.android.youtube" -> "https://www.youtube.com/results?search_query=%s"
                                                                    "com.spotify.music" -> "https://open.spotify.com/search/%s"
                                                                    "org.wikipedia" -> "https://en.wikipedia.org/wiki/%s"
                                                                    "com.reddit.frontpage" -> "https://www.reddit.com/search/?q=%s"
                                                                    "com.github.android" -> "https://github.com/search?q=%s"
                                                                    "com.google.android.apps.maps" -> "https://www.google.com/maps/search/%s"
                                                                    "com.amazon.mShop.android.shopping" -> "https://www.amazon.com/s?k=%s"
                                                                    "com.twitter.android" -> "https://x.com/search?q=%s"
                                                                    "tv.twitch.android.app" -> "https://www.twitch.tv/search?term=%s"
                                                                    "com.instagram.android" -> "https://www.instagram.com/explore/tags/%s"
                                                                    "com.pinterest" -> "https://www.pinterest.com/search/pins/?q=%s"
                                                                    "com.imdb.mobile" -> "https://www.imdb.com/find/?q=%s"
                                                                    else -> {
                                                                        val domain = appName.lowercase().filter { it.isLetterOrDigit() }
                                                                        "https://www.${domain}.com/search?q=%s"
                                                                    }
                                                                }
                                                            }
                                                            showAppPicker = false
                                                            errorMsg = null
                                                        },
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
                                                            val appDrawable = remember(pkgName) {
                                                                runCatching { context.packageManager.getApplicationIcon(pkgName) }.getOrNull()
                                                            }
                                                            val appBitmap = remember(appDrawable) {
                                                                runCatching { appDrawable?.toBitmap(width = 96, height = 96)?.asImageBitmap() }.getOrNull()
                                                            }
                                                            if (appBitmap != null) {
                                                                Image(
                                                                    bitmap = appBitmap,
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
                                        if (trimmedUrl.isBlank() || (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://"))) {
                                            errorMsg = "URL must start with http:// or https://"
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

                                        val prevBang = editingBang
                                        if (prevBang != null) {
                                            if (prevBang.isBuiltIn) {
                                                viewModel?.disableBuiltInBang(prevBang.displayPrefix)
                                            } else if (!prevBang.displayPrefix.equals(newBang.displayPrefix, ignoreCase = true)) {
                                                viewModel?.deleteCustomBang(prevBang.displayPrefix)
                                            }
                                        }

                                        localDismissedPrefixes = localDismissedPrefixes - newBang.displayPrefix
                                        viewModel?.enableBuiltInBang(newBang.displayPrefix)
                                        viewModel?.saveCustomBang(newBang)
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
    }
}

// -----------------------------------------------------------------------------------------