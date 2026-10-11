package com.pixel.intelligentsearch.core.data

import android.content.Context
import android.os.UserManager
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
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
class SettingsManager @Inject constructor(@param:ApplicationContext private val context: Context) {
    companion object {
        private const val TAG = "SettingsManager"
        private const val PREFS_NAME = "PREFERENCES_CUSTOMISATIONS"

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
        val DATASTORE_MIGRATED = booleanPreferencesKey("datastore_migration_v1_completed")
    }

    private val defaultSettings = IntelligentSearchSettings()

    @Volatile
    private var cachedSettings: IntelligentSearchSettings = defaultSettings

    private fun getSafeContext(): Context {
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        val isUnlocked = userManager?.isUserUnlocked ?: true
        return if (!isUnlocked && !context.isDeviceProtectedStorage) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
    }

    private fun getSafePrefs(): android.content.SharedPreferences {
        return getSafeContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    val settingsFlow: Flow<IntelligentSearchSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.w(TAG, "DataStore read error, falling back to defaults: ${exception.message}")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val s = IntelligentSearchSettings(
                theme = preferences[THEME] ?: defaultSettings.theme,
                searchApps = preferences[SEARCH_APPS] ?: defaultSettings.searchApps,
                searchContacts = preferences[SEARCH_CONTACTS] ?: defaultSettings.searchContacts,
                searchFiles = preferences[SEARCH_FILES] ?: defaultSettings.searchFiles,
                searchWeb = preferences[SEARCH_WEB] ?: defaultSettings.searchWeb,
                searchCalculator = preferences[SEARCH_CALCULATOR] ?: defaultSettings.searchCalculator,
                searchCalendar = preferences[SEARCH_CALENDAR] ?: defaultSettings.searchCalendar,
                searchShortcuts = preferences[SEARCH_SHORTCUTS] ?: defaultSettings.searchShortcuts,
                backgroundBlur = preferences[BACKGROUND_BLUR] ?: defaultSettings.backgroundBlur,
                showWallpaper = preferences[SHOW_WALLPAPER] ?: defaultSettings.showWallpaper,
                backgroundTransparency = preferences[BACKGROUND_TRANSPARENCY] ?: defaultSettings.backgroundTransparency,
                pillOpacity = preferences[PILL_OPACITY] ?: defaultSettings.pillOpacity,
                searchEngine = preferences[SEARCH_ENGINE] ?: defaultSettings.searchEngine,
                customSearchEngineUrl = preferences[CUSTOM_SEARCH_ENGINE_URL] ?: defaultSettings.customSearchEngineUrl,
                filesHiddenFiles = preferences[FILES_HIDDEN_FILES] ?: defaultSettings.filesHiddenFiles,
                filesThumbnails = preferences[FILES_THUMBNAILS] ?: defaultSettings.filesThumbnails,
                appAnimations = preferences[APP_ANIMATIONS] ?: defaultSettings.appAnimations,
                bottomSearch = preferences[BOTTOM_SEARCH] ?: defaultSettings.bottomSearch,
                bottomSearchResult = preferences[BOTTOM_SEARCH_RESULT] ?: defaultSettings.bottomSearchResult,
                tutorialCompleted = preferences[TUTORIAL_COMPLETED] ?: defaultSettings.tutorialCompleted,
                forceTutorial = preferences[FORCE_TUTORIAL] ?: defaultSettings.forceTutorial,
                tutorialStep = preferences[TUTORIAL_STEP] ?: defaultSettings.tutorialStep,
                gIconEnabled = preferences[G_ICON_ENABLED] ?: defaultSettings.gIconEnabled,
                widgetShowVoice = preferences[WIDGET_SHOW_VOICE] ?: defaultSettings.widgetShowVoice,
                widgetShowGemini = preferences[WIDGET_SHOW_GEMINI] ?: defaultSettings.widgetShowGemini,
                quickSearchYoutube = preferences[QUICK_SEARCH_YOUTUBE] ?: defaultSettings.quickSearchYoutube,
                quickSearchWikipedia = preferences[QUICK_SEARCH_WIKIPEDIA] ?: defaultSettings.quickSearchWikipedia,
                quickSearchPlayStore = preferences[QUICK_SEARCH_PLAY_STORE] ?: defaultSettings.quickSearchPlayStore,
                quickSearchMaps = preferences[QUICK_SEARCH_MAPS] ?: defaultSettings.quickSearchMaps,
                searchPills = preferences[SEARCH_PILLS] ?: defaultSettings.searchPills,
                widgetThemeStyle = preferences[WIDGET_THEME_STYLE] ?: defaultSettings.widgetThemeStyle,
                hiddenApps = preferences[HIDDEN_APPS] ?: defaultSettings.hiddenApps,
                appQuickLaunch = preferences[APP_QUICK_LAUNCH] ?: defaultSettings.appQuickLaunch,
                contactDirectCall = preferences[CONTACT_DIRECT_CALL] ?: defaultSettings.contactDirectCall,
                shortcutInline = preferences[SHORTCUT_INLINE] ?: defaultSettings.shortcutInline,
                appFuzzySearch = preferences[APP_FUZZY_SEARCH] ?: defaultSettings.appFuzzySearch,
                quickSearchHorizontal = preferences[QUICK_SEARCH_HORIZONTAL] ?: defaultSettings.quickSearchHorizontal,
                webResultsCount = preferences[WEB_RESULTS_COUNT] ?: defaultSettings.webResultsCount,
                contactResultsCount = preferences[CONTACT_RESULTS_COUNT] ?: defaultSettings.contactResultsCount,
                fileResultsCount = preferences[FILE_RESULTS_COUNT] ?: defaultSettings.fileResultsCount,
                shortcutResultsCount = preferences[SHORTCUT_RESULTS_COUNT] ?: defaultSettings.shortcutResultsCount,
                contextAwareQuickApps = preferences[CONTEXT_AWARE_QUICK_APPS] ?: defaultSettings.contextAwareQuickApps,
                smartClipboardSuggestions = preferences[SMART_CLIPBOARD_SUGGESTIONS] ?: defaultSettings.smartClipboardSuggestions,
                activeIconPack = preferences[ACTIVE_ICON_PACK] ?: defaultSettings.activeIconPack,
                customIconPills = preferences[CUSTOM_ICON_PILLS] ?: defaultSettings.customIconPills,
                neverShowIconPackWarning = preferences[NEVER_SHOW_ICON_PACK_WARNING] ?: defaultSettings.neverShowIconPackWarning,
                searchPreviousSearches = preferences[SEARCH_PREVIOUS_SEARCHES] ?: defaultSettings.searchPreviousSearches,
                customBangsJson = preferences[CUSTOM_BANGS_JSON] ?: defaultSettings.customBangsJson,
                searchSectionsConfigJson = preferences[SEARCH_SECTIONS_CONFIG_JSON] ?: defaultSettings.searchSectionsConfigJson,
                adaptiveIconShape = preferences[ADAPTIVE_ICON_SHAPE] ?: defaultSettings.adaptiveIconShape,
                dynamicIconMasking = preferences[DYNAMIC_ICON_MASKING] ?: defaultSettings.dynamicIconMasking,
                diagnosticsOverlayEnabled = preferences[DIAGNOSTICS_OVERLAY_ENABLED] ?: defaultSettings.diagnosticsOverlayEnabled,
                searchOverlayEnabled = preferences[SEARCH_OVERLAY_ENABLED] ?: defaultSettings.searchOverlayEnabled,
                matrixAnimationEnabled = preferences[MATRIX_ANIMATION_ENABLED] ?: defaultSettings.matrixAnimationEnabled,
                backToSearchOverlay = preferences[BACK_TO_SEARCH_OVERLAY] ?: defaultSettings.backToSearchOverlay,
                disabledWebShortcuts = preferences[DISABLED_WEB_SHORTCUTS] ?: defaultSettings.disabledWebShortcuts,
                vibrationEnabled = preferences[VIBRATION] ?: defaultSettings.vibrationEnabled
            )
            cachedSettings = s
            s
        }

