package com.pixel.intelligentsearch.core.data
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.io.IOException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "intelligent_search_settings")

@androidx.compose.runtime.Immutable
data class IntelligentSearchSettings(
    val theme: String = "system",
    val searchApps: Boolean = true,
    val searchContacts: Boolean = false,
    val searchFiles: Boolean = false,
    val searchWeb: Boolean = true,
    val searchCalculator: Boolean = true,
    val searchCalendar: Boolean = true,
    val searchShortcuts: Boolean = true,
    val backgroundBlur: Int = 50,
    val showWallpaper: Boolean = true,
    val backgroundTransparency: Int = 50,
    val pillOpacity: Int = 50,
    val searchEngine: String = "Google",
    val customSearchEngineUrl: String = "",
    val filesHiddenFiles: Boolean = false,
    val filesThumbnails: Boolean = true,
    val appAnimations: Boolean = true,
    val bottomSearch: Boolean = true,
    val bottomSearchResult: Boolean = true,
    val tutorialCompleted: Boolean = false,
    val forceTutorial: Boolean = false,
    val tutorialStep: Int = 0,
    val gIconEnabled: Boolean = true,
    val widgetShowVoice: Boolean = true,
    val widgetShowGemini: Boolean = true,
    val quickSearchYoutube: Boolean = true,
    val quickSearchWikipedia: Boolean = true,
    val quickSearchPlayStore: Boolean = true,
    val quickSearchMaps: Boolean = true,
    val searchPills: String = "com.android.chrome,com.google.android.apps.maps,com.google.android.youtube,com.android.vending,com.google.android.contacts,com.google.android.apps.nbu.files",
    val widgetThemeStyle: String = "System Default",
    val hiddenApps: Set<String> = emptySet(),
    val appQuickLaunch: Boolean = false,
    val contactDirectCall: Boolean = false,
    val shortcutInline: Boolean = true,
    val appFuzzySearch: Boolean = true,
    val quickSearchHorizontal: Boolean = false,
    val webResultsCount: Int = 5,
    val contactResultsCount: Int = 5,
    val fileResultsCount: Int = 5,
    val shortcutResultsCount: Int = 6,
    val contextAwareQuickApps: Boolean = false,
    val smartClipboardSuggestions: Boolean = false,
    val activeIconPack: String = "system_default",
    val searchOverlayEnabled: Boolean = true,
    val customIconPills: String = "",
    val neverShowIconPackWarning: Boolean = false,
    val searchPreviousSearches: Boolean = true,
    val customBangsJson: String = "[]",
    val searchSectionsConfigJson: String = "",
    val adaptiveIconShape: String = "SYSTEM_DEFAULT",
    val dynamicIconMasking: Boolean = true,
    val diagnosticsOverlayEnabled: Boolean = false,
    val matrixAnimationEnabled: Boolean = true,
    val backToSearchOverlay: Boolean = true,
    val disabledWebShortcuts: Set<String> = emptySet(),
    val vibrationEnabled: Boolean = true
)

