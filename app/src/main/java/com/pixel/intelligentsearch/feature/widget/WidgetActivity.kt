package com.pixel.intelligentsearch.feature.widget
import com.pixel.intelligentsearch.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Dedicated trampoline-free entry point for home screen widgets and Quick Settings tiles.
 * Bypasses system splash latency on direct search overlay launches via overlay-specific theme.
 */
@AndroidEntryPoint
class WidgetActivity : MainActivity()
