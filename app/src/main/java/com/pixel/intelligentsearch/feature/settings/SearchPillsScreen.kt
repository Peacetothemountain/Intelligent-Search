package com.pixel.intelligentsearch.feature.settings

import androidx.compose.ui.platform.testTag
import com.pixel.intelligentsearch.core.data.settings.SearchPillProfiles
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.pixel.intelligentsearch.core.navigation.Route
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine

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
    val label: String,
    val iconBitmap: androidx.compose.ui.graphics.ImageBitmap?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPillsScreen(
    prefs: SharedPreferences,
    onBack: () -> Unit,
    onNavigate: (com.pixel.intelligentsearch.core.navigation.Route) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel = LocalSettingsViewModel.current
    val profiles = remember(prefs) { SearchPillProfiles(prefs) }
    var activeProfile by remember { mutableIntStateOf(profiles.activeProfile) }
    // One state object, so drag handlers never write a stale copy.
    var localPillList by remember { mutableStateOf(profiles.pills()) }
    var resetCounter by remember { mutableIntStateOf(0) }

    fun persistPills(newPills: List<String>) {
        localPillList = newPills
        profiles.setPills(newPills)
    }

    fun switchProfile(id: Int) {
        localPillList = profiles.switchTo(id)
        activeProfile = id
        resetCounter++
    }

    var showAppPicker by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<AppPickerItem>>(emptyList()) }
    var isAppsLoading by remember { mutableStateOf(false) }
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedApps by remember { mutableStateOf(setOf<String>()) }
    var showMaxWarning by remember { mutableStateOf(false) }

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
                    val apps = resolveInfoList.asSequence()
                        .distinctBy { it.activityInfo.packageName }
                        .map {
                            val pkg = it.activityInfo.packageName
                            val label = it.loadLabel(pm).toString()
                            val icon = getNativeAppIcon(context, pkg)
                            AppPickerItem(packageName = pkg, label = label, iconBitmap = icon)
                        }
                        .sortedBy { it.label.lowercase() }
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

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
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
                                multiSelectMode = false
                                selectedApps = emptySet()
                                showAppPicker = false
                            }
                        }) {
                            Icon(Icons.Outlined.Check, contentDescription = "Add Selected")
                        }
                    } else if (!showAppPicker) {
                        IconButton(onClick = {
                            resetCounter++
                            localPillList = profiles.resetToSaved()
                            Toast.makeText(context, "Profile $activeProfile Reset to Saved State", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Outlined.RestartAlt, contentDescription = "Reset to Saved State")
                        }
                        IconButton(onClick = {
                            profiles.saveState(localPillList)
                            Toast.makeText(context, "Profile $activeProfile State Saved", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Outlined.Save, contentDescription = "Save Profile State")
                        }
                        IconButton(onClick = {
                            if (localPillList.size >= SearchPillProfiles.MAX_PILLS) {
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
                text = { Text("You cannot add more than 20 apps to the Quick Launch at a time.", style = MaterialTheme.typography.bodyLarge) },
                confirmButton = {
                    TextButton(onClick = { showMaxWarning = false }) {
                        Text("OK", style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        }

        if (showAppPicker) {
            var appSearchQuery by remember { mutableStateOf("") }
            val filteredApps = remember(installedApps, appSearchQuery) {
                if (appSearchQuery.isBlank()) installedApps
                else installedApps.filter {
                    it.label.contains(appSearchQuery, ignoreCase = true) ||
                    it.packageName.contains(appSearchQuery, ignoreCase = true)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search apps...") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search") },
                        shape = RoundedCornerShape(24.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }

                if (isAppsLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.testTag(com.pixel.intelligentsearch.core.ui.LOADING_INDICATOR_TAG))
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredApps, key = { it.packageName }) { appItem ->
                            val packageName = appItem.packageName
                            val isSelected = selectedApps.contains(packageName)
                            val isAlreadyAdded = localPillList.contains(packageName)

                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) else Modifier)
                            ) {
                                SettingsRow(
                                    title = appItem.label,
                                    subtitle = if (isAlreadyAdded) "Already in Quick Launch" else packageName.breakableAtDots(),
                                    icon = appItem.iconBitmap ?: Icons.Outlined.Apps,
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
                                    showDivider = true
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Segmented Profile Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (id in 1..SearchPillProfiles.PROFILE_COUNT) {
                        val isSelected = activeProfile == id
                        FilterChip(
                            selected = isSelected,
                            onClick = { switchProfile(id) },
                            label = { Text("Profile $id", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                if (localPillList.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        color = Color.Transparent
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
                                "Profile $activeProfile is Empty",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Tap '+' to add applications to Profile $activeProfile. All changes auto-save in real-time.",
                                style = MaterialTheme.typography.bodyMedium,
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
                                key(packageName, resetCounter) {
                                    val dismissState = rememberSwipeToDismissBoxState(
                                        positionalThreshold = { it * 0.5f }
                                    )
                                    LaunchedEffect(dismissState.currentValue) {
                                        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart || dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd) {
                                            hapticEngine.performHaptic(view, com.pixel.intelligentsearch.core.haptics.PixelHapticType.TICK)
                                            val updated = localPillList.toMutableList()
                                            updated.remove(packageName)
                                            persistPills(updated)
                                        }
                                    }

                                    SwipeToDismissBox(
                                        state = dismissState,
                                        enableDismissFromStartToEnd = !isReordering,
                                        enableDismissFromEndToStart = !isReordering,
                                        backgroundContent = {
                                            Box(modifier = Modifier.fillMaxSize())
                                        }
                                    ) {
                                        val appName = pillAppLabels[packageName] ?: packageName
                                        // Cached icons show at once; others are rasterized off the main thread.
                                        val appIcon by produceState(peekNativeAppIcon(packageName), packageName) {
                                            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                getNativeAppIcon(context, packageName)
                                            }
                                        }

                                        val containerBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                            .compositeOver(MaterialTheme.colorScheme.background)

                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(24.dp))
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
                                            color = containerBg,
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
                                                        Text(
                                                            text = packageName.breakableAtDots(),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                        )
                                                    }
                                                }

                                                val currentPkg by rememberUpdatedState(packageName)
                                                Box(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .pointerInput(currentPkg, localPillList.size) {
                                                            awaitEachGesture {
                                                                val down = awaitFirstDown(requireUnconsumed = false)

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
                                                                hapticEngine.performHaptic(view, com.pixel.intelligentsearch.core.haptics.PixelHapticType.TICK)

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
                                                                        hapticEngine.performHaptic(view, com.pixel.intelligentsearch.core.haptics.PixelHapticType.REORDER_SWAP)
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
                                                                        hapticEngine.performHaptic(view, com.pixel.intelligentsearch.core.haptics.PixelHapticType.TICK)

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
