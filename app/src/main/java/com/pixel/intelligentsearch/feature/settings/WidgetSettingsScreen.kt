package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.theme.isDark
import com.pixel.intelligentsearch.core.theme.pageContainer
import com.pixel.intelligentsearch.core.data.widget.WidgetDefaults
import com.pixel.intelligentsearch.core.theme.pageTrack
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import com.pixel.intelligentsearch.core.data.widget.WidgetDesignSettings
import com.pixel.intelligentsearch.core.data.widget.WidgetElementColors
import com.pixel.intelligentsearch.core.data.widget.WidgetSettingsStore
import com.pixel.intelligentsearch.core.data.widget.WidgetProfileDesign
import com.pixel.intelligentsearch.core.data.widget.WidgetProfiles
import com.pixel.intelligentsearch.feature.widget.WidgetColorRules
import com.pixel.intelligentsearch.feature.widget.getShortcutIconRes
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.data.settings.get
import android.content.SharedPreferences
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.DynamicThemeDetector
import com.pixel.intelligentsearch.core.theme.SearchIconColors
import com.pixel.intelligentsearch.core.ui.expressive.matteShell
import kotlinx.coroutines.launch

// WIDGET SETTINGS SCREEN
// -----------------------------------------------------------------------------------------

private data class SlotPreviewItem(val slotKey: String, val label: String, val resId: Int)

/**
 * The live preview's pill: its fill, and whether that fill is light, which picks the colors of the icons on it. The
 * fill is opaque, as the widget's is within its backdrop; the backdrop's own opacity is [WidgetPreviewColors.backdropAlpha].
 */
internal data class PreviewPill(val fill: Color, val isLight: Boolean)

/**
 * The preview pill of the design being edited. Whether a theme-colored pill is light or dark comes from
 * the color scheme this screen is drawn with, the same source as its fill, so the icons drawn on it keep their
 * contrast when the app's theme differs from the system's.
 */
@Composable
internal fun previewPill(
    isMaterialDesign: Boolean,
    subtheme: String,
    lockBlack: Boolean,
    accentColor: Color,
    isDynamicColorActive: Boolean
): PreviewPill {
    val appDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return previewPill(
        isMaterialDesign, subtheme, lockBlack, accentColor, isDynamicColorActive,
        themedSurface = MaterialTheme.colorScheme.surfaceContainer,
        appDark = appDark,
        lockedPill = Color(DynamicThemeDetector.resolveWidgetColors(LocalContext.current, appDark).lockedPill)
    )
}

/**
 * [previewPill] with the screen's theme passed in: [themedSurface] fills a theme-colored pill, and [lockedPill] the
 * Material design's locked one ([WidgetColorRules.lockedPill]). The fixed light and dark pills of the System subtheme
 * without dynamic color follow the app's theme ([appDark]), as the widget does: it draws in the app's theme, which the
 * system's dark theme only decides while the app follows the system.
 */
internal fun previewPill(
    isMaterialDesign: Boolean,
    subtheme: String,
    lockBlack: Boolean,
    accentColor: Color,
    isDynamicColorActive: Boolean,
    themedSurface: Color,
    appDark: Boolean,
    lockedPill: Color = Color(WidgetColorRules.LOCKED_PILL)
): PreviewPill {
    val themed = themedSurface.copy(alpha = 1f)
    val custom = accentColor.copy(alpha = 1f)
    val customIsLight = WidgetColorRules.iconsGoDark(accentColor.toArgb())
    val light = Color(0xFFF1F3F4)
    val dark = Color(0xFF303134)
    return if (isMaterialDesign) {
        when {
            lockBlack -> lockedPill.copy(alpha = 1f).let { PreviewPill(it, isLight = WidgetColorRules.iconsGoDark(it.toArgb())) }
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
                appDark -> PreviewPill(dark, isLight = false)
                else -> PreviewPill(light, isLight = true)
            }
        }
    }
}

/** The colors the preview is drawn with: this screen's scheme, and whether the app is dark. */
internal data class PreviewTheme(
    val primary: Color,
    val primaryContainer: Color,
    val surfaceContainer: Color,
    val appDark: Boolean
)

