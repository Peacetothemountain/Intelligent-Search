package com.pixel.intelligentsearch.core.data.widget

import android.content.SharedPreferences
import android.graphics.Color
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.BooleanSetting
import com.pixel.intelligentsearch.core.data.settings.IntSetting
import com.pixel.intelligentsearch.core.data.settings.StringSetting
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper

/** The two widget designs, each with its own saved customization under its own key prefix. */
enum class WidgetDesign(val prefix: String, val themeStyle: String) {
    SYSTEM("widget_system", "System Default"),
    MATERIAL("widget_material", "Material You (Minimal)");

    val savedFlagKey: String get() = "${prefix}_design_saved"

    /** The design's favourite apps row, media row, outline and corner settings. */
    val favouriteAppsKey: StringSetting
        get() = if (this == SYSTEM) SettingsManager.WIDGET_SYSTEM_FAVOURITE_APPS else SettingsManager.WIDGET_MATERIAL_FAVOURITE_APPS
    val showMediaKey: BooleanSetting
        get() = if (this == SYSTEM) SettingsManager.WIDGET_SYSTEM_SHOW_MEDIA else SettingsManager.WIDGET_MATERIAL_SHOW_MEDIA
    val outlineThicknessKey: IntSetting
        get() = if (this == SYSTEM) SettingsManager.WIDGET_SYSTEM_OUTLINE_THICKNESS else SettingsManager.WIDGET_MATERIAL_OUTLINE_THICKNESS
    val cornerRadiusKey: IntSetting
        get() = if (this == SYSTEM) SettingsManager.WIDGET_SYSTEM_CORNER_RADIUS else SettingsManager.WIDGET_MATERIAL_CORNER_RADIUS
}

/** Custom colours for each widget element (used by the "Custom" G icon style). */
data class WidgetElementColors(
    val gIcon: Int,
    val mic: Int,
    val shortcut1: Int,
    val shortcut2: Int,
    val shortcut3: Int,
    val action: Int
)

/**
 * Everything the user customizes for one widget design. Hue is 0..360, saturation, lightness, colour opacity
 * and background transparency are 0..100 (what the sliders show). [lockBlack] and [actionIcon] only apply to
 * the Material design.
 */
data class WidgetDesignSettings(
    val subtheme: String,
    val gIconTheme: String,
    val elementColors: WidgetElementColors,
    val syncColors: Boolean,
    val keepSystemG: Boolean,
    val hue: Float,
    val saturation: Float,
    val lightness: Float,
    val colorOpacity: Float,
    val transparency: Float,
    val showGIcon: Boolean,
    val showVoice: Boolean,
    val shortcut1: String,
    val shortcut2: String,
    val shortcut3: String,
    val slotOrder: String,
    val lockBlack: Boolean,
    val actionIcon: String,
    /** Whether the G gets the day's animated doodle on holidays and the first day of each season (only while it's shown). */
    val showDoodles: Boolean = true,
    /** Up to [MAX_FAVOURITE_APPS] apps, by package name, shown in a row above the bar when the widget is tall enough. */
    val favouriteApps: List<String> = emptyList(),
    /** Whether a row above the bar shows what's playing, with play/pause and next, when the widget is tall enough. */
    val showMedia: Boolean = false,
    /** The bar's outline in dp (the Material design's rim), 0 to [MAX_OUTLINE_THICKNESS]. */
    val outlineThickness: Int = 0,
    /** The bar's outer corner radius in dp, 0 to [MAX_CORNER_RADIUS]; drawn at most half the bar's height. */
    val cornerRadius: Int = 28
) {
    /**
     * The custom pill colour these settings produce (what the widget draws for the "Custom" subtheme): the HCT seed
     * Hue, Saturation and Lightness pick ([MaterialYouPaletteHelper.seedColor]), as opaque as Color Opacity says.
     */
    val customColorInt: Int
        get() = (MaterialYouPaletteHelper.seedColor(hue, saturation, lightness) and 0xFFFFFF) or
            ((colorOpacity / 100f * 255).toInt().coerceIn(0, 255) shl 24)
}

/**
 * Values that depend on the device rather than on stored settings: the theme's primary colour (default for
 * custom element colours, hue and saturation), and whether dynamic colour is active (default G icon style).
 */
data class WidgetDefaults(val primaryColor: Int, val isDynamicColorActive: Boolean) {
    private val hsv = FloatArray(3).also { Color.colorToHSV(primaryColor, it) }
    val hue: Int get() = hsv[0].toInt()
    val saturation: Int get() = (hsv[1] * 100f).toInt()
}

/**
 * Reads and writes widget design settings. The single place that knows the per-design key names and the
 * fallbacks to the keys older versions shared between designs ("widget_custom_hue", "widget_shortcut_1", ...),
 * so the settings screen and the widget renderer can't disagree.
 */
class WidgetSettingsStore(private val prefs: SharedPreferences) {

