package com.pixel.intelligentsearch.feature.tabs.gallery

import android.util.Base64
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

/*
 * What a look is (specs/gallery_mechanics.md section 2): both widget designs' style plus the search page's settings.
 * The person's own buttons are never part of a look, so every preview shows the same buttons.
 */

internal enum class Design { SYSTEM, MATERIAL }

internal const val G_SYSTEM = "System G Icon"
internal const val G_MATERIAL = "Material G Icon"
internal const val G_ACCENTED = "Accented G Icon"
/** The person's own style, never a look's: the G (and the buttons) in colors they picked. */
internal const val G_CUSTOM = "Custom G Icon"

/** One design's fields a look sets; null keeps the design's default. Field names follow the app's key suffixes. */
internal data class StyleSpec(
    val subtheme: String? = null,
    val gIcon: String? = null,
    val hue: Int? = null,
    val saturation: Int? = null,
    val lightness: Int? = null,
    val colorOpacity: Int? = null,
    val transparency: Int? = null,
    val outline: Int? = null,
    val corner: Int? = null,
    val lockBlack: Boolean? = null,
    val lockedPill: Int? = null
) {
    fun resolve(design: Design): WidgetStyle {
        val d = if (design == Design.SYSTEM) SYSTEM_DEFAULT else MATERIAL_DEFAULT
        return WidgetStyle(
            subtheme = subtheme ?: d.subtheme,
            // A look that keeps the G shows the person's own (GalleryLive.liveG), as applying it does.
            gIconTheme = gIcon ?: GalleryLive.liveG[design] ?: d.gIconTheme,
            hue = hue?.toFloat() ?: d.hue,
            saturation = saturation?.toFloat() ?: d.saturation,
            lightness = lightness?.toFloat() ?: d.lightness,
            colorOpacity = colorOpacity?.toFloat() ?: d.colorOpacity,
            transparency = transparency?.toFloat() ?: d.transparency,
            outlineThickness = outline ?: d.outlineThickness,
            cornerRadius = corner ?: d.cornerRadius,
            lockBlack = lockBlack ?: d.lockBlack,
            lockedPillColor = lockedPill ?: d.lockedPillColor
        )
    }

    companion object {
        /** Each design's defaults (WidgetDesignSettings), unused color fields at a neutral seed. */
        val SYSTEM_DEFAULT = WidgetStyle("System", G_MATERIAL, 340f, 48f, 100f, 100f, 0f, 0, 28, false, LOCKED_PILL)
        val MATERIAL_DEFAULT = WidgetStyle("Material", G_MATERIAL, 340f, 48f, 100f, 100f, 28f, 8, 32, true, LOCKED_PILL)
    }
}

internal enum class ThemeChoice { Keep, Dark, Light }

/** The search page's half of a look (SearchLook). */
internal data class SearchLook(
    val theme: ThemeChoice = ThemeChoice.Keep,
    val wallpaper: Boolean = true,
    val blur: Int = 50,
    val transparency: Int = 50,
    val pillOpacity: Int = 100,
    val stars: Boolean = true,
    /** cookie, sunny, square, digital, or null to keep the person's (cookie by default). */
    val clock: String? = null,
    /**
     * The app's theme exactly as stored (`night.mode`), for a look read from the person's own settings (GalleryLive):
     * putting it back puts back "System" too, which [theme] can't say. Never in a look code.
     */
    val appTheme: String? = null
)

internal enum class Season(val title: String, val months: String) {
    WINTER("Winter", "Dec to Feb"), SPRING("Spring", "Mar to May"), SUMMER("Summer", "Jun to Aug"), FALL("Fall", "Sep to Nov");

    companion object {
        /** Northern months; Intelligent Search asks OccasionCalendar, which also knows the southern half. */
        fun of(date: LocalDate): Season = when (date.monthValue) {
            12, 1, 2 -> WINTER
            3, 4, 5 -> SPRING
            6, 7, 8 -> SUMMER
            else -> FALL
        }
    }
}

internal enum class Origin { BuiltIn, Mine, Saved, Shared }

internal data class Look(
    val id: String,
    val name: String,
    val desc: String = "",
    /** The bar takes the wallpaper's colors (true), or keeps fixed colors of its own. */
    val dyn: Boolean = true,
    val hero: Design = Design.SYSTEM,
    val tags: Set<String> = emptySet(),
    val system: StyleSpec = StyleSpec(),
    val material: StyleSpec = StyleSpec(),
    val search: SearchLook = SearchLook(),
    val season: Season? = null,
    val note: String? = null,
    val origin: Origin = Origin.BuiltIn,
    /** For a saved copy of a built-in look: the look it came from. */
    val base: String? = null
) {
    fun style(design: Design): WidgetStyle = (if (design == Design.SYSTEM) system else material).resolve(design)
    val heroStyle: WidgetStyle get() = style(hero)

    /** Whether applying it sets a G icon style (most built-in looks do; open for the owner). */
    val setsG: Boolean get() = system.gIcon != null || material.gIcon != null
    val gChoice: String? get() = (if (hero == Design.SYSTEM) system.gIcon else material.gIcon) ?: system.gIcon ?: material.gIcon
}

