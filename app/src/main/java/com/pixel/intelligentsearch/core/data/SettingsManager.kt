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

data class IntelligentSearchSettings(
    val theme: String = "system",
    val searchApps: Boolean = false,
    val searchContacts: Boolean = false,
    val searchFiles: Boolean = false,
    val searchWeb: Boolean = false,
    val searchCalculator: Boolean = false,
    val searchCalendar: Boolean = false,
    val searchShortcuts: Boolean = false,
    val backgroundBlur: Int = 50,
    val showWallpaper: Boolean = false,
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
    val widgetThemeStyle: String = "dynamic",
    val hiddenApps: Set<String> = emptySet(),
    val appQuickLaunch: Boolean = false,
    val contactDirectCall: Boolean = false,
    val shortcutInline: Boolean = true,
    val appFuzzySearch: Boolean = false,
    val quickSearchHorizontal: Boolean = false,
    val webResultsCount: Int = 5,
    val contactResultsCount: Int = 5,
    val fileResultsCount: Int = 5,
    val shortcutResultsCount: Int = 6,
    val contextAwareQuickApps: Boolean = false,
    val smartClipboardSuggestions: Boolean = true
)

@Singleton
class SettingsManager @Inject constructor(@ApplicationContext private val context: Context) {
    companion object {
        val THEME = stringPreferencesKey("night.mode")
        val SEARCH_APPS = booleanPreferencesKey("search.apps")
        val SEARCH_CONTACTS = booleanPreferencesKey("search.contacts")
        val SEARCH_FILES = booleanPreferencesKey("search.files")
        val SEARCH_WEB = booleanPreferencesKey("search.web")
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
            IntelligentSearchSettings(
                theme = preferences[THEME] ?: "system",
                searchApps = preferences[SEARCH_APPS] ?: false,
                searchContacts = preferences[SEARCH_CONTACTS] ?: false,
                searchFiles = preferences[SEARCH_FILES] ?: false,
                searchWeb = preferences[SEARCH_WEB] ?: false,
                searchCalculator = preferences[SEARCH_CALCULATOR] ?: false,
                searchCalendar = preferences[SEARCH_CALENDAR] ?: false,
                searchShortcuts = preferences[SEARCH_SHORTCUTS] ?: false,
                backgroundBlur = preferences[BACKGROUND_BLUR] ?: 50,
                showWallpaper = preferences[SHOW_WALLPAPER] ?: false,
                backgroundTransparency = preferences[BACKGROUND_TRANSPARENCY] ?: 50,
                pillOpacity = preferences[PILL_OPACITY] ?: 50,
                searchEngine = preferences[SEARCH_ENGINE] ?: "Google",
                customSearchEngineUrl = preferences[CUSTOM_SEARCH_ENGINE_URL] ?: "",
                filesHiddenFiles = preferences[FILES_HIDDEN_FILES] ?: false,
                filesThumbnails = preferences[FILES_THUMBNAILS] ?: true,
                appAnimations = preferences[APP_ANIMATIONS] ?: true,
                bottomSearch = preferences[BOTTOM_SEARCH] ?: true,
                bottomSearchResult = preferences[BOTTOM_SEARCH_RESULT] ?: true,
                tutorialCompleted = preferences[TUTORIAL_COMPLETED] ?: false,
                forceTutorial = preferences[FORCE_TUTORIAL] ?: false,
                tutorialStep = preferences[TUTORIAL_STEP] ?: 0,
                gIconEnabled = preferences[G_ICON_ENABLED] ?: true,
                widgetShowVoice = preferences[WIDGET_SHOW_VOICE] ?: true,
                widgetShowGemini = preferences[WIDGET_SHOW_GEMINI] ?: true,
                quickSearchYoutube = preferences[QUICK_SEARCH_YOUTUBE] ?: true,
                quickSearchWikipedia = preferences[QUICK_SEARCH_WIKIPEDIA] ?: true,
                quickSearchPlayStore = preferences[QUICK_SEARCH_PLAY_STORE] ?: true,
                quickSearchMaps = preferences[QUICK_SEARCH_MAPS] ?: true,
                searchPills = preferences[SEARCH_PILLS] ?: "com.android.chrome,com.google.android.apps.maps,com.google.android.youtube,com.android.vending,com.google.android.contacts,com.google.android.apps.nbu.files",
                widgetThemeStyle = preferences[WIDGET_THEME_STYLE] ?: "dynamic",
                hiddenApps = preferences[HIDDEN_APPS] ?: emptySet(),
                appQuickLaunch = preferences[APP_QUICK_LAUNCH] ?: false,
                contactDirectCall = preferences[CONTACT_DIRECT_CALL] ?: false,
                shortcutInline = preferences[SHORTCUT_INLINE] ?: true,
                appFuzzySearch = preferences[APP_FUZZY_SEARCH] ?: false,
                quickSearchHorizontal = preferences[QUICK_SEARCH_HORIZONTAL] ?: false,
                webResultsCount = preferences[WEB_RESULTS_COUNT] ?: 5,
                contactResultsCount = preferences[CONTACT_RESULTS_COUNT] ?: 5,
                fileResultsCount = preferences[FILE_RESULTS_COUNT] ?: 5,
                shortcutResultsCount = preferences[SHORTCUT_RESULTS_COUNT] ?: 6,
                contextAwareQuickApps = preferences[CONTEXT_AWARE_QUICK_APPS] ?: false,
                smartClipboardSuggestions = preferences[SMART_CLIPBOARD_SUGGESTIONS] ?: false
            )
        }

    suspend fun <T> updateSetting(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { preferences ->
            preferences[key] = value
        }
    }
}

