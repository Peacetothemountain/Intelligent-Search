package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.widget.WidgetDefaults
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import com.pixel.intelligentsearch.core.data.widget.WidgetSettingsStore
import com.pixel.intelligentsearch.feature.widget.getShortcutIconRes
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.data.settings.get
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.ui.res.vectorResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import com.pixel.intelligentsearch.core.theme.DynamicThemeDetector
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
import com.pixel.intelligentsearch.feature.widget.PinWidgetReceiver

// WIDGET SETTINGS SCREEN
// -----------------------------------------------------------------------------------------

private data class SlotPreviewItem(val slotKey: String, val label: String, val resId: Int, val isMic: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val widgetSettingsViewModel = LocalSettingsViewModel.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    // State the editor's sections share; sections bind it under these names so their code reads the same.
    val ui = remember { WidgetEditorState(initialSlotItemHeightPx = with(density) { 84.dp.toPx() }) }
    val pendingCustomShortcutTypes = ui.pendingCustomShortcutTypes
    val pendingCustomShortcutValues = ui.pendingCustomShortcutValues
    
    val shortcutOptions = listOf(
        "None" to Icons.Default.Close,
        "Google Lens" to ImageVector.vectorResource(id = com.pixel.intelligentsearch.R.drawable.ic_camera),
        "Live" to Icons.Default.AutoAwesome,
        "Translate (text)" to Icons.Default.Translate,
        "Translate (camera)" to Icons.Default.DocumentScanner,
        "Song Search" to Icons.Default.MusicNote,
        "Weather" to Icons.Default.WbSunny,
        "Sports" to Icons.Default.SportsBasketball,
        "Dictionary" to Icons.AutoMirrored.Filled.MenuBook,
        "Homework" to Icons.Default.School,
        "Finance" to Icons.AutoMirrored.Filled.TrendingUp,
        "Saved" to Icons.Default.Bookmark,
        "News" to Icons.AutoMirrored.Filled.Article
    )

    val themeStyleState = remember { mutableStateOf(prefs[SettingsManager.WIDGET_THEME_STYLE]) }
    var localThemeStyle by themeStyleState
    val isSystem = localThemeStyle == "System Default"

    // Each design is edited as one value. WidgetSettingsStore owns the stored keys and the fallbacks to the keys
    // older versions shared between designs; the widget renderer reads the same store.
    val isDynamicActive = remember(context) { DynamicThemeDetector.isDynamicColorActive(context) }
    val defaultUserMaterialColor = MaterialTheme.colorScheme.primary.toArgb()
    val widgetStore = remember(prefs) { WidgetSettingsStore(prefs) }
    val systemDraft = remember {
        mutableStateOf(widgetStore.load(WidgetDesign.SYSTEM, WidgetDefaults(defaultUserMaterialColor, isDynamicActive)))
    }
    val materialDraft = remember {
        mutableStateOf(widgetStore.load(WidgetDesign.MATERIAL, WidgetDefaults(defaultUserMaterialColor, isDynamicActive)))
    }

    // The design being edited; every "local" value below reads and writes its draft.
    val activeDraft = if (isSystem) systemDraft else materialDraft
    val fields = remember(isSystem) { WidgetDraftFields(activeDraft, materialDraft) }
    var localSubtheme by fields::subtheme
    var localMaterialGIconTheme by fields::materialGIconTheme
    var localCustomGIconColor by fields::customGIconColor
    var localCustomMicColor by fields::customMicColor
    var localCustomShortcut1Color by fields::customShortcut1Color
    var localCustomShortcut2Color by fields::customShortcut2Color
    var localCustomShortcut3Color by fields::customShortcut3Color
    var localCustomActionColor by fields::customActionColor
    var localCustomSyncColors by fields::customSyncColors
    val colors = remember(isSystem) { WidgetColorSelection() }
    var localHue by fields::hue
    var localSaturation by fields::saturation
    var localLightness by fields::lightness
    var localColorOpacity by fields::colorOpacity
    var localTransparency by fields::transparency
    var localShowGIcon by fields::showGIcon
    var localShowVoice by fields::showVoice
    var localShortcut1 by fields::shortcut1
    var localShortcut2 by fields::shortcut2
    var localShortcut3 by fields::shortcut3
    var localSlotOrderStr by fields::slotOrderStr
    var localKeepSystemG by fields::keepSystemG
    // Material-only settings.
    var localLockBlack by fields::lockBlack
    var matLockBlack by fields::lockBlack
    var localActionIcon by fields::actionIcon

    val computedCustomColorInt = remember(localHue, localSaturation, localLightness, localColorOpacity) {
        android.graphics.Color.HSVToColor(
            (localColorOpacity / 100f * 255).toInt().coerceIn(0, 255),
            floatArrayOf(localHue, (localSaturation / 100f).coerceIn(0f, 1f), (localLightness / 100f).coerceIn(0f, 1f))
        )
    }
    var draggingSlotKey by ui::draggingSlotKey
    val view = androidx.compose.ui.platform.LocalView.current
    val hapticEngine = remember(context) { com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context) }
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by ui::selectedTab

    // What was last loaded or saved, to tell whether there are unsaved changes.
    var savedThemeStyle by remember { mutableStateOf(localThemeStyle) }
    var savedSystem by remember { mutableStateOf(systemDraft.value) }
    var savedMaterial by remember { mutableStateOf(materialDraft.value) }
    val hasUnsavedChanges by remember {
        derivedStateOf {
            localThemeStyle != savedThemeStyle ||
                systemDraft.value != savedSystem ||
                materialDraft.value != savedMaterial ||
                pendingCustomShortcutTypes.isNotEmpty() ||
                pendingCustomShortcutValues.isNotEmpty()
        }
    }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }

    val saveSettings: () -> Unit = {
        hapticEngine.performPredictiveBackHaptic(view)
        val editor = prefs.edit()
        for ((key, type) in pendingCustomShortcutTypes) {
            editor.putString("${key}_custom_type", type)
        }
        for ((key, value) in pendingCustomShortcutValues) {
            editor.putString("${key}_custom_value", value)
        }
        pendingCustomShortcutTypes.clear()
        pendingCustomShortcutValues.clear()

        // Both designs are persisted so edits made on the other tab before switching aren't lost. A design only
        // counts as saved once it differs from what was loaded, which keeps the legacy-key fallbacks intact for a
        // design the user never touched. Element colours are stored per design only; the old shared
        // "widget_custom_*" keys stay as the fallback for a design that has never been saved.
        val system = systemDraft.value
        val material = materialDraft.value
        if (isSystem || system != savedSystem) widgetStore.save(WidgetDesign.SYSTEM, system, editor)
        if (!isSystem || material != savedMaterial) widgetStore.save(WidgetDesign.MATERIAL, material, editor)

        val activeStyle = if (isSystem) "System Default" else "Material You (Minimal)"
        editor
            .put(SettingsManager.WIDGET_THEME_STYLE, activeStyle)
            .apply()
        widgetSettingsViewModel?.updateSetting(SettingsManager.WIDGET_THEME_STYLE, activeStyle)
        updateWidgets(context)
        android.widget.Toast.makeText(
            context,
            if (isSystem) "System Design Saved" else "Material Design Saved",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        savedThemeStyle = localThemeStyle
        savedSystem = system
        savedMaterial = material
    }

    val handleBack = {
        val suppressed = prefs.getBoolean("widget_suppress_unsaved_warning", false)
        if (hasUnsavedChanges && !suppressed) {
            showUnsavedChangesDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(enabled = hasUnsavedChanges && !prefs.getBoolean("widget_suppress_unsaved_warning", false)) {
        showUnsavedChangesDialog = true
    }

    if (showUnsavedChangesDialog) {
        BasicAlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false }
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Discard unsaved changes?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "You have unsaved customizations. If you leave now, none of your changes will be applied.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                prefs.edit().putBoolean("widget_suppress_unsaved_warning", true).apply()
                                showUnsavedChangesDialog = false
                                onBack()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text(
                                text = "Don't show this again",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    showUnsavedChangesDialog = false
                                    onBack()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = "Discard",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Button(
                                onClick = {
                                    showUnsavedChangesDialog = false
                                    saveSettings()
                                    onBack()
                                },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = "Save",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(containerColor = Color.Transparent, topBar = {
            TopAppBar(
                title = { Text("Widget Customization", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = handleBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    val canPinWidget = remember(context) {
                        android.appwidget.AppWidgetManager.getInstance(context)?.isRequestPinAppWidgetSupported == true
                    }
                    if (canPinWidget) {
                        IconButton(
                            onClick = {
                                // Pin the design shown in the preview, so apply any pending edits first.
                                if (hasUnsavedChanges) saveSettings()
                                val requested = com.pixel.intelligentsearch.feature.widget.PinWidgetReceiver
                                    .requestPinWidget(context, isMaterial = !isSystem)
                                if (!requested) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Your launcher doesn't support adding widgets from apps",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.AddToHomeScreen, contentDescription = "Add widget to Home screen")
                        }
                    }
                    TextButton(onClick = saveSettings) {
                        Text("Save", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Live Preview Card (Pinned at the top for real-time visual feedback)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .height(150.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                    val containerAlpha = ((100 - localTransparency.toInt()) / 100f).coerceIn(0f, 1f)
                    val colorAlpha = (localColorOpacity / 100f).coerceIn(0f, 1f)
                    val effectiveColorAlpha = (colorAlpha * containerAlpha).coerceIn(0f, 1f)
                    
                    val accentColor = if (localSubtheme == "Custom") {
                        androidx.compose.ui.graphics.Color(computedCustomColorInt)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    val previewIsMaterialYou = localThemeStyle == "Material You (Minimal)" || localThemeStyle == "Material Design"
                    val matPrimary = MaterialTheme.colorScheme.primary
                    val matSurfaceVariant = MaterialTheme.colorScheme.surfaceVariant

                    val activeColor = remember(previewIsMaterialYou, localSubtheme, computedCustomColorInt, matPrimary, matSurfaceVariant, effectiveColorAlpha) {
                        if (localSubtheme == "Custom") {
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(computedCustomColorInt, hsv)
                            androidx.compose.ui.graphics.Color(
                                android.graphics.Color.HSVToColor(
                                    255, 
                                    floatArrayOf(hsv[0], hsv[1], 1f)
                                )
                            )
                        } else if (!previewIsMaterialYou) {
                            matSurfaceVariant
                        } else {
                            matPrimary
                        }
                    }

                    val isCustomTheme = localSubtheme == "Custom"
                    val waveColor = if (isCustomTheme) {
                        accentColor
                    } else {
                        MaterialTheme.colorScheme.primary
                    }

                    GeminiCornerSwipeWaveLayer(
                        colorPrimary = waveColor,
                        colorSecondary = waveColor,
                        colorTertiary = waveColor,
                        colorAccent = waveColor,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    )

                    val previewRimColorAlpha = if (previewIsMaterialYou) {
                        if (localSubtheme == "Custom") {
                            accentColor.copy(alpha = effectiveColorAlpha)
                        } else if (localSubtheme == "Material") {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = effectiveColorAlpha)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = effectiveColorAlpha)
                        }
                    } else androidx.compose.ui.graphics.Color.Transparent
                    
                    val effectiveGIconTheme = localMaterialGIconTheme

                    val customLuminance = (0.299 * accentColor.red + 0.587 * accentColor.green + 0.114 * accentColor.blue)
                    val isPreviewPillLight = if (previewIsMaterialYou) {
                        if (localSubtheme == "Custom") (!localLockBlack && (customLuminance > 0.5f)) else (!androidx.compose.foundation.isSystemInDarkTheme() && !localLockBlack)
                    } else {
                        when (localSubtheme) {
                            "Light" -> true
                            "Dark" -> false
                            "Custom" -> customLuminance > 0.5f
                            else -> !androidx.compose.foundation.isSystemInDarkTheme()
                        }
                    }
                    
                    val materialDarkCompose = androidx.compose.ui.res.colorResource(android.R.color.system_accent1_700)

                    val rimBrush = if (previewIsMaterialYou && localSubtheme == "Custom") {
                        androidx.compose.ui.graphics.SolidColor(accentColor.copy(alpha = effectiveColorAlpha))
                    } else {
                        androidx.compose.ui.graphics.Brush.linearGradient(listOf(previewRimColorAlpha, previewRimColorAlpha))
                    }
                    
                    val previewPillColorAlpha = if (previewIsMaterialYou) {
                        if (localLockBlack) {
                            androidx.compose.ui.graphics.Color(0xFF121212)
                        } else {
                            if (localSubtheme == "Custom") {
                                accentColor.copy(alpha = effectiveColorAlpha)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = containerAlpha)
                            }
                        }
                    } else {
                        when (localSubtheme) {
                            "Light" -> androidx.compose.ui.graphics.Color(0xFFF1F3F4).copy(alpha = containerAlpha)
                            "Dark" -> androidx.compose.ui.graphics.Color(0xFF303134).copy(alpha = containerAlpha)
                            "Custom" -> accentColor.copy(alpha = effectiveColorAlpha)
                            else -> if (DynamicThemeDetector.isDynamicColorActive(context)) {
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = containerAlpha)
                            } else {
                                if (androidx.compose.foundation.isSystemInDarkTheme()) androidx.compose.ui.graphics.Color(0xFF303134).copy(alpha = containerAlpha) else androidx.compose.ui.graphics.Color(0xFFF1F3F4).copy(alpha = containerAlpha)
                            }
                        }
                    }
                    
                    val previewIsDarkSurface = if (previewIsMaterialYou) localLockBlack || (!isPreviewPillLight) else !isPreviewPillLight
                    val previewWidgetColors = remember(previewIsDarkSurface) {
                        DynamicThemeDetector.resolveWidgetColors(context, previewIsDarkSurface)
                    }

                    val customM3Colors = remember(localHue, localSaturation, previewIsDarkSurface) {
                        com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper.getMaterialYouTonalColors(
                            hue = localHue,
                            saturation = localSaturation,
                            isDarkSurface = previewIsDarkSurface
                        )
                    }

                    val rawP = if (isCustomTheme) {
                        customM3Colors.primary
                    } else {
                        previewWidgetColors.primary
                    }
                    val rawS = if (isCustomTheme) {
                        customM3Colors.secondary
                    } else {
                        previewWidgetColors.secondary
                    }
                    val rawT = if (isCustomTheme) {
                        customM3Colors.tertiary
                    } else {
                        previewWidgetColors.tertiary
                    }

                    val finalPInt = if (previewIsMaterialYou && !localLockBlack && isPreviewPillLight) {
                        com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper.darkenForContrast(rawP, 32.0)
                    } else {
                        rawP
                    }
                    val finalSInt = if (previewIsMaterialYou && !localLockBlack && isPreviewPillLight) {
                        com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper.darkenForContrast(rawS, 34.0)
                    } else {
                        rawS
                    }
                    val finalTInt = if (previewIsMaterialYou && !localLockBlack && isPreviewPillLight) {
                        com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper.darkenForContrast(rawT, 32.0)
                    } else {
                        rawT
                    }

                    val gPrimary = androidx.compose.ui.graphics.Color(finalPInt)
                    val gSecondary = androidx.compose.ui.graphics.Color(finalSInt)
                    val gTertiary = androidx.compose.ui.graphics.Color(finalTInt)
                    val googleBlue = androidx.compose.ui.graphics.Color(0xFF4285F4)
                    val googleGreen = androidx.compose.ui.graphics.Color(0xFF34A853)
                    val googleYellow = androidx.compose.ui.graphics.Color(0xFFFBBC05)
                    val googleRed = androidx.compose.ui.graphics.Color(0xFFEA4335)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .then(
                                Modifier.background(rimBrush, RoundedCornerShape(if (previewIsMaterialYou) 32.dp else 28.dp))
                            )
                            .padding(if (previewIsMaterialYou) 8.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(if (previewIsMaterialYou) 48.dp else 56.dp)
                                .then(
                                    Modifier.background(previewPillColorAlpha, RoundedCornerShape(if (previewIsMaterialYou) 24.dp else 28.dp))
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            if (localShowGIcon) {
                                val isAccented = effectiveGIconTheme == "Accented G Icon"
                                val isCustom = effectiveGIconTheme == "Custom G Icon" && !localKeepSystemG
                                val useOriginalGIcon = effectiveGIconTheme == "System G Icon" || (effectiveGIconTheme == "Custom G Icon" && localKeepSystemG)
                                val customGColorInt = if (isCustomTheme && previewIsMaterialYou && !localLockBlack && isPreviewPillLight) {
                                    com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper.darkenForContrast(localCustomGIconColor, 32.0)
                                } else {
                                    localCustomGIconColor
                                }
                                val customGColorObj = androidx.compose.ui.graphics.Color(customGColorInt)
                                ComposeGIcon(
                                    modifier = Modifier.size(24.dp),
                                    primaryColor = if (isCustom) customGColorObj else gPrimary,
                                    secondaryColor = if (isCustom) customGColorObj else gSecondary,
                                    tertiaryColor = if (isCustom) customGColorObj else gTertiary,
                                    isAccented = isAccented || isCustom,
                                    accentColor = if (isCustom) customGColorObj else gPrimary,
                                    fallbackTint = if (isCustom) customGColorObj else androidx.compose.ui.graphics.Color.Unspecified,
                                    useOriginalColors = useOriginalGIcon,
                                    isPillLight = isPreviewPillLight
                                )
                            }
                            
                            Spacer(Modifier.weight(1f))
                            
                            val useMaterialYouIcons = previewIsMaterialYou || effectiveGIconTheme == "Material G Icon" || effectiveGIconTheme == "Custom G Icon" || isPreviewPillLight
                            val slotOrder = localSlotOrderStr.split(",").filter { it.isNotBlank() }
                            val previewItemsList = mutableListOf<SlotPreviewItem>()
                            slotOrder.forEach { key ->
                                when (key) {
                                    "mic" -> {
                                        if (localShowVoice) {
                                            val micRes = if (useMaterialYouIcons) com.pixel.intelligentsearch.R.drawable.ic_mic else com.pixel.intelligentsearch.R.drawable.ic_mic_original
                                            previewItemsList.add(SlotPreviewItem("mic", "Voice Search", micRes, true))
                                        }
                                    }
                                    "shortcut1" -> {
                                        if (localShortcut1 != "None") {
                                            previewItemsList.add(SlotPreviewItem("shortcut1", localShortcut1, getShortcutIconRes(localShortcut1, useMaterialYouIcons), false))
                                        }
                                    }
                                    "shortcut2" -> {
                                        if (localShortcut2 != "None") {
                                            previewItemsList.add(SlotPreviewItem("shortcut2", localShortcut2, getShortcutIconRes(localShortcut2, useMaterialYouIcons), false))
                                        }
                                    }
                                    "shortcut3" -> {
                                        if (localShortcut3 != "None") {
                                            previewItemsList.add(SlotPreviewItem("shortcut3", localShortcut3, getShortcutIconRes(localShortcut3, useMaterialYouIcons), false))
                                        }
                                    }
                                }
                            }

                            val hasCustomShortcuts = effectiveGIconTheme == "Custom G Icon"

                            previewItemsList.forEachIndexed { idx, item ->
                                if (idx > 0) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                                val isMaterial = effectiveGIconTheme == "Material G Icon"

                                val (scPrimary, scSecondary, scTertiary, scQuaternary) = if (hasCustomShortcuts) {
                                    val scColor = if (localCustomSyncColors) {
                                        localCustomGIconColor
                                    } else {
                                        when (item.slotKey) {
                                            "mic" -> localCustomMicColor
                                            "shortcut1" -> localCustomShortcut1Color
                                            "shortcut2" -> localCustomShortcut2Color
                                            "shortcut3" -> localCustomShortcut3Color
                                            else -> localCustomGIconColor
                                        }
                                    }
                                    val effectiveScColor = if (previewIsMaterialYou && !localLockBlack && isPreviewPillLight) {
                                        com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper.darkenForContrast(scColor, 32.0)
                                    } else {
                                        scColor
                                    }
                                    val customScColorObj = androidx.compose.ui.graphics.Color(effectiveScColor)
                                    arrayOf(customScColorObj, customScColorObj, customScColorObj, customScColorObj)
                                } else when (effectiveGIconTheme) {
                                    "Accented G Icon" -> arrayOf(gPrimary, gPrimary, gPrimary, gPrimary)
                                    "Material G Icon" -> arrayOf(gPrimary, gSecondary, gTertiary, gPrimary)
                                    else -> { // System G Icon
                                        val sysTint = if (isPreviewPillLight && (!previewIsMaterialYou || !localLockBlack)) {
                                            androidx.compose.ui.graphics.Color(previewWidgetColors.onSurface)
                                        } else {
                                            androidx.compose.ui.graphics.Color.White
                                        }
                                        arrayOf(sysTint, sysTint, sysTint, sysTint)
                                    }
                                }
                                ComposeThemedShortcutIcon(
                                    resId = item.resId,
                                    contentDescription = item.label,
                                    primaryColor = scPrimary,
                                    secondaryColor = scSecondary,
                                    tertiaryColor = scTertiary,
                                    quaternaryColor = scQuaternary,
                                    isMaterialTheme = isMaterial,
                                    modifier = Modifier.size(24.dp),
                                    isPillLight = isPreviewPillLight
                                )
                            }
                        }
                        
                        if (localActionIcon != "None" && previewIsMaterialYou) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(previewPillColorAlpha, androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                val hasCustomAction = effectiveGIconTheme == "Custom G Icon"

                                val (actPrimary, actSecondary, actTertiary, actQuaternary) = if (hasCustomAction) {
                                    val actColor = if (localCustomSyncColors) localCustomGIconColor else localCustomActionColor
                                    val effectiveActColor = if (previewIsMaterialYou && !localLockBlack && isPreviewPillLight) {
                                        com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper.darkenForContrast(actColor, 32.0)
                                    } else {
                                        actColor
                                    }
                                    val customActColorObj = androidx.compose.ui.graphics.Color(effectiveActColor)
                                    arrayOf(customActColorObj, customActColorObj, customActColorObj, customActColorObj)
                                } else when (effectiveGIconTheme) {
                                    "Accented G Icon" -> arrayOf(gPrimary, gPrimary, gPrimary, gPrimary)
                                    "Material G Icon" -> arrayOf(gPrimary, gSecondary, gTertiary, gPrimary)
                                    else -> { // System G Icon
                                        val sysActionTint = if (isPreviewPillLight && (!previewIsMaterialYou || !localLockBlack)) {
                                            androidx.compose.ui.graphics.Color(previewWidgetColors.onSurface)
                                        } else {
                                            androidx.compose.ui.graphics.Color.White
                                        }
                                        arrayOf(sysActionTint, sysActionTint, sysActionTint, sysActionTint)
                                    }
                                }
                                ComposeActionIcon(
                                    iconType = localActionIcon,
                                    modifier = Modifier.size(24.dp),
                                    primaryColor = actPrimary,
                                    secondaryColor = actSecondary,
                                    tertiaryColor = actTertiary,
                                    quaternaryColor = actQuaternary,
                                    isMaterialTheme = (effectiveGIconTheme == "Material G Icon"),
                                    isPillLight = isPreviewPillLight
                                )
                            }
                        }
                    }
            }

            // System vs Material Design Switcher
            SettingsCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isSystem = localThemeStyle == "System Default"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (isSystem) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(24.dp))
                            .bouncyClickable(shape = RoundedCornerShape(24.dp)) {
                                hapticEngine.performPredictiveBackHaptic(view)
                                localThemeStyle = "System Default"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "System Design",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSystem) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSystem) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (!isSystem) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(24.dp))
                            .bouncyClickable(shape = RoundedCornerShape(24.dp)) {
                                hapticEngine.performPredictiveBackHaptic(view)
                                localThemeStyle = "Material You (Minimal)"
                                localLockBlack = true
                                matLockBlack = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Material Design",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (!isSystem) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isSystem) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Material 3 Expressive Segmented Tab Bar
            val isCustomActive = localSubtheme == "Custom"
            val tabs = remember(isCustomActive) {
                if (isCustomActive) {
                    listOf(
                        Pair("Appearance", Icons.Outlined.Palette),
                        Pair("Color Studio", Icons.Outlined.Tune),
                        Pair("Widget Shortcuts", Icons.Outlined.Widgets)
                    )
                } else {
                    listOf(
                        Pair("Appearance", Icons.Outlined.Palette),
                        Pair("Widget Shortcuts", Icons.Outlined.Widgets)
                    )
                }
            }

            LaunchedEffect(isCustomActive) {
                if (!isCustomActive) {
                    if (selectedTab == 1) {
                        selectedTab = 0
                    } else if (selectedTab > 1) {
                        selectedTab = 1
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSel = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                            .bouncyClickable(shape = RoundedCornerShape(24.dp)) {
                                hapticEngine.performPredictiveBackHaptic(view)
                                selectedTab = index
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = tab.second,
                                contentDescription = tab.first,
                                tint = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tab.first,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Scrollable Content for Active Tab
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState(), enabled = draggingSlotKey == null)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                val activeTabName = tabs.getOrNull(selectedTab)?.first ?: "Appearance"
                when (activeTabName) {
                    "Appearance" -> WidgetAppearanceTab(fields = fields, ui = ui, colors = colors, themeStyle = themeStyleState, computedCustomColorInt = computedCustomColorInt, view = view, hapticEngine = hapticEngine)

                    "Color Studio" -> WidgetColorStudioTab(fields = fields, ui = ui, computedCustomColorInt = computedCustomColorInt)

                    "Widget Shortcuts" -> WidgetShortcutsTab(fields = fields, ui = ui, view = view, hapticEngine = hapticEngine, coroutineScope = coroutineScope, density = density, shortcutOptions = shortcutOptions, themeStyle = themeStyleState)
                }
            }
        }

            WidgetEditorDialogs(fields = fields, ui = ui, colors = colors, shortcutOptions = shortcutOptions, prefs = prefs)
        }
    }
}