internal fun gName(g: String?) = when (g) {
    G_SYSTEM -> "Google's colors"
    G_ACCENTED -> "Accented"
    else -> "Material"
}

/**
 * The buttons on a preview's bar and the G's own switches, which no look changes: whether the G shows, and, for the
 * Custom G style, Keep System G and the colors picked for the G and each button.
 */
internal data class BarButtons(
    val showG: Boolean = true,
    val keepSystemG: Boolean = false,
    /** The Custom G style's colors as picked, before the bar adjusts them; null when there are none to show. */
    val colors: com.pixel.intelligentsearch.core.data.widget.WidgetElementColors? = null,
    /** Sync All Colors: every button takes the G's color. */
    val syncColors: Boolean = false,
    val showVoice: Boolean = true,
    val order: List<String> = listOf("shortcut1", "mic", "shortcut2", "shortcut3"),
    val shortcuts: Map<String, String> = mapOf("shortcut1" to "Google Lens", "shortcut2" to "Translate (text)", "shortcut3" to "None"),
    val actionIcon: String = "Search"
) {
    companion object {
        /** Sample buttons for the looks: Lens, voice, Translate and the search circle. */
        val SAMPLE = BarButtons()

        /**
         * What [look]'s bar on [design] shows: the person's own G switches on every look, as applying any look keeps
         * them; the person's own buttons on their own design ("Your design", Yours Now), the sample ones on the rest.
         */
        fun of(look: Look, design: Design): BarButtons {
            val mine = GalleryLive.liveButtons[design] ?: return SAMPLE
            return if (look.origin == Origin.Mine) mine
            else SAMPLE.copy(showG = mine.showG, keepSystemG = mine.keepSystemG, colors = mine.colors, syncColors = mine.syncColors)
        }
    }
}

private fun c(hex: Long) = hex.toInt()

