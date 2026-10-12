package com.pixel.intelligentsearch.feature.widget

import android.app.ActivityOptions
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Activity options for launches that start from a widget, Quick Settings tile or other surface
 * where Intelligent Search isn't in the foreground. Every launch is transition-free.
 *
 * Background Activity Launch opt-in: API 34 and 35 take MODE_BACKGROUND_ACTIVITY_START_ALLOWED.
 * API 36 deprecates it, and apps targeting Android 17 have it treated as ALLOW_IF_VISIBLE, so
 * that mode is requested explicitly from API 36 onwards.
 */
internal object BackgroundLaunchOptions {

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Suppress("DEPRECATION")
    private fun allowMode(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
    } else {
        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
    }

    /** Options passed when creating a PendingIntent. */
    fun forCreator(context: Context): ActivityOptions =
        ActivityOptions.makeCustomAnimation(context, 0, 0).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                setPendingIntentCreatorBackgroundActivityStartMode(allowMode())
            }
        }

    /** Options passed when this app sends a PendingIntent. */
    @Suppress("DEPRECATION")
    fun forSender(context: Context): ActivityOptions =
        ActivityOptions.makeCustomAnimation(context, 0, 0).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                setPendingIntentBackgroundActivityStartMode(allowMode())
            }
        }
}
