package com.pixel.intelligentsearch.feature.widget
import com.pixel.intelligentsearch.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * An empty subclass of MainActivity used exclusively as the entry point for widgets and tiles.
 * This allows us to declare a separate theme for widget launches (which suppresses the splash screen)
 * while letting the standard MainActivity (used by the launcher) display the default splash screen.
 */
@AndroidEntryPoint
class WidgetActivity : MainActivity()
