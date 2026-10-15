package com.pixel.intelligentsearch.core.theme

import android.content.res.Configuration

/** The app's light/dark choice, stored as the label shown in Appearance settings. */
enum class ThemeMode(val storedValue: String) {
    SYSTEM("System"),
    DARK("Material Dark"),
    LIGHT("Material Light");

    /** Whether to draw dark, given whether the system itself is in dark mode. */
    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        DARK -> true
        LIGHT -> false
        SYSTEM -> systemIsDark
    }

    companion object {
        /** Parses a stored value, including spellings older versions saved ("Dark mode", "Dark", "system", ...). */
        fun from(value: String?): ThemeMode = when (value?.trim()?.lowercase()) {
            "material dark", "dark mode", "dark" -> DARK
            "material light", "light mode", "light" -> LIGHT
            else -> SYSTEM
        }

        /** Whether [configuration] is in system dark mode, for code without Compose's isSystemInDarkTheme(). */
        fun systemIsDark(configuration: Configuration): Boolean =
            (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