/** The first sixteen built-in looks, exactly as gallery_mechanics.md section 4 lists them. */
private val FIRST_LOOKS: List<Look> = listOf(
    Look("classic", "Classic", "The one you know, just as it comes.", true, Design.SYSTEM, setOf("colors"),
        StyleSpec(gIcon = G_SYSTEM), StyleSpec(gIcon = G_SYSTEM, corner = 24)),
    Look("glass", "Wallpaper Glass", "Lets your wallpaper shine right through.", true, Design.SYSTEM, setOf("colors", "glass"),
        // A quarter see-through: at half, its icons fell under 3:1 on the brighter part of its wallpaper.
        StyleSpec(transparency = 25), StyleSpec(lockBlack = false, transparency = 25, outline = 4),
        SearchLook(blur = 30, transparency = 80, pillOpacity = 70, stars = false)),
    Look("ring", "Tonal Ring", "A chunky ring that picks up your colors.", true, Design.MATERIAL, setOf("colors", "bold"),
        StyleSpec(gIcon = G_ACCENTED, outline = 4), StyleSpec(outline = 12, transparency = 0, gIcon = G_ACCENTED),
        SearchLook(blur = 60, transparency = 40)),
    Look("square", "Soft Square", "Soft corners with a little more backbone.", true, Design.MATERIAL, setOf("colors"),
        StyleSpec(corner = 14), StyleSpec(outline = 6, corner = 12), SearchLook(clock = "square")),
    Look("ink", "Ink", "Bold black that holds its own anywhere.", true, Design.SYSTEM, setOf("colors", "dark"),
        StyleSpec(subtheme = "Dark", gIcon = G_ACCENTED), StyleSpec(lockedPill = c(0xFF131313), gIcon = G_ACCENTED),
        SearchLook(theme = ThemeChoice.Dark, blur = 70, transparency = 30)),
    Look("paper", "Paper", "Clean and quiet, like a fresh page.", true, Design.SYSTEM, setOf("colors", "light", "quiet"),
        StyleSpec(subtheme = "Light", gIcon = G_SYSTEM), StyleSpec(lockedPill = c(0xFFF7F7F7), outline = 2, transparency = 0),
        SearchLook(theme = ThemeChoice.Light, wallpaper = false, stars = false)),
    Look("bare", "Bare", "Barely there. Your wallpaper does the talking.", true, Design.SYSTEM, setOf("colors", "glass", "quiet"),
        StyleSpec(transparency = 100), StyleSpec(lockBlack = false, transparency = 100, outline = 0),
        SearchLook(blur = 0, transparency = 100, pillOpacity = 60, stars = false), note = "Best on a calm wallpaper"),
    Look("bold", "Bold", "Big, bright, and impossible to lose.", true, Design.SYSTEM, setOf("colors", "bold"),
        StyleSpec(outline = 2, gIcon = G_SYSTEM), StyleSpec(outline = 10, transparency = 0, gIcon = G_ACCENTED),
        SearchLook(wallpaper = false, stars = false)),
    Look("mono", "Mono", "Everything in soft gray, easy on the eyes.", false, Design.SYSTEM, setOf("dark", "quiet"),
        StyleSpec(subtheme = "Custom", hue = 0, saturation = 0, lightness = 22, gIcon = G_ACCENTED),
        StyleSpec(subtheme = "Custom", hue = 0, saturation = 0, lightness = 35, lockedPill = c(0xFF1A1A1A), outline = 6, transparency = 0, gIcon = G_ACCENTED),
        SearchLook(blur = 80, transparency = 20, stars = false)),
    Look("neon", "Neon", "Lights off, one violet glow left on.", false, Design.MATERIAL, setOf("dark", "bold"),
        StyleSpec(subtheme = "Custom", hue = 305, saturation = 60, lightness = 10, outline = 2, gIcon = G_ACCENTED),
        StyleSpec(subtheme = "Custom", hue = 305, saturation = 100, lightness = 60, lockedPill = c(0xFF0D0718), outline = 4, transparency = 0, gIcon = G_ACCENTED),
        SearchLook(theme = ThemeChoice.Dark, blur = 80, transparency = 20)),
    Look("sunset", "Sunset", "Golden hour, every time you look.", false, Design.SYSTEM, setOf("glass"),
        // Mostly solid (85%): at 60% the wallpaper's plum came through and muddied the bar under its icons.
        StyleSpec(subtheme = "Custom", hue = 45, saturation = 70, lightness = 75, colorOpacity = 85, gIcon = G_ACCENTED),
        StyleSpec(subtheme = "Custom", hue = 45, saturation = 80, lightness = 65, lockedPill = c(0xFFFFF1E3), outline = 6, transparency = 0, gIcon = G_ACCENTED),
        SearchLook(blur = 40, transparency = 70, pillOpacity = 80, stars = false)),
    Look("pastel", "Pastel", "Soft lilac, light as soap bubbles.", false, Design.MATERIAL, setOf("light", "quiet"),
        StyleSpec(subtheme = "Custom", hue = 300, saturation = 25, lightness = 92),
        StyleSpec(subtheme = "Custom", hue = 300, saturation = 35, lightness = 80, lockedPill = c(0xFFF7EEF6), outline = 8),
        SearchLook(theme = ThemeChoice.Light, blur = 60, transparency = 60, pillOpacity = 90, stars = false)),
    // Seasonal: fixed colors, one per season; they leave the theme and the wallpaper switch as the person has them.
    Look("harvest", "Harvest", "Orange and cream, like a knit sweater.", false, Design.MATERIAL, setOf("seasonal"),
        StyleSpec(subtheme = "Custom", hue = 50, saturation = 60, lightness = 90, gIcon = G_ACCENTED),
        StyleSpec(subtheme = "Custom", hue = 50, saturation = 75, lightness = 45, lockedPill = c(0xFFFFF1E3), outline = 8, gIcon = G_ACCENTED),
        SearchLook(blur = 60, transparency = 40, stars = false), season = Season.FALL),
    Look("frost", "First Frost", "A still, snowy morning in pale blue.", false, Design.MATERIAL, setOf("seasonal"),
        StyleSpec(subtheme = "Custom", hue = 240, saturation = 30, lightness = 94, gIcon = G_ACCENTED),
        StyleSpec(subtheme = "Custom", hue = 240, saturation = 45, lightness = 70, lockedPill = c(0xFFF4FBFF), outline = 8, gIcon = G_ACCENTED),
        SearchLook(blur = 70, transparency = 50, stars = true), season = Season.WINTER),
    Look("bloom", "Bloom", "The week the cherry trees come out.", false, Design.SYSTEM, setOf("seasonal"),
        StyleSpec(subtheme = "Custom", hue = 350, saturation = 40, lightness = 90),
        StyleSpec(subtheme = "Custom", hue = 350, saturation = 55, lightness = 75, lockedPill = c(0xFFFFF3F6), outline = 6),
        SearchLook(blur = 50, transparency = 60, stars = false, clock = "cookie"), season = Season.SPRING),
    Look("summer", "High Summer", "Long days and late sunsets.", false, Design.SYSTEM, setOf("seasonal"),
        StyleSpec(subtheme = "Custom", hue = 90, saturation = 70, lightness = 88, gIcon = G_ACCENTED),
        StyleSpec(subtheme = "Custom", hue = 90, saturation = 85, lightness = 80, lockedPill = c(0xFFFFFBE6), outline = 8, gIcon = G_ACCENTED),
        SearchLook(blur = 30, transparency = 70, stars = false, clock = "sunny"), season = Season.SUMMER),
)