/** Everything the live preview paints for one design, worked out from its draft. */
internal data class WidgetPreviewColors(
    /**
     * How opaque the backdrop (the outline, the pill and the circle, drawn as one group under the icons) is, as the
     * widget fades its backdrop ([WidgetColorRules.backdropAlphas]).
     */
    val backdropAlpha: Float,
    /**
     * The outline around the pill, at its opacity within the backdrop: the Material design's rim, or the System
     * design's accent outline.
     */
    val rim: Color,
    /** The pill, whose fill the Material design's circle shares; opaque within the backdrop. */
    val pill: PreviewPill,
    /** The Accented G style's G, mic, shortcuts and circle icon. */
    val accentPrimary: Color,
    /** What the Material G style's icons are worked out from, the search overlay's way ([SearchIconColors]). */
    val materialIconAccents: SearchIconColors.Accents,
    /** What the System G style tints the shortcuts and the circle's icon. */
    val systemIconTint: Color,
    /** The Custom G style's colors as drawn: Sync All Colors applied, and darker where the widget darkens them. */
    val customIcons: WidgetElementColors,
    /** Whether the mic is the Material You drawing rather than the original one. */
    val usesMaterialYouIcons: Boolean
)

/**
 * What the live preview paints for [draft] on the [isMaterialDesign] design, drawn with [theme], as the widget draws
 * the same settings. [iconAccents] gives the search overlay's icon accents for a light (true) or dark bar
 * ([SearchIconColors.themeAccents]), and [widgetColors] the widget's own colors for a dark (true) or light surface.
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
    // The widget colors of the app's theme, which the locked pill with no color picked follows.
    val themeColors = widgetColors(theme.appDark)
    // Locked to a dark color, the bar draws as it always did locked black; to a light one, as a light pill.
    val lockDark = isMaterialDesign && WidgetColorRules.locksDark(draft, themeColors)
    val alphas = WidgetColorRules.backdropAlphas(draft, isMaterialDesign)
    // The outline's alpha as the widget's view takes it (whole steps of 1/255), so the preview is exactly as see-through.
    val rimAlpha = WidgetColorRules.alphaInt(alphas.rim) / 255f
    val accentColor = if (isCustomTheme) Color(draft.customColorInt) else theme.primary

    val materialRim = if (isMaterialDesign) {
        // Locked, the widget rims its pill in the colors of the theme that suits it (dark for a dark pill, light
        // for a light one), whatever the app's theme.
        val lockedDarkTheme = WidgetColorRules.locksDark(draft, themeColors)
        val (rimPrimary, rimContainer) = if (lockBlack && lockedDarkTheme != theme.appDark) {
            widgetColors(lockedDarkTheme).let { Color(it.primary) to Color(it.primaryContainer) }
        } else {
            theme.primary to theme.primaryContainer
        }
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
        isDynamicColorActive = isDynamicColorActive,
        themedSurface = theme.surfaceContainer,
        appDark = theme.appDark,
        lockedPill = Color(WidgetColorRules.lockedPill(draft, themeColors))
    )
    val isPillLight = pill.isLight

    val isDarkSurface = if (isMaterialDesign) lockDark || !isPillLight else !isPillLight
    val surfaceColors = widgetColors(isDarkSurface)
    // The Custom subtheme's accent and plain icons come from its seed, already in contrast with the pill.
    val customBar = if (isCustomTheme) WidgetColorRules.customBarColors(draft, isMaterialDesign, themeColors) else null

    val accentPrimary = customBar?.let { Color(it.accent) }
        ?: Color(WidgetColorRules.iconColor(surfaceColors.primary, isMaterialDesign, lockDark, isPillLight, 32.0))
    // The System design's optional outline is its accent, as see-through as its pill: the backdrop fades both.
    val rim = materialRim ?: accentPrimary.copy(alpha = rimAlpha)

    val systemIconTint = when {
        customBar != null -> Color(customBar.plainIcon)
        isPillLight && !lockDark -> Color(surfaceColors.onSurface)
        else -> Color.White
    }
    return WidgetPreviewColors(
        backdropAlpha = alphas.backdrop,
        rim = rim,
        pill = pill,
        accentPrimary = accentPrimary,
        materialIconAccents = WidgetColorRules.materialIconAccents(draft, isPillLight, iconAccents),
        systemIconTint = systemIconTint,
        customIcons = WidgetColorRules.customIconColors(draft, isMaterialDesign, isPillLight, themeColors),
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

    // The custom color exactly as the widget will draw it from the saved settings.
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

    // The widget's design profiles, as Application Search's pill profiles work. The profile on the widget is the one
    // last saved; picking another puts its design in the editor, and Save puts it on the widget.
    val profiles = remember(prefs) { WidgetProfiles(prefs, WidgetDefaults(defaultUserMaterialColor, isDynamicActive)) }
    var widgetProfile by remember { mutableIntStateOf(profiles.activeProfile) }
    var activeProfile by remember { mutableIntStateOf(widgetProfile) }
    // Profiles left with changes during this visit: Save keeps them, leaving without saving discards them.
    val leftProfiles = remember { mutableStateMapOf<Int, WidgetProfileDesign>() }
    fun designOf(style: String) = if (style == "System Default") WidgetDesign.SYSTEM else WidgetDesign.MATERIAL
    fun editorDesign() = WidgetProfileDesign(designOf(localThemeStyle), systemDraft.value, materialDraft.value)
    fun widgetDesign() = WidgetProfileDesign(designOf(savedThemeStyle), savedSystem, savedMaterial)
    fun loadDesign(design: WidgetProfileDesign) {
        localThemeStyle = design.design.themeStyle
        systemDraft.value = design.system
        materialDraft.value = design.material
    }
    fun switchProfile(id: Int) {
        if (id == activeProfile) return
        val leaving = editorDesign()
        if (leaving == profiles.design(activeProfile, widgetDesign())) leftProfiles.remove(activeProfile) else leftProfiles[activeProfile] = leaving
        loadDesign(leftProfiles.remove(id) ?: profiles.design(id, widgetDesign()))
        activeProfile = id
    }
    LaunchedEffect(profiles) { profiles.ensureDefaultSaved(widgetDesign()) }

    val hasUnsavedChanges by remember {
        derivedStateOf {
            localThemeStyle != savedThemeStyle ||
                systemDraft.value != savedSystem ||
                materialDraft.value != savedMaterial ||
                activeProfile != widgetProfile ||
                leftProfiles.isNotEmpty() ||
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
        // design the user never touched. Element colors are stored per design only; the old shared
        // "widget_custom_*" keys stay as the fallback for a design that has never been saved.
        val system = systemDraft.value
        val material = materialDraft.value
        if (isSystem || system != savedSystem) widgetStore.save(WidgetDesign.SYSTEM, system, editor)
        if (!isSystem || material != savedMaterial) widgetStore.save(WidgetDesign.MATERIAL, material, editor)

        // The profile being edited goes on the widget; the one it replaces, and any left with changes, keep theirs.
        profiles.commit(editor, activeProfile, leftProfiles.toMap(), previousLive = widgetDesign())

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
        widgetProfile = activeProfile
        leftProfiles.clear()
    }

    // Adding the widget from here: the launcher previews, and places, the saved design, so the design in the preview
    // is saved first. With unsaved changes the user is asked ([SaveBeforePinDialog]) rather than saved silently.
    var showSaveBeforePinDialog by remember { mutableStateOf(false) }
    val pinWidget: () -> Unit = {
        val isMaterial = !isSystem
        coroutineScope.launch {
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
    if (showSaveBeforePinDialog) {
        SaveBeforePinDialog(
            onSaveAndAdd = {
                showSaveBeforePinDialog = false
                saveSettings()
                pinWidget()
            },
            onDismiss = { showSaveBeforePinDialog = false }
        )
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
        WarningCard(
            title = "Discard unsaved changes?",
            message = "You have unsaved customizations. If you leave now, none of your changes will be applied.",
            acceptLabel = "Save",
            onAccept = {
                showUnsavedChangesDialog = false
                saveSettings()
                onBack()
            },
            onDiscard = {
                showUnsavedChangesDialog = false
                onBack()
            },
            onDontShowAgain = {
                prefs.edit().putBoolean("widget_suppress_unsaved_warning", true).apply()
                showUnsavedChangesDialog = false
                onBack()
            },
            onDismissRequest = { showUnsavedChangesDialog = false }
        )
    }

    Scaffold(containerColor = Color.Transparent, topBar = {
            TopAppBar(
                title = { SettingsPageTitle("Widget Customization", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Normal) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = handleBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    // As on Application Search: Reset puts the profile's saved state in the editor (Save puts it on
                    // the widget), and the save button records the editor's design as that saved state.
                    ProfileStateActions(
                        profile = activeProfile,
                        onReset = { loadDesign(profiles.savedState(activeProfile, widgetDesign())) },
                        onSave = { profiles.saveState(activeProfile, editorDesign()) }
                    )
                    val canPinWidget = remember(context) {
                        android.appwidget.AppWidgetManager.getInstance(context)?.isRequestPinAppWidgetSupported == true
                    }
                    if (canPinWidget) {
                        IconButton(
                            onClick = {
                                // The launcher previews the saved design: changes in the preview are saved first.
                                if (hasUnsavedChanges) showSaveBeforePinDialog = true else pinWidget()
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
            // Live Preview Card (Pinned at the top for real-time visual feedback), on the preview stage (the page's own
            // color in dark, the lightest surface in light), with only the matte grain added.
            val previewShellLook = com.pixel.intelligentsearch.core.ui.expressive.rememberMatteShellLook(
                containerColor = widgetPreviewStageColor(),
                border = null
            )
            val previewIsMaterialYou = localThemeStyle == "Material You (Minimal)" || localThemeStyle == "Material Design"
            // Worked out by the widget's own rules, so the preview shows what the widget will draw.
            val previewColors = widgetPreviewColors(
                draft = activeDraft.value,
                isMaterialDesign = previewIsMaterialYou,
                theme = PreviewTheme(
                    primary = MaterialTheme.colorScheme.primary,
                    primaryContainer = MaterialTheme.colorScheme.primaryContainer,
                    surfaceContainer = MaterialTheme.colorScheme.surfaceContainer,
                    appDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                ),
                isDynamicColorActive = isDynamicActive,
                iconAccents = { isLight -> SearchIconColors.themeAccents(context, isLight) }
            ) { isDark -> DynamicThemeDetector.resolveWidgetColors(context, isDark) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .height(150.dp)
                    .matteShell(previewShellLook, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                    val isCustomTheme = localSubtheme == "Custom"
                    // The wave behind the preview takes the custom color as picked, opacity and all.
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
                    val isPreviewPillLight = previewColors.pill.isLight

                    val gPrimary = previewColors.accentPrimary
                    // The Material G style's icons are the search overlay's, worked out from these.
                    val mPrimary = androidx.compose.ui.graphics.Color(previewColors.materialIconAccents.primary)
                    val mSecondary = androidx.compose.ui.graphics.Color(previewColors.materialIconAccents.secondary)
                    val mTertiary = androidx.compose.ui.graphics.Color(previewColors.materialIconAccents.tertiary)

                    // The bar's outline and corners, as the widget draws them.
                    val barShape = com.pixel.intelligentsearch.feature.widget.WidgetBarShape.of(activeDraft.value, previewIsMaterialYou)
                    val showsCircle = localActionIcon != "None" && previewIsMaterialYou
                    // As the widget: the backgrounds are one backdrop behind the icons, faded at once, and the icons
                    // over it are laid out on the same lines without backgrounds of their own.
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    WidgetPreviewBackdrop(
                        colors = previewColors,
                        shape = barShape,
                        showsCircle = showsCircle,
                        modifier = Modifier.matchParentSize()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(barShape.outline.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(barShape.pillHeight.dp)
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
                                    isMaterialTheme = isMaterial,
                                    modifier = Modifier.size(24.dp),
                                    isPillLight = isPreviewPillLight
                                )
                            }
                        }
                        
                        if (showsCircle) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier.size(48.dp),
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

            // Widget Profiles: five whole designs to switch between, as on Application Search.
            ProfileSelector(
                count = WidgetProfiles.PROFILE_COUNT,
                selected = activeProfile,
                onSelect = { id -> switchProfile(id) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

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
            // Color Studio holds the search bar's palette (Custom subtheme) and, on the Material design, the Inner
            // Pill & Circle's: it shows whenever either applies.
            val hasColorStudio = isCustomActive || previewIsMaterialYou
            // Each tab's outlined icon, and the filled one it shows while selected.
            val tabs = remember(hasColorStudio) {
                listOfNotNull(
                    Triple("Appearance", Icons.Outlined.Palette, Icons.Filled.Palette),
                    if (hasColorStudio) Triple("Color Studio", Icons.Outlined.Tune, Icons.Filled.Tune) else null,
                    Triple("Shortcuts", Icons.Outlined.Widgets, Icons.Filled.Widgets)
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
                containerColor = MaterialTheme.colorScheme.pageTrack(0.5f),
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
                    "Appearance" -> WidgetAppearanceTab(
                        fields = fields, ui = ui, colors = colors, themeStyle = themeStyleState, computedCustomColorInt = computedCustomColorInt,
                        view = view, hapticEngine = hapticEngine
                    )

                    "Color Studio" -> {
                        // The pill as the bar draws it at Transparency 0, for the Transparency track.
                        val opaqueBackdrop = WidgetColorRules.backdropAlphas(activeDraft.value.copy(transparency = 0f), previewIsMaterialYou).backdrop
                        WidgetColorStudioTab(
                            fields = fields, ui = ui, computedCustomColorInt = computedCustomColorInt,
                            isMaterialDesign = previewIsMaterialYou,
                            barBackground = previewColors.pill.fill.copy(alpha = previewColors.pill.fill.alpha * opaqueBackdrop)
                        )
                    }

                    "Shortcuts" -> WidgetShortcutsTab(fields = fields, ui = ui, view = view, hapticEngine = hapticEngine, coroutineScope = coroutineScope, density = density, shortcutOptions = shortcutOptions, themeStyle = themeStyleState)
                }
            }
        }

            WidgetEditorDialogs(fields = fields, ui = ui, colors = colors, shortcutOptions = shortcutOptions, prefs = prefs)
        }
    }
}

/**
 * The live preview's backdrop, drawn as the widget draws its own: the outline (the Material design's rim) filling the
 * whole bar, and the pill and, when [showsCircle], the circle over it, laid out on the same lines as the icons drawn
 * over them. They are drawn as one group faded once by [WidgetPreviewColors.backdropAlpha], so where the pill and
 * circle sit over the outline they are neither painted twice nor tinted by it.
 */
