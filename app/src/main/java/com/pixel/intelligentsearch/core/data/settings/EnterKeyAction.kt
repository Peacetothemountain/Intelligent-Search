package com.pixel.intelligentsearch.core.data.settings

/**
 * Enter Key Action: what Enter (the keyboard's search key, or a physical keyboard's Enter) does in the search overlay
 * when no result is highlighted. A result highlighted with the arrow keys always opens on Enter.
 */
enum class EnterKeyAction(val storedValue: String, val label: String) {
    /** Opens the Best Match, a typed web address or the Quick Launch app, and otherwise searches the web. */
    OPEN_TOP_RESULT("open_top_result", "Open Top Result"),

    /** Always searches the web for what was typed (a bang still goes to its site). */
    SEARCH_WEB("search_web", "Search the Web");

    companion object {
        /** The action stored as [value]; anything unknown (an older or edited backup) reads as the default. */
        fun from(value: String?): EnterKeyAction = entries.firstOrNull { it.storedValue == value } ?: OPEN_TOP_RESULT
    }
}
