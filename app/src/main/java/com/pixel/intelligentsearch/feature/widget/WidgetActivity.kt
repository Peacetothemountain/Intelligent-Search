package com.pixel.intelligentsearch.feature.widget

import android.os.Bundle
import android.view.WindowManager
import com.pixel.intelligentsearch.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Dedicated trampoline-free entry point for home screen widgets and Quick Settings tiles.
 * Bypasses system splash latency on direct search overlay launches via overlay-specific theme.
 */
@AndroidEntryPoint
class WidgetActivity : MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        super.onCreate(savedInstanceState)
    }
}
