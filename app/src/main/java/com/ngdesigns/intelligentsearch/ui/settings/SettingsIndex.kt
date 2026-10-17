package com.ngdesigns.intelligentsearch.ui.settings

import com.ngdesigns.intelligentsearch.data.SettingEntry

/**
 * Every setting by key, title, section and the other words people use for it, so search finds settings too
 * ("blur", "dark mode", "typos"). The settings screens use the same keys to scroll to and highlight a setting.
 */
object SettingsIndex {
    private fun e(key: String, title: String, section: String, vararg words: String) = SettingEntry(key, title, section, words.toList())

    val entries: List<SettingEntry> = listOf(
        e("look.theme", "Theme", "Look", "dark mode", "light mode", "night mode", "appearance"),
        e("look.colorStyle", "Colour style", "Look", "color", "palette", "vibrant", "expressive", "monochrome", "material you"),
        e("look.contrast", "Contrast", "Look", "high contrast", "accessibility"),
        e("look.density", "Density", "Look", "compact", "spacing"),
        e("look.showWallpaper", "Show wallpaper", "Look", "background", "wallpaper"),
        e("look.blur", "Background blur", "Look", "blurry", "frosted"),
        e("look.transparency", "Background transparency", "Look", "see through", "opacity"),
        e("look.barOpacity", "Search bar opacity", "Look", "pill opacity"),
        e("look.animations", "Animations", "Look", "motion", "reduce motion"),
        e("look.haptics", "Haptics", "Look", "vibration", "vibrate"),
        e("look.iconShape", "Icon shape", "Look", "icons", "shape", "circle", "squircle"),
        e("look.iconPack", "Icon pack", "Look", "icons", "theme icons"),
        e("look.monochromeIcons", "Themed icons", "Look", "monochrome", "icons"),
        e("look.searchScreen", "Search screen", "Look", "overlay", "launcher search bar"),
        e("sources.apps", "Apps", "Sources", "applications"),
        e("sources.shortcuts", "App shortcuts", "Sources", "shortcuts"),
        e("sources.contacts", "Contacts", "Sources", "people", "phone numbers"),
        e("sources.calendar", "Calendar", "Sources", "events", "agenda"),
        e("sources.files", "Files", "Sources", "documents", "folders", "photos"),
        e("sources.webSuggestions", "Web suggestions", "Sources", "autocomplete", "suggest"),
        e("sources.history", "Search history", "Sources", "recent searches", "previous searches"),
        e("sources.calculator", "Calculator", "Sources", "math", "maths", "convert", "units"),
        e("sources.clipboard", "Clipboard suggestions", "Sources", "paste", "copied"),
        e("sources.contextAwareQuickApps", "Context-aware quick apps", "Sources", "smart apps"),
        e("behaviour.barAtBottom", "Search bar at the bottom", "Behaviour", "bottom bar", "position"),
        e("behaviour.resultsBottomUp", "Results from the bottom up", "Behaviour", "order", "reverse"),
        e("behaviour.typos", "Allow typos", "Behaviour", "fuzzy", "spelling", "misspelled"),
        e("behaviour.quickLaunch", "Quick launch", "Behaviour", "open instantly", "auto open"),
        e("behaviour.contactDirectCall", "Call contacts directly", "Behaviour", "direct call", "dial"),
        e("behaviour.assistantStartsWithVoice", "Assistant starts listening", "Behaviour", "voice", "microphone"),
        e("behaviour.backToSearch", "Back returns to search", "Behaviour", "back gesture"),
        e("ranking.apps", "Apps priority", "Ranking", "weight", "order"),
        e("ranking.contacts", "People priority", "Ranking", "weight", "order"),
        e("ranking.files", "Files priority", "Ranking", "weight", "order"),
        e("ranking.web", "Web priority", "Ranking", "weight", "order"),
        e("ranking.results", "Number of results", "Ranking", "limit", "how many"),
        e("web.engine", "Search engine", "Web", "google", "bing", "duckduckgo", "tor", "default search"),
        e("web.customTemplate", "Custom search address", "Web", "custom engine", "url"),
        e("web.shortcuts", "Quick web shortcuts", "Web", "bangs", "!g", "trigger"),
        e("web.quickButtons", "Quick search buttons", "Web", "youtube", "wikipedia", "play store", "maps"),
        e("quickApps.profiles", "Quick apps", "Quick apps", "search in apps", "pills", "profiles"),
        e("privacy.hiddenApps", "Hidden apps", "Privacy", "hide", "private", "secret"),
        e("privacy.assistants", "Share with assistants", "Privacy", "app functions", "gemini"),
        e("widgets", "Widgets", "Widgets", "home screen", "search bar widget", "colours"),
        e("entry.tile", "Quick Settings tile", "Ways in", "tile", "notification shade"),
        e("entry.assistant", "Default digital assistant", "Ways in", "assistant", "voice"),
        e("entry.searchRole", "Home screen search bar", "Ways in", "search engine role", "launcher"),
        e("backup", "Encrypted backup", "Backup", "export", "import", "restore", "passphrase"),
        e("backup.automatic", "Automatic backup", "Backup", "weekly", "schedule"),
        e("help.tutorial", "Tutorial", "Help", "guide", "how to"),
        e("help.setup", "Finish setting up", "Help", "setup", "checklist"),
        e("help.about", "About", "Help", "version", "privacy policy", "support", "email"),
    )
}
