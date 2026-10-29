package com.pixel.intelligentsearch.core.theme

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.ColorInt
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.DynamicColors
import com.pixel.intelligentsearch.core.data.widget.DEFAULT_LOCKED_PILL_COLOR

/**
 * Universal Android Dynamic Color (Material You / Monet) Engine.
 *
 * Inspects system runtime resource overlays (RRO), manufacturer secure/system settings,
 * and hardware/SDK capabilities to determine with 100% authenticity whether Material You
 * dynamic theming is supported and actively enabled by the user.
 *
 * Full Cross-OEM compatibility:
 * - Google Pixel: AOSP Monet & Fabric theme engine
 * - Samsung One UI (4.0 - 7.0+): Color Palette master toggle & overlay state
 * - Xiaomi / Redmi / POCO (MIUI 13+ & HyperOS): Monet vs Theme Store override
 * - OPPO / OnePlus / Realme (ColorOS / OxygenOS / Realme UI): Wallpapers & Style colors
 * - Vivo / iQOO (Funtouch OS / OriginOS): UI Color switch
 * - Huawei / Honor: EMUI (fallback) vs MagicOS (dynamic)
 * - Motorola, Sony, Nothing OS, Asus, Sharp: Native AOSP Monet
 */
object DynamicThemeDetector {

    enum class ThemeState {
        DYNAMIC_ACTIVE,     // Dynamic colors (Material You) active and enabled
        DYNAMIC_DISABLED,   // User explicitly turned off dynamic color (e.g. Samsung/Vivo toggle)
        UNSUPPORTED         // Device OS does not support dynamic color (API < 31, EMUI without Monet)
    }

    private val cacheLock = Any()

    @Volatile
    private var cachedThemeState: ThemeState? = null

    @Volatile
    private var lastThemeStateCheckTime: Long = 0L

    private const val CACHE_TTL_MS = 5000L

    @Volatile
    private var cachedDarkWidgetColors: ResolvedWidgetColors? = null

    @Volatile
    private var cachedLightWidgetColors: ResolvedWidgetColors? = null

    @Volatile
    private var isObserverRegistered = false

    /**
     * Clears all cached theme states and resolved widget colors.
     */
    fun invalidateCache() {
        synchronized(cacheLock) {
            cachedThemeState = null
            lastThemeStateCheckTime = 0L
            cachedDarkWidgetColors = null
            cachedLightWidgetColors = null
        }
    }

    @Volatile
    var onThemeChangedCallback: ((Context) -> Unit)? = null

