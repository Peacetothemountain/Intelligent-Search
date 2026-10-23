package com.pixel.intelligentsearch.core.data

import com.pixel.intelligentsearch.core.data.widget.MAX_CORNER_RADIUS
import com.pixel.intelligentsearch.core.data.widget.MAX_OUTLINE_THICKNESS
import com.pixel.intelligentsearch.core.data.widget.WidgetProfiles
import com.pixel.intelligentsearch.core.util.storageContext
import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import android.util.Log
import androidx.core.content.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.preferencesDataStoreFile
import com.pixel.intelligentsearch.core.data.settings.BooleanSetting
import com.pixel.intelligentsearch.core.data.settings.EnterKeyAction
import com.pixel.intelligentsearch.core.data.settings.IntSetting
import com.pixel.intelligentsearch.core.data.settings.SettingKey
import com.pixel.intelligentsearch.core.data.settings.StringSetSetting
import com.pixel.intelligentsearch.core.data.settings.StringSetting
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Settings stored in DataStore by versions before the single-store migration; read once, then deleted. */
private val Context.legacySettingsDataStore by preferencesDataStore(name = SettingsManager.LEGACY_DATASTORE_NAME)

/** Immutable snapshot of the app-wide settings, read by the overlay, settings screens and services. */
@androidx.compose.runtime.Immutable
data class IntelligentSearchSettings(
    val theme: String = SettingsManager.THEME.default,
    val searchApps: Boolean = SettingsManager.SEARCH_APPS.default,
    val searchContacts: Boolean = SettingsManager.SEARCH_CONTACTS.default,
    val searchFiles: Boolean = SettingsManager.SEARCH_FILES.default,
    val searchWeb: Boolean = SettingsManager.SEARCH_WEB.default,
    val searchCalculator: Boolean = SettingsManager.SEARCH_CALCULATOR.default,
    val searchCalendar: Boolean = SettingsManager.SEARCH_CALENDAR.default,
    val searchAndroidSettings: Boolean = SettingsManager.SEARCH_ANDROID_SETTINGS.default,
    val backgroundBlur: Int = SettingsManager.BACKGROUND_BLUR.default,
    val showWallpaper: Boolean = SettingsManager.SHOW_WALLPAPER.default,
    val backgroundTransparency: Int = SettingsManager.BACKGROUND_TRANSPARENCY.default,
    val pillOpacity: Int = SettingsManager.PILL_OPACITY.default,
    val searchEngine: String = SettingsManager.SEARCH_ENGINE.default,
    val customSearchEngineUrl: String = SettingsManager.CUSTOM_SEARCH_ENGINE_URL.default,
    val filesHiddenFiles: Boolean = SettingsManager.FILES_HIDDEN_FILES.default,
    val filesThumbnails: Boolean = SettingsManager.FILES_THUMBNAILS.default,
    val appAnimations: Boolean = SettingsManager.APP_ANIMATIONS.default,
    val bottomSearch: Boolean = SettingsManager.BOTTOM_SEARCH.default,
    val bottomSearchResult: Boolean = SettingsManager.BOTTOM_SEARCH_RESULT.default,
    val tutorialCompleted: Boolean = SettingsManager.TUTORIAL_COMPLETED.default,
    val forceTutorial: Boolean = SettingsManager.FORCE_TUTORIAL.default,
    val tutorialStep: Int = SettingsManager.TUTORIAL_STEP.default,
    val widgetShowVoice: Boolean = SettingsManager.WIDGET_SHOW_VOICE.default,
    val searchPills: String = SettingsManager.SEARCH_PILLS.default,
    val widgetThemeStyle: String = SettingsManager.WIDGET_THEME_STYLE.default,
    val hiddenApps: Set<String> = SettingsManager.HIDDEN_APPS.default,
    val appQuickLaunch: Boolean = SettingsManager.APP_QUICK_LAUNCH.default,
    val contactDirectCall: Boolean = SettingsManager.CONTACT_DIRECT_CALL.default,
    val contactActions: Boolean = SettingsManager.CONTACT_ACTIONS.default,
    val shortcutInline: Boolean = SettingsManager.SHORTCUT_INLINE.default,
    val appFuzzySearch: Boolean = SettingsManager.APP_FUZZY_SEARCH.default,
    val quickSearchHorizontal: Boolean = SettingsManager.QUICK_SEARCH_HORIZONTAL.default,
    val webResultsCount: Int = SettingsManager.WEB_RESULTS_COUNT.default,
    val contactResultsCount: Int = SettingsManager.CONTACT_RESULTS_COUNT.default,
    val fileResultsCount: Int = SettingsManager.FILE_RESULTS_COUNT.default,
    val shortcutResultsCount: Int = SettingsManager.SHORTCUT_RESULTS_COUNT.default,
    val contextAwareQuickApps: Boolean = SettingsManager.CONTEXT_AWARE_QUICK_APPS.default,
    val smartClipboardSuggestions: Boolean = SettingsManager.SMART_CLIPBOARD_SUGGESTIONS.default,
    val activeIconPack: String = SettingsManager.ACTIVE_ICON_PACK.default,
    val searchOverlayEnabled: Boolean = SettingsManager.SEARCH_OVERLAY_ENABLED.default,
    val customIconPills: String = SettingsManager.CUSTOM_ICON_PILLS.default,
    val neverShowIconPackWarning: Boolean = SettingsManager.NEVER_SHOW_ICON_PACK_WARNING.default,
    val searchPreviousSearches: Boolean = SettingsManager.SEARCH_PREVIOUS_SEARCHES.default,
    val customBangsJson: String = SettingsManager.CUSTOM_BANGS_JSON.default,
    val webShortcutTriggerSymbol: String = SettingsManager.WEB_SHORTCUT_TRIGGER_SYMBOL.default,
    val adaptiveIconShape: String = SettingsManager.ADAPTIVE_ICON_SHAPE.default,
    val dynamicIconMasking: Boolean = SettingsManager.DYNAMIC_ICON_MASKING.default,
    val matrixAnimationEnabled: Boolean = SettingsManager.MATRIX_ANIMATION_ENABLED.default,
    val backToSearchOverlay: Boolean = SettingsManager.BACK_TO_SEARCH_OVERLAY.default,
    val disabledWebShortcuts: Set<String> = SettingsManager.DISABLED_WEB_SHORTCUTS.default,
    val vibrationEnabled: Boolean = SettingsManager.VIBRATION.default
)