/** A fixed color as the editor's Hue, Saturation and Lightness: its HCT hue, its share of the most chroma there, its tone. */
@Suppress("RestrictedApi")
private fun hsl(rgb: Long): IntArray {
    val hct = com.google.android.material.color.utilities.Hct.fromInt(rgb.toInt() or 0xFF000000.toInt())
    val max = GalleryPalette.maxChroma(hct.hue, hct.tone)
    val share = if (max <= 0.0) 0 else (hct.chroma / max * 100).toInt().coerceIn(0, 100)
    return intArrayOf(Math.round(hct.hue).toInt() % 360, share, Math.round(hct.tone).toInt().coerceIn(0, 100))
}

/** A bar of its own fixed color [pill] (Custom), with an outline [outline] dp and [transparency] percent see-through. */
private fun fixed(pill: Long, g: String?, outline: Int = 0, transparency: Int = 0, corner: Int? = null): StyleSpec {
    val v = hsl(pill)
    return StyleSpec(subtheme = "Custom", hue = v[0], saturation = v[1], lightness = v[2], outline = outline, transparency = transparency, corner = corner, gIcon = g)
}

/** A Material bar: a rim of [ring] [outline] dp thick around a solid [pill]. */
private fun rimmed(ring: Long, pill: Long, outline: Int, g: String?, corner: Int? = null): StyleSpec {
    val v = hsl(ring)
    return StyleSpec(subtheme = "Custom", hue = v[0], saturation = v[1], lightness = v[2], lockedPill = c(pill), outline = outline, transparency = 0, corner = corner, gIcon = g)
}

