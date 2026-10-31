package com.pixel.intelligentsearch.core.system

import android.content.Context
import android.content.Intent
import com.pixel.intelligentsearch.core.util.resolveActivityCompat

/**
 * Google's apps the search hands off to, named once: the Google app (search, voice, Discover, and its Lens and weather
 * pages), Google Lens, Pixel Weather, Google Translate and Gemini, with the one way each Lens and weather hand-off is
 * chosen.
 * A hand-off opens the other app; nothing is sent by this app.
 */
object GoogleApps {
    /** The Google app (search, Discover, voice search, and Lens and weather inside it). */
    const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
    /** The standalone Google Lens app. */
    const val LENS_APP = "com.google.ar.lens"
    /** Pixel Weather. */
    const val WEATHER_APP = "com.google.android.apps.weather"
    /** Google Translate. */
    const val TRANSLATE_APP = "com.google.android.apps.translate"
    /** The Gemini app. */
    const val GEMINI_APP = "com.google.android.apps.bard"

    /** The Google app's Lens entry, for a Lens without the standalone app. */
    const val LENS_DEEPLINK_ACTIVITY = "com.google.android.apps.search.lens.deeplink.LensDeeplink"
    /** The Google app's "Search image with Lens" share target. */
    const val LENS_SHARE_ACTIVITY = "com.google.android.apps.search.lens.LensShareEntryPointActivity"
    /** The Google app's weather page. */
    const val WEATHER_ACTIVITY = "com.google.android.apps.search.weather.WeatherExportedActivity"
    /** The Google app's voice search. */
    const val VOICE_SEARCH_ACTIVITY = "com.google.android.googlequicksearchbox.VoiceSearchActivity"

    /** The Lens camera: the Lens app if installed, else the Google app's Lens; [translate] opens it translating. */
    fun lensIntent(context: Context, translate: Boolean = false): Intent? {
        val pm = context.packageManager
        val app = runCatching { pm.getLaunchIntentForPackage(LENS_APP) }.getOrNull()
            ?.takeIf { pm.resolveActivityCompat(it) != null }
        val lens = app ?: Intent().setClassName(GOOGLE_APP, LENS_DEEPLINK_ACTIVITY)
            .takeIf { runCatching { pm.resolveActivityCompat(it) }.getOrNull() != null }
            ?: return null
        if (translate) lens.putExtra("lens_mode", "translate")
        return lens.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Whether Google Lens can be opened at all: the Lens app, or the Google app's Lens. */
    fun isLensAvailable(context: Context): Boolean = lensIntent(context) != null

    /** The weather app to open from a forecast: Pixel Weather, else the Google app's weather page; null for neither. */
    fun weatherIntent(context: Context): Intent? {
        val pm = context.packageManager
        runCatching { pm.getLaunchIntentForPackage(WEATHER_APP) }.getOrNull()?.let { return it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        val google = Intent(Intent.ACTION_VIEW).setClassName(GOOGLE_APP, WEATHER_ACTIVITY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return google.takeIf { pm.resolveActivityCompat(it) != null }
    }
}
