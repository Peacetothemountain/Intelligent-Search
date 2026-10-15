package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.data.SettingsManager
import android.content.SharedPreferences
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AppShortcut
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomIconsScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val viewModel = LocalSettingsViewModel.current
    val hapticEngine = remember(context) { com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context) }

    var activeIconPack by rememberSetting(SettingsManager.ACTIVE_ICON_PACK)
    var neverShowWarning by rememberSetting(SettingsManager.NEVER_SHOW_ICON_PACK_WARNING) {}
    var storedPillString by rememberSetting(SettingsManager.CUSTOM_ICON_PILLS)
    val effectivePillString = remember(storedPillString) {
        if (storedPillString.isNotBlank()) storedPillString else prefs.getString("custom_icon_packs_order", "") ?: ""
    }

    var installedPacks by remember { mutableStateOf<List<com.pixel.intelligentsearch.core.util.IconPackInfo>>(emptyList()) }
    var allPackMap by remember { mutableStateOf<Map<String, Pair<String, ImageBitmap?>>>(mapOf("system_default" to Pair("System Default", null))) }
    var isPacksLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val (packs, map) = withContext(Dispatchers.IO) {
            val p = com.pixel.intelligentsearch.core.util.IconPackManager.getInstalledIconPacks(context)
            val m = mutableMapOf<String, Pair<String, ImageBitmap?>>()
            m["system_default"] = Pair("System Default", null)
            p.forEach { pack ->
                val bmp = try { pack.icon?.toBitmap()?.asImageBitmap() } catch (_: Exception) { null }
                m[pack.packageName] = Pair(pack.label, bmp)
            }
            p to m
        }
        installedPacks = packs
        allPackMap = map
        isPacksLoading = false
    }

    val validPackSet = remember(installedPacks) {
        (listOf("system_default") + installedPacks.map { it.packageName }).toSet()
    }

    var localPillList by remember(effectivePillString, validPackSet) {
        val storedList = effectivePillString.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() && validPackSet.contains(it) }
        val merged = (storedList + listOf("system_default") + installedPacks.map { it.packageName })
            .distinct()
            .filter { validPackSet.contains(it) }
        mutableStateOf(merged)
    }

    var draggingPackage by remember { mutableStateOf<String?>(null) }
    var itemDragOffset by remember { mutableFloatStateOf(0f) }
    var autoScrollVelocity by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(draggingPackage, autoScrollVelocity) {
        if (draggingPackage != null && autoScrollVelocity != 0f) {
            while (isActive && draggingPackage != null && autoScrollVelocity != 0f) {
                val canScroll = if (autoScrollVelocity < 0f) listState.canScrollBackward else listState.canScrollForward
                if (!canScroll) break
                listState.scrollBy(autoScrollVelocity)
                kotlinx.coroutines.delay(16)
            }
        }
    }

    var pendingIconPack by remember { mutableStateOf<String?>(null) }

    fun activatePack(pkg: String) {
        if (pkg == activeIconPack) return
        if (activeIconPack != "system_default" && !neverShowWarning) {
            pendingIconPack = pkg
        } else {
            activeIconPack = pkg
            viewModel?.updateActiveIconPack(pkg)
            com.pixel.intelligentsearch.core.util.IconPackManager.clearCache()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Custom Icons", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (pendingIconPack != null) {
                AlertDialog(
                    onDismissRequest = { pendingIconPack = null },
                    title = { Text("Change Icon Pack", fontWeight = FontWeight.Bold) },
                    text = { Text("Are you sure you want to change the icon pack?") },
                    confirmButton = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    neverShowWarning = true
                                    if (pendingIconPack != null) {
                                        val chosen = pendingIconPack!!
                                        activeIconPack = chosen
                                        viewModel?.updateActiveIconPack(chosen)
                                        com.pixel.intelligentsearch.core.util.IconPackManager.clearCache()
                                        pendingIconPack = null
                                    }
                                }
                            ) {
                                Text("Never show again", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            
                            Row {
                                TextButton(onClick = { pendingIconPack = null }) {
                                    Text("Cancel")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (pendingIconPack != null) {
                                            val chosen = pendingIconPack!!
                                            activeIconPack = chosen
                                            viewModel?.updateActiveIconPack(chosen)
                                            com.pixel.intelligentsearch.core.util.IconPackManager.clearCache()
                                            pendingIconPack = null
                                        }
                                    }
                                ) {
                                    Text("Save")
                                }
                            }
                        }
                    },
                    dismissButton = null
                )
            }

            if (isPacksLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    userScrollEnabled = draggingPackage == null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                ) {
                    items(localPillList, key = { it }) { packageName ->
                        val isDragging = draggingPackage == packageName

                        val elevation by animateDpAsState(
                            targetValue = if (isDragging) 8.dp else 0.dp,
                            label = "elevation"
                        )
                        val scale by animateFloatAsState(
                            targetValue = if (isDragging) 1.03f else 1f,
                            label = "scale"
                        )

                        val draggingModifier = if (isDragging) {
                            Modifier.zIndex(10f).graphicsLayer {
                                translationY = itemDragOffset
                                scaleX = scale
                                scaleY = scale
                            }
                        } else {
                            Modifier.animateItem().zIndex(0f).graphicsLayer {
                                translationY = 0f
                                scaleX = scale
                                scaleY = scale
                            }
                        }

                        val packInfo = allPackMap[packageName] ?: Pair(packageName, null)
                        val packLabel = packInfo.first
                        val packIcon = packInfo.second
                        val isSelected = activeIconPack == packageName

                        Box(modifier = draggingModifier) {
                            val containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            }
                            val borderColor = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = containerColor),
                                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                                elevation = CardDefaults.cardElevation(defaultElevation = elevation)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        if (packageName == "system_default") {
                                            Icon(
                                                imageVector = Icons.Outlined.Palette,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        } else if (packIcon != null) {
                                            Image(
                                                bitmap = packIcon,
                                                contentDescription = null,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Outlined.AppShortcut,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(
                                                text = packLabel,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (packageName == "system_default") "Default Monet dynamic icons" else packageName,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        com.pixel.intelligentsearch.core.ui.expressive.GoogleMessagesSwitch(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                if (checked) {
                                                    activatePack(packageName)
                                                } else {
                                                    if (activeIconPack == packageName) {
                                                        activatePack("system_default")
                                                    }
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        val currentPkg by rememberUpdatedState(packageName)
                                        Icon(
                                            imageVector = Icons.Outlined.DragHandle,
                                            contentDescription = "Drag to reorder",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.pointerInput(currentPkg) {
                                                detectVerticalDragGestures(
                                                    onDragStart = {
                                                        hapticEngine.performHaptic(view, com.pixel.intelligentsearch.core.haptics.PixelHapticType.TICK)
                                                        draggingPackage = currentPkg
                                                        itemDragOffset = 0f
                                                        autoScrollVelocity = 0f
                                                    },
                                                    onDragEnd = {
                                                        autoScrollVelocity = 0f
                                                        draggingPackage = null
                                                        itemDragOffset = 0f
                                                        storedPillString = localPillList.joinToString(",")
                                                        viewModel?.updateCustomIconPills(storedPillString)
                                                        prefs.edit()
                                                            .put(SettingsManager.CUSTOM_ICON_PILLS, storedPillString)
                                                            .putString("custom_icon_packs_order", storedPillString)
                                                            .apply()
                                                    },
                                                    onDragCancel = {
                                                        autoScrollVelocity = 0f
                                                        draggingPackage = null
                                                        itemDragOffset = 0f
                                                    },
                                                    onVerticalDrag = { change, dragAmount ->
                                                        change.consume()
                                                        itemDragOffset += dragAmount

                                                        val currentDraggingPackage = draggingPackage ?: return@detectVerticalDragGestures
                                                        val draggingItem = listState.layoutInfo.visibleItemsInfo.find {
                                                             it.key == currentDraggingPackage
                                                        }
                                                        if (draggingItem != null) {
                                                            val spacing = listState.layoutInfo.mainAxisItemSpacing.toFloat()
                                                            val itemHeight = draggingItem.size.toFloat() + spacing
                                                            val from = localPillList.indexOf(currentDraggingPackage)
                                                            val distanceFromBottom = listState.layoutInfo.viewportSize.height - (draggingItem.offset + draggingItem.size)
                                                            val hotZonePx = 160f

                                                            if (itemDragOffset < 0f && draggingItem.offset < hotZonePx && listState.canScrollBackward) {
                                                                val proximity = (1f - (draggingItem.offset / hotZonePx)).coerceIn(0.2f, 1f)
                                                                autoScrollVelocity = -22f * proximity
                                                            } else if (itemDragOffset > 0f && distanceFromBottom < hotZonePx && listState.canScrollForward) {
                                                                val proximity = (1f - (distanceFromBottom / hotZonePx)).coerceIn(0.2f, 1f)
                                                                autoScrollVelocity = 22f * proximity
                                                            } else {
                                                                autoScrollVelocity = 0f
                                                            }

                                                            if (from != -1) {
                                                                if (itemDragOffset > itemHeight / 2f && from < localPillList.size - 1) {
                                                                    hapticEngine.performHaptic(view, com.pixel.intelligentsearch.core.haptics.PixelHapticType.REORDER_SWAP)
                                                                    val currentList = localPillList.toMutableList()
                                                                    java.util.Collections.swap(currentList, from, from + 1)
                                                                    localPillList = currentList
                                                                    itemDragOffset -= itemHeight
                                                                } else if (itemDragOffset < -itemHeight / 2f && from > 0) {
                                                                    hapticEngine.performHaptic(view, com.pixel.intelligentsearch.core.haptics.PixelHapticType.REORDER_SWAP)
                                                                    val currentList = localPillList.toMutableList()
                                                                    java.util.Collections.swap(currentList, from, from - 1)
                                                                    localPillList = currentList
                                                                    itemDragOffset += itemHeight
                                                                }
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
