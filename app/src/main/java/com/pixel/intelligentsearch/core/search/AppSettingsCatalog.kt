package com.pixel.intelligentsearch.core.search

/**
 * Intelligent Search's own settings pages, searchable beside Android's ([AndroidSettingsCatalog]): typing "theme",
 * "hidden apps" or "backup" offers the page that changes it. Each page carries its route key
 * ([com.pixel.intelligentsearch.core.navigation.Route.fromKey]) instead of a Settings action, and opens in the app's
 * own settings rather than the phone's. Keywords avoid app names ("contacts", "files"): those are the apps'.
 */
object AppSettingsCatalog {

    /** The id prefix that marks a result as one of these pages, so a pinned one finds its way back. */
    const val ID_PREFIX = "app_page:"

    private fun page(route: String, title: String, iconType: String, vararg keywords: String) =
        AndroidSettingsCatalog.Page(ID_PREFIX + route, title, action = "", iconType = iconType, keywords = keywords.toList(), appRoute = route)

    val pages: List<AndroidSettingsCatalog.Page> = listOf(
        page("appearance", "Appearance", "is_appearance",
            "app theme", "theme", "wallpaper", "background blur", "transparency", "pill opacity", "vibration",
            "haptics", "matrix animation", "morph animation", "microphone button"),
        page("search_sources", "Search Sources", "is_sources",
            "sources", "what to search"),
        page("search_behavior", "Search Behavior", "is_behavior",
            "keyboard", "at a glance", "quick app panel", "bottom search bar", "clipboard suggestions", "enter key"),
        page("widget", "Widget Customization", "is_widget",
            "widget", "widgets", "search bar widget", "color studio", "widget shortcuts", "g icon"),
        page("launch_portal", "Launch Portal", "is_launch",
            "quick settings tile", "search tile", "home screen shortcut", "floating button", "notification shortcut"),
        page("app_search", "App & Shortcut Search", "is_apps",
            "app search", "application search", "quick panel apps"),
        page("search_pills", "Application Search", "is_pills",
            "search pills", "add applications", "quick launch", "app pills"),
        page("manage_hidden_apps", "Manage Hidden Apps", "is_hidden",
            "hidden apps", "hide apps", "hide an app", "unhide apps"),
        page("web_search", "Web Search", "is_web",
            "search engine", "web suggestions", "quick web shortcuts", "bangs", "custom search engine", "tor"),
        page("contact_search", "Contact Search", "is_contacts",
            "direct call"),
        page("file_search", "File Search", "is_files",
            "search folders", "photos and videos", "documents"),
        page("custom_icons", "Custom Icons", "is_icons",
            "icon pack", "icon packs", "icons"),
        page("backup_restore", "Encrypted Backup", "is_backup",
            "backup", "restore", "export settings", "import settings", "passkey", "passphrase"),
        page("ai_answers", "AI Answers", "is_ai",
            "ai", "api key", "claude", "chatgpt", "gemini", "openai"),
        page("setup", "Finish Setting Up", "is_setup",
            "setup", "set up", "permissions", "calendar search"),
    )
}