/** Twenty-four more looks, each with a wallpaper of its own, filed under the same kinds (the approved swatch fan). */
private val MORE_LOOKS: List<Look> = listOf(
    // Your Colors
    Look("tide", "Tide", "A deep, dark bar in your wallpaper's color.", true, Design.SYSTEM, setOf("colors"),
        StyleSpec(subtheme = "Dark", gIcon = G_SYSTEM), StyleSpec(lockedPill = c(0xFF0F3740), outline = 6, gIcon = G_SYSTEM),
        SearchLook(blur = 60, transparency = 40)),
    Look("clay", "Clay", "A soft rim in your wallpaper's color.", true, Design.MATERIAL, setOf("colors"),
        StyleSpec(outline = 2, gIcon = G_ACCENTED), StyleSpec(lockedPill = c(0xFFFDF0E6), outline = 8, transparency = 0, gIcon = G_ACCENTED),
        SearchLook(blur = 50, transparency = 50)),
    Look("meadow", "Meadow", "A light bar, tinted by your wallpaper.", true, Design.SYSTEM, setOf("colors"),
        StyleSpec(subtheme = "Light"), StyleSpec(lockedPill = c(0xFFEEF7DC), outline = 6),
        SearchLook(blur = 40, transparency = 60, stars = false)),
    // Light
    Look("linen", "Linen", "A pressed white shirt, nothing fancy.", false, Design.SYSTEM, setOf("light"),
        fixed(0xFFFDFAF4, G_SYSTEM, outline = 2), rimmed(0xFFCDBFA6, 0xFFFDFAF4, 2, G_SYSTEM),
        SearchLook(theme = ThemeChoice.Light, wallpaper = false, stars = false)),
    Look("sky", "Sky", "A clear morning with nowhere to be.", false, Design.MATERIAL, setOf("light"),
        fixed(0xFFF6FBFF, G_ACCENTED), rimmed(0xFFA9CFF2, 0xFFF6FBFF, 8, G_ACCENTED),
        SearchLook(theme = ThemeChoice.Light, blur = 40, transparency = 70, stars = false)),
    Look("peach", "Peach", "The soft orange of a ripe peach.", false, Design.SYSTEM, setOf("light"),
        fixed(0xFFFFF0E6, null), rimmed(0xFFFFC9B3, 0xFFFFF0E6, 6, null),
        SearchLook(theme = ThemeChoice.Light, blur = 50, transparency = 60, stars = false)),
    Look("mint", "Mint", "Pale green, cool as mint in ice water.", false, Design.MATERIAL, setOf("light"),
        fixed(0xFFF3FCF8, G_ACCENTED, corner = 14), rimmed(0xFFA8DCC6, 0xFFF3FCF8, 6, G_ACCENTED, corner = 12),
        SearchLook(theme = ThemeChoice.Light, blur = 50, transparency = 60, stars = false, clock = "square")),
    // Dark
    Look("midnight", "Midnight", "Deep blue for the quiet hours.", false, Design.SYSTEM, setOf("dark"),
        fixed(0xFF0E1838, G_ACCENTED), rimmed(0xFF3A4C8C, 0xFF0E1838, 4, G_ACCENTED),
        SearchLook(theme = ThemeChoice.Dark, blur = 70, transparency = 30)),
    Look("ember", "Ember", "The last warm glow of a campfire.", false, Design.MATERIAL, setOf("dark"),
        fixed(0xFF160A07, G_ACCENTED, outline = 2), rimmed(0xFFE6602D, 0xFF160A07, 4, G_ACCENTED),
        SearchLook(theme = ThemeChoice.Dark, blur = 80, transparency = 20)),
    Look("forest", "Forest", "Pines at dusk, still and green.", false, Design.SYSTEM, setOf("dark"),
        fixed(0xFF11261B, null), rimmed(0xFF2E5A42, 0xFF11261B, 6, null),
        SearchLook(theme = ThemeChoice.Dark, blur = 70, transparency = 30, stars = false)),
    Look("carbon", "Carbon", "Sleek and dark, nothing extra.", false, Design.SYSTEM, setOf("dark"),
        fixed(0xFF1B1B1E, G_SYSTEM, outline = 2), rimmed(0xFF55555C, 0xFF1B1B1E, 4, G_SYSTEM),
        SearchLook(theme = ThemeChoice.Dark, blur = 80, transparency = 20, stars = false)),
    // Glass
    Look("lagoon", "Lagoon", "Clear blue water you could swim in.", true, Design.SYSTEM, setOf("glass"),
        StyleSpec(transparency = 50), StyleSpec(lockBlack = false, transparency = 40, outline = 4),
        SearchLook(blur = 30, transparency = 80, pillOpacity = 70, stars = false)),
    Look("rose", "Rose Glass", "A soft pink tint over your wallpaper.", false, Design.SYSTEM, setOf("glass"),
        fixed(0xFFFFE8F0, G_ACCENTED, transparency = 44), rimmed(0xFFF2A9C1, 0xFFFFE8F0, 6, G_ACCENTED).copy(lockBlack = false, transparency = 40),
        SearchLook(blur = 30, transparency = 75, pillOpacity = 75, stars = false)),
    Look("smoke", "Smoke", "Smoky glass that calms a busy wallpaper.", false, Design.SYSTEM, setOf("glass"),
        fixed(0xFF22242E, G_ACCENTED, transparency = 45), rimmed(0xFF3A3D4C, 0xFF22242E, 4, G_ACCENTED).copy(lockBlack = false, transparency = 40),
        SearchLook(theme = ThemeChoice.Dark, blur = 50, transparency = 50, pillOpacity = 75, stars = false)),
    // Bold
    Look("signal", "Signal", "Loud on purpose. You can't miss it.", false, Design.SYSTEM, setOf("bold"),
        fixed(0xFFFFD92E, G_ACCENTED, outline = 2), rimmed(0xFF141414, 0xFFFFD92E, 8, G_ACCENTED),
        SearchLook(wallpaper = false, stars = false)),
    Look("cobalt", "Cobalt", "Strong blue, sure of itself.", false, Design.MATERIAL, setOf("bold"),
        fixed(0xFFE9EEFF, G_ACCENTED, outline = 2), rimmed(0xFF0F2172, 0xFFE9EEFF, 10, G_ACCENTED),
        SearchLook(blur = 40, transparency = 50, stars = false)),
    Look("pop", "Pop", "Hot pink and not sorry about it.", false, Design.SYSTEM, setOf("bold"),
        fixed(0xFFFF2F77, G_ACCENTED, outline = 2), rimmed(0xFFFFFFFF, 0xFFFF2F77, 6, G_ACCENTED),
        SearchLook(blur = 40, transparency = 50, stars = false)),
    Look("stencil", "Stencil", "Sharp edges, straight talk.", false, Design.MATERIAL, setOf("bold"),
        fixed(0xFFF4F0E6, G_ACCENTED, outline = 2, corner = 8), rimmed(0xFF111111, 0xFFF4F0E6, 6, G_ACCENTED, corner = 6),
        SearchLook(wallpaper = false, stars = false, clock = "square")),
    // Quiet
    Look("fog", "Fog", "A thin gray haze over your wallpaper.", true, Design.SYSTEM, setOf("quiet"),
        StyleSpec(subtheme = "Light", transparency = 74), StyleSpec(lockBlack = false, transparency = 70, outline = 2),
        SearchLook(blur = 60, transparency = 60, pillOpacity = 70, stars = false)),
    Look("sand", "Sand", "Warm and unhurried, like a long beach walk.", false, Design.SYSTEM, setOf("quiet"),
        fixed(0xFFEFE2C9, null), rimmed(0xFFD9C6A2, 0xFFEFE2C9, 6, null),
        SearchLook(blur = 50, transparency = 60, stars = false)),
    Look("dusk", "Dusk", "That soft purple right after sunset.", false, Design.SYSTEM, setOf("quiet"),
        fixed(0xFF3A3154, null), rimmed(0xFF5A4C80, 0xFF3A3154, 6, null),
        SearchLook(theme = ThemeChoice.Dark, blur = 70, transparency = 30)),
    // Seasonal
    Look("maple", "Maple", "Red leaves on a crisp afternoon.", false, Design.MATERIAL, setOf("seasonal"),
        fixed(0xFFFFF1E6, G_ACCENTED, outline = 2), rimmed(0xFFC2412D, 0xFFFFF1E6, 8, G_ACCENTED),
        SearchLook(blur = 60, transparency = 40, stars = false), season = Season.FALL),
    Look("aurora", "Aurora", "The sky putting on a show over fresh snow.", false, Design.SYSTEM, setOf("seasonal"),
        fixed(0xFF0D2233, G_ACCENTED), rimmed(0xFF2F8F72, 0xFF0D2233, 6, G_ACCENTED),
        SearchLook(theme = ThemeChoice.Dark, blur = 70, transparency = 30, stars = true), season = Season.WINTER),
    Look("shore", "Shore", "Toes in the sand, waves rolling in.", false, Design.SYSTEM, setOf("seasonal"),
        fixed(0xFFFFF7E2, G_ACCENTED), rimmed(0xFF7FC6DC, 0xFFFFF7E2, 8, G_ACCENTED),
        SearchLook(blur = 30, transparency = 70, stars = false), season = Season.SUMMER),
)