@Composable
internal fun WidgetPreviewBackdrop(
    colors: WidgetPreviewColors,
    shape: com.pixel.intelligentsearch.feature.widget.WidgetBarShape,
    showsCircle: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .graphicsLayer {
                alpha = colors.backdropAlpha
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .then(if (shape.outline > 0) Modifier.background(colors.rim, RoundedCornerShape(shape.outerRadius.dp)) else Modifier)
            .padding(shape.outline.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(colors.pill.fill, RoundedCornerShape(shape.pillRadius.dp))
        )
        if (showsCircle) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(com.pixel.intelligentsearch.feature.widget.WidgetBarShape.CIRCLE_SIZE.dp)
                    .background(colors.pill.fill, if (shape.circleIsRound) CircleShape else RoundedCornerShape(shape.circleRadius.dp))
            )
        }
    }
}

/**
 * Asked when the widget is added to the Home screen with unsaved changes: the launcher's dialog previews, and places,
 * the saved design, so the design in the preview has to be saved first for the three to match.
 */
@Composable
internal fun SaveBeforePinDialog(onSaveAndAdd: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save and Add to Home Screen?") },
        text = {
            Text(
                "The widget is added with the design in the preview, so your unsaved changes are saved first. " +
                    "Search bar widgets already on your Home screen change to it too."
            )
        },
        confirmButton = { TextButton(onClick = onSaveAndAdd) { Text("Save and Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * What a widget preview sits on. In dark, the page's own color, as a dark home screen shows. In light the page is the
 * same tint as the System design's default bar, which would vanish into it, so the preview sits on the lightest surface
 * (the settings cards' white), where that bar shows as the soft gray pill it is on a light wallpaper.
 */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun widgetPreviewStageColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.isDark) scheme.background else scheme.pageContainer
}
