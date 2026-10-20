package com.pixel.intelligentsearch.core.search

import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * The Android Settings pages search can open directly: a hand-written list, since Settings publishes no
 * searchable index to other apps. Each page names the [Settings] action that opens it, its title as the
 * Settings app shows it, the words people type for it and the icon its result row uses. Pages whose action
 * arrived after Android 12 are only listed on releases that have it; whether a page opens on this particular
 * phone is checked at search time ([AndroidSettingsSearch]).
 */
object AndroidSettingsCatalog {

    /** One Settings page: [action] opens it, [keywords] are matched besides the [title]. */
    class Page(
        val id: String,
        val title: String,
        val action: String,
        val iconType: String,
        val keywords: List<String>
    )

    /**
     * `Settings.ACTION_SYSTEM_UPDATE_SETTINGS`: public from API level 36.1, and answered by the Settings app
     * before that. Spelled out so older releases can look it up too.
     */
    private const val SYSTEM_UPDATE_SETTINGS = "android.settings.SYSTEM_UPDATE_SETTINGS"

    /** The Settings app's own action for its Text-to-Speech page; not in the SDK, so only offered where it resolves. */
    private const val TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"

    private fun page(id: String, title: String, action: String, iconType: String, vararg keywords: String) =
        Page(id, title, action, iconType, keywords.toList())

