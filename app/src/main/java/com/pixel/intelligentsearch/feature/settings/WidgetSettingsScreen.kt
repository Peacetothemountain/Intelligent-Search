package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.widget.WidgetDefaults
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import com.pixel.intelligentsearch.core.data.widget.WidgetDesignSettings
import com.pixel.intelligentsearch.core.data.widget.WidgetElementColors
import com.pixel.intelligentsearch.core.data.widget.WidgetSettingsStore
import com.pixel.intelligentsearch.core.data.widget.WidgetThemeStore
import com.pixel.intelligentsearch.feature.widget.WidgetColorRules
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
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import com.pixel.intelligentsearch.core.theme.DynamicThemeDetector
import com.pixel.intelligentsearch.core.theme.SearchIconColors
import com.pixel.intelligentsearch.core.ui.expressive.matteShell
import com.pixel.intelligentsearch.feature.widget.PinWidgetReceiver
import kotlinx.coroutines.launch

// WIDGET SETTINGS SCREEN
// -----------------------------------------------------------------------------------------

private data class SlotPreviewItem(val slotKey: String, val label: String, val resId: Int)

/** The live preview's pill: its fill, and whether that fill is light, which picks the colours of the icons on it. */
internal data class PreviewPill(val fill: Color, val isLight: Boolean)

/**
 * The preview pill of the design being edited. Whether a theme-coloured pill is light or dark comes from
 * the colour scheme this screen is drawn with, the same source as its fill, so the icons drawn on it keep their
 * contrast when the app's theme differs from the system's. The fixed light and dark pills of the System
 * subtheme without dynamic colour still follow the system, as the widget does.
 */
@Composable
internal fun previewPill(
    isMaterialDesign: Boolean,
    subtheme: String,
    lockBlack: Boolean,
    accentColor: Color,
    containerAlpha: Float,
    effectiveColorAlpha: Float,
    isDynamicColorActive: Boolean
): PreviewPill = previewPill(
    isMaterialDesign, subtheme, lockBlack, accentColor, containerAlpha, effectiveColorAlpha, isDynamicColorActive,
    themedSurface = MaterialTheme.colorScheme.surfaceContainer,
    appDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
    systemDark = androidx.compose.foundation.isSystemInDarkTheme()
)

/** [previewPill] with the screen's theme passed in: [themedSurface] fills a theme-coloured pill. */
internal fun previewPill(
    isMaterialDesign: Boolean,
    subtheme: String,
    lockBlack: Boolean,
    accentColor: Color,
    containerAlpha: Float,
    effectiveColorAlpha: Float,
    isDynamicColorActive: Boolean,
    themedSurface: Color,
    appDark: Boolean,
    systemDark: Boolean
): PreviewPill {
    val themed = themedSurface.copy(alpha = containerAlpha)
    val custom = accentColor.copy(alpha = effectiveColorAlpha)
    val customIsLight = WidgetColorRules.iconsGoDark(accentColor.toArgb())
    val light = Color(0xFFF1F3F4).copy(alpha = containerAlpha)
    val dark = Color(0xFF303134).copy(alpha = containerAlpha)
    return if (isMaterialDesign) {
        when {
            lockBlack -> PreviewPill(Color(0xFF121212), isLight = false)
            subtheme == "Custom" -> PreviewPill(custom, customIsLight)
            else -> PreviewPill(themed, !appDark)
        }
    } else {
        when (subtheme) {
            "Light" -> PreviewPill(light, isLight = true)
            "Dark" -> PreviewPill(dark, isLight = false)
            "Custom" -> PreviewPill(custom, customIsLight)
            else -> when {
                isDynamicColorActive -> PreviewPill(themed, !appDark)
                systemDark -> PreviewPill(dark, isLight = false)
                else -> PreviewPill(light, isLight = true)
            }
        }
    }
}

/** The colours the preview is drawn with: this screen's scheme, and whether the app and the system are dark. */
internal data class PreviewTheme(
    val primary: Color,
    val primaryContainer: Color,
    val surfaceContainer: Color,
    val appDark: Boolean,
    val systemDark: Boolean
)