/**
 * The single store for app-wide settings: one SharedPreferences file, read synchronously (widgets,
 * direct boot and the overlay's first frame need that) and observed as flows. Every setting is declared
 * once below with its type, default and any older spellings.
 */
@Singleton
class SettingsManager @Inject constructor(@param:ApplicationContext private val context: Context) {

    companion object {
        private const val TAG = "SettingsManager"
        const val PREFS_NAME = "PREFERENCES_CUSTOMISATIONS"
        internal const val LEGACY_DATASTORE_NAME = "intelligent_search_settings"

        /** Bumped when stored data needs a one-time migration. */
        private const val STORAGE_VERSION_KEY = "settings_storage_version"
        private const val CURRENT_STORAGE_VERSION = 1

        private const val DEFAULT_SEARCH_PILLS = "com.android.chrome,com.google.android.apps.maps," +
            "com.google.android.youtube,com.android.vending,com.google.android.contacts,com.google.android.apps.nbu.files"

        val THEME = StringSetting("night.mode", ThemeMode.SYSTEM.storedValue, "theme")
        val SEARCH_APPS = BooleanSetting("search.apps", true, "search_apps")
        val SEARCH_CONTACTS = BooleanSetting("search.contacts", false, "search_contacts")
        val SEARCH_FILES = BooleanSetting("search.files", false, "search_files")
        val SEARCH_WEB = BooleanSetting("search.web", true, "search_web")
        val SEARCH_WEB_SUGGESTIONS = BooleanSetting("search.web.suggestions", true)
        val SEARCH_PREVIOUS_SEARCHES = BooleanSetting("search_previous_searches", true)
        val SEARCH_CALCULATOR = BooleanSetting("search.calculator", true, "search_calculator")
        /** Fetches the European Central Bank's rates for currency answers once a day while the Calculator is on; off, nothing is fetched. */
        val LIVE_CURRENCY_RATES = BooleanSetting("live_currency_rates", true)
        val SEARCH_CALENDAR = BooleanSetting("search.calendar", true, "search_calendar")
        /**
         * Definitions shown in the overlay ("define serendipity"), fetched from the Free Dictionary API
         * (dictionaryapi.dev). Off by default; also needs Web and Web Suggestions on and Tor not the engine.
         */
        val DICTIONARY_ANSWERS = BooleanSetting("dictionary_answers", false)
        /** Forecasts shown in the overlay ("weather tomorrow"), fetched from Open-Meteo; the same conditions apply. */
        val WEATHER_ANSWERS = BooleanSetting("weather_answers", false)
        /** Where weather answers are for when the query names no place: "latitude,longitude;name", empty for none. */
        val WEATHER_HOME_LOCATION = StringSetting("weather_home_location", "")
        /** Weather answers use the phone's approximate location (when allowed) instead of the home location. */
        val WEATHER_USE_DEVICE_LOCATION = BooleanSetting("weather_use_device_location", false)
        /**
         * Switched app shortcut results on; nothing reads it any more. Android lets only the home screen app read
         * other apps' shortcuts, so they never showed, and the row became the Quick App Panel switch
         * ([QUICK_SEARCH_HORIZONTAL]). Stays declared so the migration and older backups recognise it.
         */
        val SEARCH_SHORTCUTS = BooleanSetting("search.shortcuts", true, "search_shortcuts")
        /** Offers Android Settings pages ("battery", "app info chrome") among the results. */
        val SEARCH_ANDROID_SETTINGS = BooleanSetting("search.android.settings", true)
        val BACKGROUND_BLUR = IntSetting("search.background.blur", 50, 0..100, "background.blur")
        val SHOW_WALLPAPER = BooleanSetting("search.background.show.wall", true, "show_wallpaper", "show.wallpaper")
        val BACKGROUND_TRANSPARENCY = IntSetting("search.background.transparency", 50, 0..100, "background.transparency")
        /** The search pill's opacity in percent; see [PILL_OPACITY_LINEAR_KEY] for what older versions stored. */
        val PILL_OPACITY = IntSetting("search.pill.opacity", 100, 0..100, "pill.opacity")
        val SEARCH_ENGINE = StringSetting("search.engine", "Google")
        val CUSTOM_SEARCH_ENGINE_URL = StringSetting("custom_search_engine_url", "")
        val CUSTOM_SEARCH_ENGINE_NAME = StringSetting("custom_search_engine_name", "Custom")
        val FILES_HIDDEN_FILES = BooleanSetting("search.files.hidden.files", false, "search_files_hidden_files")
        val FILES_THUMBNAILS = BooleanSetting("search.files.thumbnails", true, "search_files_thumbnails")
        /** File types left out of file search, by [FileTypeGroup.id]; empty searches every type. */
        val FILES_EXCLUDED_TYPES = StringSetSetting("search.files.excluded_types", emptySet())
        /**
         * Subfolders of the chosen folders that file search skips, as tree URIs. Like the chosen folders themselves
         * they only mean something on this phone, so backups leave them out (BackupPreferenceSchema.DEVICE_LOCAL_KEYS).
         */
        val FILES_EXCLUDED_FOLDERS = StringSetSetting("search.files.excluded_folders", emptySet())
        /** The app each file type opens with ([OpenWithChoice]); the phone's default app unless chosen. */
        val FILES_OPEN_WITH: Map<FileTypeGroup, StringSetting> = FileTypeGroup.entries.associateWith { group ->
            StringSetting("search.files.open_with.${group.id}", OpenWithChoice.DEFAULT_STORED)
        }
        val APP_ANIMATIONS = BooleanSetting("app_animations", true, "app.animation")
        val MORPH_ANIMATION_ENABLED = BooleanSetting("morph_animation_enabled", false)
        val BOTTOM_SEARCH = BooleanSetting("settings.bottom.search", true, "bottom_search")
        val BOTTOM_SEARCH_RESULT = BooleanSetting("settings.bottom.search.result", true, "bottom_search_result")
        val TUTORIAL_COMPLETED = BooleanSetting("tutorial_completed", false)
        val FORCE_TUTORIAL = BooleanSetting("force_tutorial", false)
        val TUTORIAL_STEP = IntSetting("tutorial_step", 0, 0..20)
        // Nothing reads this, WIDGET_SHOW_GEMINI, QUICK_SEARCH_YOUTUBE/WIKIPEDIA/PLAY_STORE/MAPS or
        // DIAGNOSTICS_OVERLAY_ENABLED any more; they stay declared so the migration and older backups recognise them.
        val G_ICON_ENABLED = BooleanSetting("g_icon_enabled", true)
        val WIDGET_SHOW_VOICE = BooleanSetting("widget_show_voice", true)
        val WIDGET_SHOW_GEMINI = BooleanSetting("widget_show_gemini", true)
        val QUICK_SEARCH_YOUTUBE = BooleanSetting("quick_search_youtube", true)
        val QUICK_SEARCH_WIKIPEDIA = BooleanSetting("quick_search_wikipedia", true)
        val QUICK_SEARCH_PLAY_STORE = BooleanSetting("quick_search_play_store", true)
        val QUICK_SEARCH_MAPS = BooleanSetting("quick_search_maps", true)
        val SEARCH_PILLS = StringSetting("search_pills", DEFAULT_SEARCH_PILLS, "search.pills")
        val WIDGET_THEME_STYLE = StringSetting("widget.theme.style", "System Default", "widget_theme_style")
        val HIDDEN_APPS = StringSetSetting("hidden_apps", emptySet())
        val APP_QUICK_LAUNCH = BooleanSetting("app_quick_launch", false)
        val CONTACT_DIRECT_CALL = BooleanSetting("contact_direct_call", false, "contact.direct.call")
        /** Call, Message, Email and chat-app buttons on contact results. */
        val CONTACT_ACTIONS = BooleanSetting("contact_actions", true)
        val SHORTCUT_INLINE = BooleanSetting("shortcut.inline", true, "shortcut_inline")
        val APP_FUZZY_SEARCH = BooleanSetting("app.fuzzy.search", true, "app_fuzzy_search")
        /** The overlay's quick-app panel; switched from Search Behavior and from App & Shortcut Search. */
        val QUICK_SEARCH_HORIZONTAL = BooleanSetting("quick_search_horizontal", false, "quick.search.horizontal")
        val WEB_RESULTS_COUNT = IntSetting("web_results_count", 5, 0..20)
        val CONTACT_RESULTS_COUNT = IntSetting("contact_results_count", 5, 0..20)
        val FILE_RESULTS_COUNT = IntSetting("file_results_count", 5, 0..20)
        /**
         * Max Quick Panel Apps: how many apps the quick-app panel shows, chosen or recent. Named for the shortcut
         * results it once capped; the name stays so backups keep restoring it. See [QUICK_PANEL_CAPS_CHOSEN_APPS_KEY].
         */
        val SHORTCUT_RESULTS_COUNT = IntSetting("shortcut_results_count", 6, 1..20)
        val SEARCH_WEIGHT_APPS = IntSetting("search_weight_apps", 50, 0..100)
        val SEARCH_WEIGHT_CONTACTS = IntSetting("search_weight_contacts", 50, 0..100)
        val SEARCH_WEIGHT_FILES = IntSetting("search_weight_files", 50, 0..100)
        val SEARCH_WEIGHT_WEB = IntSetting("search_weight_web", 50, 0..100)
        val CONTEXT_AWARE_QUICK_APPS = BooleanSetting("context_aware_quick_apps", false)
        val SMART_CLIPBOARD_SUGGESTIONS = BooleanSetting("smart_clipboard_suggestions", false)
        /** The assistant gesture starts listening; off, it opens search with the keyboard ready instead. */
        val ASSISTANT_STARTS_WITH_VOICE = BooleanSetting("assistant_starts_with_voice", false)
        val ACTIVE_ICON_PACK = StringSetting("active_icon_pack", "system_default")
        val SEARCH_OVERLAY_ENABLED = BooleanSetting("search_overlay_enabled", true)
        val CUSTOM_ICON_PILLS = StringSetting("custom_icon_pills", "", "custom_icon_packs_order")
        val NEVER_SHOW_ICON_PACK_WARNING = BooleanSetting("never_show_icon_pack_warning", false)
        val CUSTOM_BANGS_JSON = StringSetting("custom_bangs_json", "[]")
        val WEB_SHORTCUT_TRIGGER_SYMBOL = StringSetting("web_shortcut_trigger_symbol", "!")
        val SEARCH_QUICK_SHORTCUTS = BooleanSetting("search_quick_shortcuts", true)
        val SHOW_AVAILABLE_DIRECT_SHORTCUTS = BooleanSetting("search_show_available_direct_shortcuts", true)
        val ADAPTIVE_ICON_SHAPE = StringSetting("adaptive_icon_shape", "SYSTEM_DEFAULT")
        val DYNAMIC_ICON_MASKING = BooleanSetting("dynamic_icon_masking", true)
        val DIAGNOSTICS_OVERLAY_ENABLED = BooleanSetting("diagnostics_overlay_enabled", false)
        val MATRIX_ANIMATION_ENABLED = BooleanSetting("matrix_animation_enabled", true)
        val BACK_TO_SEARCH_OVERLAY = BooleanSetting("settings_back_to_search_overlay", true)
        val DISABLED_WEB_SHORTCUTS = StringSetSetting("disabled_web_shortcuts", emptySet())
        val VIBRATION = BooleanSetting("vibration_enabled", true)
        /** The voice search (mic) button in the search overlay's search bar; off removes it from the bar. */
        val OVERLAY_SHOW_MIC = BooleanSetting("overlay_show_mic", true)
        /**
         * Show Keyboard When Search Opens: the overlay opens with the search field focused and the keyboard up. Off, it
         * opens with the field unfocused and the keyboard down until the field is tapped. Every way in (the widget,
         * the assistant gesture, a launcher shortcut) follows it.
         */
        val SHOW_KEYBOARD_ON_OPEN = BooleanSetting("show_keyboard_on_open", true)
        /** What Enter does when no result is highlighted, as an [EnterKeyAction]'s stored value. */
        val ENTER_KEY_ACTION = StringSetting("enter_key_action", EnterKeyAction.OPEN_TOP_RESULT.storedValue)
        val DEBUG_UNLOCKED = BooleanSetting("debug_unlocked", false)
        val DEBUG_VERBOSE_LOGGING = BooleanSetting("debug.verbose_logging", false)
        val DEBUG_SHOW_PERF_STATS = BooleanSetting("debug.show_perf_stats", false)
        val DEBUG_FORCE_SEARCH_ERROR = BooleanSetting("debug.force_search_error", false)
        val DEBUG_SIMULATE_LATENCY = BooleanSetting("debug.simulate_latency", false)
        val DEBUG_MOCK_LARGE_DATASET = BooleanSetting("debug.mock_large_dataset", false)
        val DEBUG_MOCK_TRENDING = BooleanSetting("debug.mock_zero_state", false)
        /** The user's consent to let on-device assistants search contacts (AppFunctions); off by default. */
        val APP_FUNCTIONS_SHARE_CONTACTS = BooleanSetting("appfunctions_share_contacts", false)
        /** Pinned results, as JSON (ResultShortcuts); read through ResultShortcutStore. Older nickname and trigger word fields are ignored. */
        val RESULT_SHORTCUTS = StringSetting("result_shortcuts_json", "[]")
        /**
         * Search Your Notifications: the user's opt-in to let the notification listener read active notifications into
         * memory for search and At a Glance's timers. Off by default; it works only with notification access.
         */
        val SEARCH_NOTIFICATIONS = BooleanSetting("search.notifications", false)
        /** At a Glance: timely cards above the empty search. Each item has its own switch below. */
        val GLANCE_ENABLED = BooleanSetting("glance.enabled", true)
        val GLANCE_ALARM = BooleanSetting("glance.alarm", true)
        /** Timers and stopwatches, read from the clock's notifications (needs [SEARCH_NOTIFICATIONS]). */
        val GLANCE_TIMERS = BooleanSetting("glance.timers", true)
        /** At a Glance's Media Controls, read from media sessions (needs notification access). */
        val GLANCE_MEDIA = BooleanSetting("glance.media", true)
        /** Contacts' birthdays this week (needs contacts access). */
        val GLANCE_BIRTHDAYS = BooleanSetting("glance.birthdays", true)
        val GLANCE_BATTERY = BooleanSetting("glance.battery", true)
        /**
         * AI Answers: questions ("…?", "ask …") offer an Ask row that, only when tapped, sends that one question to
         * [AI_PROVIDER] with the user's own key (kept in AiKeyVault, never here). Off by default; a backup can't
         * switch it on.
         */
        val AI_ANSWERS_ENABLED = BooleanSetting("ai.answers.enabled", false)
        /** The [com.pixel.intelligentsearch.core.ai.AiProvider] id AI Answers asks. */
        val AI_PROVIDER = StringSetting("ai.answers.provider", "anthropic")
        /** The chosen model of [AI_PROVIDER]; blank (or another provider's) means its default. */
        val AI_MODEL = StringSetting("ai.answers.model", "")

        // Each search bar widget design's extras, kept per design like the rest of its customization
        // (WidgetSettingsStore reads and writes them). Favourite apps are package names, comma separated, in order.
        val WIDGET_SYSTEM_FAVOURITE_APPS = StringSetting("widget_system_favourite_apps", "")
        val WIDGET_MATERIAL_FAVOURITE_APPS = StringSetting("widget_material_favourite_apps", "")
        /** The media row (current media title, play/pause, next); it needs notification access (MediaSessionAccess). */
        val WIDGET_SYSTEM_SHOW_MEDIA = BooleanSetting("widget_system_show_media", false)
        val WIDGET_MATERIAL_SHOW_MEDIA = BooleanSetting("widget_material_show_media", false)
        /** The bar's outline in dp: the System design has none by default, the Material design's rim is 8dp. */
        val WIDGET_SYSTEM_OUTLINE_THICKNESS = IntSetting("widget_system_outline_thickness", 0, 0..MAX_OUTLINE_THICKNESS)
        val WIDGET_MATERIAL_OUTLINE_THICKNESS = IntSetting("widget_material_outline_thickness", 8, 0..MAX_OUTLINE_THICKNESS)
        /** The bar's outer corner radius in dp; drawn at most half the bar's height (fully round). */
        val WIDGET_SYSTEM_CORNER_RADIUS = IntSetting("widget_system_corner_radius", 28, 0..MAX_CORNER_RADIUS)
        val WIDGET_MATERIAL_CORNER_RADIUS = IntSetting("widget_material_corner_radius", 32, 0..MAX_CORNER_RADIUS)
        /** The widget's design profile now on the widget (WidgetProfiles), 1 to WidgetProfiles.PROFILE_COUNT. */
        val WIDGET_ACTIVE_PROFILE = IntSetting("widget_active_profile", 1, 1..WidgetProfiles.PROFILE_COUNT)
        /** Each widget profile's design, kept while another profile is on the widget (WidgetProfiles), as JSON. */
        val WIDGET_PROFILE_DESIGNS: List<StringSetting> =
            (1..WidgetProfiles.PROFILE_COUNT).map { StringSetting("widget_profile_$it", "") }
        /** The design each widget profile's Reset returns to (WidgetProfiles), as JSON. */
        val WIDGET_PROFILE_SAVED_DESIGNS: List<StringSetting> =
            (1..WidgetProfiles.PROFILE_COUNT).map { StringSetting("widget_profile_${it}_saved", "") }
        /**
         * What each placed countdown widget counts down to, by widget id (CountdownEventStore), as JSON. Widget ids are
         * this phone's own, so backups leave it out (BackupPreferenceSchema.DEVICE_LOCAL_KEYS).
         */
        val COUNTDOWN_EVENTS = StringSetting("countdown_widget_events", "{}")
        /**
         * Launch Portal's extra ways to open search. Each is off by default and works only with its permission
         * (notifications; display over other apps), so a restore never switches one on.
         */
        val NOTIFICATION_SHORTCUT = BooleanSetting("launch.search_notification", false)
        val FLOATING_SEARCH_BUTTON = BooleanSetting("launch.floating_button", false)
        /** The edge the floating button rests on, "left" or "right" (ScreenEdge). */
        val FLOATING_BUTTON_EDGE = StringSetting("launch.floating_button_edge", "right")
        /** Its height on that edge, per-mille of the height it can travel (0 top, 1000 bottom). */
        val FLOATING_BUTTON_POSITION = IntSetting("launch.floating_button_position", 600, 0..1000)
        val EDGE_SWIPE = BooleanSetting("launch.edge_swipe", false)
        /** The edge the swipe strip runs along, "left" or "right" (ScreenEdge). */
        val EDGE_SWIPE_EDGE = StringSetting("launch.edge_swipe_edge", "right")
        /** Where the strip's centre is, in percent of the screen's usable height. */
        val EDGE_SWIPE_POSITION = IntSetting("launch.edge_swipe_position", 50, 0..100)
        /** How tall the strip is, in percent of the screen's usable height. */
        val EDGE_SWIPE_HEIGHT = IntSetting("launch.edge_swipe_height", 25, 10..60)

        /** Every declared setting, for migration, backup validation and change filtering. */
        val ALL: List<SettingKey<*>> = listOf(
            THEME, SEARCH_APPS, SEARCH_CONTACTS, SEARCH_FILES, SEARCH_WEB, SEARCH_WEB_SUGGESTIONS,
            SEARCH_PREVIOUS_SEARCHES, SEARCH_CALCULATOR, LIVE_CURRENCY_RATES, SEARCH_CALENDAR, SEARCH_SHORTCUTS, SEARCH_ANDROID_SETTINGS, BACKGROUND_BLUR,
            SHOW_WALLPAPER, BACKGROUND_TRANSPARENCY, PILL_OPACITY, SEARCH_ENGINE, CUSTOM_SEARCH_ENGINE_URL,
            CUSTOM_SEARCH_ENGINE_NAME, FILES_HIDDEN_FILES, FILES_THUMBNAILS, APP_ANIMATIONS, MORPH_ANIMATION_ENABLED,
            BOTTOM_SEARCH, BOTTOM_SEARCH_RESULT, TUTORIAL_COMPLETED, FORCE_TUTORIAL, TUTORIAL_STEP, G_ICON_ENABLED,
            WIDGET_SHOW_VOICE, WIDGET_SHOW_GEMINI, QUICK_SEARCH_YOUTUBE, QUICK_SEARCH_WIKIPEDIA,
            QUICK_SEARCH_PLAY_STORE, QUICK_SEARCH_MAPS, SEARCH_PILLS, WIDGET_THEME_STYLE, HIDDEN_APPS,
            APP_QUICK_LAUNCH, CONTACT_DIRECT_CALL, CONTACT_ACTIONS, SHORTCUT_INLINE, APP_FUZZY_SEARCH,
            QUICK_SEARCH_HORIZONTAL, WEB_RESULTS_COUNT, CONTACT_RESULTS_COUNT, FILE_RESULTS_COUNT,
            SHORTCUT_RESULTS_COUNT, SEARCH_WEIGHT_APPS, SEARCH_WEIGHT_CONTACTS, SEARCH_WEIGHT_FILES,
            SEARCH_WEIGHT_WEB, CONTEXT_AWARE_QUICK_APPS, SMART_CLIPBOARD_SUGGESTIONS, ASSISTANT_STARTS_WITH_VOICE,
            ACTIVE_ICON_PACK,
            SEARCH_OVERLAY_ENABLED, CUSTOM_ICON_PILLS, NEVER_SHOW_ICON_PACK_WARNING, CUSTOM_BANGS_JSON,
            WEB_SHORTCUT_TRIGGER_SYMBOL, SEARCH_QUICK_SHORTCUTS, SHOW_AVAILABLE_DIRECT_SHORTCUTS,
            ADAPTIVE_ICON_SHAPE, DYNAMIC_ICON_MASKING, DIAGNOSTICS_OVERLAY_ENABLED, MATRIX_ANIMATION_ENABLED,
            BACK_TO_SEARCH_OVERLAY, DISABLED_WEB_SHORTCUTS, VIBRATION, OVERLAY_SHOW_MIC, DEBUG_UNLOCKED, DEBUG_VERBOSE_LOGGING,
            SHOW_KEYBOARD_ON_OPEN, ENTER_KEY_ACTION,
            DEBUG_SHOW_PERF_STATS, DEBUG_FORCE_SEARCH_ERROR, DEBUG_SIMULATE_LATENCY, DEBUG_MOCK_LARGE_DATASET,
            DEBUG_MOCK_TRENDING, APP_FUNCTIONS_SHARE_CONTACTS, SEARCH_NOTIFICATIONS, GLANCE_ENABLED, GLANCE_ALARM,
            GLANCE_TIMERS, GLANCE_MEDIA, GLANCE_BIRTHDAYS, GLANCE_BATTERY, RESULT_SHORTCUTS,
            DICTIONARY_ANSWERS, WEATHER_ANSWERS,
            WEATHER_HOME_LOCATION, WEATHER_USE_DEVICE_LOCATION, FILES_EXCLUDED_TYPES, FILES_EXCLUDED_FOLDERS,
            WIDGET_SYSTEM_FAVOURITE_APPS, WIDGET_MATERIAL_FAVOURITE_APPS, WIDGET_SYSTEM_SHOW_MEDIA,
            WIDGET_MATERIAL_SHOW_MEDIA, WIDGET_SYSTEM_OUTLINE_THICKNESS, WIDGET_MATERIAL_OUTLINE_THICKNESS,
            WIDGET_SYSTEM_CORNER_RADIUS, WIDGET_MATERIAL_CORNER_RADIUS, WIDGET_ACTIVE_PROFILE, COUNTDOWN_EVENTS,
            AI_ANSWERS_ENABLED, AI_PROVIDER, AI_MODEL,
            NOTIFICATION_SHORTCUT, FLOATING_SEARCH_BUTTON, FLOATING_BUTTON_EDGE, FLOATING_BUTTON_POSITION,
            EDGE_SWIPE, EDGE_SWIPE_EDGE, EDGE_SWIPE_POSITION, EDGE_SWIPE_HEIGHT
        ) + FILES_OPEN_WITH.values + WIDGET_PROFILE_DESIGNS + WIDGET_PROFILE_SAVED_DESIGNS

        private val BY_STORED_NAME: Map<String, SettingKey<*>> =
            ALL.flatMap { key -> key.allNames.map { it to key } }.toMap()

        /** The declared setting stored under [storedName] (canonical or legacy spelling), if any. */
        fun keyFor(storedName: String): SettingKey<*>? = BY_STORED_NAME[storedName]

        /**
         * The settings file as it can be read right now, for code without a [SettingsManager] (widgets, receivers,
         * services): normal storage once the user has unlocked since boot, device-protected storage before that.
         */
        fun storedPrefs(context: Context): SharedPreferences =
            context.storageContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        /** Settings keys retired with their features; removed by the migration. */
        private val RETIRED_NAMES = listOf("search_sections_config_json", "datastore_migration_v1_completed")

        /**
         * Settings an older build stored that no longer mean anything: Search from Selected Text (its entry is always
         * on now), At a Glance's one-time codes (Android 15 hides them from listeners), On-Device Translation
         * (translations go to Google Translate), Saved Links (removed; Web Shortcuts open sites) and the widget's
         * Saved Themes (moved into the widget profiles first, WidgetProfiles.migrateSavedThemes). A value an older
         * build stored is dropped at every start, and backups never restore one.
         */
        val REMOVED_NAMES: Set<String> = setOf(
            "search_from_selected_text", "glance.codes", "on_device_translation", "saved_links_json",
            WidgetProfiles.SAVED_THEMES_KEY
        )

        /** The folder in the app's files where an older build kept Saved Links' site icons; deleted at start. */
        internal const val REMOVED_SAVED_LINK_ICONS_DIR = "saved_link_icons"

        /**
         * Set once [PILL_OPACITY] holds the pill's real opacity. Older versions drew their default, 50, fully
         * opaque, so a store (or backup) without this flag reads 50 as 100. Backups carry it, so a restore can
         * tell which kind of value a file holds; restores never write it.
         */
        const val PILL_OPACITY_LINEAR_KEY = "pill_opacity_linear_v1"

        /** A [PILL_OPACITY] value saved before [PILL_OPACITY_LINEAR_KEY], as the pill's real opacity. */
        fun pillOpacityFromOldScale(stored: Int): Int = if (stored == 50) 100 else stored

        /**
         * Set once [SHORTCUT_RESULTS_COUNT] caps the chosen Quick Launch apps too. Older versions capped only recent
         * apps and showed every chosen one, so a store (or backup) without this flag has the max raised to fit its
         * chosen apps. Backups carry it, so a restore can tell which kind of file it has; restores never write it.
         */
        const val QUICK_PANEL_CAPS_CHOSEN_APPS_KEY = "quick_panel_caps_chosen_apps_v1"

        /**
         * Raises [SHORTCUT_RESULTS_COUNT] in [editor] to the number of chosen Quick Launch apps in [sp] when the panel
         * shows them (Context Aware Quick Apps off) and the max is lower, so the panel shows what it did before the
         * max applied to them.
         */
        fun fitQuickPanelToChosenApps(sp: SharedPreferences, editor: SharedPreferences.Editor) {
            if (sp[CONTEXT_AWARE_QUICK_APPS]) return
            val chosen = sp[SEARCH_PILLS].split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct().size
            if (chosen > sp[SHORTCUT_RESULTS_COUNT]) editor.put(SHORTCUT_RESULTS_COUNT, chosen)
        }
    }

