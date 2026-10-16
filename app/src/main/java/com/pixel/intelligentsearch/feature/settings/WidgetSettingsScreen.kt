package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.widget.WidgetDefaults
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import com.pixel.intelligentsearch.core.data.widget.WidgetDesignSettings
import com.pixel.intelligentsearch.core.data.widget.WidgetSettingsStore
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import com.pixel.intelligentsearch.core.theme.DynamicThemeDetector
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
import com.pixel.intelligentsearch.feature.widget.PinWidgetReceiver
import kotlinx.coroutines.launch

// WIDGET SETTINGS SCREEN
// -----------------------------------------------------------------------------------------

private data class SlotPreviewItem(val slotKey: String, val label: String, val resId: Int, val isMic: Boolean)
/** A read-write view of one field of a design draft held in [state], so the UI keeps binding to plain vars. */
private fun <T> draftField(
    state: MutableState<WidgetDesignSettings>,
    get: (WidgetDesignSettings) -> T,
    set: (WidgetDesignSettings, T) -> WidgetDesignSettings
) = object : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = get(state.value)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        state.value = set(state.value, value)
    }
}

private data class CustomElementTarget(val key: String, val label: String, val color: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val widgetSettingsViewModel = LocalSettingsViewModel.current
    val pendingCustomShortcutTypes = remember { mutableStateMapOf<String, String>() }
    val pendingCustomShortcutValues = remember { mutableStateMapOf<String, String>() }
    var showShortcutSheet by remember { mutableStateOf(false) }
    var showCustomUrlDialogFor by remember { mutableStateOf<String?>(null) }
    var showCustomAppDialogFor by remember { mutableStateOf<String?>(null) }
    var customInputValue by remember { mutableStateOf("") }
    var expandedDropdownFor by remember { mutableStateOf<String?>(null) }
    var showHexInput by remember { mutableStateOf(false) }
    var tempHexInput by remember { mutableStateOf("") }
    
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

    var localThemeStyle by remember { mutableStateOf(prefs[SettingsManager.WIDGET_THEME_STYLE]) }
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
    var localSubtheme by draftField(activeDraft, { it.subtheme }) { d, v -> d.copy(subtheme = v) }
    var localMaterialGIconTheme by draftField(activeDraft, { it.gIconTheme }) { d, v -> d.copy(gIconTheme = v) }
    var localCustomGIconColor by draftField(activeDraft, { it.elementColors.gIcon }) { d, v -> d.copy(elementColors = d.elementColors.copy(gIcon = v)) }
    var localCustomMicColor by draftField(activeDraft, { it.elementColors.mic }) { d, v -> d.copy(elementColors = d.elementColors.copy(mic = v)) }
    var localCustomShortcut1Color by draftField(activeDraft, { it.elementColors.shortcut1 }) { d, v -> d.copy(elementColors = d.elementColors.copy(shortcut1 = v)) }
    var localCustomShortcut2Color by draftField(activeDraft, { it.elementColors.shortcut2 }) { d, v -> d.copy(elementColors = d.elementColors.copy(shortcut2 = v)) }
    var localCustomShortcut3Color by draftField(activeDraft, { it.elementColors.shortcut3 }) { d, v -> d.copy(elementColors = d.elementColors.copy(shortcut3 = v)) }
    var localCustomActionColor by draftField(activeDraft, { it.elementColors.action }) { d, v -> d.copy(elementColors = d.elementColors.copy(action = v)) }
    var localCustomSyncColors by draftField(activeDraft, { it.syncColors }) { d, v -> d.copy(syncColors = v) }
    var activeCustomColorTarget by remember(isSystem) { mutableStateOf("g_icon") }
    var showCustomHexDialog by remember { mutableStateOf(false) }
    var tempCustomHexInput by remember { mutableStateOf("") }
    var customIconHue by remember(isSystem) { mutableFloatStateOf(0f) }
    var customIconSat by remember(isSystem) { mutableFloatStateOf(100f) }
    var customIconVal by remember(isSystem) { mutableFloatStateOf(100f) }
    var localHue by draftField(activeDraft, { it.hue }) { d, v -> d.copy(hue = v) }
    var localSaturation by draftField(activeDraft, { it.saturation }) { d, v -> d.copy(saturation = v) }
    var localLightness by draftField(activeDraft, { it.lightness }) { d, v -> d.copy(lightness = v) }
    var localColorOpacity by draftField(activeDraft, { it.colorOpacity }) { d, v -> d.copy(colorOpacity = v) }
    var localTransparency by draftField(activeDraft, { it.transparency }) { d, v -> d.copy(transparency = v) }
    var localShowGIcon by draftField(activeDraft, { it.showGIcon }) { d, v -> d.copy(showGIcon = v) }
    var localShowVoice by draftField(activeDraft, { it.showVoice }) { d, v -> d.copy(showVoice = v) }
    var localShortcut1 by draftField(activeDraft, { it.shortcut1 }) { d, v -> d.copy(shortcut1 = v) }
    var localShortcut2 by draftField(activeDraft, { it.shortcut2 }) { d, v -> d.copy(shortcut2 = v) }
    var localShortcut3 by draftField(activeDraft, { it.shortcut3 }) { d, v -> d.copy(shortcut3 = v) }
    var localSlotOrderStr by draftField(activeDraft, { it.slotOrder }) { d, v -> d.copy(slotOrder = v) }
    var localKeepSystemG by draftField(activeDraft, { it.keepSystemG }) { d, v -> d.copy(keepSystemG = v) }
    // Material-only settings.
    var localLockBlack by draftField(materialDraft, { it.lockBlack }) { d, v -> d.copy(lockBlack = v) }
    var matLockBlack by draftField(materialDraft, { it.lockBlack }) { d, v -> d.copy(lockBlack = v) }
    var localActionIcon by draftField(materialDraft, { it.actionIcon }) { d, v -> d.copy(actionIcon = v) }

    val computedCustomColorInt = remember(localHue, localSaturation, localLightness, localColorOpacity) {
        android.graphics.Color.HSVToColor(
            (localColorOpacity / 100f * 255).toInt().coerceIn(0, 255),
            floatArrayOf(localHue, (localSaturation / 100f).coerceIn(0f, 1f), (localLightness / 100f).coerceIn(0f, 1f))
        )
    }
    var activeShortcutSlot by remember { mutableIntStateOf(1) }
    var draggingSlotKey by remember { mutableStateOf<String?>(null) }
    val view = androidx.compose.ui.platform.LocalView.current
    val hapticEngine = remember(context) { com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context) }
    val coroutineScope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    var slotItemHeightPx by remember { mutableFloatStateOf(with(density) { 84.dp.toPx() }) }
    var itemDragOffset by remember { mutableFloatStateOf(0f) }
    var selectedTab by remember { mutableIntStateOf(0) }

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
                    "Appearance" -> {
                        // TAB 0: APPEARANCE
                        val isMaterialYou = localThemeStyle == "Material You (Minimal)" || localThemeStyle == "Material Design"

                        // G Icon options
                        SettingsCard {
                            SettingsRowToggle(
                                title = "Display G Icon",
                                subtitle = "Show Google Logo on Search Bar Widget.",
                                icon = null,
                                customIcon = {
                                    Image(
                                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_g_logo_colored),
                                        contentDescription = "G Icon",
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                isChecked = localShowGIcon,
                                onCheckedChange = { localShowGIcon = it },
                                showDivider = true
                            )
                        }

                        if (isMaterialYou) {
                            Text("WIDGET ACTION ICON", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
                            SettingsCard {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val actionOptions = listOf(
                                        Triple("None", "None", null),
                                        Triple("Search", "Search", com.pixel.intelligentsearch.R.drawable.ic_search_lens_expressive),
                                        Triple("Assistant", "Assistant", com.pixel.intelligentsearch.R.drawable.ic_lens_action),
                                        Triple("Now Playing", "Playing", com.pixel.intelligentsearch.R.drawable.ic_music)
                                    )
                                    val currentAction = if (localActionIcon == "Gemini") "Assistant" else localActionIcon
                                    actionOptions.forEach { (optionKey, label, iconRes) ->
                                        val isSel = currentAction == optionKey
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(64.dp)
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(
                                                    if (isSel) MaterialTheme.colorScheme.primaryContainer 
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                )
                                                .bouncyClickable(shape = RoundedCornerShape(20.dp)) {
                                                    hapticEngine.performPredictiveBackHaptic(view)
                                                    localActionIcon = optionKey
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier.padding(4.dp)
                                            ) {
                                                if (iconRes != null) {
                                                    ComposeActionIcon(
                                                        iconType = optionKey,
                                                        modifier = Modifier.size(22.dp),
                                                        primaryColor = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        secondaryColor = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        tertiaryColor = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                } else {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "None",
                                                        modifier = Modifier.size(22.dp),
                                                        tint = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Floating Theme Pills
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (localThemeStyle == "System Default") {
                                val systemOpts = listOf("System", "Light", "Dark", "Custom")
                                systemOpts.forEach { opt ->
                                    val isSel = localSubtheme == opt
                                    val dynColor = androidx.compose.ui.graphics.Color(computedCustomColorInt)
                                    val bgModifier = if (opt == "Custom") {
                                        if (isSel) {
                                            Modifier.background(dynColor, RoundedCornerShape(32.dp))
                                        } else {
                                            Modifier.background(
                                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                    colors = listOf(
                                                        dynColor.copy(alpha = 0.5f),
                                                        dynColor
                                                    )
                                                ),
                                                shape = RoundedCornerShape(32.dp)
                                            )
                                        }
                                    } else {
                                        Modifier.background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), RoundedCornerShape(32.dp))
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .then(bgModifier)
                                            .clip(RoundedCornerShape(32.dp))
                                            .bouncyClickable(shape = RoundedCornerShape(32.dp)) { localSubtheme = opt },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val textColor = if (isSel) {
                                            if (opt == "Custom") {
                                                val luminance = (0.299 * dynColor.red + 0.587 * dynColor.green + 0.114 * dynColor.blue)
                                                if (luminance > 0.5f) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
                                            } else MaterialTheme.colorScheme.onPrimaryContainer
                                        } else if (opt == "Custom") {
                                            androidx.compose.ui.graphics.Color.White
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        Text(opt, style = MaterialTheme.typography.labelMedium, color = textColor)
                                    }
                                }
                            } else {
                                val matOpts = listOf("Material", "Custom")
                                matOpts.forEach { opt ->
                                    val isSel = localSubtheme == opt
                                    val dynColor = androidx.compose.ui.graphics.Color(computedCustomColorInt)
                                    val bgModifier = if (opt == "Custom") {
                                        if (isSel) {
                                            Modifier.background(dynColor, RoundedCornerShape(32.dp))
                                        } else {
                                            Modifier.background(
                                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                    colors = listOf(
                                                        dynColor.copy(alpha = 0.5f),
                                                        dynColor
                                                    )
                                                ),
                                                shape = RoundedCornerShape(32.dp)
                                            )
                                        }
                                    } else {
                                        Modifier.background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), RoundedCornerShape(32.dp))
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .then(bgModifier)
                                            .clip(RoundedCornerShape(32.dp))
                                            .bouncyClickable(shape = RoundedCornerShape(32.dp)) {
                                                localSubtheme = opt
                                                if (opt == "Material") {
                                                    localLockBlack = true
                                                    matLockBlack = true
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val textColor = if (isSel) {
                                            if (opt == "Custom") {
                                                val luminance = (0.299 * dynColor.red + 0.587 * dynColor.green + 0.114 * dynColor.blue)
                                                if (luminance > 0.5f) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
                                            } else MaterialTheme.colorScheme.onPrimaryContainer
                                        } else if (opt == "Custom") {
                                            androidx.compose.ui.graphics.Color.White
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        Text(opt, style = MaterialTheme.typography.labelMedium, color = textColor)
                                    }
                                }
                            }
                        }

                        Text("G ICON STYLE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
                        // G Icon Row with Custom G Icon button right beside Accented G Icon
                        SettingsCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val opts = listOf("System G Icon", "Material G Icon", "Accented G Icon", "Custom G Icon")
                                opts.forEach { opt ->
                                    val isSel = localMaterialGIconTheme == opt
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                                            .bouncyClickable(shape = RoundedCornerShape(20.dp)) {
                                                hapticEngine.performPredictiveBackHaptic(view)
                                                localMaterialGIconTheme = opt
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val label = when (opt) {
                                            "System G Icon" -> "System"
                                            "Material G Icon" -> "Material"
                                            "Accented G Icon" -> "Accented"
                                            "Custom G Icon" -> "Custom"
                                            else -> opt
                                        }
                                        Text(
                                            text = label,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Material Design Inner Pill & Circle Switcher
                        val isMaterialDesign = localThemeStyle == "Material You (Minimal)" || localThemeStyle == "Material Design"
                        if (isMaterialDesign) {
                            Spacer(modifier = Modifier.height(8.dp))
                            SettingsCard {
                                SettingsRowToggle(
                                    title = "Inner Pill & Circle State",
                                    subtitle = "Force Inner Pill & Circle to #121212.",
                                    isChecked = localLockBlack,
                                    onCheckedChange = { checked ->
                                        localLockBlack = checked
                                        matLockBlack = checked
                                    }
                                )
                            }
                        }

                        if (localMaterialGIconTheme == "Custom G Icon") {
                            Text("CUSTOM ICON COLORS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
                            SettingsCard {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // 0. Default Google Color Pallete Switch Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Default Google Color Pallete", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                            Text(
                                                "Preserve multi-colored Google logo while customizing shortcuts and action button",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        com.pixel.intelligentsearch.core.ui.expressive.GoogleMessagesSwitch(
                                            checked = localKeepSystemG,
                                            onCheckedChange = { keep ->
                                                localKeepSystemG = keep
                                            }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // 1. Sync All Colors Switch Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Sync All Colors", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                            Text(
                                                if (localKeepSystemG) "Set shortcuts and action icon to the same color" else "Set G icon, shortcuts, and action icon to the same color",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        com.pixel.intelligentsearch.core.ui.expressive.GoogleMessagesSwitch(
                                            checked = localCustomSyncColors,
                                            onCheckedChange = { synced ->
                                                localCustomSyncColors = synced
                                                if (synced) {
                                                    localCustomMicColor = localCustomGIconColor
                                                    localCustomShortcut1Color = localCustomGIconColor
                                                    localCustomShortcut2Color = localCustomGIconColor
                                                    localCustomShortcut3Color = localCustomGIconColor
                                                    localCustomActionColor = localCustomGIconColor
                                                }
                                            }
                                        )
                                    }

                                    // 2. Intelligent Shortcut / Element Selector (When Sync All is disabled)
                                    val activeElements = remember(localShowGIcon, localKeepSystemG, localShowVoice, localShortcut1, localShortcut2, localShortcut3, localActionIcon, localSlotOrderStr, isMaterialYou, localCustomGIconColor, localCustomMicColor, localCustomShortcut1Color, localCustomShortcut2Color, localCustomShortcut3Color, localCustomActionColor) {
                                        val list = mutableListOf<CustomElementTarget>()
                                        if (localShowGIcon && !localKeepSystemG) {
                                            list.add(CustomElementTarget("g_icon", "G Icon", localCustomGIconColor))
                                        }
                                        val slotOrder = localSlotOrderStr.split(",").filter { it.isNotBlank() }
                                        slotOrder.forEach { key ->
                                            when (key) {
                                                "mic" -> if (localShowVoice) list.add(CustomElementTarget("mic", "Voice Search", localCustomMicColor))
                                                "shortcut1" -> if (localShortcut1 != "None") list.add(CustomElementTarget("shortcut1", localShortcut1, localCustomShortcut1Color))
                                                "shortcut2" -> if (localShortcut2 != "None") list.add(CustomElementTarget("shortcut2", localShortcut2, localCustomShortcut2Color))
                                                "shortcut3" -> if (localShortcut3 != "None") list.add(CustomElementTarget("shortcut3", localShortcut3, localCustomShortcut3Color))
                                            }
                                        }
                                        if (isMaterialYou && localActionIcon != "None") {
                                            list.add(CustomElementTarget("action", "Action: $localActionIcon", localCustomActionColor))
                                        }
                                        if (list.isEmpty()) {
                                            if (!localKeepSystemG) {
                                                list.add(CustomElementTarget("g_icon", "G Icon", localCustomGIconColor))
                                            } else {
                                                list.add(CustomElementTarget("mic", "Voice Search", localCustomMicColor))
                                            }
                                        }
                                        list
                                    }

                                    LaunchedEffect(activeElements) {
                                        if (activeElements.none { it.key == activeCustomColorTarget }) {
                                            activeCustomColorTarget = activeElements.firstOrNull()?.key ?: "g_icon"
                                        }
                                    }

                                    if (!localCustomSyncColors) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            "SELECT ELEMENT TO CUSTOMIZE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        var elementDropdownExpanded by remember { mutableStateOf(false) }
                                        val currentSelectedElement = activeElements.firstOrNull { it.key == activeCustomColorTarget } ?: activeElements.first()

                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(52.dp)
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                                    .border(
                                                        width = 1.dp,
                                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                                        shape = RoundedCornerShape(20.dp)
                                                    )
                                                    .bouncyClickable(shape = RoundedCornerShape(20.dp)) {
                                                        hapticEngine.performPredictiveBackHaptic(view)
                                                        elementDropdownExpanded = true
                                                    }
                                                    .padding(horizontal = 16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                                        .background(androidx.compose.ui.graphics.Color(currentSelectedElement.color))
                                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = currentSelectedElement.label,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Icon(
                                                    imageVector = if (elementDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                                    contentDescription = "Dropdown",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            MaterialTheme(
                                                shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(24.dp))
                                            ) {
                                                androidx.compose.material3.DropdownMenu(
                                                    expanded = elementDropdownExpanded,
                                                    onDismissRequest = { elementDropdownExpanded = false },
                                                    modifier = Modifier
                                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                                                        .clip(RoundedCornerShape(24.dp))
                                                ) {
                                                    activeElements.forEach { element ->
                                                        val isSelected = element.key == activeCustomColorTarget
                                                        androidx.compose.material3.DropdownMenuItem(
                                                            leadingIcon = {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(16.dp)
                                                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                                                        .background(androidx.compose.ui.graphics.Color(element.color))
                                                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
                                                                )
                                                            },
                                                            text = {
                                                                Text(
                                                                    text = element.label,
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                                )
                                                            },
                                                            trailingIcon = if (isSelected) {
                                                                {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Check,
                                                                        contentDescription = null,
                                                                        tint = MaterialTheme.colorScheme.primary,
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                }
                                                            } else null,
                                                            onClick = {
                                                                hapticEngine.performPredictiveBackHaptic(view)
                                                                activeCustomColorTarget = element.key
                                                                elementDropdownExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(20.dp))

                                    val (activeColor, activeLabel) = when {
                                        localCustomSyncColors -> Pair(localCustomGIconColor, "All Icons")
                                        activeCustomColorTarget == "g_icon" -> Pair(localCustomGIconColor, "G Icon")
                                        activeCustomColorTarget == "mic" -> Pair(localCustomMicColor, "Voice Search")
                                        activeCustomColorTarget == "shortcut1" -> Pair(localCustomShortcut1Color, localShortcut1)
                                        activeCustomColorTarget == "shortcut2" -> Pair(localCustomShortcut2Color, localShortcut2)
                                        activeCustomColorTarget == "shortcut3" -> Pair(localCustomShortcut3Color, localShortcut3)
                                        activeCustomColorTarget == "action" -> Pair(localCustomActionColor, "Action: $localActionIcon")
                                        else -> Pair(localCustomGIconColor, "G Icon")
                                    }

                                    val updateTargetColor: (Int) -> Unit = { newColor ->
                                        if (localCustomSyncColors) {
                                            localCustomGIconColor = newColor
                                            localCustomMicColor = newColor
                                            localCustomShortcut1Color = newColor
                                            localCustomShortcut2Color = newColor
                                            localCustomShortcut3Color = newColor
                                            localCustomActionColor = newColor
                                        } else {
                                            when (activeCustomColorTarget) {
                                                "g_icon" -> localCustomGIconColor = newColor
                                                "mic" -> localCustomMicColor = newColor
                                                "shortcut1" -> localCustomShortcut1Color = newColor
                                                "shortcut2" -> localCustomShortcut2Color = newColor
                                                "shortcut3" -> localCustomShortcut3Color = newColor
                                                "action" -> localCustomActionColor = newColor
                                                else -> localCustomGIconColor = newColor
                                            }
                                        }
                                    }

                                    LaunchedEffect(activeCustomColorTarget, localCustomSyncColors, isSystem, localMaterialGIconTheme) {
                                        val h = FloatArray(3)
                                        android.graphics.Color.colorToHSV(activeColor, h)
                                        if (h[1] > 0.01f || h[2] > 0.01f) {
                                            customIconHue = h[0]
                                        }
                                        customIconSat = h[1] * 100f
                                        customIconVal = h[2] * 100f
                                    }

                                    // 3. Hue Slider (matching Color Studio)
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.Palette, contentDescription = "Hue", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Hue", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                                Spacer(modifier = Modifier.weight(1f))
                                                Text("${customIconHue.toInt()}°", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                    colors = listOf(
                                                        androidx.compose.ui.graphics.Color.Red,
                                                        androidx.compose.ui.graphics.Color.Yellow,
                                                        androidx.compose.ui.graphics.Color.Green,
                                                        androidx.compose.ui.graphics.Color.Cyan,
                                                        androidx.compose.ui.graphics.Color.Blue,
                                                        androidx.compose.ui.graphics.Color.Magenta,
                                                        androidx.compose.ui.graphics.Color.Red
                                                    )
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            )) {
                                                Android17Slider(
                                                    showTrack = false,
                                                    enableHaptics = false,
                                                    value = customIconHue,
                                                    onValueChange = { 
                                                        customIconHue = it
                                                        val newCol = android.graphics.Color.HSVToColor(
                                                            floatArrayOf(it, (customIconSat / 100f).coerceIn(0f, 1f), (customIconVal / 100f).coerceIn(0f, 1f))
                                                        )
                                                        updateTargetColor(newCol)
                                                    },
                                                    valueRange = 0f..360f,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))

                                    // 4. Saturation Slider (matching Color Studio)
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.WaterDrop, contentDescription = "Saturation", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Saturation", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                                Spacer(modifier = Modifier.weight(1f))
                                                Text("${customIconSat.toInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                    colors = listOf(
                                                        androidx.compose.ui.graphics.Color.White,
                                                        androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(floatArrayOf(customIconHue, 1f, 1f)))
                                                    )
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            )) {
                                                Android17Slider(
                                                    showTrack = false,
                                                    enableHaptics = false,
                                                    value = customIconSat,
                                                    onValueChange = { 
                                                        customIconSat = it
                                                        val newCol = android.graphics.Color.HSVToColor(
                                                            floatArrayOf(customIconHue, (it / 100f).coerceIn(0f, 1f), (customIconVal / 100f).coerceIn(0f, 1f))
                                                        )
                                                        updateTargetColor(newCol)
                                                    },
                                                    valueRange = 0f..100f,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))

                                    // 5. Brightness Slider (matching Color Studio)
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Outlined.BrightnessMedium, contentDescription = "Brightness", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Brightness", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                                Spacer(modifier = Modifier.weight(1f))
                                                Text("${customIconVal.toInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                    colors = listOf(
                                                        androidx.compose.ui.graphics.Color.Black,
                                                        androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(floatArrayOf(customIconHue, (customIconSat / 100f).coerceIn(0f, 1f), 1f)))
                                                    )
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            )) {
                                                Android17Slider(
                                                    showTrack = false,
                                                    enableHaptics = false,
                                                    value = customIconVal,
                                                    onValueChange = { 
                                                        customIconVal = it
                                                        val newCol = android.graphics.Color.HSVToColor(
                                                            floatArrayOf(customIconHue, (customIconSat / 100f).coerceIn(0f, 1f), (it / 100f).coerceIn(0f, 1f))
                                                        )
                                                        updateTargetColor(newCol)
                                                    },
                                                    valueRange = 0f..100f,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))

                                    // 6. Hex Color Pill Bar (matching Color Studio)
                                    val activeHex = String.format("#%06X", (0xFFFFFF and activeColor))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                            .clickable {
                                                tempCustomHexInput = activeHex
                                                showCustomHexDialog = true
                                            }
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(androidx.compose.ui.graphics.Color(activeColor))
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            "$activeLabel  •  $activeHex",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit Hex",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    "Color Studio" -> {
                        // TAB 1: COLOR STUDIO
                        val dynamicCustomColor = androidx.compose.ui.graphics.Color(computedCustomColorInt)
                        val transparencyTrackGradient = remember(computedCustomColorInt) {
                            listOf(
                                dynamicCustomColor,
                                dynamicCustomColor.copy(alpha = 0.7f),
                                dynamicCustomColor.copy(alpha = 0.35f),
                                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f),
                                androidx.compose.ui.graphics.Color(0xFF3C4043).copy(alpha = 0.6f),
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f)
                            )
                        }

                        // Custom Color Palette Container on Top
                        Text("CUSTOM COLOR PALETTE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
                        SettingsCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // 1. Hue Slider
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Palette, contentDescription = "Hue", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Hue", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text("${localHue.toInt()}°", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                colors = listOf(
                                                    androidx.compose.ui.graphics.Color.Red,
                                                    androidx.compose.ui.graphics.Color.Yellow,
                                                    androidx.compose.ui.graphics.Color.Green,
                                                    androidx.compose.ui.graphics.Color.Cyan,
                                                    androidx.compose.ui.graphics.Color.Blue,
                                                    androidx.compose.ui.graphics.Color.Magenta,
                                                    androidx.compose.ui.graphics.Color.Red
                                                )
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        )) {
                                            Android17Slider(
                                                showTrack = false,
                                                enableHaptics = false,
                                                value = localHue,
                                                onValueChange = { 
                                                    localSubtheme = "Custom"
                                                    localHue = it 
                                                },
                                                valueRange = 0f..360f,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                // 2. Saturation Slider
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.WaterDrop, contentDescription = "Saturation", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Saturation", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text("${localSaturation.toInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                colors = listOf(
                                                    androidx.compose.ui.graphics.Color.White,
                                                    androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(floatArrayOf(localHue, 1f, 1f)))
                                                )
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        )) {
                                            Android17Slider(
                                                showTrack = false,
                                                enableHaptics = false,
                                                value = localSaturation,
                                                onValueChange = { 
                                                    localSubtheme = "Custom"
                                                    localSaturation = it 
                                                },
                                                valueRange = 0f..100f,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))

                                // 3. Lightness Slider
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Outlined.BrightnessMedium, contentDescription = "Lightness", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Lightness", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text("${localLightness.toInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                colors = listOf(
                                                    androidx.compose.ui.graphics.Color.Black,
                                                    androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(floatArrayOf(localHue, (localSaturation / 100f).coerceIn(0f, 1f), 1f)))
                                                )
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        )) {
                                            Android17Slider(
                                                showTrack = false,
                                                enableHaptics = false,
                                                value = localLightness,
                                                onValueChange = { 
                                                    localSubtheme = "Custom"
                                                    localLightness = it 
                                                },
                                                valueRange = 0f..100f,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                                
                                // 4. Color Opacity Slider
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Contrast, contentDescription = "Color Opacity", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Color Opacity", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text("${localColorOpacity.toInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                colors = listOf(
                                                    androidx.compose.ui.graphics.Color.Transparent,
                                                    androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(floatArrayOf(localHue, localSaturation / 100f, 1f)))
                                                )
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        )) {
                                            Android17Slider(
                                                value = localColorOpacity,
                                                enableHaptics = false,
                                                onValueChange = { 
                                                    localSubtheme = "Custom"
                                                    localColorOpacity = it 
                                                },
                                                valueRange = 0f..100f,
                                                modifier = Modifier.fillMaxWidth(),
                                                showTrack = false
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // 5. Transparency Slider (under color opacity in the same container)
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Opacity, contentDescription = "Transparency", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Transparency", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text("${localTransparency.toInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                colors = transparencyTrackGradient
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        )) {
                                            Android17Slider(
                                                value = localTransparency,
                                                enableHaptics = false,
                                                onValueChange = { localTransparency = it },
                                                valueRange = 0f..100f,
                                                modifier = Modifier.fillMaxWidth(),
                                                showTrack = false
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // 6. Hex Color Pill Bar (pill bar under custom color pallet sliders in the same container)
                                val customColorHex = String.format("#%06X", (0xFFFFFF and computedCustomColorInt))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                        .clickable {
                                            tempHexInput = customColorHex
                                            showHexInput = true
                                        }
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(dynamicCustomColor)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        customColorHex,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Hex",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    "Widget Shortcuts" -> {
                        // TAB 2: WIDGET SHORTCUTS
                        Text("WIDGET SHORTCUTS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
                        Text("Drag Handle to Reorder, Swipe to Disable, and Tap to Customize.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val currentOrderList = localSlotOrderStr.split(",").filter { it.isNotBlank() }
                            currentOrderList.forEach { slotKey ->
                                key(slotKey) {
                                    val isDragging = draggingSlotKey == slotKey
                                    val elevation by androidx.compose.animation.core.animateDpAsState(
                                        targetValue = if (isDragging) 8.dp else 0.dp,
                                        label = "elevation"
                                    )
                                    val scale by androidx.compose.animation.core.animateFloatAsState(
                                        targetValue = if (isDragging) 1.03f else 1f,
                                        label = "scale"
                                    )
                                    val swipeOffsetX = remember { androidx.compose.animation.core.Animatable(0f) }

                                    val currentPositionIndex = currentOrderList.indexOf(slotKey)
                                    val cardTitle = when (slotKey) {
                                        "mic" -> "Microphone Slot:"
                                        else -> "Shortcut Slot: ${currentPositionIndex + 1}"
                                    }

                                    val cardSubtext = when (slotKey) {
                                        "mic" -> if (localShowVoice) "Voice Search" else "None"
                                        "shortcut1" -> localShortcut1
                                        "shortcut2" -> localShortcut2
                                        else -> localShortcut3
                                    }

                                    val cardIcon = when (slotKey) {
                                        "mic" -> if (localShowVoice) Icons.Default.Mic else Icons.Default.Close
                                        else -> shortcutOptions.find { it.first == cardSubtext }?.second ?: Icons.Default.Close
                                    }

                                    val cardModifier = if (isDragging) {
                                        Modifier
                                            .zIndex(10f)
                                            .graphicsLayer {
                                                translationY = itemDragOffset
                                                scaleX = scale
                                                scaleY = scale
                                            }
                                    } else {
                                        Modifier
                                            .zIndex(0f)
                                            .graphicsLayer {
                                                translationX = swipeOffsetX.value
                                                scaleX = scale
                                                scaleY = scale
                                            }
                                    }

                                    Box(
                                        modifier = cardModifier
                                            .fillMaxWidth()
                                            .onSizeChanged {
                                                if (it.height > 0) {
                                                    slotItemHeightPx = it.height.toFloat() + with(density) { 12.dp.toPx() }
                                                }
                                            }
                                            .pointerInput(slotKey, isDragging, isSystem) {
                                                if (!isDragging) {
                                                    detectHorizontalDragGestures(
                                                        onDragEnd = {
                                                            coroutineScope.launch {
                                                                if (kotlin.math.abs(swipeOffsetX.value) > 200f) {
                                                                    hapticEngine.performPredictiveBackHaptic(view)
                                                                    when (slotKey) {
                                                                        "mic" -> localShowVoice = false
                                                                        "shortcut1" -> localShortcut1 = "None"
                                                                        "shortcut2" -> localShortcut2 = "None"
                                                                        "shortcut3" -> localShortcut3 = "None"
                                                                    }
                                                                }
                                                                swipeOffsetX.animateTo(
                                                                    targetValue = 0f,
                                                                    animationSpec = androidx.compose.animation.core.spring(
                                                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                                                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                                                    )
                                                                )
                                                            }
                                                        },
                                                        onDragCancel = {
                                                            coroutineScope.launch {
                                                                swipeOffsetX.animateTo(
                                                                    targetValue = 0f,
                                                                    animationSpec = androidx.compose.animation.core.spring(
                                                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                                                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                                                    )
                                                                )
                                                            }
                                                        },
                                                        onHorizontalDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float ->
                                                            change.consume()
                                                            coroutineScope.launch {
                                                                swipeOffsetX.snapTo(swipeOffsetX.value + dragAmount)
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .shadow(elevation, RoundedCornerShape(24.dp))
                                                .clip(RoundedCornerShape(24.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
                                            color = Color.Transparent
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
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable {
                                                            hapticEngine.performPredictiveBackHaptic(view)
                                                            if (slotKey == "mic") {
                                                                localShowVoice = !localShowVoice
                                                            } else {
                                                                activeShortcutSlot = when (slotKey) {
                                                                    "shortcut1" -> 1
                                                                    "shortcut2" -> 2
                                                                    else -> 3
                                                                }
                                                                showShortcutSheet = true
                                                            }
                                                        }
                                                ) {
                                                    Icon(
                                                        imageVector = cardIcon,
                                                        contentDescription = cardSubtext,
                                                        modifier = Modifier.size(36.dp),
                                                        tint = if (cardSubtext == "None") MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(16.dp))
                                                    Column {
                                                        Text(
                                                            text = cardTitle,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontSize = 16.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        Text(
                                                            text = cardSubtext,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                            fontSize = 12.sp
                                                        )
                                                    }
                                                }

                                                val currentSlot by rememberUpdatedState(slotKey)
                                                Icon(
                                                    imageVector = Icons.Outlined.DragHandle,
                                                    contentDescription = "Drag to reorder",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    modifier = Modifier.pointerInput(currentSlot, isSystem) {
                                                        detectVerticalDragGestures(
                                                            onDragStart = {
                                                                hapticEngine.performPredictiveBackHaptic(view)
                                                                draggingSlotKey = currentSlot
                                                                itemDragOffset = 0f
                                                            },
                                                            onDragEnd = {
                                                                draggingSlotKey = null
                                                                itemDragOffset = 0f
                                                            },
                                                            onDragCancel = {
                                                                draggingSlotKey = null
                                                                itemDragOffset = 0f
                                                            },
                                                            onVerticalDrag = { change, dragAmount ->
                                                                change.consume()
                                                                itemDragOffset += dragAmount

                                                                val currentDraggingSlot = draggingSlotKey ?: return@detectVerticalDragGestures
                                                                val currentList = localSlotOrderStr.split(",").filter { it.isNotBlank() }
                                                                val from = currentList.indexOf(currentDraggingSlot)
                                                                val itemHeight = slotItemHeightPx

                                                                if (from != -1 && itemHeight > 0f) {
                                                                    if (itemDragOffset > itemHeight / 2f && from < currentList.size - 1) {
                                                                        hapticEngine.performPredictiveBackHaptic(view)
                                                                        val updated = currentList.toMutableList()
                                                                        java.util.Collections.swap(updated, from, from + 1)
                                                                        localSlotOrderStr = updated.joinToString(",")
                                                                        itemDragOffset -= itemHeight
                                                                    } else if (itemDragOffset < -itemHeight / 2f && from > 0) {
                                                                        hapticEngine.performPredictiveBackHaptic(view)
                                                                        val updated = currentList.toMutableList()
                                                                        java.util.Collections.swap(updated, from, from - 1)
                                                                        localSlotOrderStr = updated.joinToString(",")
                                                                        itemDragOffset += itemHeight
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

            if (showShortcutSheet) {
                ModalBottomSheet(onDismissRequest = { showShortcutSheet = false }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Select Widget Shortcut", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(shortcutOptions.size) { index ->
                                val option = shortcutOptions[index]
                                val isCustomizable = option.first in listOf("Weather", "Sports", "Dictionary")
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.bouncyClickable {
                                        if (isCustomizable) {
                                            expandedDropdownFor = option.first
                                        } else {
                                            when (activeShortcutSlot) {
                                                1 -> localShortcut1 = option.first
                                                2 -> localShortcut2 = option.first
                                                3 -> localShortcut3 = option.first
                                            }
                                            showShortcutSheet = false
                                        }
                                    }
                                ) {
                                    val currentShortcutSlotVal = when (activeShortcutSlot) {
                                        1 -> localShortcut1
                                        2 -> localShortcut2
                                        else -> localShortcut3
                                    }
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = option.second,
                                            contentDescription = option.first,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .background(
                                                    if (currentShortcutSlotVal == option.first) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                    androidx.compose.foundation.shape.CircleShape
                                                )
                                                .padding(16.dp),
                                            tint = if (currentShortcutSlotVal == option.first) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (isCustomizable) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "Options",
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .size(20.dp)
                                                    .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                            androidx.compose.material3.DropdownMenu(
                                                expanded = expandedDropdownFor == option.first,
                                                onDismissRequest = { expandedDropdownFor = null }
                                            ) {
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text("Default (Google)") },
                                                    onClick = {
                                                        // Drop any custom URL/app override so the slot really returns to the default target.
                                                        val shortcutKey = option.first
                                                        if (prefs.getString("${shortcutKey}_custom_type", "default") == "default") {
                                                            pendingCustomShortcutTypes.remove(shortcutKey)
                                                            pendingCustomShortcutValues.remove(shortcutKey)
                                                        } else {
                                                            pendingCustomShortcutTypes[shortcutKey] = "default"
                                                            pendingCustomShortcutValues.remove(shortcutKey)
                                                        }
                                                        when (activeShortcutSlot) {
                                                            1 -> localShortcut1 = option.first
                                                            2 -> localShortcut2 = option.first
                                                            3 -> localShortcut3 = option.first
                                                        }
                                                        showShortcutSheet = false
                                                        expandedDropdownFor = null
                                                    }
                                                )
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text("Custom Website (URL)") },
                                                    onClick = {
                                                        customInputValue = ""
                                                        showCustomUrlDialogFor = option.first
                                                        expandedDropdownFor = null
                                                    }
                                                )
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text("Custom App (APK)") },
                                                    onClick = {
                                                        customInputValue = ""
                                                        showCustomAppDialogFor = option.first
                                                        expandedDropdownFor = null
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = option.first,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 8.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showHexInput) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showHexInput = false },
                    title = { Text("Enter Hex Color") },
                    text = {
                        androidx.compose.material3.OutlinedTextField(
                            value = tempHexInput,
                            onValueChange = { tempHexInput = it },
                            label = { Text("Hex Code") }
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            try {
                                val color = android.graphics.Color.parseColor(if (tempHexInput.startsWith("#")) tempHexInput else "#$tempHexInput")
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(color, hsv)
                                localHue = hsv[0]
                                localSaturation = hsv[1] * 100
                                localLightness = hsv[2] * 100
                                localSubtheme = "Custom"
                            } catch (e: Exception) {}
                            showHexInput = false
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showHexInput = false }) { Text("Cancel") }
                    }
                )
            }

            if (showCustomHexDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showCustomHexDialog = false },
                    title = { Text("Enter Hex Color") },
                    text = {
                        androidx.compose.material3.OutlinedTextField(
                            value = tempCustomHexInput,
                            onValueChange = { tempCustomHexInput = it },
                            label = { Text("Hex Code (e.g. #4285F4)") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            try {
                                val color = android.graphics.Color.parseColor(if (tempCustomHexInput.startsWith("#")) tempCustomHexInput else "#$tempCustomHexInput")
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(color, hsv)
                                if (hsv[1] > 0.01f || hsv[2] > 0.01f) {
                                    customIconHue = hsv[0]
                                }
                                customIconSat = hsv[1] * 100f
                                customIconVal = hsv[2] * 100f
                                if (localCustomSyncColors) {
                                    localCustomGIconColor = color
                                    localCustomMicColor = color
                                    localCustomShortcut1Color = color
                                    localCustomShortcut2Color = color
                                    localCustomShortcut3Color = color
                                    localCustomActionColor = color
                                } else {
                                    when (activeCustomColorTarget) {
                                        "g_icon" -> localCustomGIconColor = color
                                        "mic" -> localCustomMicColor = color
                                        "shortcut1" -> localCustomShortcut1Color = color
                                        "shortcut2" -> localCustomShortcut2Color = color
                                        "shortcut3" -> localCustomShortcut3Color = color
                                        "action" -> localCustomActionColor = color
                                        else -> localCustomGIconColor = color
                                    }
                                }
                            } catch (e: Exception) {}
                            showCustomHexDialog = false
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCustomHexDialog = false }) { Text("Cancel") }
                    }
                )
            }

            if (showCustomUrlDialogFor != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showCustomUrlDialogFor = null },
                    title = { Text("Custom URL for ${showCustomUrlDialogFor}") },
                    text = {
                        val candidate = customInputValue.trim()
                        androidx.compose.material3.OutlinedTextField(
                            value = customInputValue,
                            onValueChange = { customInputValue = it },
                            label = { Text("Enter URL") },
                            singleLine = true,
                            isError = candidate.isNotEmpty() && !isValidShortcutUrl(candidate),
                            supportingText = if (candidate.isNotEmpty() && !isValidShortcutUrl(candidate)) {
                                { Text("Enter a web address, e.g. example.com or https://example.com") }
                            } else null
                        )
                    },
                    confirmButton = {
                        TextButton(enabled = isValidShortcutUrl(customInputValue.trim()), onClick = {
                            val slot = showCustomUrlDialogFor
                            if (slot != null) {
                                pendingCustomShortcutTypes[slot] = "url"
                                pendingCustomShortcutValues[slot] = customInputValue.trim()
                                when (activeShortcutSlot) {
                                    1 -> localShortcut1 = slot
                                    2 -> localShortcut2 = slot
                                    3 -> localShortcut3 = slot
                                }
                            }
                            showShortcutSheet = false
                            showCustomUrlDialogFor = null
                        }) { Text("Confirm") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCustomUrlDialogFor = null }) { Text("Cancel") }
                    }
                )
            }

            if (showCustomAppDialogFor != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showCustomAppDialogFor = null },
                    title = { Text("Custom App for ${showCustomAppDialogFor}") },
                    text = {
                        androidx.compose.material3.OutlinedTextField(
                            value = customInputValue,
                            onValueChange = { customInputValue = it },
                            label = { Text("Enter Package Name") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(enabled = PACKAGE_NAME_REGEX.matches(customInputValue.trim()), onClick = {
                            val slot = showCustomAppDialogFor
                            if (slot != null) {
                                pendingCustomShortcutTypes[slot] = "app"
                                pendingCustomShortcutValues[slot] = customInputValue.trim()
                                when (activeShortcutSlot) {
                                    1 -> localShortcut1 = slot
                                    2 -> localShortcut2 = slot
                                    3 -> localShortcut3 = slot
                                }
                            }
                            showShortcutSheet = false
                            showCustomAppDialogFor = null
                        }) { Text("Confirm") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCustomAppDialogFor = null }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