/** Every built-in look: the first sixteen and the twenty-four that came with the swatch fan. */
internal val BUILT_IN_LOOKS: List<Look> = FIRST_LOOKS + MORE_LOOKS

internal fun Look.matches(filter: String): Boolean = if (filter == "all") season == null else filter in tags

/** The rack's order for a filter: the look in season first. */
internal fun rack(filter: String, today: LocalDate): List<Look> {
    val now = Season.of(today)
    return BUILT_IN_LOOKS.filter { it.matches(filter) }.sortedByDescending { it.season == now }
}

/** Every built-in look in the rack's order: the twelve, then the seasonal ones. */
internal fun fullRack(today: LocalDate): List<Look> = rack("all", today) + rack("seasonal", today)

/** The ISO week [today] is in, as "2026-W47": the look of the week is kept under it. */
internal fun weekKey(today: LocalDate): String =
    "${today.get(IsoFields.WEEK_BASED_YEAR)}-W${today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"

/** Look of the week: the ISO week number modulo the non-seasonal looks, worked out on the phone; skips the look that is on. */
internal fun lookOfTheWeek(today: LocalDate, currentId: String): Look {
    val pool = rack("all", today)
    var i = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) % pool.size
    if (pool[i].id == currentId) i = (i + 1) % pool.size
    return pool[i]
}

/** Whether the look's app theme is dark, given the phone's ([phoneDark]): its own, or the phone's when it keeps it. */
internal fun Look.appDark(phoneDark: Boolean): Boolean =
    search.theme == ThemeChoice.Dark || (search.theme != ThemeChoice.Light && phoneDark)

/** Whether applying it changes the theme the person sees now. */
internal fun Look.switchesTheme(phoneDark: Boolean): Boolean = search.theme != ThemeChoice.Keep && appDark(phoneDark) != phoneDark

/** Where its colors come from, the bar's and the search page's in one line (the search page always takes the wallpaper's). */
internal fun Look.label(): String = if (dyn) "Bar and search: your wallpaper's colors" else "Bar: fixed colors · Search: your wallpaper's colors"

internal enum class FactIcon { Dark, Light, G, Info, Contrast, Calendar, Palette, Paint }

internal data class Fact(val icon: FactIcon, val text: String)

