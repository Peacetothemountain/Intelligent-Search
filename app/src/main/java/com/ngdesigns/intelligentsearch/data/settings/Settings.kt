package com.ngdesigns.intelligentsearch.data.settings

import com.ngdesigns.intelligentsearch.engine.web.SafeUrl
import com.ngdesigns.intelligentsearch.engine.web.WebShortcuts
import kotlinx.serialization.Serializable

/**
 * Every setting in the app, in one immutable value. It is stored as one versioned JSON document, backed up as
 * exactly that document, and passed through [sanitized] whenever it is read or restored, so a hand-edited or
 * corrupted backup can never put an out-of-range value in front of search.
 */
@Serializable
data class Settings(
    val version: Int = CURRENT_VERSION,
    val look: Look = Look(),
    val sources: Sources = Sources(),
    val behaviour: Behaviour = Behaviour(),
    val ranking: Ranking = Ranking(),
    val web: Web = Web(),
    val quickApps: QuickApps = QuickApps(),
    val privacy: Privacy = Privacy(),
    val files: Files = Files(),
    val widgets: Widgets = Widgets(),
    val onboarding: Onboarding = Onboarding(),
    val debug: Debug = Debug(),
) {
    /** Every value forced into its allowed range; unknown or unsafe entries dropped. */
    fun sanitized(): Settings = copy(
        version = CURRENT_VERSION,
        look = look.copy(
            blur = look.blur.coerceIn(0, 100),
            transparency = look.transparency.coerceIn(0, 100),
            barOpacity = look.barOpacity.coerceIn(0, 100),
        ),
        ranking = ranking.copy(
            apps = ranking.apps.coerceIn(-2, 2), contacts = ranking.contacts.coerceIn(-2, 2),
            files = ranking.files.coerceIn(-2, 2), web = ranking.web.coerceIn(-2, 2),
            contactResults = ranking.contactResults.coerceIn(0, 20), fileResults = ranking.fileResults.coerceIn(0, 20),
            webResults = ranking.webResults.coerceIn(0, 20), shortcutResults = ranking.shortcutResults.coerceIn(1, 20),
        ),
        web = web.copy(
            customTemplate = SafeUrl.template(web.customTemplate)?.template ?: "",
            shortcutSymbol = web.shortcutSymbol.trim().take(3).ifEmpty { "!" }.takeIf { s -> s.none { it.isLetterOrDigit() || it.isWhitespace() } } ?: "!",
            customShortcuts = web.customShortcuts.mapNotNull { WebShortcuts.custom(it.key, it.name, it.template, it.appPackage)?.let { s -> CustomShortcut(s.key, s.name, s.urlTemplate, s.appPackage) } }
                .distinctBy { it.key }.take(MAX_CUSTOM_SHORTCUTS),
            disabledBuiltIns = web.disabledBuiltIns.filter { key -> WebShortcuts.builtIn.any { it.key == key } }.toSet(),
        ),
        quickApps = quickApps.copy(
            profiles = quickApps.profiles.filter { it.name.isNotBlank() }.distinctBy { it.name }.take(MAX_PROFILES)
                .map { it.copy(apps = it.apps.filter(::isPackageName).distinct().take(MAX_QUICK_APPS)) }
                .ifEmpty { QuickApps().profiles },
        ).let { q -> if (q.profiles.any { it.name == q.active }) q else q.copy(active = q.profiles.first().name) },
        privacy = privacy.copy(hiddenApps = privacy.hiddenApps.filter(::isPackageName).toSet()),
        widgets = widgets.copy(designs = widgets.designs.mapValues { (_, d) -> d.sanitized() }),
    )

    companion object {
        const val CURRENT_VERSION = 1
        const val MAX_QUICK_APPS = 20
        const val MAX_PROFILES = 8
        const val MAX_CUSTOM_SHORTCUTS = 50
        private val packagePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
        fun isPackageName(s: String) = s.length <= 255 && packagePattern.matches(s)
    }
}

@Serializable enum class ThemeMode { System, Light, Dark }

/**
 * How the colour scheme is made from the wallpaper. [System] is the phone's own Material You scheme (whatever style
 * the user picked in Wallpaper & style); the others are Material's scheme variants generated from the wallpaper colour.
 */
@Serializable enum class ColorStyle { System, TonalSpot, Vibrant, Expressive, FruitSalad, Rainbow, Content, Fidelity, Monochrome, Neutral }

@Serializable enum class Contrast { System, Standard, Medium, High }

@Serializable enum class Density { Comfortable, Compact }

@Serializable
data class Look(
    val theme: ThemeMode = ThemeMode.System,
    val colorStyle: ColorStyle = ColorStyle.System,
    val contrast: Contrast = Contrast.System,
    val density: Density = Density.Comfortable,
    val showWallpaper: Boolean = true,
    val blur: Int = 50,
    val transparency: Int = 50,
    val barOpacity: Int = 50,
    val animations: Boolean = true,
    val haptics: Boolean = true,
    /** A Material shape name for app icons, or "" for the system's own icon shape. */
    val iconShape: String = "",
    /** An icon pack's package, or "" for system icons. */
    val iconPack: String = "",
    /** Give apps without a themed icon a monochrome one, so themed icons stay consistent. */
    val monochromeIcons: Boolean = true,
    /** Off: the launcher's search bar and the assistant gesture open the default assistant instead. */
    val searchScreen: Boolean = true,
)

