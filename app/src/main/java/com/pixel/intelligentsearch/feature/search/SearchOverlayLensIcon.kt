package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.SettingsManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
import com.pixel.intelligentsearch.core.theme.DynamicThemeDetector
import com.pixel.intelligentsearch.core.data.*

/** The four colours the search screen's icons are drawn in, following the widget icon theme and Material You. */
internal class SearchIconPalette(val rim: Color, val top: Color, val handle: Color, val sparkle: Color)

/**
 * The search icons' colours: the icon theme chosen in Widget Customization (Material, Accented, System four-colour or
 * Custom), with the Material theme drawing on the wallpaper's dynamic colours. Shared by the lens and mic icons so they
 * always match.
 */
@Composable
internal fun rememberSearchIconPalette(): SearchIconPalette {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val isLight = scheme.surface.luminance() >= 0.5f

    val prefs = remember(context) { context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE) }
    // Follow the icon theme of whichever widget design is active, as saved by Widget Customization.
    val isMaterialWidget = prefs[SettingsManager.WIDGET_THEME_STYLE]?.startsWith("Material") == true
    val effectiveTheme = (if (isMaterialWidget) {
        prefs.getString("widget_material_material_g_icon", prefs.getString("widget_material_g_icon", null))
    } else {
        prefs.getString("widget_system_material_g_icon", null)
    }) ?: "Material G Icon"
    // Colours are saved per design; a design never saved falls back to the older shared keys.
    val designPrefix = if (isMaterialWidget) "widget_material" else "widget_system"
    fun designInt(element: String, fallback: Int): Int = when {
        prefs.contains("${designPrefix}_custom_$element") -> prefs.getInt("${designPrefix}_custom_$element", fallback)
        else -> prefs.getInt("widget_custom_$element", fallback)
    }
    val isCustomSync = if (prefs.contains("${designPrefix}_custom_sync_colors")) {
        prefs.getBoolean("${designPrefix}_custom_sync_colors", false)
    } else {
        prefs.getBoolean("widget_custom_sync_colors", false)
    }
    val customColor = if (isCustomSync) {
        designInt("g_icon_color", scheme.primary.toArgb())
    } else {
        designInt("action_icon_color", prefs.getInt("widget_custom_shortcuts_color", scheme.primary.toArgb()))
    }

    val resolvedColors = remember(context, isLight) {
        DynamicThemeDetector.resolveWidgetColors(context, isDark = !isLight)
    }

    val pColor = if (resolvedColors.isDynamicActive) resolvedColors.primary else scheme.primary.toArgb()
    val sColor = if (resolvedColors.isDynamicActive) resolvedColors.secondary else scheme.secondary.toArgb()
    val tColor = if (resolvedColors.isDynamicActive) resolvedColors.tertiary else scheme.tertiary.toArgb()

    val fourPalette = remember(pColor, sColor, tColor, isLight) {
        MaterialYouPaletteHelper.getFourColorMaterialGPalette(pColor, sColor, tColor, isLight)
    }

    return when (effectiveTheme) {
        "Custom G Icon" -> Color(customColor).let { SearchIconPalette(it, it, it, it) }
        "Accented G Icon" -> Color(pColor).let { SearchIconPalette(it, it, it, it) }
        "System G Icon" -> SearchIconPalette(Color(0xFF4285F4), Color(0xFFFBBC05), Color(0xFF34A853), Color(0xFFEA4335))
        else -> SearchIconPalette(
            rim = Color(fourPalette.rightArcAndBar), top = Color(fourPalette.topArc),
            handle = Color(fourPalette.bottomArc), sparkle = Color(fourPalette.leftArc),
        )
    }
}

@Composable
fun SearchOverlayLensIcon(
    modifier: Modifier = Modifier
) {
    val palette = rememberSearchIconPalette()
    val path1 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 10.5,4 A 6.5,6.5 0 0 0 4,10.5 A 6.5,6.5 0 0 0 10.5,17 A 6.5,6.5 0 0 0 15,15").toPath() }
    val path2 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 14,5 A 6.5,6.5 0 0 0 10.5,4 A 6.5,6.5 0 0 0 5,7.5").toPath() }
    val path3 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 15.5,15.5 L 20,20").toPath() }
    val path4 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 18,0.5 C 18,4 21,6.5 24,6.5 C 21,6.5 18,9 18,12.5 C 18,9 15,6.5 12,6.5 C 15,6.5 18,4 18,0.5 Z").toPath() }

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val scaleX = size.width / 24f
        val scaleY = size.height / 24f
        scale(scaleX, scaleY, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(path = path1, color = palette.rim, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawPath(path = path2, color = palette.top, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawPath(path = path3, color = palette.handle, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.0f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawPath(path = path4, color = palette.sparkle)
        }
    }
}

/**
 * The search bar's voice key, after Gboard Rambler's sparkle mic, in the same four colours as the lens icon: the
 * capsule like the lens rim, the cradle like its top arc, the stem like its handle, and the same sparkle.
 */
@Composable
fun SearchOverlayMicIcon(modifier: Modifier = Modifier) {
    val palette = rememberSearchIconPalette()
    val capsule = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 9,3 A 3,3 0 0 1 12,6 L 12,11 A 3,3 0 0 1 6,11 L 6,6 A 3,3 0 0 1 9,3 Z").toPath() }
    val cradle = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 4,11 A 5,5 0 0 0 14,11").toPath() }
    val stem = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 9,16 L 9,20.5").toPath() }
    val sparkle = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 18,0.5 C 18,4 21,6.5 24,6.5 C 21,6.5 18,9 18,12.5 C 18,9 15,6.5 12,6.5 C 15,6.5 18,4 18,0.5 Z").toPath() }
    androidx.compose.foundation.Canvas(modifier = modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(path = capsule, color = palette.rim)
            drawPath(path = cradle, color = palette.top, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawPath(path = stem, color = palette.handle, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawPath(path = sparkle, color = palette.sparkle)
        }
    }
}
