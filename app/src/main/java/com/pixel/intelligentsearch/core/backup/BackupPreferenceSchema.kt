package com.pixel.intelligentsearch.core.backup

/**
 * What a restored SharedPreferences entry is allowed to look like. Backup files are user-supplied, so a
 * value of the wrong type (ClassCastException on read) or out of range (e.g. a negative result count fed
 * to take()) would otherwise crash search on every launch until app data is cleared.
 */
internal object BackupPreferenceSchema {

    const val MAX_KEYS = 2_000
    const val MAX_STRING_LENGTH = 64 * 1024
    const val MAX_STRING_SET_SIZE = 1_000

    /** Types of every key the app reads with a fixed literal name. */
    private val READ_TYPES: Map<String, String> = buildMap {
        fun typed(type: String, vararg keys: String) { for (key in keys) put(key, type) }
        typed(
            "BOOLEAN",
            "app.fuzzy.search", "app_animations", "app_fuzzy_search", "app_quick_launch", "bottom_search",
            "bottom_search_result", "contact.direct.call", "contact_direct_call", "context_aware_quick_apps",
            "diagnostics_overlay_enabled", "dynamic_icon_masking", "force_tutorial", "g_icon_enabled",
            "matrix_animation_enabled", "never_show_icon_pack_warning", "quick.search.horizontal",
            "quick_search_horizontal", "quick_search_maps", "quick_search_play_store", "quick_search_wikipedia",
            "quick_search_youtube", "search.android.settings", "search.apps", "search.background.show.wall", "search.calculator",
            "search.calendar", "search.contacts", "search.files", "search.files.hidden.files",
            "search.files.thumbnails", "search.shortcuts", "search.web", "search.web.suggestions", "search_apps",
            "search_calculator", "search_calendar", "search_contacts", "search_files", "search_files_hidden_files",
            "search_files_thumbnails", "search_overlay_enabled", "search_previous_searches", "search_shortcuts",
            "search_web", "settings.bottom.search", "settings.bottom.search.result",
            "settings_back_to_search_overlay", "shortcut.inline", "shortcut_inline", "show.wallpaper",
            "show_wallpaper", "smart_clipboard_suggestions", "tutorial_completed", "vibration_enabled",
            "widget_custom_keep_system_g", "widget_custom_sync_colors", "widget_material_custom_keep_system_g",
            "widget_material_custom_sync_colors", "widget_material_design_saved", "widget_material_lock_black",
            "widget_material_show_doodles", "widget_material_show_g_icon", "widget_material_show_voice",
            "widget_show_g_icon", "widget_show_gemini", "widget_show_voice", "widget_suppress_unsaved_warning",
            "widget_system_custom_keep_system_g", "widget_system_custom_sync_colors", "widget_system_design_saved",
            "widget_system_show_doodles", "widget_system_show_g_icon", "widget_system_show_voice", "widget_system_lock_black"
        )
        // The per-design element colours the widget reads with getInt (WidgetDesignSettings).
        for (design in listOf("widget", "widget_system", "widget_material")) {
            for (element in listOf("g_icon", "mic", "shortcut1", "shortcut2", "shortcut3", "action_icon", "shortcuts")) {
                put("${design}_custom_${element}_color", "INT")
            }
        }
        typed(
            "INT",
            "active_app_search_profile", "background.blur", "background.transparency", "contact_results_count",
            "file_results_count", "pill.opacity", "search.background.blur", "search.background.transparency",
            "search.pill.opacity", "search_weight_apps", "search_weight_contacts", "search_weight_files",
            "search_weight_web", "shortcut_results_count", "tutorial_step", "web_results_count",
            "widget.background.transparency", "widget_custom_color_opacity", "widget_custom_hue",
            "widget_custom_lightness", "widget_custom_saturation", "widget_custom_shortcuts_color",
            "widget_material_background_transparency", "widget_material_custom_color_int",
            "widget_material_custom_color_opacity", "widget_material_custom_hue", "widget_material_custom_lightness",
            "widget_material_custom_saturation", "widget_system_background_transparency",
            "widget_system_custom_color_int", "widget_system_custom_color_opacity", "widget_system_custom_hue",
            "widget_system_custom_lightness", "widget_system_custom_saturation"
        )
        typed(
            "STRING",
            "active_icon_pack", "adaptive_icon_shape", "custom_bangs_json", "custom_icon_packs_order",
            "custom_icon_pills", "custom_search_engine_url", "night.mode", "search.engine", "search.pills",
            "search_pills", "search_sections_config_json", "theme", "web_shortcut_trigger_symbol",
            "widget.theme.style", "widget_action_icon", "widget_material_action_icon", "widget_material_g_icon",
            "widget_material_material_g_icon", "widget_material_shortcut_1", "widget_material_shortcut_2",
            "widget_material_shortcut_3", "widget_material_shortcut_order", "widget_material_subtheme",
            "widget_shortcut_1", "widget_shortcut_2", "widget_shortcut_3", "widget_shortcut_order",
            "widget_subtheme", "widget_system_material_g_icon", "widget_system_shortcut_1",
            "widget_system_shortcut_2", "widget_system_shortcut_3", "widget_system_shortcut_order",
            "widget_system_subtheme", "widget_theme_style"
        )
        typed("STRING_SET", "disabled_web_shortcuts", "hidden_apps", "search.files.tree_uris", "search.files.media_uris")
    }

    fun expectedType(key: String): String? = READ_TYPES[key]

    /** Ranges the settings UI can produce; restored ints outside them are rejected. */
    fun intRange(key: String): IntRange? = when {
        // 0 is legitimate: switching to an empty pill profile stores a shortcut count of 0.
        key.endsWith("_results_count") -> 0..20
        key.startsWith("search_weight_") -> 0..100
        key.endsWith("_hue") -> 0..360
        key.endsWith("_saturation") || key.endsWith("_lightness") -> 0..100
        key.contains("blur") || key.contains("transparency") || key.endsWith("opacity") -> 0..100
        key == "tutorial_step" -> 0..20
        key == "active_app_search_profile" -> 0..16
        else -> null
    }

    /** Debug-only switches are never imported: a backup must not turn on developer behaviour. */
    fun isDebugKey(key: String): Boolean = key.startsWith("debug.") || key.startsWith("debug_")
}
