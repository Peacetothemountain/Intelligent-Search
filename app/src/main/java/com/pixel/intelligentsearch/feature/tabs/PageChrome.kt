package com.pixel.intelligentsearch.feature.tabs

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable

/**
 * What the shell hands each tab's page. The shell owns the floating pill, back between tabs, and the page background
 * with its glow (Settings and Wellbeing); Gallery paints its own background and may show the wallpaper.
 */
@Immutable
class PageChrome(
    /** The status bar at the top; the floating pill and the navigation bar at the bottom. Pad scrolling content by it. */
    val contentPadding: PaddingValues,
    /** The app bar's back arrow: does what the system back gesture does (from Gallery or Wellbeing, back to Settings). */
    val onBack: () -> Unit,
    /** True while the page shows a full-screen layer (a preview, the weekly recap): the pill slides away. */
    val setCovered: (Boolean) -> Unit,
    /**
     * Whether the page needs Android's wallpaper behind the window while it is in sight. Gallery needs it by default
     * (its windows look through to the real wallpaper); a mode that draws everything itself can say false, and the
     * window then keeps Android's back-to-home preview. Settings and Wellbeing never need it.
     */
    val showWallpaper: (Boolean) -> Unit = {},
    /** The page is at its very top, however it got there: the pill comes back. */
    val revealPill: () -> Unit = {},
    /** Opens Widget Customization over the tabs (Gallery's Customize). */
    val openWidgetSettings: () -> Unit = {},
)