    val pages: List<Page> = buildList {
        // Network & connections
        add(page("wifi", "Wi-Fi", Settings.ACTION_WIFI_SETTINGS, "wifi",
            "wifi", "wi fi", "wireless", "wlan", "networks", "wifi networks"))
        add(page("network", "Network & Internet", Settings.ACTION_WIRELESS_SETTINGS, "network",
            "network", "internet", "hotspot", "tethering", "wifi hotspot", "portable hotspot", "mobile hotspot", "data saver",
            "private dns"))
        add(page("mobile_network", "Mobile Network", Settings.ACTION_DATA_ROAMING_SETTINGS, "mobile_network",
            "mobile network", "mobile data", "cellular", "cellular data", "roaming", "sim", "carrier", "apn"))
        add(page("data_usage", "Data Usage", Settings.ACTION_DATA_USAGE_SETTINGS, "data_usage",
            "data usage", "data use", "mobile data usage", "data limit", "data warning"))
        add(page("airplane", "Airplane Mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS, "airplane",
            "airplane mode", "airplane", "aeroplane mode", "flight mode"))
        add(page("bluetooth", "Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, "bluetooth",
            "bluetooth", "bt", "blue tooth", "pairing", "pair new device", "connected devices", "headphones", "earbuds"))
        add(page("nfc", "NFC", Settings.ACTION_NFC_SETTINGS, "nfc", "nfc", "near field communication"))
        add(page("contactless", "Contactless Payments", Settings.ACTION_NFC_PAYMENT_SETTINGS, "contactless",
            "contactless payments", "tap and pay", "nfc payment", "default payment app"))
        add(page("cast", "Cast", Settings.ACTION_CAST_SETTINGS, "cast",
            "cast", "screen cast", "screen mirroring", "mirror screen"))
        add(page("vpn", "VPN", Settings.ACTION_VPN_SETTINGS, "vpn", "vpn", "virtual private network"))

        // Battery
        add(page("battery", "Battery", Intent.ACTION_POWER_USAGE_SUMMARY, "battery",
            "battery", "battery usage", "battery percentage", "battery health", "power", "charging"))
        add(page("battery_saver", "Battery Saver", Settings.ACTION_BATTERY_SAVER_SETTINGS, "battery_saver",
            "battery saver", "power saver", "low power mode", "extreme battery saver"))
        add(page("battery_optimization", "Battery Optimization", Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, "battery",
            "battery optimization", "battery optimisation", "background usage", "unrestricted battery"))

        // Display & sound
        add(page("display", "Display", Settings.ACTION_DISPLAY_SETTINGS, "display",
            "display", "screen", "brightness", "adaptive brightness", "dark mode", "dark theme", "light mode",
            "screen timeout", "font size", "text size", "display size", "refresh rate", "smooth display"))
        add(page("night_light", "Night Light", Settings.ACTION_NIGHT_DISPLAY_SETTINGS, "night_light",
            "night light", "blue light", "eye comfort", "night mode", "warm screen"))
        add(page("auto_rotate", "Auto-Rotate Screen", Settings.ACTION_AUTO_ROTATE_SETTINGS, "auto_rotate",
            "auto rotate", "autorotate", "rotation", "screen rotation", "rotate"))
        add(page("wallpaper", "Wallpaper & Style", Intent.ACTION_SET_WALLPAPER, "wallpaper",
            "wallpaper", "wallpapers", "change wallpaper", "background", "wallpaper and style"))
        add(page("screen_saver", "Screen Saver", Settings.ACTION_DREAM_SETTINGS, "screen_saver",
            "screen saver", "screensaver", "daydream"))
        add(page("sound", "Sound & Vibration", Settings.ACTION_SOUND_SETTINGS, "sound",
            "sound", "vibration", "vibrate", "haptics", "volume", "ringtone", "notification sound", "alarm sound"))
        add(page("dnd", "Do Not Disturb", Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS, "dnd",
            "do not disturb", "dnd", "silence", "priority mode", "modes"))

        // Notifications & apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(page("notifications", "App Notifications", Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS, "notifications",
                "notifications", "app notifications", "notification settings"))
        }
        add(page("notification_access", "Device & App Notifications", Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, "notification_access",
            "notification access", "notification listener", "notification read reply"))
        add(page("apps", "Apps", Settings.ACTION_APPLICATION_SETTINGS, "apps",
            "apps", "applications", "app info", "installed apps", "all apps", "manage apps", "uninstall apps"))
        add(page("default_apps", "Default Apps", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, "default_apps",
            "default apps", "default browser", "default dialer", "default calling app", "default sms app", "default assistant",
            "open by default"))
        add(page("home_app", "Home App", Settings.ACTION_HOME_SETTINGS, "home_app",
            "home app", "launcher", "default launcher", "home screen app"))
        add(page("overlay", "Display Over Other Apps", Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "overlay",
            "display over other apps", "draw over other apps", "overlay permission", "appear on top"))
        add(page("write_settings", "Modify System Settings", Settings.ACTION_MANAGE_WRITE_SETTINGS, "write_settings",
            "modify system settings", "write settings"))
        add(page("unknown_apps", "Install Unknown Apps", Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "unknown_apps",
            "install unknown apps", "unknown sources", "sideload", "apk install"))
        add(page("all_files_access", "All Files Access", Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION, "all_files_access",
            "all files access", "manage all files", "file access"))
        add(page("usage_access", "Usage Access", Settings.ACTION_USAGE_ACCESS_SETTINGS, "usage_access",
            "usage access", "usage data access", "app usage access"))

        // Storage, location, privacy & security
        add(page("storage", "Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "storage",
            "storage", "free up space", "space", "disk", "memory", "internal storage"))
        add(page("location", "Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS, "location",
            "location", "gps", "location services", "location access"))
        add(page("privacy", "Privacy", Settings.ACTION_PRIVACY_SETTINGS, "privacy",
            "privacy", "permissions", "permission manager", "microphone access", "privacy dashboard"))
        add(page("security", "Security", Settings.ACTION_SECURITY_SETTINGS, "security",
            "security", "screen lock", "lock screen", "pin", "password", "pattern", "fingerprint", "face unlock",
            "biometrics", "find my device"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            add(page("passwords", "Passwords, Passkeys & Accounts", Settings.ACTION_CREDENTIAL_PROVIDER, "passwords",
                "passwords", "passkeys", "password manager", "autofill", "credential manager"))
        }
        add(page("accounts", "Accounts", Settings.ACTION_SYNC_SETTINGS, "accounts",
            "accounts", "sync", "account sync", "add account"))
        add(page("wallet", "Wallet", Settings.ACTION_QUICK_ACCESS_WALLET_SETTINGS, "wallet",
            "wallet", "quick access wallet", "show wallet"))

        // Accessibility & input
        add(page("accessibility", "Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS, "accessibility",
            "accessibility", "a11y", "talkback", "magnification", "select to speak", "color correction", "colour correction"))
        add(page("captions", "Caption Preferences", Settings.ACTION_CAPTIONING_SETTINGS, "captions",
            "captions", "caption preferences", "subtitles", "live caption"))
        add(page("languages", "Languages", Settings.ACTION_LOCALE_SETTINGS, "languages",
            "language", "languages", "system language", "locale"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(page("regional", "Regional Preferences", Settings.ACTION_REGIONAL_PREFERENCES_SETTINGS, "regional",
                "regional preferences", "region", "temperature units", "first day of week", "numbers preferences"))
        }
        add(page("keyboard", "On-Screen Keyboard", Settings.ACTION_INPUT_METHOD_SETTINGS, "keyboard",
            "keyboard", "on screen keyboard", "input method", "gboard", "virtual keyboard"))
        add(page("physical_keyboard", "Physical Keyboard", Settings.ACTION_HARD_KEYBOARD_SETTINGS, "keyboard",
            "physical keyboard", "hardware keyboard", "keyboard layout"))
        add(page("dictionary", "Personal Dictionary", Settings.ACTION_USER_DICTIONARY_SETTINGS, "dictionary",
            "personal dictionary", "user dictionary", "dictionary"))
        // Voice input is speech recognition; reading text aloud is the Text-to-Speech page's.
        add(page("voice_input", "Voice Input", Settings.ACTION_VOICE_INPUT_SETTINGS, "voice_input",
            "voice input", "speech recognition", "voice typing"))
        add(page("tts", "Text-to-Speech Output", TTS_SETTINGS, "tts",
            "text to speech", "tts", "speech rate", "speech output", "preferred engine"))

        // System
        add(page("date_time", "Date & Time", Settings.ACTION_DATE_SETTINGS, "date_time",
            "date and time", "date & time", "date", "time", "time zone", "timezone", "24 hour"))
        add(page("printing", "Printing", Settings.ACTION_PRINT_SETTINGS, "printing", "printing", "print", "printer"))
        add(page("developer", "Developer Options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "developer",
            "developer options", "developer", "usb debugging", "adb", "wireless debugging", "dev options"))
        add(page("about", "About Phone", Settings.ACTION_DEVICE_INFO_SETTINGS, "about",
            "about phone", "about device", "build number", "android version", "device name", "imei", "model number",
            "phone model"))
        add(page("system_update", "System Update", SYSTEM_UPDATE_SETTINGS, "system_update",
            "system update", "software update", "android update", "security update"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            add(page("satellite", "Satellite Messaging", Settings.ACTION_SATELLITE_SETTING, "satellite",
                "satellite", "satellite messaging", "satellite sos"))
        }
    }

    /**
     * Phrases that name a Settings page no public action opens. A query that is one of them gets no page rather
     * than a near miss: "screen time" is Digital Wellbeing's, not Display's "screen timeout".
     */
    val unlisted: Set<String> = setOf("screen time")
}
