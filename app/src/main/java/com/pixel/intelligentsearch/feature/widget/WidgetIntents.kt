package com.pixel.intelligentsearch.feature.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.core.net.toUri
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.answers.GoogleTranslate
import com.pixel.intelligentsearch.core.system.GoogleApps
import com.pixel.intelligentsearch.core.util.queryIntentActivitiesCompat
import com.pixel.intelligentsearch.core.util.resolveActivityCompat

fun getVoiceSearchIntent(context: Context): Intent {
    val googleVoice = Intent(Intent.ACTION_MAIN).apply {
        setClassName(GoogleApps.GOOGLE_APP, GoogleApps.VOICE_SEARCH_ACTIVITY)
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
    return ownVoiceSearchIntent(context)
}

/**
 * This app's own voice search. A plain recognizer hands the words back only to whoever started it: the search starts
 * it and searches them. No recognizer at all: the search opens so the user can type instead.
 */
private fun ownVoiceSearchIntent(context: Context): Intent {
    return Intent(context, WidgetActivity::class.java).apply {
        if (speechRecognizerIntent(context) != null) {
            action = ACTION_RECOGNIZE_SPEECH_IN_SEARCH
            putExtra(EXTRA_RECOGNIZE_SPEECH, true)
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}

/** Asks the search to start [speechRecognizerIntent] and search what is said. */
const val EXTRA_RECOGNIZE_SPEECH = "com.pixel.intelligentsearch.extra.RECOGNIZE_SPEECH"

private const val ACTION_RECOGNIZE_SPEECH_IN_SEARCH = "com.pixel.intelligentsearch.RECOGNIZE_SPEECH"

/**
 * Asks the private [WidgetActivity] to search the web for [query] in another app the moment it's tapped, saying so if
 * none can, and show nothing itself: for a doodle drawn while no other app could search the web.
 */
fun webSearchFromWidgetIntent(context: Context, query: String): Intent =
    Intent(context, WidgetActivity::class.java).apply {
        action = ACTION_SEARCH_WEB
        putExtra(android.app.SearchManager.QUERY, query)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }

/** [webSearchFromWidgetIntent]'s action. */
const val ACTION_SEARCH_WEB = "com.pixel.intelligentsearch.SEARCH_WEB"

/** The phone's plain speech recognizer, to start for a result, or null when none is installed. */
fun speechRecognizerIntent(context: Context): Intent? {
    val speech = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    return speech.takeIf { context.packageManager.resolveActivityCompat(it) != null }
}

fun getShortcutIconRes(shortcut: String, isMaterialYou: Boolean = true): Int {
    return when (shortcut) {
        "Voice Search" -> if (isMaterialYou) R.drawable.ic_mic else R.drawable.ic_mic_original
        "Google Lens" -> R.drawable.ic_camera
        "Assistant", "Gemini" -> R.drawable.ic_lens_action
        "Search", "Live" -> R.drawable.ic_search_lens_expressive
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
        "Calculator" -> R.drawable.ic_calculator
        "Discover" -> R.drawable.ic_discover
        else -> R.drawable.ic_camera
    }
}

/** The widget's Lens shortcut: Google Lens ([GoogleApps.lensIntent]), else Lens on the web, else the search overlay. */
fun getLensSearchIntent(context: Context): Intent {
    GoogleApps.lensIntent(context)?.let { return it }
    val webLens = Intent(Intent.ACTION_VIEW, "https://lens.google.com".toUri()).apply {
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

/** The widget's camera translate shortcut: Google Lens translating, else the search overlay's hand-off to it. */
fun getLensTranslateIntent(context: Context): Intent =
    GoogleApps.lensIntent(context, translate = true) ?: Intent(context, WidgetActivity::class.java).apply {
        action = "com.pixel.intelligentsearch.LAUNCH_LENS_TRANSLATE"
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
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
    for (fallback in assistantFallbacks(context, isTheAssistant)) {
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

/**
 * Where [launchGeminiCornerOverlay] opens the assistant without launchAssist, in order; the first that is installed
 * opens. While Intelligent Search [isTheAssistant], the Google app's voice command only forwards to Android's "Default
 * digital assistant app" page, as Google isn't the assistant, so then the button opens Gemini itself, else the Google
 * app's voice search, else this app's own.
 */
internal fun assistantFallbacks(context: Context, isTheAssistant: Boolean): List<Intent> {
    val gemini = context.packageManager.getLaunchIntentForPackage(GoogleApps.GEMINI_APP)
    if (!isTheAssistant) {
        return listOfNotNull(Intent(Intent.ACTION_VOICE_COMMAND).setPackage(GoogleApps.GOOGLE_APP), gemini, getVoiceSearchIntent(context))
    }
    val googleVoice = Intent(Intent.ACTION_MAIN).setClassName(GoogleApps.GOOGLE_APP, GoogleApps.VOICE_SEARCH_ACTIVITY)
    return listOfNotNull(gemini, googleVoice, ownVoiceSearchIntent(context))
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
        "Translate (text)" -> context.packageManager.getLaunchIntentForPackage(GoogleTranslate.PACKAGE)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse(GoogleTranslate.WEB_HOME)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        "Translate (camera)" -> getLensTranslateIntent(context)
        "Weather" -> getCustomIntentOrDefault(context, "Weather") {
            torTopicSearch(context, "Weather")?.let { return@getCustomIntentOrDefault it }
            val intentWeatherApp = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.weather")
            if (intentWeatherApp != null) {
                intentWeatherApp.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            } else {
                val intentSearchWeather = Intent(Intent.ACTION_VIEW).apply {
                    setClassName(GoogleApps.GOOGLE_APP, GoogleApps.WEATHER_ACTIVITY)
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
            torTopicSearch(context, "Sports")?.let { return@getCustomIntentOrDefault it }
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra("query", "Sports")
                setPackage(GoogleApps.GOOGLE_APP)
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
        "Finance" -> torTopicSearch(context, "Finance")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/finance")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        "Saved" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com/save")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        "News" -> torTopicSearch(context, "News")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://news.google.com")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        "Calculator" -> getCalculatorIntent(context)
        "Discover" -> getDiscoverIntent(context)
        else -> getLensSearchIntent(context)
    }
}

/**
 * With Tor as the search engine, the shortcuts that would open Google's pages or apps (News, Sports, Finance, Weather)
 * search their [topic] through Tor Browser instead, as the bar's web searches do, so nothing goes around Tor; without
 * Tor Browser they open its Play Store page. Null when Tor isn't the engine.
 */
internal fun torTopicSearch(context: Context, topic: String): Intent? {
    val prefs = com.pixel.intelligentsearch.core.data.SettingsManager.storedPrefs(context)
    if (!com.pixel.intelligentsearch.core.security.NetworkPolicy.torSelected(prefs)) return null
    return when (val launch = com.pixel.intelligentsearch.core.security.SafeWebLaunch.torSearch(context, topic)) {
        is com.pixel.intelligentsearch.core.security.SafeWebLaunch.Launch.Open -> launch.intent
        else -> Intent(
            Intent.ACTION_VIEW,
            "market://details?id=${com.pixel.intelligentsearch.core.security.SafeWebLaunch.TOR_BROWSER_PACKAGES.first()}".toUri()
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** The Calculator shortcut tells the search overlay to open with the number pad, ready to calculate as you type. */
const val EXTRA_CALCULATOR = "com.pixel.intelligentsearch.extra.CALCULATOR"

/**
 * The Calculator shortcut: the search overlay, which works sums out as they're typed (its math engine), opened with
 * the number pad (digits, + - * / and brackets) instead of the letters. The extra is honoured only through the
 * private WidgetActivity. With the overlay switched off it opens Google search like the bar does.
 */
fun getCalculatorIntent(context: Context): Intent =
    Intent(context, WidgetActivity::class.java).apply {
        action = ACTION_LAUNCH_CALCULATOR
        putExtra(EXTRA_CALCULATOR, true)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }

private const val ACTION_LAUNCH_CALCULATOR = "com.pixel.intelligentsearch.LAUNCH_CALCULATOR"

/** Whether the Discover shortcut can open Discover: the Google app is installed and can be opened. */
fun isDiscoverAvailable(context: Context): Boolean = context.packageManager.getLaunchIntentForPackage(GoogleApps.GOOGLE_APP) != null

/**
 * The Discover shortcut. Google publishes no intent for the Discover feed itself (launchers reach it through a
 * private overlay service only Google's launcher may bind), so this opens the Google app's main screen, whose home
 * tab is the Discover feed. Without the Google app it opens the app's Play Store page.
 */
fun getDiscoverIntent(context: Context): Intent =
    context.packageManager.getLaunchIntentForPackage(GoogleApps.GOOGLE_APP)?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        ?: Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$GoogleApps.GOOGLE_APP".toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

fun getCustomIntentOrDefault(context: Context, shortcut: String, defaultIntent: () -> Intent): Intent {
    val prefs = com.pixel.intelligentsearch.core.data.SettingsManager.storedPrefs(context)
    val customType = prefs.getString("${shortcut}_custom_type", "default")
    val customValue = prefs.getString("${shortcut}_custom_value", "")

    if (customType == "url" && !customValue.isNullOrEmpty()) {
        return Intent(Intent.ACTION_VIEW, customShortcutUri(customValue)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    } else if (customType == "app" && !customValue.isNullOrEmpty()) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(customValue)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return launchIntent
        }
    }

    return defaultIntent()
}

/**
 * The page a shortcut set to open a website opens. An address typed without a scheme opens over https. A scheme is
 * recognised however it was typed ("HTTPS://..."), and lower-cased, so it isn't taken for part of the address.
 */
internal fun customShortcutUri(address: String): Uri {
    val hasScheme = address.startsWith("http://", ignoreCase = true) || address.startsWith("https://", ignoreCase = true)
    return (if (hasScheme) address else "https://$address").toUri().normalizeScheme()
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
            setPackage(GoogleApps.GOOGLE_APP)
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