    /**
     * The backing file. Before the user's first unlock only device-protected storage is readable, so reads
     * there see defaults (and widgets render defaults) until unlock.
     */
    val prefs: SharedPreferences
        get() = storedPrefs(context)

    operator fun <T : Any> get(key: SettingKey<T>): T = prefs[key]

    fun <T : Any> set(key: SettingKey<T>, value: T) {
        prefs.edit().put(key, value).apply()
    }

    /** Suspend form of [set] for call sites already in coroutines; the write itself is asynchronous. */
    suspend fun <T : Any> updateSetting(key: SettingKey<T>, value: T) = set(key, value)

    /** The current value of [key], then every change to it. */
    fun <T : Any> observe(key: SettingKey<T>): Flow<T> = callbackFlow {
        val store = prefs
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, changed ->
            if (changed == null || changed in key.allNames) trySend(sp[key])
        }
        store.registerOnSharedPreferenceChangeListener(listener)
        trySend(store[key])
        awaitClose { store.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate().distinctUntilChanged()

    /** The current settings snapshot, then a new one whenever a declared setting changes. */
    val settingsFlow: Flow<IntelligentSearchSettings> = callbackFlow {
        val store = prefs
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, changed ->
            // A null key means the file was cleared.
            if (changed == null || keyFor(changed) != null) trySend(snapshotOf(sp))
        }
        store.registerOnSharedPreferenceChangeListener(listener)
        trySend(snapshotOf(store))
        awaitClose { store.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate().distinctUntilChanged()

    /** The settings right now, read synchronously (SharedPreferences keeps them in memory). */
    fun getInitialSettings(): IntelligentSearchSettings = snapshotOf(prefs)

    private fun snapshotOf(sp: SharedPreferences) = IntelligentSearchSettings(
        theme = sp[THEME],
        searchApps = sp[SEARCH_APPS],
        searchContacts = sp[SEARCH_CONTACTS],
        searchFiles = sp[SEARCH_FILES],
        searchWeb = sp[SEARCH_WEB],
        searchCalculator = sp[SEARCH_CALCULATOR],
        searchCalendar = sp[SEARCH_CALENDAR],
        searchAndroidSettings = sp[SEARCH_ANDROID_SETTINGS],
        backgroundBlur = sp[BACKGROUND_BLUR],
        showWallpaper = sp[SHOW_WALLPAPER],
        backgroundTransparency = sp[BACKGROUND_TRANSPARENCY],
        pillOpacity = sp[PILL_OPACITY],
        searchEngine = sp[SEARCH_ENGINE],
        customSearchEngineUrl = sp[CUSTOM_SEARCH_ENGINE_URL],
        filesHiddenFiles = sp[FILES_HIDDEN_FILES],
        filesThumbnails = sp[FILES_THUMBNAILS],
        appAnimations = sp[APP_ANIMATIONS],
        bottomSearch = sp[BOTTOM_SEARCH],
        bottomSearchResult = sp[BOTTOM_SEARCH_RESULT],
        tutorialCompleted = sp[TUTORIAL_COMPLETED],
        forceTutorial = sp[FORCE_TUTORIAL],
        tutorialStep = sp[TUTORIAL_STEP],
        widgetShowVoice = sp[WIDGET_SHOW_VOICE],
        searchPills = sp[SEARCH_PILLS],
        widgetThemeStyle = sp[WIDGET_THEME_STYLE],
        hiddenApps = sp[HIDDEN_APPS],
        appQuickLaunch = sp[APP_QUICK_LAUNCH],
        contactDirectCall = sp[CONTACT_DIRECT_CALL],
        contactActions = sp[CONTACT_ACTIONS],
        shortcutInline = sp[SHORTCUT_INLINE],
        appFuzzySearch = sp[APP_FUZZY_SEARCH],
        quickSearchHorizontal = sp[QUICK_SEARCH_HORIZONTAL],
        webResultsCount = sp[WEB_RESULTS_COUNT],
        contactResultsCount = sp[CONTACT_RESULTS_COUNT],
        fileResultsCount = sp[FILE_RESULTS_COUNT],
        shortcutResultsCount = sp[SHORTCUT_RESULTS_COUNT],
        contextAwareQuickApps = sp[CONTEXT_AWARE_QUICK_APPS],
        smartClipboardSuggestions = sp[SMART_CLIPBOARD_SUGGESTIONS],
        activeIconPack = sp[ACTIVE_ICON_PACK],
        searchOverlayEnabled = sp[SEARCH_OVERLAY_ENABLED],
        customIconPills = sp[CUSTOM_ICON_PILLS],
        neverShowIconPackWarning = sp[NEVER_SHOW_ICON_PACK_WARNING],
        searchPreviousSearches = sp[SEARCH_PREVIOUS_SEARCHES],
        customBangsJson = sp[CUSTOM_BANGS_JSON],
        webShortcutTriggerSymbol = sp[WEB_SHORTCUT_TRIGGER_SYMBOL],
        adaptiveIconShape = sp[ADAPTIVE_ICON_SHAPE],
        dynamicIconMasking = sp[DYNAMIC_ICON_MASKING],
        matrixAnimationEnabled = sp[MATRIX_ANIMATION_ENABLED],
        backToSearchOverlay = sp[BACK_TO_SEARCH_OVERLAY],
        disabledWebShortcuts = sp[DISABLED_WEB_SHORTCUTS],
        vibrationEnabled = sp[VIBRATION]
    )

    /**
     * Widgets can render before the user's first unlock, when only device-protected storage is readable.
     * Keeps a copy of the settings they draw with there: widget appearance keys plus a few app-wide ones.
     * Private data (hidden apps, chosen files) is never copied. Call once the user is unlocked.
     */
    fun startDirectBootMirror() {
        if (directBootMirror != null) return
        val credentialPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deviceProtectedPrefs = context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val mirror = SharedPreferences.OnSharedPreferenceChangeListener { sp, changed ->
            if (changed != null && isMirroredForWidgets(changed)) copyEntry(sp, deviceProtectedPrefs, changed)
        }
        // Full copy first, so the mirror is complete even for values changed by older versions.
        val editor = deviceProtectedPrefs.edit().clear()
        credentialPrefs.all.forEach { (name, value) -> if (isMirroredForWidgets(name)) putEntry(editor, name, value) }
        editor.apply()
        credentialPrefs.registerOnSharedPreferenceChangeListener(mirror)
        directBootMirror = mirror
    }

    // Held so SharedPreferences (which keeps listeners weakly) doesn't drop it.
    @Volatile
    private var directBootMirror: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private val widgetAppWideNames =
        listOf(THEME, WIDGET_SHOW_VOICE, WIDGET_THEME_STYLE, SEARCH_OVERLAY_ENABLED)
            .map { it.name }.toSet()

    private fun isMirroredForWidgets(name: String): Boolean = name.startsWith("widget") || name in widgetAppWideNames

    private fun copyEntry(from: SharedPreferences, to: SharedPreferences, name: String) {
        val editor = to.edit()
        if (from.contains(name)) putEntry(editor, name, from.all[name]) else editor.remove(name)
        editor.apply()
    }

    private fun putEntry(editor: SharedPreferences.Editor, name: String, value: Any?) {
        when (value) {
            is Boolean -> editor.putBoolean(name, value)
            is Int -> editor.putInt(name, value)
            is Long -> editor.putLong(name, value)
            is Float -> editor.putFloat(name, value)
            is String -> editor.putString(name, value)
            is Set<*> -> editor.putStringSet(name, value.filterIsInstance<String>().toSet())
            else -> editor.remove(name)
        }
    }

    /**
     * One-time move to the single store (run after the user is unlocked): imports values only the old
     * DataStore copy still held, folds every legacy spelling into its canonical name, normalizes the theme,
     * drops retired keys, and deletes the DataStore file. Then moves the pill opacity to its linear meaning (see
     * [migratePillOpacity]), fits the quick panel's max to the chosen apps (see [migrateQuickPanelMax]), moves the
     * widget's Saved Themes into its profiles ([WidgetProfiles.migrateSavedThemes]) and drops removed settings
     * ([REMOVED_NAMES]) and the removed Saved Links' icon files. Safe to call repeatedly.
     */
    suspend fun migrateToSingleStore() = withContext(Dispatchers.IO) {
        val sp = prefs
        if (sp.getInt(STORAGE_VERSION_KEY, 0) < CURRENT_STORAGE_VERSION && !foldIntoSingleStore(sp)) return@withContext
        migratePillOpacity(sp)
        migrateQuickPanelMax(sp)
        WidgetProfiles.migrateSavedThemes(sp)
        if (REMOVED_NAMES.any(sp::contains)) sp.edit { REMOVED_NAMES.forEach(::remove) }
        java.io.File(context.filesDir, REMOVED_SAVED_LINK_ICONS_DIR).takeIf { it.exists() }?.let { runCatching { it.deleteRecursively() } }
    }

    /** The single-store move itself; false if it couldn't be saved, so it runs again at the next start. */
    private suspend fun foldIntoSingleStore(sp: SharedPreferences): Boolean {
        val editor = sp.edit()
        importLegacyDataStore(sp, editor)
        for (key in ALL) {
            if (key.legacyNames.isEmpty() || key.allNames.none(sp::contains)) continue
            @Suppress("UNCHECKED_CAST")
            val typed = key as SettingKey<Any>
            editor.put(typed, sp[typed])
        }
        if (sp.contains(THEME.name) || THEME.legacyNames.any(sp::contains)) {
            editor.put(THEME, ThemeMode.from(sp[THEME]).storedValue)
        }
        RETIRED_NAMES.forEach(editor::remove)
        editor.putInt(STORAGE_VERSION_KEY, CURRENT_STORAGE_VERSION)
        // The old store may hold the only copy of what was imported: keep it until the import is on disk, and the
        // migration runs again at the next start.
        if (!editor.commit()) return false
        runCatching { context.preferencesDataStoreFile(LEGACY_DATASTORE_NAME).delete() }
        return true
    }

    /**
     * Once per install: older versions drew a pill opacity of 50 (their default) fully opaque, so a stored 50 becomes
     * 100 and the pill looks the same after the update. Any other value already meant its own opacity.
     */
    private fun migratePillOpacity(sp: SharedPreferences) {
        if (sp.getBoolean(PILL_OPACITY_LINEAR_KEY, false)) return
        sp.edit {
            if (PILL_OPACITY.allNames.any(sp::contains)) put(PILL_OPACITY, pillOpacityFromOldScale(sp[PILL_OPACITY]))
            putBoolean(PILL_OPACITY_LINEAR_KEY, true)
        }
    }

    /**
     * Once per install: Max Quick Panel Apps used to cap only recent apps, so an install that chose more Quick Launch
     * apps than its max has the max raised to fit them, and the panel looks the same after the update.
     */
    private fun migrateQuickPanelMax(sp: SharedPreferences) {
        if (sp.getBoolean(QUICK_PANEL_CAPS_CHOSEN_APPS_KEY, false)) return
        sp.edit {
            fitQuickPanelToChosenApps(sp, this)
            putBoolean(QUICK_PANEL_CAPS_CHOSEN_APPS_KEY, true)
        }
    }

    /**
     * Copies DataStore values for settings that SharedPreferences doesn't hold under any spelling. Every
     * write since DataStore was added went to SharedPreferences first, so it wins whenever both have a value.
     */
    private suspend fun importLegacyDataStore(sp: SharedPreferences, editor: SharedPreferences.Editor) {
        if (!context.preferencesDataStoreFile(LEGACY_DATASTORE_NAME).exists()) return
        val stored: Map<Preferences.Key<*>, Any> = try {
            context.legacySettingsDataStore.data.first().asMap()
        } catch (e: Exception) {
            Log.w(TAG, "Old settings store unreadable; keeping current settings", e)
            return
        }
        for ((dsKey, value) in stored) {
            val key = keyFor(dsKey.name) ?: continue
            if (key.allNames.any(sp::contains)) continue
            when (key) {
                is BooleanSetting -> (value as? Boolean)?.let { editor.put(key, it) }
                is IntSetting -> (value as? Int)?.let { editor.put(key, it) }
                is StringSetting -> (value as? String)?.let { editor.put(key, it) }
                is StringSetSetting -> (value as? Set<*>)?.filterIsInstance<String>()?.let { editor.put(key, it.toSet()) }
            }
        }
    }
}