    /**
     * Whether the stored values for [design] are the user's choices: saved by this version's editor, or left
     * behind by an older build, which wrote the design's own keys without the saved flag (and, for the System
     * design, the shared custom hue). Until then the design shows its defaults.
     */
    fun isSaved(design: WidgetDesign): Boolean {
        val p = design.prefix
        return prefs.getBoolean(design.savedFlagKey, false) ||
            prefs.contains("${p}_subtheme") ||
            prefs.contains("${p}_custom_color_int") ||
            prefs.contains(if (design == WidgetDesign.SYSTEM) "widget_custom_hue" else "${p}_lock_black")
    }

    fun load(design: WidgetDesign, defaults: WidgetDefaults): WidgetDesignSettings {
        val p = design.prefix
        val saved = isSaved(design)
        val isSystem = design == WidgetDesign.SYSTEM

        fun string(key: String, legacy: String, default: String): String =
            prefs.getString("${p}_$key", if (saved) prefs.getString(legacy, default) else default) ?: default
        fun int(key: String, legacy: String, default: Int): Int =
            prefs.getInt("${p}_$key", if (saved) prefs.getInt(legacy, default) else default)
        // Element colours: per-design key, else the old shared key, else the fallback. The old builds wrote
        // Google blue as a placeholder; an unsaved placeholder means "use the fallback". For the slots the
        // fallback is the old single "all shortcuts" colour when one was stored, else the theme colour.
        fun color(key: String, fallback: Int = defaults.primaryColor): Int {
            val perDesign = "${p}_custom_${key}_color"
            val value = prefs.getInt(perDesign, prefs.getInt("widget_custom_${key}_color", fallback))
            return if (value == GOOGLE_BLUE_PLACEHOLDER && !prefs.contains(perDesign)) fallback else value
        }
        val slotFallback = when {
            prefs.contains("${p}_custom_shortcuts_color") -> prefs.getInt("${p}_custom_shortcuts_color", defaults.primaryColor)
            prefs.contains("widget_custom_shortcuts_color") -> prefs.getInt("widget_custom_shortcuts_color", defaults.primaryColor)
            else -> defaults.primaryColor
        }
        // Hue 277 / saturation 51 were the old hard-coded defaults; when not saved for this design, use the theme's.
        fun themed(key: String, legacy: String, oldDefault: Int, themeDefault: Int): Int {
            val value = int(key, legacy, themeDefault)
            return if (value == oldDefault && !prefs.contains("${p}_$key")) themeDefault else value
        }
        fun shortcut(n: Int, default: String): String =
            string("shortcut_$n", "widget_shortcut_$n", default).let { if (it == "Voice Search") "None" else it }

        // Hue, Saturation and Lightness pick an HCT seed. Versions before the seed's flag read them as HSV, so values
        // they left (or none, for the defaults they showed) are read as the colour they showed, in the whole numbers
        // the sliders hold.
        val hue = themed("custom_hue", "widget_custom_hue", 277, defaults.hue)
        val saturation = themed("custom_saturation", "widget_custom_saturation", 51, defaults.saturation)
        val lightness = int("custom_lightness", "widget_custom_lightness", 100)
        val seed = if (prefs.getBoolean(hctFlagKey(design), false)) {
            MaterialYouPaletteHelper.SeedSliders(hue.toFloat(), saturation.toFloat(), lightness.toFloat())
        } else {
            val shown = Color.HSVToColor(floatArrayOf(hue.toFloat(), (saturation / 100f).coerceIn(0f, 1f), (lightness / 100f).coerceIn(0f, 1f)))
            MaterialYouPaletteHelper.wholeSeedSliders(shown, greyHue = hue.toFloat())
        }

        val defaultGIcon = if (isSystem) {
            if (defaults.isDynamicColorActive) "Material G Icon" else "System G Icon"
        } else "Material G Icon"

        return WidgetDesignSettings(
            subtheme = string("subtheme", "widget_subtheme", if (isSystem) "System" else "Material"),
            gIconTheme = string("material_g_icon", "widget_material_g_icon", defaultGIcon),
            elementColors = WidgetElementColors(
                gIcon = color("g_icon"),
                mic = color("mic", slotFallback),
                shortcut1 = color("shortcut1", slotFallback),
                shortcut2 = color("shortcut2", slotFallback),
                shortcut3 = color("shortcut3", slotFallback),
                action = color("action_icon")
            ),
            syncColors = prefs.getBoolean("${p}_custom_sync_colors", prefs.getBoolean("widget_custom_sync_colors", false)),
            keepSystemG = prefs.getBoolean("${p}_custom_keep_system_g", prefs.getBoolean("widget_custom_keep_system_g", false)),
            hue = seed.hue,
            saturation = seed.saturation,
            lightness = seed.lightness,
            colorOpacity = int("custom_color_opacity", "widget_custom_color_opacity", 100).toFloat(),
            transparency = int("background_transparency", "widget.background.transparency", if (isSystem) 0 else 28).toFloat(),
            showGIcon = showsGIcon(design),
            showVoice = if (saved) prefs.getBoolean("${p}_show_voice", prefs[SettingsManager.WIDGET_SHOW_VOICE]) else true,
            shortcut1 = shortcut(1, "Google Lens"),
            shortcut2 = shortcut(2, "None"),
            shortcut3 = shortcut(3, "None"),
            slotOrder = string("shortcut_order", "widget_shortcut_order", DEFAULT_SLOT_ORDER),
            lockBlack = prefs.getBoolean("${p}_lock_black", true),
            actionIcon = string("action_icon", "widget_action_icon", "Search"),
            showDoodles = prefs.getBoolean("${p}_show_doodles", true),
            favouriteApps = favouriteApps(prefs[design.favouriteAppsKey]),
            showMedia = prefs[design.showMediaKey],
            outlineThickness = prefs[design.outlineThicknessKey],
            cornerRadius = prefs[design.cornerRadiusKey]
        )
    }

