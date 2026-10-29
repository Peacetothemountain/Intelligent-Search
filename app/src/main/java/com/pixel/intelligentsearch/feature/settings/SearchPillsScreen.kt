package com.pixel.intelligentsearch.feature.settings

import androidx.activity.compose.BackHandler
import com.pixel.intelligentsearch.core.theme.pageTrack
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.pixel.intelligentsearch.core.data.settings.SearchPillProfiles
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// SEARCH PILLS SCREEN
// -----------------------------------------------------------------------------------------
private val nativeAppIconCache = com.pixel.intelligentsearch.core.ui.imageBitmapLruCache<String>(heapDivisor = 32)

fun peekNativeAppIcon(packageName: String): androidx.compose.ui.graphics.ImageBitmap? = nativeAppIconCache.get(packageName)

fun getNativeAppIcon(context: Context, packageName: String): androidx.compose.ui.graphics.ImageBitmap? {
    val cached = nativeAppIconCache.get(packageName)
    if (cached != null) return cached
    return try {
        val pm = context.packageManager
        val drawable = pm.getApplicationIcon(packageName)
        val bitmap = if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val d = drawable.mutate()
            val width = (if (d.intrinsicWidth > 0) d.intrinsicWidth else 96).coerceIn(1, 192)
            val height = (if (d.intrinsicHeight > 0) d.intrinsicHeight else 96).coerceIn(1, 192)
            val bmp = android.graphics.Bitmap.createBitmap(
                width,
                height,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bmp)
            d.setBounds(0, 0, canvas.width, canvas.height)
            d.draw(canvas)
            bmp
        }
        val imgBitmap = bitmap.asImageBitmap()
        nativeAppIconCache.put(packageName, imgBitmap)
        imgBitmap
    } catch (e: Exception) {
        null
    }
}

