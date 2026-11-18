package com.pixel.intelligentsearch.feature.tabs

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.pixel.intelligentsearch.feature.tabs.ui.findActivity

/**
 * Shows Android's wallpaper behind the window while [enabled], and only then.
 *
 * A window over the wallpaper loses Android's back-to-home preview (the window shrinking towards the home screen as
 * the back gesture is held): Android plays only the arrow for it. So the window shows the wallpaper only while a page
 * that looks through to it is in sight (Gallery), and Settings, where back leaves the app, never does.
 *
 * The window is translucent from its start (SettingsActivity.onCreate), so turning this on later is safe: the wallpaper shows at
 * once, behind every frame, and the window's buffer is cleared between frames (on the Pixel 11 Pro XL, Android 17,
 * switched on and off five times in a row it showed the wallpaper each time).
 */
@Composable
internal fun WallpaperBehindWindow(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled, context) {
        val window = context.findActivity()?.window
        if (!enabled || window == null) return@DisposableEffect onDispose { }
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER) }
    }
}