/** What a person should know before applying: theme, the G icon, search colors, a caution, the season. */
internal fun Look.facts(phoneDark: Boolean, today: LocalDate): List<Fact> = buildList {
    val sw = switchesTheme(phoneDark)
    if (search.theme == ThemeChoice.Dark) add(Fact(FactIcon.Dark, if (sw) "Switches to dark theme" else "Always dark theme"))
    if (search.theme == ThemeChoice.Light) add(Fact(FactIcon.Light, if (sw) "Switches to light theme" else "Always light theme"))
    add(Fact(FactIcon.G, if (setsG) "Sets the G icon: ${gName(gChoice)}" else "Keeps your G icon"))
    note?.let { add(Fact(FactIcon.Contrast, it)) }
    if (season != null && season != Season.of(today)) add(Fact(FactIcon.Calendar, "In season ${season.months}"))
}

/** What TalkBack reads for a look: its name, what it is, its label and anything to know, then its state. */
internal fun Look.spoken(on: Boolean, phoneDark: Boolean, today: LocalDate): String =
    (listOf(name, desc.removeSuffix("."), label()) + facts(phoneDark, today).map { it.text } + (if (on) listOf("On your phone now") else emptyList()))
        .filter { it.isNotBlank() }.joinToString(". ") + "."

/*
 * Look codes. IS2. = the profile JSON (both designs) plus name and search, deflated, base64url, with a CRC: the packing
 * IS1. uses today. Only values that differ from the defaults are written; buttons are left out.
 */
internal object LookCode {
    private val SUBTHEMES = setOf("System", "Light", "Dark", "Material", "Custom")
    private val G_STYLES = setOf(G_SYSTEM, G_MATERIAL, G_ACCENTED)
    private val CLOCKS = setOf("cookie", "sunny", "square", "digital")
    private val NAME = Regex("^[\\p{L}\\p{N} ._'&-]{1,24}$")
    /** The most pasted text, code and unpacked look read: far above any real code, far below what could hurt. */
    private const val MAX_TEXT = 16 * 1024
    private const val MAX_CODE = 4 * 1024
    private const val MAX_JSON = 8 * 1024

    private fun StyleSpec.toJson() = JSONObject().apply {
        subtheme?.let { put("subtheme", it) }
        gIcon?.let { put("material_g_icon", it) }
        hue?.let { put("custom_hue", it) }
        saturation?.let { put("custom_saturation", it) }
        lightness?.let { put("custom_lightness", it) }
        colorOpacity?.let { put("custom_color_opacity", it) }
        transparency?.let { put("background_transparency", it) }
        outline?.let { put("outline_thickness", it) }
        corner?.let { put("corner_radius", it) }
        lockBlack?.let { put("lock_black", it) }
        lockedPill?.let { put("lock_pill_color", "#%06x".format(it and 0xFFFFFF)) }
    }

