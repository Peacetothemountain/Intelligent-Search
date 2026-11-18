package com.pixel.intelligentsearch.feature.tabs.gallery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import android.content.Context
import android.content.SharedPreferences
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.data.widget.WidgetDefaults
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import com.pixel.intelligentsearch.core.data.widget.WidgetDesignSettings
import com.pixel.intelligentsearch.core.data.widget.WidgetSettingsStore
import com.pixel.intelligentsearch.core.theme.DynamicThemeDetector
import com.pixel.intelligentsearch.core.theme.ThemeMode

/**
 * Where Gallery meets the app's real settings. A look is both widget designs' style plus the search page's
 * appearance, and every field of it is one of the app's own settings:
 *
 * | Look field                  | Setting                                                    |
 * |-----------------------------|------------------------------------------------------------|
 * | hero design                 | `widget.theme.style` (the Search Box bar's design)          |
 * | subtheme                    | `widget_<design>_subtheme`                                 |
 * | gIcon                       | `widget_<design>_material_g_icon` (null keeps the person's) |
 * | hue, saturation, lightness  | `widget_<design>_custom_hue / _saturation / _lightness`    |
 * | colorOpacity                | `widget_<design>_custom_color_opacity`                     |
 * | transparency                | `widget_<design>_background_transparency`                  |
 * | outline                     | `widget_<design>_outline_thickness`                        |
 * | corner                      | `widget_<design>_corner_radius`                            |
 * | lockBlack, lockedPill       | `widget_material_lock_black`, `widget_material_lock_pill_color` |
 * | search.theme                | `night.mode` (Keep leaves it)                              |
 * | search.wallpaper            | `search.background.show.wall`                              |
 * | search.blur                 | `search.background.blur`                                   |
 * | search.transparency         | `search.background.transparency`                           |
 * | search.pillOpacity          | `search.pill.opacity`                                      |
 * | search.stars                | `matrix_animation_enabled`                                 |
 * | search.clock                | `glance.clock_style` (null keeps the person's)             |
 *
 * A look is appearance only: the person's buttons (the G and voice switches, shortcuts and their order, the circle's
 * action), doodles, custom icon colors and every other setting stay as they are. Each design is written through
 * [WidgetSettingsStore], as Widget Customization's Save writes it, so the widget, its editor and backups agree.
 */
internal object GalleryLive {
    /** The live look's id: the person's own design, as the settings have it now. */
    const val LIVE_ID = "live"

    /**
     * The person's G icon style for each design as the settings have it ([refreshG]): what a look that keeps the G
     * shows in its previews (StyleSpec.resolve), as applying it keeps that G.
     */
    val liveG = androidx.compose.runtime.mutableStateMapOf<Design, String>()

    /** The person's buttons and G switches for each design as the settings have them ([refreshG]; BarButtons.of). */
    val liveButtons = androidx.compose.runtime.mutableStateMapOf<Design, BarButtons>()

    /** Yours Now, the first look of the fan: the search bar as the settings have it now ([refreshG] reads it). */
    const val NOW_ID = "yours"
    private const val NOW_NAME = "Yours Now"
    private const val NOW_DESC = "Your search bar as it is right now, on your wallpaper."
    var now by androidx.compose.runtime.mutableStateOf(Look(NOW_ID, NOW_NAME, NOW_DESC, origin = Origin.Mine))
        private set

    fun refreshG(context: Context) {
        val l = live(context, prefs(context))
        liveG[Design.SYSTEM] = l.system.gIconTheme
        liveG[Design.MATERIAL] = l.material.gIconTheme
        l.system.buttons().let { if (liveButtons[Design.SYSTEM] != it) liveButtons[Design.SYSTEM] = it }
        l.material.buttons().let { if (liveButtons[Design.MATERIAL] != it) liveButtons[Design.MATERIAL] = it }
        val look = read(context).copy(id = NOW_ID, name = NOW_NAME, desc = NOW_DESC)
        if (look != now) now = look
    }