    /**
     * Synchronous snapshot backed by memory or SharedPreferences migration fallback,
     * ensuring fast first-frame rendering without desynchronization.
     */
    fun getInitialSettings(): IntelligentSearchSettings {
        return if (cachedSettings != defaultSettings) {
            cachedSettings
        } else {
            readLegacySettings()
        }
    }

    /** Reads settings from the SharedPreferences mirror, honouring every historical key spelling. */
    private fun readLegacySettings(): IntelligentSearchSettings {
        val prefs = try {
            getSafePrefs()
        } catch (e: Exception) {
            Log.w(TAG, "Error accessing SharedPreferences: ${e.message}")
            null
        }
        return IntelligentSearchSettings(
            theme = prefs?.getString("night.mode", "System") ?: "System",
            searchApps = if (prefs?.contains("search.apps") == true) prefs.getBoolean("search.apps", true) else prefs?.getBoolean("search_apps", true) ?: true,
            searchContacts = if (prefs?.contains("search.contacts") == true) prefs.getBoolean("search.contacts", false) else prefs?.getBoolean("search_contacts", false) ?: false,
            searchFiles = if (prefs?.contains("search.files") == true) prefs.getBoolean("search.files", false) else prefs?.getBoolean("search_files", false) ?: false,
            searchWeb = if (prefs?.contains("search.web") == true) prefs.getBoolean("search.web", true) else prefs?.getBoolean("search_web", true) ?: true,
            searchCalculator = if (prefs?.contains("search.calculator") == true) prefs.getBoolean("search.calculator", true) else prefs?.getBoolean("search_calculator", true) ?: true,
            searchCalendar = if (prefs?.contains("search.calendar") == true) prefs.getBoolean("search.calendar", true) else prefs?.getBoolean("search_calendar", true) ?: true,
            searchShortcuts = if (prefs?.contains("search.shortcuts") == true) prefs.getBoolean("search.shortcuts", true) else prefs?.getBoolean("search_shortcuts", true) ?: true,
            backgroundBlur = prefs?.getInt("search.background.blur", prefs.getInt("background.blur", 50)) ?: 50,
            showWallpaper = if (prefs?.contains("search.background.show.wall") == true) prefs.getBoolean("search.background.show.wall", true) else prefs?.getBoolean("show_wallpaper", prefs.getBoolean("show.wallpaper", true)) ?: true,
            backgroundTransparency = prefs?.getInt("search.background.transparency", prefs.getInt("background.transparency", 50)) ?: 50,
            pillOpacity = prefs?.getInt("search.pill.opacity", prefs.getInt("pill.opacity", 50)) ?: 50,
            searchEngine = prefs?.getString("search.engine", "Google") ?: "Google",
            customSearchEngineUrl = prefs?.getString("custom_search_engine_url", "") ?: "",
            filesHiddenFiles = if (prefs?.contains("search.files.hidden.files") == true) prefs.getBoolean("search.files.hidden.files", false) else prefs?.getBoolean("search_files_hidden_files", false) ?: false,
            filesThumbnails = if (prefs?.contains("search.files.thumbnails") == true) prefs.getBoolean("search.files.thumbnails", true) else prefs?.getBoolean("search_files_thumbnails", true) ?: true,
            appAnimations = prefs?.getBoolean("app_animations", true) ?: true,
            bottomSearch = if (prefs?.contains("settings.bottom.search") == true) prefs.getBoolean("settings.bottom.search", true) else prefs?.getBoolean("bottom_search", true) ?: true,
            bottomSearchResult = if (prefs?.contains("settings.bottom.search.result") == true) prefs.getBoolean("settings.bottom.search.result", true) else prefs?.getBoolean("bottom_search_result", true) ?: true,
            tutorialCompleted = prefs?.getBoolean("tutorial_completed", false) ?: false,
            forceTutorial = prefs?.getBoolean("force_tutorial", false) ?: false,
            tutorialStep = prefs?.getInt("tutorial_step", 0) ?: 0,
            gIconEnabled = prefs?.getBoolean("g_icon_enabled", true) ?: true,
            widgetShowVoice = prefs?.getBoolean("widget_show_voice", true) ?: true,
            widgetShowGemini = prefs?.getBoolean("widget_show_gemini", true) ?: true,
            quickSearchYoutube = prefs?.getBoolean("quick_search_youtube", true) ?: true,
            quickSearchWikipedia = prefs?.getBoolean("quick_search_wikipedia", true) ?: true,
            quickSearchPlayStore = prefs?.getBoolean("quick_search_play_store", true) ?: true,
            quickSearchMaps = prefs?.getBoolean("quick_search_maps", true) ?: true,
            searchPills = prefs?.getString("search_pills", "com.android.chrome,com.google.android.apps.maps,com.google.android.youtube,com.android.vending,com.google.android.contacts,com.google.android.apps.nbu.files") ?: "com.android.chrome,com.google.android.apps.maps,com.google.android.youtube,com.android.vending,com.google.android.contacts,com.google.android.apps.nbu.files",
            widgetThemeStyle = (if (prefs?.contains("widget.theme.style") == true) prefs.getString("widget.theme.style", "System Default") else prefs?.getString("widget_theme_style", "System Default")) ?: "System Default",
            hiddenApps = prefs?.getStringSet("hidden_apps", emptySet())?.toSet() ?: emptySet(),
            appQuickLaunch = prefs?.getBoolean("app_quick_launch", false) ?: false,
            contactDirectCall = prefs?.getBoolean("contact_direct_call", false) ?: false,
            shortcutInline = if (prefs?.contains("shortcut.inline") == true) prefs.getBoolean("shortcut.inline", true) else prefs?.getBoolean("shortcut_inline", true) ?: true,
            appFuzzySearch = if (prefs?.contains("app.fuzzy.search") == true) prefs.getBoolean("app.fuzzy.search", true) else prefs?.getBoolean("app_fuzzy_search", true) ?: true,
            quickSearchHorizontal = if (prefs?.contains("quick_search_horizontal") == true) prefs.getBoolean("quick_search_horizontal", false) else prefs?.getBoolean("quick.search.horizontal", false) ?: false,
            webResultsCount = prefs?.getInt("web_results_count", 5) ?: 5,
            contactResultsCount = prefs?.getInt("contact_results_count", 5) ?: 5,
            fileResultsCount = prefs?.getInt("file_results_count", 5) ?: 5,
            shortcutResultsCount = prefs?.getInt("shortcut_results_count", 6) ?: 6,
            contextAwareQuickApps = prefs?.getBoolean("context_aware_quick_apps", false) ?: false,
            smartClipboardSuggestions = prefs?.getBoolean("smart_clipboard_suggestions", false) ?: false,
            activeIconPack = prefs?.getString("active_icon_pack", "system_default") ?: "system_default",
            customIconPills = prefs?.getString("custom_icon_pills", "") ?: "",
            neverShowIconPackWarning = prefs?.getBoolean("never_show_icon_pack_warning", false) ?: false,
            searchPreviousSearches = prefs?.getBoolean("search_previous_searches", true) ?: true,
            customBangsJson = prefs?.getString("custom_bangs_json", "[]") ?: "[]",
            searchSectionsConfigJson = prefs?.getString("search_sections_config_json", "") ?: "",
            adaptiveIconShape = prefs?.getString("adaptive_icon_shape", "SYSTEM_DEFAULT") ?: "SYSTEM_DEFAULT",
            dynamicIconMasking = prefs?.getBoolean("dynamic_icon_masking", true) ?: true,
            diagnosticsOverlayEnabled = prefs?.getBoolean("diagnostics_overlay_enabled", false) ?: false,
            searchOverlayEnabled = prefs?.getBoolean("search_overlay_enabled", true) ?: true,
            matrixAnimationEnabled = prefs?.getBoolean("matrix_animation_enabled", true) ?: true,
            backToSearchOverlay = prefs?.getBoolean("settings_back_to_search_overlay", true) ?: true,
            disabledWebShortcuts = prefs?.getStringSet("disabled_web_shortcuts", emptySet())?.toSet() ?: emptySet(),
            vibrationEnabled = prefs?.getBoolean("vibration_enabled", true) ?: true
        )
    }

