package com.pixel.intelligentsearch.feature.widget

import com.pixel.intelligentsearch.core.data.settings.get
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.speech.RecognizerIntent
import android.util.Log
import com.pixel.intelligentsearch.R

private const val TAG = "SearchWidgetProvider"

internal fun PackageManager.resolveActivityCompat(intent: Intent, flags: Long = 0L): ResolveInfo? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        resolveActivity(intent, PackageManager.ResolveInfoFlags.of(flags))
    } else {
        @Suppress("DEPRECATION")
        resolveActivity(intent, flags.toInt())
    }
}

internal fun PackageManager.queryIntentActivitiesCompat(intent: Intent, flags: Long = 0L): List<ResolveInfo> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags))
    } else {
        @Suppress("DEPRECATION")
        queryIntentActivities(intent, flags.toInt())
    }
}

internal fun PackageManager.getApplicationInfoCompat(packageName: String, flags: Long = 0L): ApplicationInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags))
    } else {
        @Suppress("DEPRECATION")
        getApplicationInfo(packageName, flags.toInt())
    }
}

fun getVoiceSearchIntent(context: Context): Intent {
    val googleVoice = Intent(Intent.ACTION_MAIN).apply {
        setClassName("com.google.android.googlequicksearchbox", "com.google.android.googlequicksearchbox.VoiceSearchActivity")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (context.packageManager.resolveActivityCompat(googleVoice) != null) {
        return googleVoice
    }
    val webSearch = Intent(RecognizerIntent.ACTION_WEB_SEARCH).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (context.packageManager.resolveActivityCompat(webSearch) != null) {
        return webSearch
    }
    val speech = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (context.packageManager.resolveActivityCompat(speech) != null) {
        return speech
    }
    // No recognizer at all: open the overlay so the user can type instead.
    return Intent(context, WidgetActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}

fun getShortcutIconRes(shortcut: String, isMaterialYou: Boolean = true): Int {
    return when (shortcut) {
        "Voice Search" -> if (isMaterialYou) R.drawable.ic_mic else R.drawable.ic_mic_original
        "Google Lens" -> R.drawable.ic_camera
        "Assistant", "Live", "Gemini" -> R.drawable.ic_lens_action
        "Search" -> R.drawable.ic_search_lens_expressive
        "Song Search", "Now Playing" -> R.drawable.ic_music
        "Translate (text)" -> R.drawable.ic_translate
        "Translate (camera)" -> R.drawable.ic_document_scanner
        "Weather" -> R.drawable.ic_weather
        "Sports" -> R.drawable.ic_sports
        "Dictionary" -> R.drawable.ic_dictionary
        "Homework" -> R.drawable.ic_homework
        "Finance" -> R.drawable.ic_finance
        "Saved" -> R.drawable.ic_saved
        "News" -> R.drawable.ic_news
        else -> R.drawable.ic_camera
    }
}

fun getLensSearchIntent(context: Context): Intent {
    try {
        val lensStandalone = context.packageManager.getLaunchIntentForPackage("com.google.ar.lens")?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (lensStandalone != null && context.packageManager.resolveActivityCompat(lensStandalone) != null) {
            return lensStandalone
        }

        val lensIntent = Intent().apply {
            setClassName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.deeplink.LensDeeplink")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (context.packageManager.resolveActivityCompat(lensIntent) != null) {
            return lensIntent
        }
    } catch (_: Exception) {}

    val webLens = Intent(Intent.ACTION_VIEW, Uri.parse("https://lens.google.com")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (context.packageManager.resolveActivityCompat(webLens) != null) {
        return webLens
    }
    return Intent(context, WidgetActivity::class.java).apply {
        action = "com.pixel.intelligentsearch.LAUNCH_LENS"
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}

fun getLensTranslateIntent(context: Context): Intent {
    try {
        val lensStandalone = context.packageManager.getLaunchIntentForPackage("com.google.ar.lens")?.apply {
            putExtra("lens_mode", "translate")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (lensStandalone != null && context.packageManager.resolveActivityCompat(lensStandalone) != null) {
            return lensStandalone
        }

        val translateIntent = Intent().apply {
            setClassName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.deeplink.LensDeeplink")
            putExtra("lens_mode", "translate")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (context.packageManager.resolveActivityCompat(translateIntent) != null) {
            return translateIntent
        }
    } catch (_: Exception) {}
    return Intent(context, WidgetActivity::class.java).apply {
        action = "com.pixel.intelligentsearch.LAUNCH_LENS_TRANSLATE"
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}

/**
 * Opens the Gemini corner-swipe overlay for the widget's Search and Assistant buttons, exactly as the
 * corner swipe gesture does: SearchManager.launchAssist with the gesture invocation type. That method
 * is hidden, so if a future Android blocks it the assistant is opened directly instead of the tap doing
 * nothing. ACTION_ASSIST itself is avoided because this app registers for it and would loop back into
 * its own overlay.
 */
fun launchGeminiCornerOverlay(context: Context): Boolean {
    // launchAssist shows whichever app holds the assistant role. When that is Intelligent Search itself, it would only
    // open this app's own search, so Gemini is reached through the fallbacks below instead.
    val isTheAssistant = runCatching {
        context.getSystemService(android.app.role.RoleManager::class.java)?.isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT) == true
    }.getOrDefault(false)
    try {
        val searchManager = context.getSystemService(Context.SEARCH_SERVICE) as? android.app.SearchManager
        val launchAssist = searchManager?.javaClass?.methods?.find { it.name == "launchAssist" && it.parameterTypes.size == 1 }
        if (launchAssist != null && !isTheAssistant) {
            val args = android.os.Bundle().apply {
                putInt("invocation_type", 1) // INVOCATION_TYPE_GESTURE: the corner swipe
                putBoolean("launch_gemini_overlay", true)
            }
            launchAssist.invoke(searchManager, args)
            return true
        }
    } catch (e: Throwable) {
        android.util.Log.w("SearchWidgetProvider", "SearchManager.launchAssist failed", e)
    }
    val fallbacks = listOfNotNull(
        Intent(Intent.ACTION_VOICE_COMMAND).setPackage("com.google.android.googlequicksearchbox"),
        context.packageManager.getLaunchIntentForPackage("com.google.android.apps.bard"),
        getVoiceSearchIntent(context)
    )
    for (fallback in fallbacks) {
        try {
            if (context.packageManager.resolveActivityCompat(fallback) == null) continue
            context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (e: Exception) {
            android.util.Log.w("SearchWidgetProvider", "Assistant fallback ${fallback.action} failed", e)
        }
    }
    return false
}

fun getVoiceActionIntent(context: Context): Intent {
    return Intent(context, WidgetActivity::class.java).apply {
        action = "com.pixel.intelligentsearch.LAUNCH_GEMINI_OVERLAY"
        putExtra("launch_gemini_overlay", true)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}

fun getGeminiSearchIntent(context: Context): Intent = getVoiceActionIntent(context)

fun getShortcutIntent(context: Context, shortcut: String): Intent {
    return when (shortcut) {
        "Voice Search" -> getVoiceSearchIntent(context)
        "Assistant", "Live", "Gemini" -> getVoiceActionIntent(context)
        "Search" -> getGeminiSearchIntent(context)
        "Song Search", "Now Playing" -> getNowPlayingIntent(context)
        "Google Lens" -> getLensSearchIntent(context)
        "Translate (text)" -> context.packageManager.getLaunchIntentForPackage("com.google.android.apps.translate")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://translate.google.com")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        "Translate (camera)" -> getLensTranslateIntent(context)
        "Weather" -> getCustomIntentOrDefault(context, "Weather") {
            val intentWeatherApp = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.weather")
            if (intentWeatherApp != null) {
                intentWeatherApp.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            } else {
                val intentSearchWeather = Intent(Intent.ACTION_VIEW).apply {
                    setClassName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.weather.WeatherExportedActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (context.packageManager.resolveActivityCompat(intentSearchWeather) != null) {
                    intentSearchWeather
                } else {
                    // Any browser: the Google app is already known to be missing by this point.
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com/search?q=weather")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            }
        }
        "Sports" -> getCustomIntentOrDefault(context, "Sports") {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra("query", "Sports")
                setPackage("com.google.android.googlequicksearchbox")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivityCompat(intent) != null) {
                intent
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=Sports")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            }
        }
        "Dictionary" -> getCustomIntentOrDefault(context, "Dictionary") {
            val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.books")
            if (intent != null) {
                intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.books")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            }
        }
        "Homework" -> {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage("com.google.android.apps.labs.language.tailwind")
            intent?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.labs.language.tailwind")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }
        "Finance" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/finance")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        "Saved" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com/save")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        "News" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://news.google.com")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        else -> getLensSearchIntent(context)
    }
}

fun getCustomIntentOrDefault(context: Context, shortcut: String, defaultIntent: () -> Intent): Intent {
    val prefs = SearchWidgetProvider.getSafeSharedPreferences(context)
    val customType = prefs.getString("${shortcut}_custom_type", "default")
    val customValue = prefs.getString("${shortcut}_custom_value", "")

    if (customType == "url" && !customValue.isNullOrEmpty()) {
        val urlString = if (!customValue.startsWith("http://") && !customValue.startsWith("https://")) "https://$customValue" else customValue
        return Intent(Intent.ACTION_VIEW, Uri.parse(urlString)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    } else if (customType == "app" && !customValue.isNullOrEmpty()) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(customValue)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return launchIntent
        }
    }

    return defaultIntent()
}

fun getNowPlayingIntent(context: Context): Intent {
    val pm = context.packageManager

    // 1. Pixel Now Playing History via Android System Intelligence (ASI)
    try {
        val asHistoryWithPkg = Intent("com.google.android.as.NOW_PLAYING_HISTORY").apply {
            setPackage("com.google.android.as")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (pm.resolveActivityCompat(asHistoryWithPkg) != null) {
            return asHistoryWithPkg
        }

        val asHistoryGeneric = Intent("com.google.android.as.NOW_PLAYING_HISTORY").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (pm.resolveActivityCompat(asHistoryGeneric) != null) {
            return asHistoryGeneric
        }
    } catch (_: Throwable) {}

    // 2. Pixel Now Playing History via Settings Intelligence
    try {
        val settingsIntelligenceHistory = Intent("com.google.android.settings.intelligence.NOW_PLAYING_HISTORY").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (pm.resolveActivityCompat(settingsIntelligenceHistory) != null) {
            return settingsIntelligenceHistory
        }
    } catch (_: Throwable) {}

    // 3. Android System Settings: Now Playing Page
    try {
        val settingsNowPlaying = Intent("android.settings.NOW_PLAYING_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (pm.resolveActivityCompat(settingsNowPlaying) != null) {
            return settingsNowPlaying
        }
    } catch (_: Throwable) {}

    // 4. Standalone Pixel Now Playing app
    try {
        val nowPlayingIntent = pm.getLaunchIntentForPackage("com.google.android.apps.pixel.nowplaying")?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (nowPlayingIntent != null && pm.resolveActivityCompat(nowPlayingIntent) != null) {
            return nowPlayingIntent
        }
    } catch (_: Throwable) {}

    // 5. Google Sound Search / Assistant Music Search
    try {
        val googleSoundSearchIntent = Intent("com.google.android.googlequicksearchbox.MUSIC_SEARCH").apply {
            setPackage("com.google.android.googlequicksearchbox")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (pm.resolveActivityCompat(googleSoundSearchIntent) != null || pm.queryIntentActivitiesCompat(googleSoundSearchIntent).isNotEmpty()) {
            return googleSoundSearchIntent
        }

        val genericSoundSearchIntent = Intent("com.google.android.googlequicksearchbox.MUSIC_SEARCH").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (pm.resolveActivityCompat(genericSoundSearchIntent) != null || pm.queryIntentActivitiesCompat(genericSoundSearchIntent).isNotEmpty()) {
            return genericSoundSearchIntent
        }
    } catch (_: Throwable) {}

    // 6. External music recognition services (Shazam / SoundHound)
    try {
        val shazamIntent = pm.getLaunchIntentForPackage("com.shazam.android")?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (shazamIntent != null && pm.resolveActivityCompat(shazamIntent) != null) {
            return shazamIntent
        }
    } catch (_: Throwable) {}

    try {
        val soundHoundIntent = pm.getLaunchIntentForPackage("com.melodis.midomiMusicIdentifier.freemium")?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (soundHoundIntent != null && pm.resolveActivityCompat(soundHoundIntent) != null) {
            return soundHoundIntent
        }
    } catch (_: Throwable) {}

    // 7. Universal Google voice music search query
    return Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=what+song+is+this")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