    private fun WidgetDesignSettings.buttons() = BarButtons(
        showG = showGIcon, keepSystemG = keepSystemG, colors = elementColors, syncColors = syncColors,
        showVoice = showVoice, order = slotOrder.split(",").filter { it.isNotBlank() },
        shortcuts = mapOf("shortcut1" to shortcut1, "shortcut2" to shortcut2, "shortcut3" to shortcut3),
        actionIcon = actionIcon
    )

    /** How the person's search page is laid out, which no look changes: where its bar is, and whether it shows the clock. */
    class PageLayout(val barAtBottom: Boolean, val showsClock: Boolean)

    fun pageLayout(context: Context): PageLayout = prefs(context).let {
        PageLayout(it[SettingsManager.BOTTOM_SEARCH], it[SettingsManager.GLANCE_CLOCK])
    }

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    private fun defaults(context: Context, prefs: SharedPreferences): WidgetDefaults {
        val systemDark = ThemeMode.systemIsDark(context.resources.configuration)
        val dark = ThemeMode.from(prefs[SettingsManager.THEME]).isDark(systemDark)
        val colors = DynamicThemeDetector.resolveWidgetColors(context, dark)
        return WidgetDefaults(colors.primary, colors.isDynamicActive)
    }

    private fun heroOf(prefs: SharedPreferences): Design =
        if (prefs[SettingsManager.WIDGET_THEME_STYLE] == WidgetDesign.MATERIAL.themeStyle) Design.MATERIAL else Design.SYSTEM

    private fun WidgetDesignSettings.spec(): StyleSpec = StyleSpec(
        subtheme = subtheme, gIcon = gIconTheme, hue = hue.toInt(), saturation = saturation.toInt(),
        lightness = lightness.toInt(), colorOpacity = colorOpacity.toInt(), transparency = transparency.toInt(),
        outline = outlineThickness, corner = cornerRadius, lockBlack = lockBlack, lockedPill = lockedPillColor
    )

    /** The search page's half of a look as the settings have it now, with the theme exactly as stored. */
    private fun liveSearch(prefs: SharedPreferences): SearchLook {
        val theme = ThemeMode.from(prefs[SettingsManager.THEME])
        return SearchLook(
            theme = when (theme) {
                ThemeMode.DARK -> ThemeChoice.Dark
                ThemeMode.LIGHT -> ThemeChoice.Light
                ThemeMode.SYSTEM -> ThemeChoice.Keep
            },
            wallpaper = prefs[SettingsManager.SHOW_WALLPAPER],
            blur = prefs[SettingsManager.BACKGROUND_BLUR],
            transparency = prefs[SettingsManager.BACKGROUND_TRANSPARENCY],
            pillOpacity = prefs[SettingsManager.PILL_OPACITY],
            stars = prefs[SettingsManager.MATRIX_ANIMATION_ENABLED],
            clock = prefs[SettingsManager.GLANCE_CLOCK_STYLE],
            appTheme = theme.storedValue
        )
    }

    private class Live(val system: WidgetDesignSettings, val material: WidgetDesignSettings, val hero: Design, val search: SearchLook)

    private fun live(context: Context, prefs: SharedPreferences): Live {
        val store = WidgetSettingsStore(prefs)
        val defaults = defaults(context, prefs)
        return Live(store.load(WidgetDesign.SYSTEM, defaults), store.load(WidgetDesign.MATERIAL, defaults), heroOf(prefs), liveSearch(prefs))
    }

    /** The person's design as the settings have it now: a look of their own ("Your design"), every field set. */
    fun read(context: Context): Look {
        val l = live(context, prefs(context))
        val heroSettings = if (l.hero == Design.SYSTEM) l.system else l.material
        return Look(
            LIVE_ID, "Your design", origin = Origin.Mine, hero = l.hero, dyn = heroSettings.subtheme != "Custom",
            system = l.system.spec(), material = l.material.spec(), search = l.search
        )
    }

