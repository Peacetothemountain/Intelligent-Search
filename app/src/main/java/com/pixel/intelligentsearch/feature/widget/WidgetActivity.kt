package com.pixel.intelligentsearch.feature.widget

import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.pixel.intelligentsearch.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Dedicated trampoline-free entry point for home screen widgets and Quick Settings tiles.
 * Bypasses system splash latency on direct search overlay launches via overlay-specific theme
 * and ensures instant zero-duration activity transitions for Android 15/16/17 (SDK 35+).
 */
@AndroidEntryPoint
class WidgetActivity : MainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        super.onCreate(savedInstanceState)
    }

    override fun onNewIntent(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        super.onNewIntent(intent)
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