@Singleton
class SettingsManager @Inject constructor(@ApplicationContext private val context: Context) {
    companion object {
        val THEME = stringPreferencesKey("night.mode")
        val SEARCH_APPS = booleanPreferencesKey("search.apps")
        val SEARCH_CONTACTS = booleanPreferencesKey("search.contacts")
        val SEARCH_FILES = booleanPreferencesKey("search.files")
        val SEARCH_WEB = booleanPreferencesKey("search.web")
        val SEARCH_PREVIOUS_SEARCHES = booleanPreferencesKey("search_previous_searches")
        val SEARCH_CALCULATOR = booleanPreferencesKey("search.calculator")
        val SEARCH_CALENDAR = booleanPreferencesKey("search.calendar")
        val SEARCH_SHORTCUTS = booleanPreferencesKey("search.shortcuts")
        val BACKGROUND_BLUR = intPreferencesKey("search.background.blur")
        val SHOW_WALLPAPER = booleanPreferencesKey("search.background.show.wall")
        val BACKGROUND_TRANSPARENCY = intPreferencesKey("search.background.transparency")
        val PILL_OPACITY = intPreferencesKey("search.pill.opacity")
        val SEARCH_ENGINE = stringPreferencesKey("search.engine")
        val CUSTOM_SEARCH_ENGINE_URL = stringPreferencesKey("custom_search_engine_url")
        val FILES_HIDDEN_FILES = booleanPreferencesKey("search.files.hidden.files")
        val FILES_THUMBNAILS = booleanPreferencesKey("search.files.thumbnails")
        val APP_ANIMATIONS = booleanPreferencesKey("app_animations")
        val BOTTOM_SEARCH = booleanPreferencesKey("settings.bottom.search")
        val BOTTOM_SEARCH_RESULT = booleanPreferencesKey("settings.bottom.search.result")
        val TUTORIAL_COMPLETED = booleanPreferencesKey("tutorial_completed")
        val FORCE_TUTORIAL = booleanPreferencesKey("force_tutorial")
        val TUTORIAL_STEP = intPreferencesKey("tutorial_step")
        val G_ICON_ENABLED = booleanPreferencesKey("g_icon_enabled")
        val WIDGET_SHOW_VOICE = booleanPreferencesKey("widget_show_voice")
        val WIDGET_SHOW_GEMINI = booleanPreferencesKey("widget_show_gemini")
        val QUICK_SEARCH_YOUTUBE = booleanPreferencesKey("quick_search_youtube")
        val QUICK_SEARCH_WIKIPEDIA = booleanPreferencesKey("quick_search_wikipedia")
        val QUICK_SEARCH_PLAY_STORE = booleanPreferencesKey("quick_search_play_store")
        val QUICK_SEARCH_MAPS = booleanPreferencesKey("quick_search_maps")
        val SEARCH_PILLS = stringPreferencesKey("search_pills")
        val WIDGET_THEME_STYLE = stringPreferencesKey("widget.theme.style")
        val HIDDEN_APPS = stringSetPreferencesKey("hidden_apps")
        val APP_QUICK_LAUNCH = booleanPreferencesKey("app_quick_launch")
        val CONTACT_DIRECT_CALL = booleanPreferencesKey("contact_direct_call")
        val SHORTCUT_INLINE = booleanPreferencesKey("shortcut.inline")
        val APP_FUZZY_SEARCH = booleanPreferencesKey("app.fuzzy.search")
        val QUICK_SEARCH_HORIZONTAL = booleanPreferencesKey("quick_search_horizontal")
        val WEB_RESULTS_COUNT = intPreferencesKey("web_results_count")
        val CONTACT_RESULTS_COUNT = intPreferencesKey("contact_results_count")
        val FILE_RESULTS_COUNT = intPreferencesKey("file_results_count")
        val SHORTCUT_RESULTS_COUNT = intPreferencesKey("shortcut_results_count")
        val CONTEXT_AWARE_QUICK_APPS = booleanPreferencesKey("context_aware_quick_apps")
        val SMART_CLIPBOARD_SUGGESTIONS = booleanPreferencesKey("smart_clipboard_suggestions")
        val ACTIVE_ICON_PACK = stringPreferencesKey("active_icon_pack")
        val SEARCH_OVERLAY_ENABLED = booleanPreferencesKey("search_overlay_enabled")
        val CUSTOM_ICON_PILLS = stringPreferencesKey("custom_icon_pills")
        val NEVER_SHOW_ICON_PACK_WARNING = booleanPreferencesKey("never_show_icon_pack_warning")
        val CUSTOM_BANGS_JSON = stringPreferencesKey("custom_bangs_json")
        val SEARCH_SECTIONS_CONFIG_JSON = stringPreferencesKey("search_sections_config_json")
        val ADAPTIVE_ICON_SHAPE = stringPreferencesKey("adaptive_icon_shape")
        val DYNAMIC_ICON_MASKING = booleanPreferencesKey("dynamic_icon_masking")
        val DIAGNOSTICS_OVERLAY_ENABLED = booleanPreferencesKey("diagnostics_overlay_enabled")
        val MATRIX_ANIMATION_ENABLED = booleanPreferencesKey("matrix_animation_enabled")
        val BACK_TO_SEARCH_OVERLAY = booleanPreferencesKey("settings_back_to_search_overlay")
        val DISABLED_WEB_SHORTCUTS = stringSetPreferencesKey("disabled_web_shortcuts")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
    }

