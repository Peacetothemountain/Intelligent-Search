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
        super.onCreate(savedInstanceState)
        applyZeroTransitions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyZeroTransitions()
    }

    override fun finish() {
        super.finish()
        applyZeroTransitions()
    }

    private fun applyZeroTransitions() {
        window.setWindowAnimations(0)
        window.decorView.elevation = 0f
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