    suspend fun syncSharedPreferencesToDataStore(overwrite: Boolean = false) {
        try {
            val sp = getSafePrefs()
            context.dataStore.edit { preferences ->
                if (!overwrite && preferences[DATASTORE_MIGRATED] == true) {
                    return@edit
                }
                if ((overwrite || !preferences.contains(THEME)) && sp.contains("night.mode")) sp.getString("night.mode", null)?.let { preferences[THEME] = it }
                if ((overwrite || !preferences.contains(SEARCH_APPS)) && (sp.contains("search.apps") || sp.contains("search_apps"))) preferences[SEARCH_APPS] = sp.getBoolean("search.apps", sp.getBoolean("search_apps", true))
                if ((overwrite || !preferences.contains(SEARCH_CONTACTS)) && (sp.contains("search.contacts") || sp.contains("search_contacts"))) preferences[SEARCH_CONTACTS] = sp.getBoolean("search.contacts", sp.getBoolean("search_contacts", false))
                if ((overwrite || !preferences.contains(SEARCH_FILES)) && (sp.contains("search.files") || sp.contains("search_files"))) preferences[SEARCH_FILES] = sp.getBoolean("search.files", sp.getBoolean("search_files", false))
                if ((overwrite || !preferences.contains(SEARCH_WEB)) && (sp.contains("search.web") || sp.contains("search_web"))) preferences[SEARCH_WEB] = sp.getBoolean("search.web", sp.getBoolean("search_web", true))
                if ((overwrite || !preferences.contains(SEARCH_CALCULATOR)) && (sp.contains("search.calculator") || sp.contains("search_calculator"))) preferences[SEARCH_CALCULATOR] = sp.getBoolean("search.calculator", sp.getBoolean("search_calculator", true))
                if ((overwrite || !preferences.contains(SEARCH_CALENDAR)) && (sp.contains("search.calendar") || sp.contains("search_calendar"))) preferences[SEARCH_CALENDAR] = sp.getBoolean("search.calendar", sp.getBoolean("search_calendar", true))
                if ((overwrite || !preferences.contains(SEARCH_SHORTCUTS)) && (sp.contains("search.shortcuts") || sp.contains("search_shortcuts"))) preferences[SEARCH_SHORTCUTS] = sp.getBoolean("search.shortcuts", sp.getBoolean("search_shortcuts", true))
                if ((overwrite || !preferences.contains(BACKGROUND_BLUR)) && (sp.contains("search.background.blur") || sp.contains("background.blur"))) preferences[BACKGROUND_BLUR] = sp.getInt("search.background.blur", sp.getInt("background.blur", 50))
                if ((overwrite || !preferences.contains(SHOW_WALLPAPER)) && (sp.contains("search.background.show.wall") || sp.contains("show_wallpaper") || sp.contains("show.wallpaper"))) preferences[SHOW_WALLPAPER] = sp.getBoolean("search.background.show.wall", sp.getBoolean("show_wallpaper", sp.getBoolean("show.wallpaper", true)))
                if ((overwrite || !preferences.contains(BACKGROUND_TRANSPARENCY)) && (sp.contains("search.background.transparency") || sp.contains("background.transparency"))) preferences[BACKGROUND_TRANSPARENCY] = sp.getInt("search.background.transparency", sp.getInt("background.transparency", 50))
                if ((overwrite || !preferences.contains(PILL_OPACITY)) && (sp.contains("search.pill.opacity") || sp.contains("pill.opacity"))) preferences[PILL_OPACITY] = sp.getInt("search.pill.opacity", sp.getInt("pill.opacity", 50))
                if ((overwrite || !preferences.contains(SEARCH_ENGINE)) && sp.contains("search.engine")) sp.getString("search.engine", null)?.let { preferences[SEARCH_ENGINE] = it }
                if ((overwrite || !preferences.contains(CUSTOM_SEARCH_ENGINE_URL)) && sp.contains("custom_search_engine_url")) sp.getString("custom_search_engine_url", null)?.let { preferences[CUSTOM_SEARCH_ENGINE_URL] = it }
                if ((overwrite || !preferences.contains(FILES_HIDDEN_FILES)) && (sp.contains("search.files.hidden.files") || sp.contains("search_files_hidden_files"))) preferences[FILES_HIDDEN_FILES] = sp.getBoolean("search.files.hidden.files", sp.getBoolean("search_files_hidden_files", false))
                if ((overwrite || !preferences.contains(FILES_THUMBNAILS)) && (sp.contains("search.files.thumbnails") || sp.contains("search_files_thumbnails"))) preferences[FILES_THUMBNAILS] = sp.getBoolean("search.files.thumbnails", sp.getBoolean("search_files_thumbnails", true))
                if ((overwrite || !preferences.contains(APP_ANIMATIONS)) && sp.contains("app_animations")) preferences[APP_ANIMATIONS] = sp.getBoolean("app_animations", true)
                if ((overwrite || !preferences.contains(BOTTOM_SEARCH)) && (sp.contains("settings.bottom.search") || sp.contains("bottom_search"))) preferences[BOTTOM_SEARCH] = sp.getBoolean("settings.bottom.search", sp.getBoolean("bottom_search", true))
                if ((overwrite || !preferences.contains(BOTTOM_SEARCH_RESULT)) && (sp.contains("settings.bottom.search.result") || sp.contains("bottom_search_result"))) preferences[BOTTOM_SEARCH_RESULT] = sp.getBoolean("settings.bottom.search.result", sp.getBoolean("bottom_search_result", true))
                if ((overwrite || !preferences.contains(TUTORIAL_COMPLETED)) && sp.contains("tutorial_completed")) preferences[TUTORIAL_COMPLETED] = sp.getBoolean("tutorial_completed", false)
                if ((overwrite || !preferences.contains(FORCE_TUTORIAL)) && sp.contains("force_tutorial")) preferences[FORCE_TUTORIAL] = sp.getBoolean("force_tutorial", false)
                if ((overwrite || !preferences.contains(TUTORIAL_STEP)) && sp.contains("tutorial_step")) preferences[TUTORIAL_STEP] = sp.getInt("tutorial_step", 0)
                if ((overwrite || !preferences.contains(G_ICON_ENABLED)) && sp.contains("g_icon_enabled")) preferences[G_ICON_ENABLED] = sp.getBoolean("g_icon_enabled", true)
                if ((overwrite || !preferences.contains(WIDGET_SHOW_VOICE)) && sp.contains("widget_show_voice")) preferences[WIDGET_SHOW_VOICE] = sp.getBoolean("widget_show_voice", true)
                if ((overwrite || !preferences.contains(WIDGET_SHOW_GEMINI)) && sp.contains("widget_show_gemini")) preferences[WIDGET_SHOW_GEMINI] = sp.getBoolean("widget_show_gemini", true)
                if ((overwrite || !preferences.contains(QUICK_SEARCH_YOUTUBE)) && sp.contains("quick_search_youtube")) preferences[QUICK_SEARCH_YOUTUBE] = sp.getBoolean("quick_search_youtube", true)
                if ((overwrite || !preferences.contains(QUICK_SEARCH_WIKIPEDIA)) && sp.contains("quick_search_wikipedia")) preferences[QUICK_SEARCH_WIKIPEDIA] = sp.getBoolean("quick_search_wikipedia", true)
                if ((overwrite || !preferences.contains(QUICK_SEARCH_PLAY_STORE)) && sp.contains("quick_search_play_store")) preferences[QUICK_SEARCH_PLAY_STORE] = sp.getBoolean("quick_search_play_store", true)
                if ((overwrite || !preferences.contains(QUICK_SEARCH_MAPS)) && sp.contains("quick_search_maps")) preferences[QUICK_SEARCH_MAPS] = sp.getBoolean("quick_search_maps", true)
                if ((overwrite || !preferences.contains(SEARCH_PILLS)) && sp.contains("search_pills")) sp.getString("search_pills", null)?.let { preferences[SEARCH_PILLS] = it }
                if ((overwrite || !preferences.contains(WIDGET_THEME_STYLE)) && (sp.contains("widget.theme.style") || sp.contains("widget_theme_style"))) sp.getString("widget.theme.style", sp.getString("widget_theme_style", null))?.let { preferences[WIDGET_THEME_STYLE] = it }
                if ((overwrite || !preferences.contains(HIDDEN_APPS)) && sp.contains("hidden_apps")) sp.getStringSet("hidden_apps", null)?.let { preferences[HIDDEN_APPS] = it }
                if ((overwrite || !preferences.contains(APP_QUICK_LAUNCH)) && sp.contains("app_quick_launch")) preferences[APP_QUICK_LAUNCH] = sp.getBoolean("app_quick_launch", false)
                if ((overwrite || !preferences.contains(CONTACT_DIRECT_CALL)) && sp.contains("contact_direct_call")) preferences[CONTACT_DIRECT_CALL] = sp.getBoolean("contact_direct_call", false)
                if ((overwrite || !preferences.contains(SHORTCUT_INLINE)) && (sp.contains("shortcut.inline") || sp.contains("shortcut_inline"))) preferences[SHORTCUT_INLINE] = sp.getBoolean("shortcut.inline", sp.getBoolean("shortcut_inline", true))
                if ((overwrite || !preferences.contains(APP_FUZZY_SEARCH)) && (sp.contains("app.fuzzy.search") || sp.contains("app_fuzzy_search"))) preferences[APP_FUZZY_SEARCH] = sp.getBoolean("app.fuzzy.search", sp.getBoolean("app_fuzzy_search", true))
                if ((overwrite || !preferences.contains(QUICK_SEARCH_HORIZONTAL)) && (sp.contains("quick_search_horizontal") || sp.contains("quick.search.horizontal"))) preferences[QUICK_SEARCH_HORIZONTAL] = sp.getBoolean("quick_search_horizontal", sp.getBoolean("quick.search.horizontal", false))
                if ((overwrite || !preferences.contains(WEB_RESULTS_COUNT)) && sp.contains("web_results_count")) preferences[WEB_RESULTS_COUNT] = sp.getInt("web_results_count", 5)
                if ((overwrite || !preferences.contains(CONTACT_RESULTS_COUNT)) && sp.contains("contact_results_count")) preferences[CONTACT_RESULTS_COUNT] = sp.getInt("contact_results_count", 5)
                if ((overwrite || !preferences.contains(FILE_RESULTS_COUNT)) && sp.contains("file_results_count")) preferences[FILE_RESULTS_COUNT] = sp.getInt("file_results_count", 5)
                if ((overwrite || !preferences.contains(SHORTCUT_RESULTS_COUNT)) && sp.contains("shortcut_results_count")) preferences[SHORTCUT_RESULTS_COUNT] = sp.getInt("shortcut_results_count", 6)
                if ((overwrite || !preferences.contains(CONTEXT_AWARE_QUICK_APPS)) && sp.contains("context_aware_quick_apps")) preferences[CONTEXT_AWARE_QUICK_APPS] = sp.getBoolean("context_aware_quick_apps", false)
                if ((overwrite || !preferences.contains(SMART_CLIPBOARD_SUGGESTIONS)) && sp.contains("smart_clipboard_suggestions")) preferences[SMART_CLIPBOARD_SUGGESTIONS] = sp.getBoolean("smart_clipboard_suggestions", false)
                if ((overwrite || !preferences.contains(ACTIVE_ICON_PACK)) && sp.contains("active_icon_pack")) sp.getString("active_icon_pack", null)?.let { preferences[ACTIVE_ICON_PACK] = it }
                if ((overwrite || !preferences.contains(CUSTOM_ICON_PILLS)) && sp.contains("custom_icon_pills")) sp.getString("custom_icon_pills", null)?.let { preferences[CUSTOM_ICON_PILLS] = it }
                if ((overwrite || !preferences.contains(NEVER_SHOW_ICON_PACK_WARNING)) && sp.contains("never_show_icon_pack_warning")) preferences[NEVER_SHOW_ICON_PACK_WARNING] = sp.getBoolean("never_show_icon_pack_warning", false)
                if ((overwrite || !preferences.contains(SEARCH_PREVIOUS_SEARCHES)) && sp.contains("search_previous_searches")) preferences[SEARCH_PREVIOUS_SEARCHES] = sp.getBoolean("search_previous_searches", true)
                if ((overwrite || !preferences.contains(CUSTOM_BANGS_JSON)) && sp.contains("custom_bangs_json")) sp.getString("custom_bangs_json", null)?.let { preferences[CUSTOM_BANGS_JSON] = it }
                if ((overwrite || !preferences.contains(SEARCH_SECTIONS_CONFIG_JSON)) && sp.contains("search_sections_config_json")) sp.getString("search_sections_config_json", null)?.let { preferences[SEARCH_SECTIONS_CONFIG_JSON] = it }
                if ((overwrite || !preferences.contains(ADAPTIVE_ICON_SHAPE)) && sp.contains("adaptive_icon_shape")) sp.getString("adaptive_icon_shape", null)?.let { preferences[ADAPTIVE_ICON_SHAPE] = it }
                if ((overwrite || !preferences.contains(DYNAMIC_ICON_MASKING)) && sp.contains("dynamic_icon_masking")) preferences[DYNAMIC_ICON_MASKING] = sp.getBoolean("dynamic_icon_masking", true)
                if ((overwrite || !preferences.contains(DIAGNOSTICS_OVERLAY_ENABLED)) && sp.contains("diagnostics_overlay_enabled")) preferences[DIAGNOSTICS_OVERLAY_ENABLED] = sp.getBoolean("diagnostics_overlay_enabled", false)
                if ((overwrite || !preferences.contains(SEARCH_OVERLAY_ENABLED)) && sp.contains("search_overlay_enabled")) preferences[SEARCH_OVERLAY_ENABLED] = sp.getBoolean("search_overlay_enabled", true)
                if ((overwrite || !preferences.contains(MATRIX_ANIMATION_ENABLED)) && sp.contains("matrix_animation_enabled")) preferences[MATRIX_ANIMATION_ENABLED] = sp.getBoolean("matrix_animation_enabled", true)
                if ((overwrite || !preferences.contains(BACK_TO_SEARCH_OVERLAY)) && sp.contains("settings_back_to_search_overlay")) preferences[BACK_TO_SEARCH_OVERLAY] = sp.getBoolean("settings_back_to_search_overlay", true)
                if ((overwrite || !preferences.contains(DISABLED_WEB_SHORTCUTS)) && sp.contains("disabled_web_shortcuts")) sp.getStringSet("disabled_web_shortcuts", null)?.let { preferences[DISABLED_WEB_SHORTCUTS] = it }
                if ((overwrite || !preferences.contains(VIBRATION)) && sp.contains("vibration_enabled")) preferences[VIBRATION] = sp.getBoolean("vibration_enabled", true)
                preferences[DATASTORE_MIGRATED] = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error syncing SharedPreferences to DataStore", e)
        }
    }

    suspend fun <T> updateSetting(key: Preferences.Key<T>, value: T) {
        try {
            val prefs = getSafePrefs()
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
        } catch (e: Exception) {
            Log.w(TAG, "Error updating SharedPreferences for key ${key.name}: ${e.message}")
        }

        try {
            context.dataStore.edit { preferences ->
                preferences[key] = value
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error writing to DataStore for key ${key.name}: ${e.message}")
        }
    }
}