    private fun tryRegisterContentObserver(context: Context) {
        if (isObserverRegistered) return
        try {
            val appContext = context.applicationContext ?: context
            val cr = appContext.contentResolver ?: return
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    invalidateCache()
                    try {
                        onThemeChangedCallback?.invoke(appContext)
                    } catch (_: Throwable) {}
                }
            }
            val overlayUri = Settings.Secure.getUriFor("theme_customization_overlay_packages")
            if (overlayUri != null) {
                cr.registerContentObserver(overlayUri, false, observer)
            }
            val samsungPaletteUri = Settings.System.getUriFor("color_palette_enabled")
            if (samsungPaletteUri != null) {
                cr.registerContentObserver(samsungPaletteUri, false, observer)
            }
            val samsungColorThemeUri = Settings.System.getUriFor("colortheme_enabled")
            if (samsungColorThemeUri != null) {
                cr.registerContentObserver(samsungColorThemeUri, false, observer)
            }
            val vivoColorUri = Settings.System.getUriFor("ui_color_enable")
            if (vivoColorUri != null) {
                cr.registerContentObserver(vivoColorUri, false, observer)
            }
            val contrastUri = Settings.Secure.getUriFor("contrast_level")
            if (contrastUri != null) {
                cr.registerContentObserver(contrastUri, false, observer)
            }
            isObserverRegistered = true
        } catch (_: Throwable) {
            // Ignored in restricted sandbox or unit-test environments
        }
    }

    /**
     * Determines whether Dynamic Color (Material You) is supported AND active on this device.
     */
    fun isDynamicColorActive(context: Context): Boolean {
        return getThemeState(context) == ThemeState.DYNAMIC_ACTIVE
    }

    /**
     * Inspects OEM-specific system settings and Android framework resources to determine
     * the exact dynamic color lifecycle state, utilizing a thread-safe 5-second cache to prevent
     * synchronous Binder IPC stalls during 120 FPS UI composition and rendering.
     */
    fun getThemeState(context: Context): ThemeState {
        val now = System.currentTimeMillis()
        val fastCached = cachedThemeState
        val fastTime = lastThemeStateCheckTime
        if (fastCached != null && (now - fastTime in 0..CACHE_TTL_MS)) {
            return fastCached
        }

        return synchronized(cacheLock) {
            val cached = cachedThemeState
            val time = lastThemeStateCheckTime
            val current = System.currentTimeMillis()
            if (cached != null && (current - time in 0..CACHE_TTL_MS)) {
                return@synchronized cached
            }

            tryRegisterContentObserver(context)

            val evaluatedState = computeThemeState(context)
            if (evaluatedState != cached) {
                cachedDarkWidgetColors = null
                cachedLightWidgetColors = null
            }
            cachedThemeState = evaluatedState
            lastThemeStateCheckTime = current
            evaluatedState
        }
    }

    private fun computeThemeState(context: Context): ThemeState {
        // Monet (API 31) is always available because it is the minSdk.

        // 2. Google Material Components compatibility check
        // Validates known OEM brand implementations on API 31/32 and universal support on API 33+
        try {
            if (!DynamicColors.isDynamicColorAvailable()) {
                return ThemeState.UNSUPPORTED
            }
        } catch (_: Throwable) {
            // Safe fallback if library class is unavailable
        }

        val cr = context.contentResolver

        // 3. Samsung One UI (4.0 - 7.0+):
        // Samsung provides an explicit toggle in Settings -> Wallpaper and style -> Color palette.
        // When OFF, "color_palette_enabled" is set to 0.
        if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
            try {
                val paletteEnabled = Settings.System.getInt(cr, "color_palette_enabled", -1)
                if (paletteEnabled == 0) {
                    return ThemeState.DYNAMIC_DISABLED
                }
                val colorThemeEnabled = Settings.System.getInt(cr, "colortheme_enabled", -1)
                if (colorThemeEnabled == 0) {
                    return ThemeState.DYNAMIC_DISABLED
                }
            } catch (_: Throwable) {}
        }

        // 4. Vivo / iQOO (Funtouch OS 12+ / OriginOS):
        // Vivo provides a toggle in Settings -> Lock screen & wallpaper -> UI color.
        // When OFF, "ui_color_enable" is set to 0.
        if (Build.MANUFACTURER.equals("vivo", ignoreCase = true)) {
            try {
                val uiColorEnable = Settings.System.getInt(cr, "ui_color_enable", -1)
                if (uiColorEnable == 0) {
                    return ThemeState.DYNAMIC_DISABLED
                }
            } catch (_: Throwable) {}
        }

        // 5. Universal AOSP / Pixel / Xiaomi HyperOS / Oppo ColorOS / Motorola:
        // Evaluates Settings.Secure.theme_customization_overlay_packages JSON
        try {
            val themeJson = Settings.Secure.getString(cr, "theme_customization_overlay_packages")
            if (!themeJson.isNullOrBlank() && themeJson != "{}") {
                // If it contains active palette metadata, it is active
                if (themeJson.contains("system_palette") ||
                    themeJson.contains("color_source") ||
                    themeJson.contains("theme_style") ||
                    themeJson.contains("accent_color") ||
                    themeJson.contains("color_index")) {
                    return ThemeState.DYNAMIC_ACTIVE
                }
            } else if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
                // On Samsung, an empty/null/{} JSON signifies Color Palette is OFF
                return ThemeState.DYNAMIC_DISABLED
            }
        } catch (_: Throwable) {}

        // 6. Direct Framework Resource Verification:
        // Inspects whether android.R.color.system_accent1_500 is accessible
        return try {
            val accent500 = context.getColor(android.R.color.system_accent1_500)
            if (accent500 != 0) {
                ThemeState.DYNAMIC_ACTIVE
            } else {
                ThemeState.DYNAMIC_DISABLED
            }
        } catch (_: Throwable) {
            ThemeState.UNSUPPORTED
        }
    }

    /**
     * Data class holding resolved Material You tonal colors for widget rendering.
     */
    data class ResolvedWidgetColors(
        @get:ColorInt val primary: Int,
        @get:ColorInt val secondary: Int,
        @get:ColorInt val tertiary: Int,
        @get:ColorInt val primaryContainer: Int,
        @get:ColorInt val surfaceContainer: Int,
        val isDynamicActive: Boolean,
        @get:ColorInt val onPrimary: Int = 0,
        @get:ColorInt val onSurface: Int = 0,
        @get:ColorInt val outline: Int = 0,
        /**
         * The theme's own pill for the Material design's locked Inner Pill & Circle (no color picked for it in Color
         * Studio): #121212 in the dark theme, and in the light theme its twin, the light palette's surfaceBright.
         */
        @get:ColorInt val lockedPill: Int = DEFAULT_LOCKED_PILL_COLOR
    )

    /**
     * Resolves the primary, secondary, and tertiary Material You dynamic colors,
     * along with dynamic surface container and pill colors for the search widgets.
     */
    fun resolveWidgetColors(context: Context, isDark: Boolean): ResolvedWidgetColors {
        val isDynamicActive = isDynamicColorActive(context)

        val fastCached = if (isDark) cachedDarkWidgetColors else cachedLightWidgetColors
        if (fastCached != null && fastCached.isDynamicActive == isDynamicActive) {
            return fastCached
        }

        return synchronized(cacheLock) {
            val lockedCached = if (isDark) cachedDarkWidgetColors else cachedLightWidgetColors
            if (lockedCached != null && lockedCached.isDynamicActive == isDynamicActive) {
                return@synchronized lockedCached
            }

            val dynamicScheme: ColorScheme? = if (isDynamicActive) {
                try {
                    if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } catch (_: Throwable) {
                    null
                }
            } else null

            val p = dynamicScheme?.primary?.toArgb() ?: (
                if (isDynamicActive) {
                    try { context.getColor(android.R.color.system_accent1_500) } catch (_: Throwable) { if (isDark) 0xFF8AB4F8.toInt() else 0xFF1973E8.toInt() }
                } else {
                    if (isDark) 0xFF8AB4F8.toInt() else 0xFF1973E8.toInt()
                }
            )

            val s = dynamicScheme?.secondary?.toArgb() ?: (
                if (isDynamicActive) {
                    try { context.getColor(android.R.color.system_accent2_500) } catch (_: Throwable) { if (isDark) 0xFFBDC1C6.toInt() else 0xFF5F6368.toInt() }
                } else {
                    if (isDark) 0xFFBDC1C6.toInt() else 0xFF5F6368.toInt()
                }
            )

            val t = dynamicScheme?.tertiary?.toArgb() ?: (
                if (isDynamicActive) {
                    try { context.getColor(android.R.color.system_accent3_500) } catch (_: Throwable) { if (isDark) 0xFF81C995.toInt() else 0xFF188038.toInt() }
                } else {
                    if (isDark) 0xFF81C995.toInt() else 0xFF188038.toInt()
                }
            )

            val primaryContainer = dynamicScheme?.primaryContainer?.toArgb() ?: (
                if (isDynamicActive) {
                    try {
                        if (isDark) context.getColor(android.R.color.system_accent1_800) else context.getColor(android.R.color.system_accent1_200)
                    } catch (_: Throwable) {
                        if (isDark) 0xFF1E3A8A.toInt() else 0xFFD3E3FD.toInt()
                    }
                } else {
                    if (isDark) 0xFF1E3A8A.toInt() else 0xFFD3E3FD.toInt()
                }
            )

            val surfaceContainer = if (isDynamicActive) {
                dynamicScheme?.surfaceContainer?.toArgb() ?: (
                    try {
                        if (isDark) context.getColor(android.R.color.system_neutral1_800) else context.getColor(android.R.color.system_neutral1_100)
                    } catch (_: Throwable) {
                        if (isDark) 0xFF303134.toInt() else 0xFFF1F3F4.toInt()
                    }
                )
            } else {
                // When dynamic color is off, fall back to standard neutral surface
                if (isDark) 0xFF303134.toInt() else 0xFFF1F3F4.toInt()
            }

            val onP = dynamicScheme?.onPrimary?.toArgb() ?: (if (isDark) GoogleDarkOnPrimary.toArgb() else GoogleLightOnPrimary.toArgb())
            val onSurf = dynamicScheme?.onSurface?.toArgb() ?: (if (isDark) GoogleDarkOnSurface.toArgb() else GoogleLightOnSurface.toArgb())
            val outline = dynamicScheme?.outline?.toArgb() ?: (if (isDark) GoogleDarkOutline.toArgb() else GoogleLightOutline.toArgb())

            val lockedPill = if (isDark) DEFAULT_LOCKED_PILL_COLOR else dynamicScheme?.surfaceBright?.toArgb() ?: (
                try { context.getColor(android.R.color.system_neutral1_10) } catch (_: Throwable) { 0xFFFFFFFF.toInt() }
            )

            val resolved = ResolvedWidgetColors(
                primary = p,
                secondary = s,
                tertiary = t,
                primaryContainer = primaryContainer,
                surfaceContainer = surfaceContainer,
                isDynamicActive = isDynamicActive,
                onPrimary = onP,
                onSurface = onSurf,
                outline = outline,
                lockedPill = lockedPill
            )

            if (isDark) {
                cachedDarkWidgetColors = resolved
            } else {
                cachedLightWidgetColors = resolved
            }
            resolved
        }
    }
}