    val settingsFlow: Flow<IntelligentSearchSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val defaultTheme = if (isSystemDark) "Material Dark" else "Material Light"
            val sp = context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)
            IntelligentSearchSettings(
                theme = preferences[THEME] ?: sp.getString("night.mode", defaultTheme) ?: defaultTheme,
                searchApps = preferences[SEARCH_APPS] ?: (if (sp.contains("search.apps")) sp.getBoolean("search.apps", true) else sp.getBoolean("search_apps", true)),
                searchContacts = preferences[SEARCH_CONTACTS] ?: (if (sp.contains("search.contacts")) sp.getBoolean("search.contacts", false) else sp.getBoolean("search_contacts", false)),
                searchFiles = preferences[SEARCH_FILES] ?: (if (sp.contains("search.files")) sp.getBoolean("search.files", false) else sp.getBoolean("search_files", false)),
                searchWeb = preferences[SEARCH_WEB] ?: (if (sp.contains("search.web")) sp.getBoolean("search.web", true) else sp.getBoolean("search_web", true)),
                searchCalculator = preferences[SEARCH_CALCULATOR] ?: (if (sp.contains("search.calculator")) sp.getBoolean("search.calculator", true) else sp.getBoolean("search_calculator", true)),
                searchCalendar = preferences[SEARCH_CALENDAR] ?: (if (sp.contains("search.calendar")) sp.getBoolean("search.calendar", true) else sp.getBoolean("search_calendar", true)),
                searchShortcuts = preferences[SEARCH_SHORTCUTS] ?: (if (sp.contains("search.shortcuts")) sp.getBoolean("search.shortcuts", true) else sp.getBoolean("search_shortcuts", true)),
                backgroundBlur = preferences[BACKGROUND_BLUR] ?: sp.getInt("search.background.blur", sp.getInt("background.blur", 50)),
                showWallpaper = preferences[SHOW_WALLPAPER] ?: (if (sp.contains("search.background.show.wall")) sp.getBoolean("search.background.show.wall", true) else sp.getBoolean("show_wallpaper", true)),
                backgroundTransparency = preferences[BACKGROUND_TRANSPARENCY] ?: sp.getInt("search.background.transparency", sp.getInt("background.transparency", 50)),
                pillOpacity = preferences[PILL_OPACITY] ?: sp.getInt("search.pill.opacity", sp.getInt("pill.opacity", 50)),
                searchEngine = preferences[SEARCH_ENGINE] ?: sp.getString("search.engine", "Google") ?: "Google",
                customSearchEngineUrl = preferences[CUSTOM_SEARCH_ENGINE_URL] ?: sp.getString("custom_search_engine_url", "") ?: "",
                filesHiddenFiles = preferences[FILES_HIDDEN_FILES] ?: (if (sp.contains("search.files.hidden.files")) sp.getBoolean("search.files.hidden.files", false) else sp.getBoolean("search_files_hidden_files", false)),
                filesThumbnails = preferences[FILES_THUMBNAILS] ?: (if (sp.contains("search.files.thumbnails")) sp.getBoolean("search.files.thumbnails", true) else sp.getBoolean("search_files_thumbnails", true)),
                appAnimations = preferences[APP_ANIMATIONS] ?: sp.getBoolean("app_animations", true),
                bottomSearch = preferences[BOTTOM_SEARCH] ?: (if (sp.contains("settings.bottom.search")) sp.getBoolean("settings.bottom.search", true) else sp.getBoolean("bottom_search", true)),
                bottomSearchResult = preferences[BOTTOM_SEARCH_RESULT] ?: (if (sp.contains("settings.bottom.search.result")) sp.getBoolean("settings.bottom.search.result", true) else sp.getBoolean("bottom_search_result", true)),
                tutorialCompleted = preferences[TUTORIAL_COMPLETED] ?: sp.getBoolean("tutorial_completed", false),
                forceTutorial = preferences[FORCE_TUTORIAL] ?: sp.getBoolean("force_tutorial", false),
                tutorialStep = preferences[TUTORIAL_STEP] ?: sp.getInt("tutorial_step", 0),
                gIconEnabled = preferences[G_ICON_ENABLED] ?: sp.getBoolean("g_icon_enabled", true),
                widgetShowVoice = preferences[WIDGET_SHOW_VOICE] ?: sp.getBoolean("widget_show_voice", true),
                widgetShowGemini = preferences[WIDGET_SHOW_GEMINI] ?: sp.getBoolean("widget_show_gemini", true),
                quickSearchYoutube = preferences[QUICK_SEARCH_YOUTUBE] ?: sp.getBoolean("quick_search_youtube", true),
                quickSearchWikipedia = preferences[QUICK_SEARCH_WIKIPEDIA] ?: sp.getBoolean("quick_search_wikipedia", true),
                quickSearchPlayStore = preferences[QUICK_SEARCH_PLAY_STORE] ?: sp.getBoolean("quick_search_play_store", true),
                quickSearchMaps = preferences[QUICK_SEARCH_MAPS] ?: sp.getBoolean("quick_search_maps", true),
                searchPills = preferences[SEARCH_PILLS] ?: sp.getString("search_pills", "com.android.chrome,com.google.android.apps.maps,com.google.android.youtube,com.android.vending,com.google.android.contacts,com.google.android.apps.nbu.files") ?: "com.android.chrome,com.google.android.apps.maps,com.google.android.youtube,com.android.vending,com.google.android.contacts,com.google.android.apps.nbu.files",
                widgetThemeStyle = preferences[WIDGET_THEME_STYLE] ?: (if (sp.contains("widget.theme.style")) sp.getString("widget.theme.style", "System Default") else sp.getString("widget_theme_style", "System Default")) ?: "System Default",
                hiddenApps = preferences[HIDDEN_APPS] ?: (sp.getStringSet("hidden_apps", emptySet())?.toSet() ?: emptySet()),
                appQuickLaunch = preferences[APP_QUICK_LAUNCH] ?: sp.getBoolean("app_quick_launch", false),
                contactDirectCall = preferences[CONTACT_DIRECT_CALL] ?: sp.getBoolean("contact_direct_call", false),
                shortcutInline = preferences[SHORTCUT_INLINE] ?: (if (sp.contains("shortcut.inline")) sp.getBoolean("shortcut.inline", true) else sp.getBoolean("shortcut_inline", true)),
                appFuzzySearch = preferences[APP_FUZZY_SEARCH] ?: (if (sp.contains("app.fuzzy.search")) sp.getBoolean("app.fuzzy.search", true) else sp.getBoolean("app_fuzzy_search", true)),
                quickSearchHorizontal = preferences[QUICK_SEARCH_HORIZONTAL] ?: (if (sp.contains("quick_search_horizontal")) sp.getBoolean("quick_search_horizontal", false) else sp.getBoolean("quick.search.horizontal", false)),
                webResultsCount = preferences[WEB_RESULTS_COUNT] ?: sp.getInt("web_results_count", 5),
                contactResultsCount = preferences[CONTACT_RESULTS_COUNT] ?: sp.getInt("contact_results_count", 5),
                fileResultsCount = preferences[FILE_RESULTS_COUNT] ?: sp.getInt("file_results_count", 5),
                shortcutResultsCount = preferences[SHORTCUT_RESULTS_COUNT] ?: sp.getInt("shortcut_results_count", 6),
                contextAwareQuickApps = preferences[CONTEXT_AWARE_QUICK_APPS] ?: sp.getBoolean("context_aware_quick_apps", false),
                smartClipboardSuggestions = preferences[SMART_CLIPBOARD_SUGGESTIONS] ?: sp.getBoolean("smart_clipboard_suggestions", false),
                activeIconPack = preferences[ACTIVE_ICON_PACK] ?: sp.getString("active_icon_pack", "system_default") ?: "system_default",
                customIconPills = preferences[CUSTOM_ICON_PILLS] ?: sp.getString("custom_icon_pills", "") ?: "",
                neverShowIconPackWarning = preferences[NEVER_SHOW_ICON_PACK_WARNING] ?: sp.getBoolean("never_show_icon_pack_warning", false),
                searchPreviousSearches = preferences[SEARCH_PREVIOUS_SEARCHES] ?: sp.getBoolean("search_previous_searches", true),
                customBangsJson = preferences[CUSTOM_BANGS_JSON] ?: sp.getString("custom_bangs_json", "[]") ?: "[]",
                searchSectionsConfigJson = preferences[SEARCH_SECTIONS_CONFIG_JSON] ?: sp.getString("search_sections_config_json", "") ?: "",
                adaptiveIconShape = preferences[ADAPTIVE_ICON_SHAPE] ?: sp.getString("adaptive_icon_shape", "SYSTEM_DEFAULT") ?: "SYSTEM_DEFAULT",
                dynamicIconMasking = preferences[DYNAMIC_ICON_MASKING] ?: sp.getBoolean("dynamic_icon_masking", true),
                diagnosticsOverlayEnabled = preferences[DIAGNOSTICS_OVERLAY_ENABLED] ?: sp.getBoolean("diagnostics_overlay_enabled", false),
                searchOverlayEnabled = preferences[SEARCH_OVERLAY_ENABLED] ?: sp.getBoolean("search_overlay_enabled", true),
                matrixAnimationEnabled = preferences[MATRIX_ANIMATION_ENABLED] ?: sp.getBoolean("matrix_animation_enabled", true),
                backToSearchOverlay = preferences[BACK_TO_SEARCH_OVERLAY] ?: sp.getBoolean("settings_back_to_search_overlay", true),
                disabledWebShortcuts = preferences[DISABLED_WEB_SHORTCUTS] ?: (sp.getStringSet("disabled_web_shortcuts", emptySet())?.toSet() ?: emptySet()),
                vibrationEnabled = preferences[VIBRATION] ?: sp.getBoolean("vibration_enabled", true)
            )
        }

    fun getInitialSettings(): IntelligentSearchSettings {
        val prefs = context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)
        val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val defaultTheme = if (isSystemDark) "Material Dark" else "Material Light"
        return IntelligentSearchSettings(
            theme = prefs.getString("night.mode", defaultTheme) ?: defaultTheme,
            searchApps = if (prefs.contains("search.apps")) prefs.getBoolean("search.apps", true) else prefs.getBoolean("search_apps", true),
            searchContacts = if (prefs.contains("search.contacts")) prefs.getBoolean("search.contacts", false) else prefs.getBoolean("search_contacts", false),
            searchFiles = if (prefs.contains("search.files")) prefs.getBoolean("search.files", false) else prefs.getBoolean("search_files", false),
            searchWeb = if (prefs.contains("search.web")) prefs.getBoolean("search.web", true) else prefs.getBoolean("search_web", true),
            searchCalculator = if (prefs.contains("search.calculator")) prefs.getBoolean("search.calculator", true) else prefs.getBoolean("search_calculator", true),
            searchCalendar = if (prefs.contains("search.calendar")) prefs.getBoolean("search.calendar", true) else prefs.getBoolean("search_calendar", true),
            searchShortcuts = if (prefs.contains("search.shortcuts")) prefs.getBoolean("search.shortcuts", true) else prefs.getBoolean("search_shortcuts", true),
            backgroundBlur = prefs.getInt("search.background.blur", prefs.getInt("background.blur", 50)),
            showWallpaper = if (prefs.contains("search.background.show.wall")) prefs.getBoolean("search.background.show.wall", true) else prefs.getBoolean("show_wallpaper", true),
            backgroundTransparency = prefs.getInt("search.background.transparency", prefs.getInt("background.transparency", 50)),
            pillOpacity = prefs.getInt("search.pill.opacity", prefs.getInt("pill.opacity", 50)),
            searchEngine = prefs.getString("search.engine", "Google") ?: "Google",
            customSearchEngineUrl = prefs.getString("custom_search_engine_url", "") ?: "",
            filesHiddenFiles = if (prefs.contains("search.files.hidden.files")) prefs.getBoolean("search.files.hidden.files", false) else prefs.getBoolean("search_files_hidden_files", false),
            filesThumbnails = if (prefs.contains("search.files.thumbnails")) prefs.getBoolean("search.files.thumbnails", true) else prefs.getBoolean("search_files_thumbnails", true),
            appAnimations = prefs.getBoolean("app_animations", true),
            bottomSearch = if (prefs.contains("settings.bottom.search")) prefs.getBoolean("settings.bottom.search", true) else prefs.getBoolean("bottom_search", true),
            bottomSearchResult = if (prefs.contains("settings.bottom.search.result")) prefs.getBoolean("settings.bottom.search.result", true) else prefs.getBoolean("bottom_search_result", true),
            tutorialCompleted = prefs.getBoolean("tutorial_completed", false),
            forceTutorial = prefs.getBoolean("force_tutorial", false),
            tutorialStep = prefs.getInt("tutorial_step", 0),
            gIconEnabled = prefs.getBoolean("g_icon_enabled", true),
            widgetShowVoice = prefs.getBoolean("widget_show_voice", true),
            widgetShowGemini = prefs.getBoolean("widget_show_gemini", true),
            quickSearchYoutube = prefs.getBoolean("quick_search_youtube", true),
            quickSearchWikipedia = prefs.getBoolean("quick_search_wikipedia", true),
            quickSearchPlayStore = prefs.getBoolean("quick_search_play_store", true),
            quickSearchMaps = prefs.getBoolean("quick_search_maps", true),
            searchPills = prefs.getString("search_pills", "com.android.chrome,com.google.android.apps.maps,com.google.android.youtube,com.android.vending,com.google.android.contacts,com.google.android.apps.nbu.files") ?: "com.android.chrome,com.google.android.apps.maps,com.google.android.youtube,com.android.vending,com.google.android.contacts,com.google.android.apps.nbu.files",
            widgetThemeStyle = (if (prefs.contains("widget.theme.style")) prefs.getString("widget.theme.style", "System Default") else prefs.getString("widget_theme_style", "System Default")) ?: "System Default",
            hiddenApps = prefs.getStringSet("hidden_apps", emptySet())?.toSet() ?: emptySet(),
            appQuickLaunch = prefs.getBoolean("app_quick_launch", false),
            contactDirectCall = prefs.getBoolean("contact_direct_call", false),
            shortcutInline = if (prefs.contains("shortcut.inline")) prefs.getBoolean("shortcut.inline", true) else prefs.getBoolean("shortcut_inline", true),
            appFuzzySearch = if (prefs.contains("app.fuzzy.search")) prefs.getBoolean("app.fuzzy.search", true) else prefs.getBoolean("app_fuzzy_search", true),
            quickSearchHorizontal = if (prefs.contains("quick_search_horizontal")) prefs.getBoolean("quick_search_horizontal", false) else prefs.getBoolean("quick.search.horizontal", false),
            webResultsCount = prefs.getInt("web_results_count", 5),
            contactResultsCount = prefs.getInt("contact_results_count", 5),
            fileResultsCount = prefs.getInt("file_results_count", 5),
            shortcutResultsCount = prefs.getInt("shortcut_results_count", 6),
            contextAwareQuickApps = prefs.getBoolean("context_aware_quick_apps", false),
            smartClipboardSuggestions = prefs.getBoolean("smart_clipboard_suggestions", false),
            activeIconPack = prefs.getString("active_icon_pack", "system_default") ?: "system_default",
            customIconPills = prefs.getString("custom_icon_pills", "") ?: "",
            neverShowIconPackWarning = prefs.getBoolean("never_show_icon_pack_warning", false),
            searchPreviousSearches = prefs.getBoolean("search_previous_searches", true),
            customBangsJson = prefs.getString("custom_bangs_json", "[]") ?: "[]",
            searchSectionsConfigJson = prefs.getString("search_sections_config_json", "") ?: "",
            adaptiveIconShape = prefs.getString("adaptive_icon_shape", "SYSTEM_DEFAULT") ?: "SYSTEM_DEFAULT",
            dynamicIconMasking = prefs.getBoolean("dynamic_icon_masking", true),
            diagnosticsOverlayEnabled = prefs.getBoolean("diagnostics_overlay_enabled", false),
            searchOverlayEnabled = prefs.getBoolean("search_overlay_enabled", true),
            matrixAnimationEnabled = prefs.getBoolean("matrix_animation_enabled", true),
            backToSearchOverlay = prefs.getBoolean("settings_back_to_search_overlay", true),
            disabledWebShortcuts = prefs.getStringSet("disabled_web_shortcuts", emptySet())?.toSet() ?: emptySet(),
            vibrationEnabled = prefs.getBoolean("vibration_enabled", true)
        )
    }

    suspend fun syncSharedPreferencesToDataStore() {
        val sp = context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)
        context.dataStore.edit { preferences ->
            if (!preferences.contains(THEME) && sp.contains("night.mode")) sp.getString("night.mode", null)?.let { preferences[THEME] = it }
            if (!preferences.contains(SEARCH_APPS) && (sp.contains("search.apps") || sp.contains("search_apps"))) preferences[SEARCH_APPS] = sp.getBoolean("search.apps", sp.getBoolean("search_apps", true))
            if (!preferences.contains(SEARCH_CONTACTS) && (sp.contains("search.contacts") || sp.contains("search_contacts"))) preferences[SEARCH_CONTACTS] = sp.getBoolean("search.contacts", sp.getBoolean("search_contacts", false))
            if (!preferences.contains(SEARCH_FILES) && (sp.contains("search.files") || sp.contains("search_files"))) preferences[SEARCH_FILES] = sp.getBoolean("search.files", sp.getBoolean("search_files", false))
            if (!preferences.contains(SEARCH_WEB) && (sp.contains("search.web") || sp.contains("search_web"))) preferences[SEARCH_WEB] = sp.getBoolean("search.web", sp.getBoolean("search_web", true))
            if (!preferences.contains(SEARCH_CALCULATOR) && (sp.contains("search.calculator") || sp.contains("search_calculator"))) preferences[SEARCH_CALCULATOR] = sp.getBoolean("search.calculator", sp.getBoolean("search_calculator", true))
            if (!preferences.contains(SEARCH_CALENDAR) && (sp.contains("search.calendar") || sp.contains("search_calendar"))) preferences[SEARCH_CALENDAR] = sp.getBoolean("search.calendar", sp.getBoolean("search_calendar", true))
            if (!preferences.contains(SEARCH_SHORTCUTS) && (sp.contains("search.shortcuts") || sp.contains("search_shortcuts"))) preferences[SEARCH_SHORTCUTS] = sp.getBoolean("search.shortcuts", sp.getBoolean("search_shortcuts", true))
            if (!preferences.contains(BACKGROUND_BLUR) && (sp.contains("search.background.blur") || sp.contains("background.blur"))) preferences[BACKGROUND_BLUR] = sp.getInt("search.background.blur", sp.getInt("background.blur", 50))
            if (!preferences.contains(SHOW_WALLPAPER) && (sp.contains("search.background.show.wall") || sp.contains("show.wallpaper"))) preferences[SHOW_WALLPAPER] = sp.getBoolean("search.background.show.wall", sp.getBoolean("show.wallpaper", true))
            if (!preferences.contains(BACKGROUND_TRANSPARENCY) && (sp.contains("search.background.transparency") || sp.contains("background.transparency"))) preferences[BACKGROUND_TRANSPARENCY] = sp.getInt("search.background.transparency", sp.getInt("background.transparency", 50))
            if (!preferences.contains(PILL_OPACITY) && (sp.contains("search.pill.opacity") || sp.contains("pill.opacity"))) preferences[PILL_OPACITY] = sp.getInt("search.pill.opacity", sp.getInt("pill.opacity", 50))
            if (!preferences.contains(SEARCH_ENGINE) && sp.contains("search.engine")) sp.getString("search.engine", null)?.let { preferences[SEARCH_ENGINE] = it }
            if (!preferences.contains(CUSTOM_SEARCH_ENGINE_URL) && sp.contains("custom_search_engine_url")) sp.getString("custom_search_engine_url", null)?.let { preferences[CUSTOM_SEARCH_ENGINE_URL] = it }
            if (!preferences.contains(FILES_HIDDEN_FILES) && (sp.contains("search.files.hidden.files") || sp.contains("search_files_hidden_files"))) preferences[FILES_HIDDEN_FILES] = sp.getBoolean("search.files.hidden.files", sp.getBoolean("search_files_hidden_files", false))
            if (!preferences.contains(FILES_THUMBNAILS) && (sp.contains("search.files.thumbnails") || sp.contains("search_files_thumbnails"))) preferences[FILES_THUMBNAILS] = sp.getBoolean("search.files.thumbnails", sp.getBoolean("search_files_thumbnails", true))
            if (!preferences.contains(APP_ANIMATIONS) && sp.contains("app_animations")) preferences[APP_ANIMATIONS] = sp.getBoolean("app_animations", true)
            if (!preferences.contains(BOTTOM_SEARCH) && (sp.contains("settings.bottom.search") || sp.contains("bottom_search"))) preferences[BOTTOM_SEARCH] = sp.getBoolean("settings.bottom.search", sp.getBoolean("bottom_search", true))
            if (!preferences.contains(BOTTOM_SEARCH_RESULT) && (sp.contains("settings.bottom.search.result") || sp.contains("bottom_search_result"))) preferences[BOTTOM_SEARCH_RESULT] = sp.getBoolean("settings.bottom.search.result", sp.getBoolean("bottom_search_result", true))
            if (!preferences.contains(TUTORIAL_COMPLETED) && sp.contains("tutorial_completed")) preferences[TUTORIAL_COMPLETED] = sp.getBoolean("tutorial_completed", false)
            if (!preferences.contains(FORCE_TUTORIAL) && sp.contains("force_tutorial")) preferences[FORCE_TUTORIAL] = sp.getBoolean("force_tutorial", false)
            if (!preferences.contains(TUTORIAL_STEP) && sp.contains("tutorial_step")) preferences[TUTORIAL_STEP] = sp.getInt("tutorial_step", 0)
            if (!preferences.contains(G_ICON_ENABLED) && sp.contains("g_icon_enabled")) preferences[G_ICON_ENABLED] = sp.getBoolean("g_icon_enabled", true)
            if (!preferences.contains(WIDGET_SHOW_VOICE) && sp.contains("widget_show_voice")) preferences[WIDGET_SHOW_VOICE] = sp.getBoolean("widget_show_voice", true)
            if (!preferences.contains(WIDGET_SHOW_GEMINI) && sp.contains("widget_show_gemini")) preferences[WIDGET_SHOW_GEMINI] = sp.getBoolean("widget_show_gemini", true)
            if (!preferences.contains(QUICK_SEARCH_YOUTUBE) && sp.contains("quick_search_youtube")) preferences[QUICK_SEARCH_YOUTUBE] = sp.getBoolean("quick_search_youtube", true)
            if (!preferences.contains(QUICK_SEARCH_WIKIPEDIA) && sp.contains("quick_search_wikipedia")) preferences[QUICK_SEARCH_WIKIPEDIA] = sp.getBoolean("quick_search_wikipedia", true)
            if (!preferences.contains(QUICK_SEARCH_PLAY_STORE) && sp.contains("quick_search_play_store")) preferences[QUICK_SEARCH_PLAY_STORE] = sp.getBoolean("quick_search_play_store", true)
            if (!preferences.contains(QUICK_SEARCH_MAPS) && sp.contains("quick_search_maps")) preferences[QUICK_SEARCH_MAPS] = sp.getBoolean("quick_search_maps", true)
            if (!preferences.contains(SEARCH_PILLS) && sp.contains("search_pills")) sp.getString("search_pills", null)?.let { preferences[SEARCH_PILLS] = it }
            if (!preferences.contains(WIDGET_THEME_STYLE) && (sp.contains("widget.theme.style") || sp.contains("widget_theme_style"))) sp.getString("widget.theme.style", sp.getString("widget_theme_style", null))?.let { preferences[WIDGET_THEME_STYLE] = it }
            if (!preferences.contains(HIDDEN_APPS) && sp.contains("hidden_apps")) sp.getStringSet("hidden_apps", null)?.let { preferences[HIDDEN_APPS] = it }
            if (!preferences.contains(APP_QUICK_LAUNCH) && sp.contains("app_quick_launch")) preferences[APP_QUICK_LAUNCH] = sp.getBoolean("app_quick_launch", false)
            if (!preferences.contains(CONTACT_DIRECT_CALL) && sp.contains("contact_direct_call")) preferences[CONTACT_DIRECT_CALL] = sp.getBoolean("contact_direct_call", false)
            if (!preferences.contains(SHORTCUT_INLINE) && (sp.contains("shortcut.inline") || sp.contains("shortcut_inline"))) preferences[SHORTCUT_INLINE] = sp.getBoolean("shortcut.inline", sp.getBoolean("shortcut_inline", true))
            if (!preferences.contains(APP_FUZZY_SEARCH) && (sp.contains("app.fuzzy.search") || sp.contains("app_fuzzy_search"))) preferences[APP_FUZZY_SEARCH] = sp.getBoolean("app.fuzzy.search", sp.getBoolean("app_fuzzy_search", true))
            if (!preferences.contains(QUICK_SEARCH_HORIZONTAL) && (sp.contains("quick_search_horizontal") || sp.contains("quick.search.horizontal"))) preferences[QUICK_SEARCH_HORIZONTAL] = sp.getBoolean("quick_search_horizontal", sp.getBoolean("quick.search.horizontal", false))
            if (!preferences.contains(WEB_RESULTS_COUNT) && sp.contains("web_results_count")) preferences[WEB_RESULTS_COUNT] = sp.getInt("web_results_count", 5)
            if (!preferences.contains(CONTACT_RESULTS_COUNT) && sp.contains("contact_results_count")) preferences[CONTACT_RESULTS_COUNT] = sp.getInt("contact_results_count", 5)
            if (!preferences.contains(FILE_RESULTS_COUNT) && sp.contains("file_results_count")) preferences[FILE_RESULTS_COUNT] = sp.getInt("file_results_count", 5)
            if (!preferences.contains(SHORTCUT_RESULTS_COUNT) && sp.contains("shortcut_results_count")) preferences[SHORTCUT_RESULTS_COUNT] = sp.getInt("shortcut_results_count", 6)
            if (!preferences.contains(CONTEXT_AWARE_QUICK_APPS) && sp.contains("context_aware_quick_apps")) preferences[CONTEXT_AWARE_QUICK_APPS] = sp.getBoolean("context_aware_quick_apps", false)
            if (!preferences.contains(SMART_CLIPBOARD_SUGGESTIONS) && sp.contains("smart_clipboard_suggestions")) preferences[SMART_CLIPBOARD_SUGGESTIONS] = sp.getBoolean("smart_clipboard_suggestions", false)
            if (!preferences.contains(ACTIVE_ICON_PACK) && sp.contains("active_icon_pack")) sp.getString("active_icon_pack", null)?.let { preferences[ACTIVE_ICON_PACK] = it }
            if (!preferences.contains(CUSTOM_ICON_PILLS) && sp.contains("custom_icon_pills")) sp.getString("custom_icon_pills", null)?.let { preferences[CUSTOM_ICON_PILLS] = it }
            if (!preferences.contains(NEVER_SHOW_ICON_PACK_WARNING) && sp.contains("never_show_icon_pack_warning")) preferences[NEVER_SHOW_ICON_PACK_WARNING] = sp.getBoolean("never_show_icon_pack_warning", false)
            if (!preferences.contains(SEARCH_PREVIOUS_SEARCHES) && sp.contains("search_previous_searches")) preferences[SEARCH_PREVIOUS_SEARCHES] = sp.getBoolean("search_previous_searches", true)
            if (!preferences.contains(CUSTOM_BANGS_JSON) && sp.contains("custom_bangs_json")) sp.getString("custom_bangs_json", null)?.let { preferences[CUSTOM_BANGS_JSON] = it }
            if (!preferences.contains(SEARCH_SECTIONS_CONFIG_JSON) && sp.contains("search_sections_config_json")) sp.getString("search_sections_config_json", null)?.let { preferences[SEARCH_SECTIONS_CONFIG_JSON] = it }
            if (!preferences.contains(ADAPTIVE_ICON_SHAPE) && sp.contains("adaptive_icon_shape")) sp.getString("adaptive_icon_shape", null)?.let { preferences[ADAPTIVE_ICON_SHAPE] = it }
            if (!preferences.contains(DYNAMIC_ICON_MASKING) && sp.contains("dynamic_icon_masking")) preferences[DYNAMIC_ICON_MASKING] = sp.getBoolean("dynamic_icon_masking", true)
            if (!preferences.contains(DIAGNOSTICS_OVERLAY_ENABLED) && sp.contains("diagnostics_overlay_enabled")) preferences[DIAGNOSTICS_OVERLAY_ENABLED] = sp.getBoolean("diagnostics_overlay_enabled", false)
            if (!preferences.contains(SEARCH_OVERLAY_ENABLED) && sp.contains("search_overlay_enabled")) preferences[SEARCH_OVERLAY_ENABLED] = sp.getBoolean("search_overlay_enabled", true)
            if (!preferences.contains(MATRIX_ANIMATION_ENABLED) && sp.contains("matrix_animation_enabled")) preferences[MATRIX_ANIMATION_ENABLED] = sp.getBoolean("matrix_animation_enabled", true)
            if (!preferences.contains(BACK_TO_SEARCH_OVERLAY) && sp.contains("settings_back_to_search_overlay")) preferences[BACK_TO_SEARCH_OVERLAY] = sp.getBoolean("settings_back_to_search_overlay", true)
            if (!preferences.contains(DISABLED_WEB_SHORTCUTS) && sp.contains("disabled_web_shortcuts")) sp.getStringSet("disabled_web_shortcuts", null)?.let { preferences[DISABLED_WEB_SHORTCUTS] = it }
            if (!preferences.contains(VIBRATION) && sp.contains("vibration_enabled")) preferences[VIBRATION] = sp.getBoolean("vibration_enabled", true)
        }
    }

    suspend fun <T> updateSetting(key: Preferences.Key<T>, value: T) {
        val prefs = context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        val legacyKey = when (key.name) {
            "search.apps" -> "search_apps"
            "search_apps" -> "search.apps"
            "search.contacts" -> "search_contacts"
            "search_contacts" -> "search.contacts"
            "search.files" -> "search_files"
            "search_files" -> "search.files"
            "search.web" -> "search_web"
            "search_web" -> "search.web"
            "search.calculator" -> "search_calculator"
            "search_calculator" -> "search.calculator"
            "search.calendar" -> "search_calendar"
            "search_calendar" -> "search.calendar"
            "search.shortcuts" -> "search_shortcuts"
            "search_shortcuts" -> "search.shortcuts"
            "search.background.show.wall" -> "show_wallpaper"
            "show_wallpaper" -> "search.background.show.wall"
            "search.background.blur" -> "background.blur"
            "background.blur" -> "search.background.blur"
            "search.background.transparency" -> "background.transparency"
            "background.transparency" -> "search.background.transparency"
            "search.pill.opacity" -> "pill.opacity"
            "pill.opacity" -> "search.pill.opacity"
            "search.files.hidden.files" -> "search_files_hidden_files"
            "search_files_hidden_files" -> "search.files.hidden.files"
            "search.files.thumbnails" -> "search_files_thumbnails"
            "search_files_thumbnails" -> "search.files.thumbnails"
            "settings.bottom.search" -> "bottom_search"
            "bottom_search" -> "settings.bottom.search"
            "settings.bottom.search.result" -> "bottom_search_result"
            "bottom_search_result" -> "settings.bottom.search.result"
            "shortcut.inline" -> "shortcut_inline"
            "shortcut_inline" -> "shortcut.inline"
            "app.fuzzy.search" -> "app_fuzzy_search"
            "app_fuzzy_search" -> "app.fuzzy.search"
            "quick_search_horizontal" -> "quick.search.horizontal"
            "quick.search.horizontal" -> "quick_search_horizontal"
            "widget.theme.style" -> "widget_theme_style"
            "widget_theme_style" -> "widget.theme.style"
            "night.mode" -> "theme"
            "theme" -> "night.mode"
            else -> null
        }

        when (value) {
            is Boolean -> {
                editor.putBoolean(key.name, value)
                if (legacyKey != null) editor.putBoolean(legacyKey, value)
            }
            is Int -> {
                editor.putInt(key.name, value)
                if (legacyKey != null) editor.putInt(legacyKey, value)
            }
            is Long -> {
                editor.putLong(key.name, value)
                if (legacyKey != null) editor.putLong(legacyKey, value)
            }
            is Float -> {
                editor.putFloat(key.name, value)
                if (legacyKey != null) editor.putFloat(legacyKey, value)
            }
            is String -> {
                editor.putString(key.name, value)
                if (legacyKey != null) editor.putString(legacyKey, value)
            }
            is Set<*> -> {
                @Suppress("UNCHECKED_CAST")
                val stringSet = value as? Set<String> ?: emptySet()
                editor.putStringSet(key.name, HashSet(stringSet))
                if (legacyKey != null) editor.putStringSet(legacyKey, HashSet(stringSet))
            }
        }
        editor.apply()

        context.dataStore.edit { preferences ->
            preferences[key] = value
        }
    }
}