    fun encode(look: Look): String {
        val d = SearchLook()
        val s = look.search
        val search = JSONObject().apply {
            if (s.theme != d.theme) put("theme", s.theme.name)
            if (s.wallpaper != d.wallpaper) put("wallpaper", s.wallpaper)
            if (s.blur != d.blur) put("blur", s.blur)
            if (s.transparency != d.transparency) put("transparency", s.transparency)
            if (s.pillOpacity != d.pillOpacity) put("pillOpacity", s.pillOpacity)
            if (s.stars != d.stars) put("stars", s.stars)
            s.clock?.let { put("clock", it) }
        }
        val json = JSONObject().apply {
            put("name", look.name)
            put("design", look.hero.name)
            put("SYSTEM", look.system.toJson())
            put("MATERIAL", look.material.toJson())
            put("search", search)
        }.toString().toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true).apply { setInput(json); finish() }
        val buf = ByteArray(json.size + 64)
        val n = deflater.deflate(buf).also { deflater.end() }
        val crc = CRC32().apply { update(json) }.value
        val out = ByteArray(n + 5)
        out[0] = 1
        System.arraycopy(buf, 0, out, 1, n)
        for (i in 0 until 4) out[n + 1 + i] = (crc shr (24 - 8 * i) and 0xFF).toByte()
        return "IS2." + Base64.encodeToString(out, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private class Bad : Exception()

    private fun JSONObject?.style(): StyleSpec {
        if (this == null) return StyleSpec()
        fun int(k: String, lo: Int, hi: Int): Int? {
            if (!has(k)) return null
            val v = get(k)
            if (v !is Number || v.toDouble() < lo || v.toDouble() > hi) throw Bad()
            return v.toInt()
        }
        fun str(k: String, allowed: Set<String>): String? {
            if (!has(k)) return null
            val v = get(k)
            if (v !is String || v !in allowed) throw Bad()
            return v
        }
        val lockBlack = if (has("lock_black")) (get("lock_black") as? Boolean ?: throw Bad()) else null
        val pill = if (has("lock_pill_color")) {
            val v = get("lock_pill_color") as? String ?: throw Bad()
            if (!Regex("^#[0-9a-fA-F]{6}$").matches(v)) throw Bad()
            (0xFF000000 or v.substring(1).toLong(16)).toInt()
        } else null
        return StyleSpec(
            subtheme = str("subtheme", SUBTHEMES), gIcon = str("material_g_icon", G_STYLES),
            hue = int("custom_hue", 0, 360), saturation = int("custom_saturation", 0, 100), lightness = int("custom_lightness", 0, 100),
            colorOpacity = int("custom_color_opacity", 0, 100), transparency = int("background_transparency", 0, 100),
            outline = int("outline_thickness", 0, 12), corner = int("corner_radius", 0, 40), lockBlack = lockBlack, lockedPill = pill
        )
    }

    /** The look in [text], or null when it holds no valid code (an unknown field is dropped; a bad value refuses it). */
    fun decode(text: String): Look? {
        // The "IS2." prefix in any case (typed by hand, or changed by a keyboard); the code after it is case-sensitive.
        // A real code is well under a kilobyte, packed or not; anything far bigger is refused before it is unpacked, so a
        // crafted code can't fill the memory.
        if (text.length > MAX_TEXT) return null
        val m = Regex("[Ii][Ss]2\\.([A-Za-z0-9_-]{8,})").find(text) ?: return null
        if (m.groupValues[1].length > MAX_CODE) return null
        return try {
            val b = Base64.decode(m.groupValues[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            if (b.size < 6) return null
            val n = b.size - 4
            val body = b.copyOfRange(1, n)
            val json = if (b[0].toInt() == 1) {
                val inflater = Inflater(true).apply { setInput(body) }
                val out = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(1024)
                try {
                    while (!inflater.finished()) {
                        val k = inflater.inflate(chunk)
                        if (k == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                        if (out.size() + k > MAX_JSON) return null
                        out.write(chunk, 0, k)
                    }
                } finally {
                    inflater.end()
                }
                out.toByteArray()
            } else body
            val crc = CRC32().apply { update(json) }.value
            var stored = 0L
            for (i in 0 until 4) stored = (stored shl 8) or (b[n + i].toLong() and 0xFF)
            if (crc != stored) return null
            val o = JSONObject(String(json, Charsets.UTF_8))
            val design = when (o.optString("design")) { "SYSTEM" -> Design.SYSTEM; "MATERIAL" -> Design.MATERIAL; else -> return null }
            val name = shortName(o.optString("name")) ?: "Shared look"
            val system = o.optJSONObject("SYSTEM").style()
            val material = o.optJSONObject("MATERIAL").style()
            val sj = o.optJSONObject("search")
            val d = SearchLook()
            val search = if (sj == null) d else SearchLook(
                theme = if (sj.has("theme")) ThemeChoice.entries.firstOrNull { it.name == sj.get("theme") } ?: throw Bad() else d.theme,
                wallpaper = if (sj.has("wallpaper")) sj.get("wallpaper") as? Boolean ?: throw Bad() else d.wallpaper,
                blur = sj.range("blur", d.blur),
                transparency = sj.range("transparency", d.transparency),
                pillOpacity = sj.range("pillOpacity", d.pillOpacity),
                stars = if (sj.has("stars")) sj.get("stars") as? Boolean ?: throw Bad() else d.stars,
                clock = if (sj.has("clock")) (sj.get("clock") as? String)?.takeIf { it in CLOCKS } ?: throw Bad() else null
            )
            // Its id comes from what it holds: the same code pasted twice is the same look, two different codes never are.
            Look(
                id = "shared:%08x".format(crc), name = name, desc = "From a look code.", dyn = system.subtheme != "Custom" && material.subtheme != "Custom",
                hero = design, system = system, material = material, search = search, origin = Origin.Shared
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * A shared look's name as this app keeps names: at most 24 characters. A longer one is cut at the last word that
     * fits (or at 24 characters when no word break comes late enough); a name with characters a name can't hold is
     * refused (null).
     */
    fun shortName(raw: String): String? {
        val name = raw.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty()) return null
        if (name.length <= 24) return name.takeIf { NAME.matches(it) }
        val cut = name.take(25).lastIndexOf(' ').takeIf { it >= 12 } ?: 24
        return name.take(cut).trimEnd().takeIf { NAME.matches(it) }
    }

    private fun JSONObject.range(k: String, default: Int): Int {
        if (!has(k)) return default
        val v = get(k)
        if (v !is Number || v.toDouble() < 0 || v.toDouble() > 100) throw Bad()
        return v.toInt()
    }
}