@Serializable
data class Sources(
    val apps: Boolean = true,
    val shortcuts: Boolean = true,
    val contacts: Boolean = false,
    val calendar: Boolean = true,
    val files: Boolean = false,
    val webSuggestions: Boolean = true,
    val history: Boolean = true,
    val calculator: Boolean = true,
    val clipboard: Boolean = false,
    val contextAwareQuickApps: Boolean = false,
)

@Serializable
data class Behaviour(
    val barAtBottom: Boolean = true,
    val resultsBottomUp: Boolean = true,
    val typos: Boolean = true,
    val quickLaunch: Boolean = false,
    val contactDirectCall: Boolean = false,
    val assistantStartsWithVoice: Boolean = true,
    val backToSearch: Boolean = true,
    val shortcutsInline: Boolean = true,
    val recentShortcuts: Boolean = true,
)

@Serializable
data class Ranking(
    val apps: Int = 0,
    val contacts: Int = 0,
    val files: Int = 0,
    val web: Int = 0,
    val contactResults: Int = 5,
    val fileResults: Int = 5,
    val webResults: Int = 5,
    val shortcutResults: Int = 6,
)

@Serializable
data class CustomShortcut(val key: String, val name: String, val template: String, val appPackage: String? = null)

@Serializable
data class Web(
    /** "Google", "Bing", "DuckDuckGo", "Tor" or "Custom". */
    val engine: String = "Google",
    val customTemplate: String = "",
    val shortcutsEnabled: Boolean = true,
    val shortcutSymbol: String = "!",
    val showAppShortcuts: Boolean = true,
    val disabledBuiltIns: Set<String> = emptySet(),
    val customShortcuts: List<CustomShortcut> = emptyList(),
    val quickYouTube: Boolean = true,
    val quickWikipedia: Boolean = true,
    val quickPlayStore: Boolean = true,
    val quickMaps: Boolean = true,
)

@Serializable
data class QuickAppProfile(val name: String, val apps: List<String>)

@Serializable
data class QuickApps(
    val profiles: List<QuickAppProfile> = listOf(
        QuickAppProfile("Everyday", listOf("com.android.chrome", "com.google.android.apps.maps", "com.google.android.youtube", "com.spotify.music")),
    ),
    val active: String = "Everyday",
    val horizontal: Boolean = false,
) {
    val activeApps: List<String> get() = profiles.firstOrNull { it.name == active }?.apps ?: emptyList()
}

@Serializable
data class Privacy(
    val hiddenApps: Set<String> = emptySet(),
    /** Let on-device assistants search contacts through AppFunctions. */
    val shareContactsWithAssistants: Boolean = false,
)

/** Folder and photo picker grants are tied to this phone, so [folders] and [media] stay out of backups. */
@Serializable
data class Files(
    val folders: List<String> = emptyList(),
    val media: List<String> = emptyList(),
    val showHidden: Boolean = false,
    val thumbnails: Boolean = true,
)

@Serializable enum class WidgetDesign { Bar, Tile }

@Serializable enum class WidgetAction { Voice, Lens, Assistant, TranslateText, TranslateCamera, Weather, Sports, Dictionary, Homework, Finance, Saved, News, NowPlaying, App, Web, None }

@Serializable
data class WidgetSlot(val action: WidgetAction = WidgetAction.None, val target: String = "")

@Serializable
data class WidgetStyle(
    /** Element colours as ARGB, by element name; absent elements follow the wallpaper scheme. */
    val colors: Map<String, Long> = emptyMap(),
    val transparency: Int = 0,
    val showLogo: Boolean = true,
    val slots: List<WidgetSlot> = listOf(WidgetSlot(WidgetAction.Voice), WidgetSlot(WidgetAction.Lens), WidgetSlot(), WidgetSlot()),
) {
    fun sanitized() = copy(
        transparency = transparency.coerceIn(0, 100),
        slots = (slots.take(4) + List((4 - slots.size).coerceAtLeast(0)) { WidgetSlot() }).map { s ->
            when (s.action) {
                WidgetAction.App -> if (Settings.isPackageName(s.target)) s else WidgetSlot()
                WidgetAction.Web -> SafeUrl.address(s.target)?.let { s.copy(target = it) } ?: WidgetSlot()
                else -> s.copy(target = "")
            }
        },
    )
}

@Serializable
data class Widgets(val designs: Map<WidgetDesign, WidgetStyle> = WidgetDesign.entries.associateWith { WidgetStyle() })

@Serializable
data class Onboarding(val tutorialDone: Boolean = false, val setupDismissed: Boolean = false)

@Serializable
data class Debug(
    val unlocked: Boolean = false,
    val verboseLogging: Boolean = false,
    val performanceHud: Boolean = false,
    val forceSearchError: Boolean = false,
    val simulateLatency: Boolean = false,
    val mockLargeDataset: Boolean = false,
    val mockTrending: Boolean = false,
)

/** The settings as backed up: the device-bound folder grants and the debug switches stay on this phone. */
fun Settings.forBackup(): Settings = copy(files = files.copy(folders = emptyList(), media = emptyList()), debug = Debug())

/** A restored backup applied over this phone's settings, keeping what never travels in a backup. */
fun Settings.restoredFrom(backup: Settings): Settings =
    backup.sanitized().copy(files = backup.files.copy(folders = files.folders, media = files.media), debug = debug)