data class AppPickerItem(
    val packageName: String,
    val label: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPillsScreen(
    prefs: SharedPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val profiles = remember(prefs) { SearchPillProfiles(prefs) }
    var activeProfile by remember { mutableIntStateOf(profiles.activeProfile) }
    // One state object, so drag handlers never write a stale copy.
    var localPillList by remember { mutableStateOf(profiles.pills()) }
    var resetCounter by remember { mutableIntStateOf(0) }
    // Pills Reset brought back, each with how many times: a new key gives each a fresh card that slides in.
    var revived by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    fun persistPills(newPills: List<String>) {
        localPillList = newPills
        profiles.setPills(newPills)
    }

    // Pills swiped away and still flying off screen: gone as far as the user is concerned, saved once clear of the
    // edge, or at once if the list goes first (the page closes, the picker opens, the profile changes).
    val leavingPills = remember { mutableStateListOf<String>() }
    fun finishLeaving(packageName: String) {
        if (leavingPills.remove(packageName)) persistPills(localPillList - packageName)
    }
    fun flushLeaving() {
        if (leavingPills.isEmpty()) return
        val gone = leavingPills.toSet()
        leavingPills.clear()
        persistPills(localPillList - gone)
    }

    // Pills to slide in from the right when next shown (after Reset, or added from the picker), each with its place
    // in the ripple. Asked for under [enterRound]; anything not shown soon after (scrolled out of view) just appears.
    var enteringPills by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var enterRound by remember { mutableIntStateOf(0) }
    fun slideIn(packages: List<String>) {
        enteringPills = packages.withIndex().associate { (i, pkg) -> pkg to i }
        enterRound++
    }

    fun switchProfile(id: Int) {
        if (id == activeProfile) return
        flushLeaving()
        enteringPills = emptyMap()
        localPillList = profiles.switchTo(id)
        activeProfile = id
        resetCounter++
    }

    // TalkBack's way to reorder: moves a pill one place, as dragging it over its neighbour does.
    fun movePill(packageName: String, by: Int): Boolean {
        val from = localPillList.indexOf(packageName)
        val to = from + by
        if (from == -1 || to !in localPillList.indices) return false
        persistPills(localPillList.toMutableList().apply { add(to, removeAt(from)) })
        return true
    }

    var showAppPicker by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<AppPickerItem>>(emptyList()) }
    var isAppsLoading by remember { mutableStateOf(false) }
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedApps by remember { mutableStateOf(setOf<String>()) }
    var showMaxWarning by remember { mutableStateOf(false) }
    // The picker's search, opened from the magnifier into a pill at the bottom; leaving the picker closes and clears it.
    var pickerSearching by remember { mutableStateOf(false) }
    var appSearchQuery by remember { mutableStateOf("") }
    LaunchedEffect(showAppPicker) {
        if (!showAppPicker) {
            pickerSearching = false
            appSearchQuery = ""
        }
    }

    LaunchedEffect(showAppPicker) {
        if (showAppPicker && installedApps.isEmpty()) {
            isAppsLoading = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val pm = context.packageManager
                    val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                        addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    }
                    val resolveInfoList = pm.queryIntentActivities(intent, 0)
                    // Labels only: icons are drawn per row as it comes into view, so the list doesn't wait for all of them.
                    val apps = resolveInfoList.asSequence()
                        .distinctBy { it.activityInfo.packageName }
                        .map { AppPickerItem(packageName = it.activityInfo.packageName, label = it.loadLabel(pm).toString()) }
                        // Installed AI assistants lead the list: their pills ask them the search.
                        .sortedWith(compareBy({ com.pixel.intelligentsearch.core.bangs.AiAssistants.forPackage(it.packageName) == null }, { it.label.lowercase() }))
                        .toList()
                    installedApps = apps
                } catch (e: Exception) {
                    android.util.Log.w("IntelligentSearch", "Unexpected error", e)
                } finally {
                    isAppsLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) { profiles.ensureDefaultSaved() }

    // System back steps out of multi-select, then out of the picker, as the toolbar's back button does.
    BackHandler(enabled = showAppPicker) {
        if (multiSelectMode) {
            multiSelectMode = false
            selectedApps = emptySet()
        } else {
            showAppPicker = false
        }
    }
    // Back closes the picker's search first.
    BackHandler(enabled = showAppPicker && pickerSearching) {
        pickerSearching = false
        appSearchQuery = ""
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = settingsTopBarColors(),
                title = {
                    if (showAppPicker && multiSelectMode) {
                        Text("${selectedApps.size} Selected", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, autoSize = oneLineAutoSize(MaterialTheme.typography.headlineSmall))
                    } else if (showAppPicker) {
                        Text("Add Applications", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, autoSize = oneLineAutoSize(MaterialTheme.typography.headlineSmall))
                    } else {
                        Text("Application Search", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, autoSize = oneLineAutoSize(MaterialTheme.typography.headlineSmall))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showAppPicker && multiSelectMode) {
                            multiSelectMode = false
                            selectedApps = emptySet()
                        } else if (showAppPicker) {
                            showAppPicker = false
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(if (showAppPicker && multiSelectMode) Icons.Outlined.Close else Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (showAppPicker && multiSelectMode) {
                        IconButton(onClick = {
                            val currentList = localPillList.toMutableList()
                            val newApps = selectedApps.filter { !currentList.contains(it) }
                            if (currentList.size + newApps.size > SearchPillProfiles.MAX_PILLS) {
                                showMaxWarning = true
                            } else {
                                currentList.addAll(newApps)
                                resetCounter++
                                persistPills(currentList)
                                slideIn(newApps)
                                multiSelectMode = false
                                selectedApps = emptySet()
                                showAppPicker = false
                            }
                        }) {
                            Icon(Icons.Outlined.Check, contentDescription = "Add Selected")
                        }
                    } else if (showAppPicker) {
                        SearchToggleButton(open = pickerSearching, onToggle = {
                            if (pickerSearching) appSearchQuery = ""
                            pickerSearching = !pickerSearching
                        })
                    } else {
                        ProfileStateActions(
                            profile = activeProfile,
                            onReset = {
                                // Reset puts back the saved list, so a pill still flying off stays. Only the pills
                                // that were missing (swiped away, or still flying off) slide back in; the rest stay put.
                                val shown = (localPillList - leavingPills.toSet()).toSet()
                                leavingPills.clear()
                                val saved = profiles.resetToSaved()
                                val missing = saved.filter { it !in shown }
                                revived = revived + missing.associateWith { (revived[it] ?: 0) + 1 }
                                localPillList = saved
                                slideIn(missing)
                            },
                            // A pill flying off is already gone: it isn't part of what's saved.
                            onSave = { profiles.saveState(localPillList - leavingPills.toSet()) }
                        )
                        IconButton(onClick = {
                            if (localPillList.size - leavingPills.size >= SearchPillProfiles.MAX_PILLS) {
                                showMaxWarning = true
                            } else {
                                showAppPicker = true
                            }
                        }) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add Pill")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (showMaxWarning) {
            AlertDialog(
                onDismissRequest = { showMaxWarning = false },
                title = { Text("Maximum Reached", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
                text = { Text("You cannot add more than ${SearchPillProfiles.MAX_PILLS} apps to the Quick Launch at a time.", style = MaterialTheme.typography.bodyLarge) },
                confirmButton = {
                    TextButton(onClick = { showMaxWarning = false }) {
                        Text("OK", style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        }

        if (showAppPicker) {
            val filteredApps = remember(installedApps, appSearchQuery) {
                if (appSearchQuery.isBlank()) installedApps
                else installedApps.filter {
                    it.label.contains(appSearchQuery, ignoreCase = true) ||
                    it.packageName.contains(appSearchQuery, ignoreCase = true)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isAppsLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.testTag(com.pixel.intelligentsearch.core.ui.LOADING_INDICATOR_TAG))
                    }
                } else {
                    // The same container and rows as Manage Hidden Apps, without the switch: tapping an app adds it.
                    com.pixel.intelligentsearch.core.ui.expressive.MatteShell(
                        shape = RoundedCornerShape(24.dp),
                        containerColor = MaterialTheme.colorScheme.pageTrack(0.5f),
                        border = null,
                        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
                    ) {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp).underBottomSearch(pickerSearching),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = if (pickerSearching) PILL_CLEARANCE else 0.dp)
                    ) {
                        items(filteredApps.size, key = { filteredApps[it].packageName }) { index ->
                            val appItem = filteredApps[index]
                            val packageName = appItem.packageName
                            val isSelected = selectedApps.contains(packageName)
                            val isAlreadyAdded = localPillList.contains(packageName)
                            // Cached icons show at once; others are rasterized off the main thread.
                            val appIcon by produceState(peekNativeAppIcon(packageName), packageName) {
                                value = withContext(Dispatchers.IO) { getNativeAppIcon(context, packageName) }
                            }

                            SettingsAppRow(
                                title = appItem.label,
                                subtitle = when {
                                    isAlreadyAdded -> "Already in Quick Launch."
                                    com.pixel.intelligentsearch.core.bangs.AiAssistants.forPackage(packageName) != null -> "AI Assistant. Its Pill Asks It Your Search."
                                    // An app without a label of its own is already titled by its package name.
                                    appItem.label == packageName -> ""
                                    else -> packageName.breakableAtDots()
                                },
                                icon = appIcon ?: Icons.Outlined.Apps,
                                selected = isSelected,
                                onClick = {
                                    if (multiSelectMode) {
                                        selectedApps = if (isSelected) selectedApps - packageName else selectedApps + packageName
                                        if (selectedApps.isEmpty()) multiSelectMode = false
                                    } else {
                                        if (!isAlreadyAdded) {
                                            if (localPillList.size >= SearchPillProfiles.MAX_PILLS) {
                                                showMaxWarning = true
                                            } else {
                                                val updated = localPillList + packageName
                                                resetCounter++
                                                persistPills(updated)
                                                slideIn(listOf(packageName))
                                                showAppPicker = false
                                            }
                                        } else {
                                            showAppPicker = false
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!multiSelectMode) {
                                        multiSelectMode = true
                                        selectedApps = setOf(packageName)
                                    }
                                },
                                // Nor over the selected row below it.
                                showDivider = index < filteredApps.size - 1 &&
                                    filteredApps[index + 1].packageName !in selectedApps
                            )
                        }
                        if (filteredApps.isEmpty() && appSearchQuery.isNotBlank()) {
                            item(key = "no_match") { NoAppsMatch(appSearchQuery) }
                        }
                    }
                    }
                }
            }
            BottomSearchPill(
                open = pickerSearching,
                query = appSearchQuery,
                onQueryChange = { appSearchQuery = it },
                placeholder = "Search Apps",
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Segmented Profile Bar: each profile's outlined icon, filled while it is the active one.
                ProfileSelector(
                    count = SearchPillProfiles.PROFILE_COUNT,
                    selected = activeProfile,
                    onSelect = { id -> switchProfile(id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )

                if (localPillList.isEmpty()) {
                    com.pixel.intelligentsearch.core.ui.expressive.MatteShell(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(24.dp),
                        containerColor = MaterialTheme.colorScheme.pageTrack(0.4f),
                        border = null
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Outlined.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "Profile $activeProfile Is Empty",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Tap '+' to Add Applications to Profile $activeProfile. All Changes Auto-Save in Real Time.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    if (localPillList.size >= SearchPillProfiles.MAX_PILLS) {
                                        showMaxWarning = true
                                    } else {
                                        showAppPicker = true
                                    }
                                },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add Applications")
                            }
                        }
                    }
                } else {
                    val view = LocalView.current
                    val hapticEngine = remember(context) { com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context) }
                    var draggingPackage by remember { mutableStateOf<String?>(null) }
                    var dragStartIndex by remember { mutableIntStateOf(-1) }
                    var targetSlotIndex by remember { mutableIntStateOf(-1) }
                    var itemDragOffset by remember { mutableFloatStateOf(0f) }
                    var isDropping by remember { mutableStateOf(false) }
                    var dropJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                    var wasReordered by remember { mutableStateOf(false) }
                    val density = LocalDensity.current
                    val defaultItemHeight = with(density) { 86.dp.toPx() }
                    var measuredItemHeight by remember { mutableFloatStateOf(defaultItemHeight) }
                    var autoScrollVelocity by remember { mutableFloatStateOf(0f) }
                    val listState = rememberLazyListState()
                    val coroutineScope = rememberCoroutineScope()
                    val animationsOn by com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled()

                    // The list going away (page closed, picker opened) saves any pill still flying off.
                    DisposableEffect(Unit) { onDispose { flushLeaving() } }

                    // Pills added below the fold scroll into view to slide in; a slide-in not shown within a moment
                    // (the pill was out of view) is dropped, so it can't play later on a scroll.
                    LaunchedEffect(enterRound) {
                        if (enteringPills.isEmpty()) return@LaunchedEffect
                        val first = localPillList.indexOfFirst { it in enteringPills }
                        // Coming back from the picker, the list may not be laid out yet: wait for its first layout.
                        val visible = kotlinx.coroutines.withTimeoutOrNull(500) {
                            androidx.compose.runtime.snapshotFlow { listState.layoutInfo.visibleItemsInfo }.first { it.isNotEmpty() }
                        }.orEmpty().map { it.index }
                        if (first >= 0 && visible.isNotEmpty() && first !in visible) listState.animateScrollToItem(first)
                        kotlinx.coroutines.delay(1_500)
                        enteringPills = emptyMap()
                    }

                    LaunchedEffect(wasReordered) {
                        if (wasReordered) {
                            kotlinx.coroutines.delay(50)
                            wasReordered = false
                        }
                    }

                    LaunchedEffect(draggingPackage, autoScrollVelocity) {
                        if (draggingPackage != null && autoScrollVelocity != 0f) {
                            while (isActive && draggingPackage != null && autoScrollVelocity != 0f) {
                                val canScroll = if (autoScrollVelocity < 0f) listState.canScrollBackward else listState.canScrollForward
                                if (!canScroll) break
                                val scrolled = listState.scrollBy(autoScrollVelocity)
                                itemDragOffset += scrolled
                                kotlinx.coroutines.delay(16)
                            }
                        }
                    }

                    var pillAppLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
                    LaunchedEffect(localPillList) {
                        withContext(Dispatchers.IO) {
                            val pm = context.packageManager
                            val map = pillAppLabels.toMutableMap()
                            var changed = false
                            localPillList.forEach { pkg ->
                                if (!map.containsKey(pkg)) {
                                    try {
                                        val info = pm.getApplicationInfo(pkg, 0)
                                        map[pkg] = pm.getApplicationLabel(info).toString()
                                        getNativeAppIcon(context, pkg)
                                    } catch (_: Exception) {
                                        map[pkg] = pkg
                                    }
                                    changed = true
                                }
                            }
                            if (changed || pillAppLabels.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    pillAppLabels = map
                                }
                            }
                        }
                    }

                    val isReordering = draggingPackage != null || isDropping

                    androidx.compose.foundation.lazy.LazyColumn(
                        state = listState,
                        userScrollEnabled = !isReordering,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
                    ) {
                        items(localPillList, key = { it }) { packageName ->
                            val itemIndex = localPillList.indexOf(packageName)
                            val isDragging = draggingPackage == packageName

                            val itemShiftAnim = remember(packageName) { androidx.compose.animation.core.Animatable(0f) }

                            val targetShiftY = when {
                                isDragging -> 0f
                                draggingPackage != null && dragStartIndex != -1 && targetSlotIndex != -1 && measuredItemHeight > 10f -> {
                                    val startIdx = dragStartIndex
                                    val targetIdx = targetSlotIndex
                                    if (startIdx < targetIdx && itemIndex in (startIdx + 1)..targetIdx) {
                                        -measuredItemHeight
                                    } else if (startIdx > targetIdx && itemIndex in targetIdx until startIdx) {
                                        measuredItemHeight
                                    } else {
                                        0f
                                    }
                                }
                                else -> 0f
                            }

                            LaunchedEffect(targetShiftY, isReordering) {
                                if (!isReordering) {
                                    itemShiftAnim.snapTo(0f)
                                } else {
                                    itemShiftAnim.animateTo(
                                        targetValue = targetShiftY,
                                        animationSpec = androidx.compose.animation.core.spring(
                                            stiffness = 850f,
                                            dampingRatio = 0.70f
                                        )
                                    )
                                }
                            }


                            val itemModifier = if (isDragging) {
                                Modifier
                                    .zIndex(10f)
                                    .graphicsLayer {
                                        translationY = itemDragOffset
                                    }
                            } else {
                                Modifier
                                    .then(
                                        if (isReordering || wasReordered) {
                                            Modifier
                                        } else {
                                            Modifier.animateItem(
                                                fadeInSpec = null,
                                                fadeOutSpec = null,
                                                placementSpec = androidx.compose.animation.core.spring(
                                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy
                                                )
                                            )
                                        }
                                    )
                                    .zIndex(0f)
                                    .graphicsLayer {
                                        // Read here, in the draw phase, so the shift animation doesn't recompose the row.
                                        translationY = if (isReordering) itemShiftAnim.value else 0f
                                    }
                            }

                            Box(modifier = itemModifier) {
                                key(packageName, resetCounter, revived[packageName] ?: 0) {
                                    // Swiped toward a wall, the card presses into it, springs off, comes back through
                                    // the middle and leaves past the other edge; after Reset or an add it slides in
                                    // from the right and settles with a small bounce.
                                    val enterPlace = remember { enteringPills[packageName] }
                                    val bounce = rememberWallBounce(
                                        gap = 16.dp,
                                        entering = enterPlace != null && animationsOn,
                                        animate = { animationsOn },
                                        onThreshold = { past ->
                                            if (past) hapticEngine.performPredictiveBackHaptic(view)
                                            else hapticEngine.performPredictiveBackCancelHaptic(view)
                                        },
                                        onCommit = { if (packageName !in leavingPills) leavingPills.add(packageName) },
                                        onExited = { finishLeaving(packageName) }
                                    )
                                    // Scrolled out of view (or the list gone) mid-flight: it would never come back.
                                    DisposableEffect(bounce) {
                                        onDispose { if (bounce.phase == WallBouncePhase.Exiting) finishLeaving(packageName) }
                                    }
                                    LaunchedEffect(bounce) {
                                        if (enterPlace != null) {
                                            enteringPills = enteringPills - packageName
                                            bounce.enter(WallBounce.enterDelay(enterPlace))
                                        }
                                    }

                                    Box(modifier = Modifier.wallBounce(bounce, enabled = !isReordering)) {
                                        val appName = pillAppLabels[packageName] ?: packageName
                                        // Cached icons show at once; others are rasterized off the main thread.
                                        val appIcon by produceState(peekNativeAppIcon(packageName), packageName) {
                                            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                getNativeAppIcon(context, packageName)
                                            }
                                        }

                                        val containerBg = MaterialTheme.colorScheme.pageTrack(0.6f)
                                            .compositeOver(MaterialTheme.colorScheme.background)

                                        com.pixel.intelligentsearch.core.ui.expressive.MatteShell(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                // TalkBack can't swipe or drag the row, so removing and moving it are actions on it.
                                                .semantics(mergeDescendants = true) {
                                                    customActions = buildList {
                                                        add(CustomAccessibilityAction("Remove") { bounce.dismiss(-1f); true })
                                                        if (itemIndex > 0) add(CustomAccessibilityAction("Move up") { movePill(packageName, -1) })
                                                        if (itemIndex < localPillList.lastIndex) add(CustomAccessibilityAction("Move down") { movePill(packageName, 1) })
                                                    }
                                                },
                                            containerColor = containerBg,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                            shape = RoundedCornerShape(24.dp)
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
                                                    val icon = appIcon
                                                    if (icon != null) {
                                                        Image(
                                                            bitmap = icon,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(36.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(16.dp))
                                                    }
                                                    Column {
                                                        Text(
                                                            text = appName.breakableAtDots(),
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        // Without a label (the app's gone) the title is already the
                                                        // package name, so it isn't repeated under it.
                                                        if (appName != packageName) {
                                                            Text(
                                                                text = packageName.breakableAtDots(),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                            )
                                                        }
                                                    }
                                                }

                                                val currentPkg by rememberUpdatedState(packageName)
                                                Box(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .pointerInput(currentPkg, localPillList.size) {
                                                            awaitEachGesture {
                                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                                // Not while a pill is still flying off: its place in the list is about to go.
                                                                if (leavingPills.isNotEmpty()) return@awaitEachGesture

                                                                dropJob?.cancel()
                                                                dropJob = null
                                                                if (isDropping && draggingPackage != null && dragStartIndex != -1 && targetSlotIndex != -1 && dragStartIndex != targetSlotIndex) {
                                                                    val currentList = localPillList.toMutableList()
                                                                    if (dragStartIndex in currentList.indices) {
                                                                        val item = currentList.removeAt(dragStartIndex)
                                                                        val safeTarget = targetSlotIndex.coerceIn(0, currentList.size)
                                                                        currentList.add(safeTarget, item)
                                                                        persistPills(currentList)
                                                                    }
                                                                }
                                                                isDropping = false

                                                                val startIdx = localPillList.indexOf(currentPkg)
                                                                if (startIdx == -1) return@awaitEachGesture

                                                                down.consume()

                                                                val info = listState.layoutInfo.visibleItemsInfo.find {
                                                                    it.key == currentPkg || it.key.toString().endsWith(currentPkg)
                                                                }
                                                                val spacing = listState.layoutInfo.mainAxisItemSpacing.toFloat()
                                                                if (info != null && info.size > 0) {
                                                                    measuredItemHeight = info.size.toFloat() + spacing
                                                                }

                                                                draggingPackage = currentPkg
                                                                dragStartIndex = startIdx
                                                                targetSlotIndex = startIdx
                                                                itemDragOffset = 0f
                                                                autoScrollVelocity = 0f
                                                                hapticEngine.performPredictiveBackHaptic(view)

                                                                val pointerId = down.id
                                                                while (true) {
                                                                    val event = awaitPointerEvent()
                                                                    val dragChange = event.changes.firstOrNull { it.id == pointerId } ?: break
                                                                    if (!dragChange.pressed) {
                                                                        dragChange.consume()
                                                                        break
                                                                    }

                                                                    val deltaY = dragChange.positionChange().y
                                                                    dragChange.consume()
                                                                    itemDragOffset += deltaY

                                                                    val currentHeight = if (measuredItemHeight > 10f) measuredItemHeight else defaultItemHeight
                                                                    val totalSlots = localPillList.size
                                                                    val rawDelta = kotlin.math.round(itemDragOffset / currentHeight).toInt()
                                                                    val newTarget = (startIdx + rawDelta).coerceIn(0, totalSlots - 1)
                                                                    if (newTarget != targetSlotIndex) {
                                                                        hapticEngine.performPredictiveBackHaptic(view)
                                                                        targetSlotIndex = newTarget
                                                                    }

                                                                    val draggingItem = listState.layoutInfo.visibleItemsInfo.find {
                                                                        it.key == draggingPackage || it.key.toString().endsWith(draggingPackage ?: "")
                                                                    }
                                                                    if (draggingItem != null) {
                                                                        val currentItemTop = draggingItem.offset + itemDragOffset
                                                                        val currentItemBottom = currentItemTop + draggingItem.size
                                                                        val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
                                                                        val hotZonePx = with(density) { 72.dp.toPx() }

                                                                        if (currentItemTop < hotZonePx && listState.canScrollBackward) {
                                                                            val proximity = (1f - (currentItemTop / hotZonePx).coerceIn(0f, 1f))
                                                                            autoScrollVelocity = -22f * proximity
                                                                        } else if (currentItemBottom > viewportHeight - hotZonePx && listState.canScrollForward) {
                                                                            val distanceFromEdge = viewportHeight - currentItemBottom
                                                                            val proximity = (1f - (distanceFromEdge / hotZonePx).coerceIn(0f, 1f))
                                                                            autoScrollVelocity = 22f * proximity
                                                                        } else {
                                                                            autoScrollVelocity = 0f
                                                                        }
                                                                    }
                                                                }

                                                                // GESTURE FINISHED (Finger lifted)
                                                                autoScrollVelocity = 0f
                                                                val activePkg = draggingPackage
                                                                val sIdx = dragStartIndex
                                                                val tIdx = targetSlotIndex
                                                                val currentHeight = if (measuredItemHeight > 10f) measuredItemHeight else defaultItemHeight

                                                                if (activePkg != null && sIdx != -1 && tIdx != -1 && sIdx != tIdx && currentHeight > 10f) {
                                                                    dropJob = coroutineScope.launch {
                                                                        isDropping = true
                                                                        val settleTargetOffset = (tIdx - sIdx) * currentHeight
                                                                        androidx.compose.animation.core.Animatable(itemDragOffset).animateTo(
                                                                            targetValue = settleTargetOffset,
                                                                            animationSpec = androidx.compose.animation.core.spring(
                                                                                stiffness = 850f,
                                                                                dampingRatio = 0.70f
                                                                            )
                                                                        ) {
                                                                            itemDragOffset = value
                                                                        }
                                                                        hapticEngine.performPredictiveBackHaptic(view)

                                                                        wasReordered = true
                                                                        val currentList = localPillList.toMutableList()
                                                                        if (sIdx in currentList.indices) {
                                                                            val item = currentList.removeAt(sIdx)
                                                                            val safeTarget = tIdx.coerceIn(0, currentList.size)
                                                                            currentList.add(safeTarget, item)
                                                                            persistPills(currentList)
                                                                        }

                                                                        draggingPackage = null
                                                                        dragStartIndex = -1
                                                                        targetSlotIndex = -1
                                                                        itemDragOffset = 0f
                                                                        isDropping = false
                                                                    }
                                                                } else if (activePkg != null) {
                                                                    dropJob = coroutineScope.launch {
                                                                        isDropping = true
                                                                        if (kotlin.math.abs(itemDragOffset) > 1f) {
                                                                            androidx.compose.animation.core.Animatable(itemDragOffset).animateTo(
                                                                                targetValue = 0f,
                                                                                animationSpec = androidx.compose.animation.core.spring(
                                                                                    stiffness = 850f,
                                                                                    dampingRatio = 0.70f
                                                                                )
                                                                            ) {
                                                                                itemDragOffset = value
                                                                            }
                                                                        }
                                                                        draggingPackage = null
                                                                        dragStartIndex = -1
                                                                        targetSlotIndex = -1
                                                                        itemDragOffset = 0f
                                                                        isDropping = false
                                                                    }
                                                                } else {
                                                                    draggingPackage = null
                                                                    dragStartIndex = -1
                                                                    targetSlotIndex = -1
                                                                    itemDragOffset = 0f
                                                                    isDropping = false
                                                                }
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.DragHandle,
                                                        contentDescription = "Drag to reorder",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/** A package name that may wrap after any dot (zero-width spaces), so narrow screens never split it mid-word. */
private fun String.breakableAtDots(): String = replace(".", ".\u200B")