    /**
     * [live] with [spec]'s style for [design]: a field the look leaves out takes the design's default (the G icon and
     * the clock keep the person's). Hue, saturation, lightness and color opacity are only the custom color, so a look
     * that isn't Custom leaves the person's custom color as it was for the next time they pick Custom.
     */
    private fun styled(design: Design, live: WidgetDesignSettings, spec: StyleSpec): WidgetDesignSettings {
        val s = spec.resolve(design)
        val custom = s.subtheme == "Custom" || spec.hue != null
        return live.copy(
            subtheme = s.subtheme,
            gIconTheme = spec.gIcon ?: live.gIconTheme,
            hue = if (custom) s.hue else live.hue,
            saturation = if (custom) s.saturation else live.saturation,
            lightness = if (custom) s.lightness else live.lightness,
            colorOpacity = if (custom) s.colorOpacity else live.colorOpacity,
            transparency = s.transparency,
            outlineThickness = s.outlineThickness,
            cornerRadius = s.cornerRadius,
            lockBlack = if (design == Design.MATERIAL) s.lockBlack else live.lockBlack,
            lockedPillColor = s.lockedPillColor or 0xFF000000.toInt()
        )
    }

    private fun themeValue(search: SearchLook, current: String): String = search.appTheme ?: when (search.theme) {
        ThemeChoice.Keep -> current
        ThemeChoice.Dark -> ThemeMode.DARK.storedValue
        ThemeChoice.Light -> ThemeMode.LIGHT.storedValue
    }

    /** Whether [look] is what the settings have now: applying it would change nothing. */
    fun isOn(context: Context, look: Look): Boolean {
        val prefs = prefs(context)
        val l = live(context, prefs)
        val s = look.search
        return heroOf(prefs) == look.hero &&
            styled(Design.SYSTEM, l.system, look.system) == l.system &&
            styled(Design.MATERIAL, l.material, look.material) == l.material &&
            themeValue(s, ThemeMode.from(prefs[SettingsManager.THEME]).storedValue) == ThemeMode.from(prefs[SettingsManager.THEME]).storedValue &&
            s.wallpaper == l.search.wallpaper && s.blur == l.search.blur && s.transparency == l.search.transparency &&
            s.pillOpacity == l.search.pillOpacity && s.stars == l.search.stars && (s.clock == null || s.clock == l.search.clock)
    }

    /**
     * Makes [look] the widget's and the search page's look: both designs' style, the Search Box bar's design and the
     * search page's appearance, in one edit, then redraws every placed widget. The search page takes it the next
     * time it opens.
     */
    fun apply(context: Context, look: Look) {
        val prefs = prefs(context)
        val l = live(context, prefs)
        val store = WidgetSettingsStore(prefs)
        val s = look.search
        prefs.edit {
        val editor = this
        store.save(WidgetDesign.SYSTEM, styled(Design.SYSTEM, l.system, look.system), editor)
        store.save(WidgetDesign.MATERIAL, styled(Design.MATERIAL, l.material, look.material), editor)
        editor.put(SettingsManager.WIDGET_THEME_STYLE, if (look.hero == Design.MATERIAL) WidgetDesign.MATERIAL.themeStyle else WidgetDesign.SYSTEM.themeStyle)
        editor.put(SettingsManager.THEME, themeValue(s, ThemeMode.from(prefs[SettingsManager.THEME]).storedValue))
        editor.put(SettingsManager.SHOW_WALLPAPER, s.wallpaper)
        editor.put(SettingsManager.BACKGROUND_BLUR, s.blur.coerceIn(0, 100))
        editor.put(SettingsManager.BACKGROUND_TRANSPARENCY, s.transparency.coerceIn(0, 100))
        editor.put(SettingsManager.PILL_OPACITY, s.pillOpacity.coerceIn(0, 100))
        editor.put(SettingsManager.MATRIX_ANIMATION_ENABLED, s.stars)
        s.clock?.let { editor.put(SettingsManager.GLANCE_CLOCK_STYLE, it) }
        }
        refreshG(context)
        // Leaving Settings redraws the widgets too (SettingsActivity.onPause); this shows the look at once.
        runCatching { com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider.updateAllWidgets(context.applicationContext, null) }
    }
}