    private fun showsGIcon(design: WidgetDesign): Boolean =
        if (isSaved(design)) prefs.getBoolean("${design.prefix}_show_g_icon", prefs.getBoolean("widget_show_g_icon", true)) else true

    /** Whether [design] draws the day's doodle with its G: the G is shown and doodles are on. */
    fun showsDoodle(design: WidgetDesign): Boolean =
        showsGIcon(design) && prefs.getBoolean("${design.prefix}_show_doodles", true)

    /** Writes [settings] for [design] into [editor] and marks the design saved; the caller applies the edit. */
    fun save(design: WidgetDesign, settings: WidgetDesignSettings, editor: SharedPreferences.Editor) {
        val p = design.prefix
        val c = settings.elementColors
        editor
            .putBoolean(design.savedFlagKey, true)
            .putString("${p}_subtheme", settings.subtheme)
            .putString("${p}_material_g_icon", settings.gIconTheme)
            .putInt("${p}_custom_g_icon_color", c.gIcon)
            .putInt("${p}_custom_mic_color", c.mic)
            .putInt("${p}_custom_shortcut1_color", c.shortcut1)
            .putInt("${p}_custom_shortcut2_color", c.shortcut2)
            .putInt("${p}_custom_shortcut3_color", c.shortcut3)
            .putInt("${p}_custom_action_icon_color", c.action)
            .putBoolean("${p}_custom_sync_colors", settings.syncColors)
            .putBoolean("${p}_custom_keep_system_g", settings.keepSystemG)
            .putBoolean(hctFlagKey(design), true)
            .putInt("${p}_custom_hue", settings.hue.toInt())
            .putInt("${p}_custom_saturation", settings.saturation.toInt())
            .putInt("${p}_custom_lightness", settings.lightness.toInt())
            .putInt("${p}_custom_color_opacity", settings.colorOpacity.toInt())
            .putInt("${p}_custom_color_int", settings.customColorInt)
            .put(design.favouriteAppsKey, settings.favouriteApps.distinct().take(MAX_FAVOURITE_APPS).joinToString(","))
            .put(design.showMediaKey, settings.showMedia)
            .put(design.outlineThicknessKey, settings.outlineThickness)
            .put(design.cornerRadiusKey, settings.cornerRadius)
            .putInt("${p}_background_transparency", settings.transparency.toInt())
            .putBoolean("${p}_show_g_icon", settings.showGIcon)
            .putBoolean("${p}_show_doodles", settings.showDoodles)
            .putBoolean("${p}_show_voice", settings.showVoice)
            .putString("${p}_shortcut_1", settings.shortcut1)
            .putString("${p}_shortcut_2", settings.shortcut2)
            .putString("${p}_shortcut_3", settings.shortcut3)
            .putString("${p}_shortcut_order", settings.slotOrder)
        if (design == WidgetDesign.MATERIAL) {
            editor
                .putBoolean("${p}_lock_black", settings.lockBlack)
                .putString("${p}_action_icon", settings.actionIcon)
        }
    }

    companion object {
        const val DEFAULT_SLOT_ORDER = "shortcut1,mic,shortcut2,shortcut3"

        /** The stored favourite apps: distinct package names, in order, at most [MAX_FAVOURITE_APPS]. */
        fun favouriteApps(stored: String): List<String> =
            stored.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_FAVOURITE_APPS)

        /** Set once [design]'s Hue, Saturation and Lightness are stored as an HCT seed rather than as HSV. */
        fun hctFlagKey(design: WidgetDesign): String = "${design.prefix}_custom_color_hct"
        private const val GOOGLE_BLUE_PLACEHOLDER = 0xFF4285F4.toInt()
    }
}

/** The most apps the favourite apps row holds. */
const val MAX_FAVOURITE_APPS = 5

/** The thickest outline the bar can have, in dp. */
const val MAX_OUTLINE_THICKNESS = 12

/** The largest corner radius that can be stored, in dp (the bar draws at most half its height). */
const val MAX_CORNER_RADIUS = 40