/** Everything the live preview paints for one design, worked out from its draft. */
internal data class WidgetPreviewColors(
    /** The outline around the pill: the Material design's rim, or the System design's accent outline. */
    val rim: Color,
    /** The pill, whose fill the Material design's circle shares. */
    val pill: PreviewPill,
    /** The Accented G style's G, mic, shortcuts and circle icon. */
    val accentPrimary: Color,
    /** What the Material G style's icons are worked out from, the search overlay's way ([SearchIconColors]). */
    val materialIconAccents: SearchIconColors.Accents,
    /** What the System G style tints the shortcuts and the circle's icon. */
    val systemIconTint: Color,
    /** The Custom G style's colours as drawn: Sync All Colors applied, and darker where the widget darkens them. */
    val customIcons: WidgetElementColors,
    /** Whether the mic is the Material You drawing rather than the original one. */
    val usesMaterialYouIcons: Boolean
)

/**
 * What the live preview paints for [draft] on the [isMaterialDesign] design, drawn with [theme], as the widget draws
 * the same settings. [iconAccents] gives the search overlay's icon accents for a light (true) or dark bar
 * ([SearchIconColors.themeAccents]), and [widgetColors] the widget's own colours for a dark (true) or light surface.
 */
internal fun widgetPreviewColors(
    draft: WidgetDesignSettings,
    isMaterialDesign: Boolean,
    theme: PreviewTheme,
    isDynamicColorActive: Boolean,
    iconAccents: (isLight: Boolean) -> SearchIconColors.Accents,
    widgetColors: (isDark: Boolean) -> DynamicThemeDetector.ResolvedWidgetColors
): WidgetPreviewColors {
    val isCustomTheme = draft.subtheme == "Custom"
    val lockBlack = draft.lockBlack
    // Alphas as the widget's views take them (whole steps of 1/255), so the preview is exactly as see-through.
    fun alpha(value: Float) = WidgetColorRules.alphaInt(value) / 255f
    val containerAlpha = alpha(WidgetColorRules.backgroundAlpha(draft))
    val effectiveColorAlpha = alpha(WidgetColorRules.customColorAlpha(draft))
    val accentColor = if (isCustomTheme) Color(draft.customColorInt) else theme.primary

    val materialRim = if (isMaterialDesign) {
        // Locked black, the widget rims its black pill in the dark theme's colours, whatever the theme.
        val (rimPrimary, rimContainer) = if (lockBlack && !theme.appDark) {
            widgetColors(true).let { Color(it.primary) to Color(it.primaryContainer) }
        } else {
            theme.primary to theme.primaryContainer
        }
        val rimAlpha = alpha(WidgetColorRules.rimAlpha(draft))
        when (draft.subtheme) {
            "Custom" -> accentColor.copy(alpha = rimAlpha)
            "Material" -> rimContainer.copy(alpha = rimAlpha)
            else -> rimPrimary.copy(alpha = rimAlpha)
        }
    } else null

    val pill = previewPill(
        isMaterialDesign = isMaterialDesign,
        subtheme = draft.subtheme,
        lockBlack = lockBlack,
        accentColor = accentColor,
        containerAlpha = containerAlpha,
        effectiveColorAlpha = effectiveColorAlpha,
        isDynamicColorActive = isDynamicColorActive,
        themedSurface = theme.surfaceContainer,
        appDark = theme.appDark,
        systemDark = theme.systemDark
    )
    val isPillLight = pill.isLight

    val isDarkSurface = if (isMaterialDesign) lockBlack || !isPillLight else !isPillLight
    val surfaceColors = widgetColors(isDarkSurface)
    // The Custom subtheme's accent and plain icons come from its seed, already in contrast with the pill.
    val customBar = if (isCustomTheme) WidgetColorRules.customBarColors(draft, isMaterialDesign) else null

    val accentPrimary = customBar?.let { Color(it.accent) }
        ?: Color(WidgetColorRules.iconColor(surfaceColors.primary, isMaterialDesign, lockBlack, isPillLight, 32.0))
    // The System design's optional outline is its accent, as see-through as its pill.
    val rim = materialRim ?: accentPrimary.copy(alpha = pill.fill.alpha)

    val systemIconTint = when {
        customBar != null -> Color(customBar.plainIcon)
        isPillLight && (!isMaterialDesign || !lockBlack) -> Color(surfaceColors.onSurface)
        else -> Color.White
    }
    return WidgetPreviewColors(
        rim = rim,
        pill = pill,
        accentPrimary = accentPrimary,
        materialIconAccents = WidgetColorRules.materialIconAccents(draft, isPillLight, iconAccents),
        systemIconTint = systemIconTint,
        customIcons = WidgetColorRules.customIconColors(draft, isMaterialDesign, isPillLight),
        usesMaterialYouIcons = WidgetColorRules.usesMaterialYouIcons(isMaterialDesign, draft.gIconTheme, isPillLight, draft.subtheme)
    )
}

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
    
    // Discover is the Google app's feed, so it's offered only while the Google app is installed.
    val discoverAvailable = remember(context) { com.pixel.intelligentsearch.feature.widget.isDiscoverAvailable(context) }
    val shortcutOptions = listOfNotNull(
        "None" to Icons.Default.Close,
        "Google Lens" to ImageVector.vectorResource(id = com.pixel.intelligentsearch.R.drawable.ic_camera),
        "Live" to ImageVector.vectorResource(id = com.pixel.intelligentsearch.R.drawable.ic_search_lens_expressive),
        "Translate (text)" to Icons.Default.Translate,
        "Translate (camera)" to Icons.Default.DocumentScanner,
        "Song Search" to Icons.Default.MusicNote,
        "Weather" to Icons.Default.WbSunny,
        "Sports" to Icons.Default.SportsBasketball,
        "Dictionary" to Icons.AutoMirrored.Filled.MenuBook,
        "Homework" to Icons.Default.School,
        "Finance" to Icons.AutoMirrored.Filled.TrendingUp,
        "Saved" to Icons.Default.Bookmark,
        "News" to Icons.AutoMirrored.Filled.Article,
        "Calculator" to Icons.Default.Calculate,
        ("Discover" to Icons.Default.Explore).takeIf { discoverAvailable }
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

    // Named designs the user saved, for the design being edited.
    val themeStore = remember(prefs) { WidgetThemeStore(prefs) }
    var themesVersion by remember { mutableIntStateOf(0) }
    val activeDesign = if (isSystem) WidgetDesign.SYSTEM else WidgetDesign.MATERIAL
    val savedThemes = remember(themesVersion, activeDesign) {
        val themes = themeStore.list(WidgetDefaults(defaultUserMaterialColor, isDynamicActive)).filter { it.design == activeDesign }
        WidgetSavedThemes(
            themes = themes,
            save = { name -> themeStore.save(name, activeDesign, activeDraft.value); themesVersion++ },
            apply = { theme -> activeDraft.value = theme.settings },
            rename = { theme, name -> themeStore.rename(theme.name, theme.design, name).also { themesVersion++ } },
            delete = { theme -> themeStore.delete(theme.name, theme.design); themesVersion++ }
        )
    }
    val fields = remember(isSystem) { WidgetDraftFields(activeDraft, materialDraft) }
    var localSubtheme by fields::subtheme
    var localMaterialGIconTheme by fields::materialGIconTheme
    val colors = remember(isSystem) { WidgetColorSelection() }
    var localShowGIcon by fields::showGIcon
    var localShowVoice by fields::showVoice
    var localShortcut1 by fields::shortcut1
    var localShortcut2 by fields::shortcut2
    var localShortcut3 by fields::shortcut3
    var localSlotOrderStr by fields::slotOrderStr
    var localKeepSystemG by fields::keepSystemG
    // Material-only settings.
    var localActionIcon by fields::actionIcon

    // The custom colour exactly as the widget will draw it from the saved settings.
    val computedCustomColorInt = activeDraft.value.customColorInt
    var draggingSlotKey by ui::draggingSlotKey
    val view = androidx.compose.ui.platform.LocalView.current
    val hapticEngine = remember(context) { com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context) }
    val coroutineScope = rememberCoroutineScope()
    var selectedTabName by ui::selectedTabName

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
                title = { Text("Widget Customization", style = MaterialTheme.typography.titleLarge, maxLines = 1, autoSize = oneLineAutoSize(MaterialTheme.typography.titleLarge)) },
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
                                val isMaterial = !isSystem
                                coroutineScope.launch {
                                    // The launcher's dialog previews the widget as just saved.
                                    val requested = com.pixel.intelligentsearch.feature.widget.PinWidgetReceiver
                                        .requestPinWidget(context, isMaterial = isMaterial)
                                    if (!requested) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Your launcher doesn't support adding widgets from apps.",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
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
            // Live Preview Card (Pinned at the top for real-time visual feedback), in the page's own colour, so the
            // widget sits on what a Home screen would show behind it, with only the matte grain added.
            val previewShellLook = com.pixel.intelligentsearch.core.ui.expressive.rememberMatteShellLook(
                containerColor = MaterialTheme.colorScheme.background,
                border = null
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    // Taller when the favourite apps or media row shows above the bar.
                    .heightIn(min = 150.dp)
                    .matteShell(previewShellLook, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                    val previewIsMaterialYou = localThemeStyle == "Material You (Minimal)" || localThemeStyle == "Material Design"
                    val isCustomTheme = localSubtheme == "Custom"
                    // The wave behind the preview takes the custom colour as picked, opacity and all.
                    val waveColor = if (isCustomTheme) {
                        androidx.compose.ui.graphics.Color(computedCustomColorInt)
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

                    val effectiveGIconTheme = localMaterialGIconTheme
                    // Worked out by the widget's own rules, so the preview shows what the widget will draw.
                    val previewColors = widgetPreviewColors(
                        draft = activeDraft.value,
                        isMaterialDesign = previewIsMaterialYou,
                        theme = PreviewTheme(
                            primary = MaterialTheme.colorScheme.primary,
                            primaryContainer = MaterialTheme.colorScheme.primaryContainer,
                            surfaceContainer = MaterialTheme.colorScheme.surfaceContainer,
                            appDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
                            systemDark = androidx.compose.foundation.isSystemInDarkTheme()
                        ),
                        isDynamicColorActive = isDynamicActive,
                        iconAccents = { isLight -> SearchIconColors.themeAccents(context, isLight) }
                    ) { isDark -> DynamicThemeDetector.resolveWidgetColors(context, isDark) }
                    val pill = previewColors.pill
                    val isPreviewPillLight = pill.isLight

                    val rimBrush = if (previewIsMaterialYou && isCustomTheme) {
                        androidx.compose.ui.graphics.SolidColor(previewColors.rim)
                    } else {
                        androidx.compose.ui.graphics.Brush.linearGradient(listOf(previewColors.rim, previewColors.rim))
                    }

                    val previewPillColorAlpha = pill.fill

                    val gPrimary = previewColors.accentPrimary
                    // The Material G style's icons are the search overlay's, worked out from these.
                    val mPrimary = androidx.compose.ui.graphics.Color(previewColors.materialIconAccents.primary)
                    val mSecondary = androidx.compose.ui.graphics.Color(previewColors.materialIconAccents.secondary)
                    val mTertiary = androidx.compose.ui.graphics.Color(previewColors.materialIconAccents.tertiary)

                    // The bar's outline and corners, as the widget draws them.
                    val barShape = com.pixel.intelligentsearch.feature.widget.WidgetBarShape.of(activeDraft.value, previewIsMaterialYou)
                    val draft = activeDraft.value
                    Column(
                        modifier = Modifier.padding(vertical = if (draft.favouriteApps.isNotEmpty() || draft.showMedia) 20.dp else 0.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                    if (draft.favouriteApps.isNotEmpty()) {
                        WidgetPreviewFavourites(
                            apps = draft.favouriteApps,
                            accent = gPrimary,
                            plate = previewPillColorAlpha,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    if (draft.showMedia) {
                        WidgetPreviewMedia(
                            shape = barShape,
                            fill = previewPillColorAlpha,
                            titleColor = previewColors.systemIconTint,
                            accent = gPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .then(
                                if (barShape.outline > 0) Modifier.background(rimBrush, RoundedCornerShape(barShape.outerRadius.dp)) else Modifier
                            )
                            .padding(barShape.outline.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(barShape.pillHeight.dp)
                                .then(
                                    Modifier.background(previewPillColorAlpha, RoundedCornerShape(barShape.pillRadius.dp))
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            if (localShowGIcon) {
                                val isAccented = effectiveGIconTheme == "Accented G Icon"
                                val isCustom = effectiveGIconTheme == "Custom G Icon" && !localKeepSystemG
                                val useOriginalGIcon = effectiveGIconTheme == "System G Icon" || (effectiveGIconTheme == "Custom G Icon" && localKeepSystemG)
                                val customGColorObj = androidx.compose.ui.graphics.Color(previewColors.customIcons.gIcon)
                                ComposeGIcon(
                                    modifier = Modifier.size(24.dp),
                                    primaryColor = if (isCustom) customGColorObj else mPrimary,
                                    secondaryColor = if (isCustom) customGColorObj else mSecondary,
                                    tertiaryColor = if (isCustom) customGColorObj else mTertiary,
                                    isAccented = isAccented || isCustom,
                                    accentColor = if (isCustom) customGColorObj else gPrimary,
                                    fallbackTint = if (isCustom) customGColorObj else androidx.compose.ui.graphics.Color.Unspecified,
                                    useOriginalColors = useOriginalGIcon,
                                    isPillLight = isPreviewPillLight
                                )
                            }

                            Spacer(Modifier.weight(1f))
                            
                            val useMaterialYouIcons = previewColors.usesMaterialYouIcons
                            val slotOrder = localSlotOrderStr.split(",").filter { it.isNotBlank() }
                            val previewItemsList = mutableListOf<SlotPreviewItem>()
                            slotOrder.forEach { key ->
                                when (key) {
                                    "mic" -> {
                                        if (localShowVoice) {
                                            val micRes = if (useMaterialYouIcons) com.pixel.intelligentsearch.R.drawable.ic_mic else com.pixel.intelligentsearch.R.drawable.ic_mic_original
                                            previewItemsList.add(SlotPreviewItem("mic", "Voice Search", micRes))
                                        }
                                    }
                                    "shortcut1" -> {
                                        if (localShortcut1 != "None") {
                                            previewItemsList.add(SlotPreviewItem("shortcut1", localShortcut1, getShortcutIconRes(localShortcut1, useMaterialYouIcons)))
                                        }
                                    }
                                    "shortcut2" -> {
                                        if (localShortcut2 != "None") {
                                            previewItemsList.add(SlotPreviewItem("shortcut2", localShortcut2, getShortcutIconRes(localShortcut2, useMaterialYouIcons)))
                                        }
                                    }
                                    "shortcut3" -> {
                                        if (localShortcut3 != "None") {
                                            previewItemsList.add(SlotPreviewItem("shortcut3", localShortcut3, getShortcutIconRes(localShortcut3, useMaterialYouIcons)))
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
                                    val icons = previewColors.customIcons
                                    val scColor = when (item.slotKey) {
                                        "mic" -> icons.mic
                                        "shortcut1" -> icons.shortcut1
                                        "shortcut2" -> icons.shortcut2
                                        "shortcut3" -> icons.shortcut3
                                        else -> icons.gIcon
                                    }
                                    val customScColorObj = androidx.compose.ui.graphics.Color(scColor)
                                    arrayOf(customScColorObj, customScColorObj, customScColorObj, customScColorObj)
                                } else when (effectiveGIconTheme) {
                                    "Accented G Icon" -> arrayOf(gPrimary, gPrimary, gPrimary, gPrimary)
                                    "Material G Icon" -> arrayOf(mPrimary, mSecondary, mTertiary, mPrimary)
                                    else -> { // System G Icon
                                        val sysTint = previewColors.systemIconTint
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
                                    .background(
                                        previewPillColorAlpha,
                                        if (barShape.circleIsRound) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(barShape.circleRadius.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                val hasCustomAction = effectiveGIconTheme == "Custom G Icon"

                                val (actPrimary, actSecondary, actTertiary, actQuaternary) = if (hasCustomAction) {
                                    val customActColorObj = androidx.compose.ui.graphics.Color(previewColors.customIcons.action)
                                    arrayOf(customActColorObj, customActColorObj, customActColorObj, customActColorObj)
                                } else when (effectiveGIconTheme) {
                                    "Accented G Icon" -> arrayOf(gPrimary, gPrimary, gPrimary, gPrimary)
                                    "Material G Icon" -> arrayOf(mPrimary, mSecondary, mTertiary, mPrimary)
                                    else -> { // System G Icon
                                        val sysActionTint = previewColors.systemIconTint
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
            }

            // System vs Material Design Switcher, in a settings card as it always was.
            SettingsCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                lit = false
            ) {
                ShellSelector(
                    options = listOf("System Default", "Material You (Minimal)"),
                    selected = if (isSystem) "System Default" else "Material You (Minimal)",
                    // Only shows the other design; its own settings, like the Material design's Inner Pill & Circle
                    // State, stay as the user left them.
                    onSelect = { localThemeStyle = it },
                    itemHeight = 44.dp,
                    indicatorShape = RoundedCornerShape(24.dp)
                ) { option, isSel ->
                    Text(
                        if (option == "System Default") "System Design" else "Material Design",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        autoSize = oneLineAutoSize(MaterialTheme.typography.labelLarge),
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            // Material 3 Expressive Segmented Tab Bar
            val isCustomActive = localSubtheme == "Custom"
            // Each tab's outlined icon, and the filled one it shows while selected.
            val tabs = remember(isCustomActive) {
                listOfNotNull(
                    Triple("Appearance", Icons.Outlined.Palette, Icons.Filled.Palette),
                    if (isCustomActive) Triple("Color Studio", Icons.Outlined.Tune, Icons.Filled.Tune) else null,
                    Triple("Widget Shortcuts", Icons.Outlined.Widgets, Icons.Filled.Widgets)
                )
            }

            // The tab is kept by name, so a design without the open tab (Color Studio needs the Custom subtheme)
            // falls back to Appearance and the other tabs stay put.
            val activeTabName = selectedTabName.takeIf { name -> tabs.any { it.first == name } } ?: "Appearance"
            // Remember the fallback, so a tab that comes back later (picking Custom again) doesn't reopen on its own.
            LaunchedEffect(activeTabName) {
                if (selectedTabName != activeTabName) selectedTabName = activeTabName
            }

            ShellSelector(
                options = tabs,
                selected = tabs.first { it.first == activeTabName },
                onSelect = { selectedTabName = it.first },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                itemHeight = 44.dp,
                role = androidx.compose.ui.semantics.Role.Tab,
                // The tab bar's own track, with the matte grain added.
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(28.dp),
                indicatorShape = RoundedCornerShape(24.dp)
            ) { tab, isSel ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isSel) tab.third else tab.second,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tab.first,
                        autoSize = oneLineAutoSize(MaterialTheme.typography.labelMedium),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
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
                when (activeTabName) {
                    "Appearance" -> WidgetAppearanceTab(fields = fields, ui = ui, colors = colors, themeStyle = themeStyleState, computedCustomColorInt = computedCustomColorInt, view = view, hapticEngine = hapticEngine, savedThemes = savedThemes)

                    "Color Studio" -> WidgetColorStudioTab(fields = fields, ui = ui, computedCustomColorInt = computedCustomColorInt)

                    "Widget Shortcuts" -> WidgetShortcutsTab(fields = fields, ui = ui, view = view, hapticEngine = hapticEngine, coroutineScope = coroutineScope, density = density, shortcutOptions = shortcutOptions, themeStyle = themeStyleState)
                }
            }
        }

            WidgetEditorDialogs(fields = fields, ui = ui, colors = colors, shortcutOptions = shortcutOptions, prefs = prefs)
        }
    }
}

