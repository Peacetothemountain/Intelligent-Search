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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import com.pixel.intelligentsearch.core.theme.SearchIconColors
import com.pixel.intelligentsearch.core.data.*

/** The four colours the search screen's icons are drawn in, following the widget icon theme and Material You. */
internal class SearchIconPalette(val rim: Color, val top: Color, val handle: Color, val sparkle: Color, val mic: MicPalette)

/** The mic's colours: Material You's three accent tiers ([SearchIconColors]), which the widget's mic is drawn in too. */
internal class MicPalette(val capsule: Color, val stem: Color, val leftCradle: Color, val rightCradle: Color)

/**
 * The search bar's icons as path data in a 24-unit square: the lens with its sparkle, and Gboard Rambler's sparkle
 * mic. The overlay, the home screen widget and the widget editor's preview all draw them from here.
 */
internal object SearchIconPaths {
    const val LENS_RIM = "M 10.5,4 A 6.5,6.5 0 0 0 4,10.5 A 6.5,6.5 0 0 0 10.5,17 A 6.5,6.5 0 0 0 15,15"
    const val LENS_TOP = "M 14,5 A 6.5,6.5 0 0 0 10.5,4 A 6.5,6.5 0 0 0 5,7.5"
    const val LENS_HANDLE = "M 15.5,15.5 L 20,20"
    const val LENS_SPARKLE = "M 18,0.5 C 18,4 21,6.5 24,6.5 C 21,6.5 18,9 18,12.5 C 18,9 15,6.5 12,6.5 C 15,6.5 18,4 18,0.5 Z"
    const val LENS_RIM_WIDTH = 2.5f
    const val LENS_HANDLE_WIDTH = 3.0f

    const val MIC_CAPSULE = "M12,15c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v7c0,1.66 1.34,3 3,3z"
    const val MIC_STEM = "M11,18.92h2V22h-2z"
    const val MIC_LEFT_CRADLE = "M7,12H5c0,1.93 0.78,3.68 2.05,4.95l1.41,-1.41C7.56,14.63 7,13.38 7,12z"
    const val MIC_RIGHT_CRADLE = "M12,17c-1.38,0 -2.63,-0.56 -3.54,-1.47l-1.41,1.41C8.32,18.21 10.07,19 12.01,19c3.87,0 6.98,-3.14 6.98,-7h-2c0,2.76 -2.23,5 -4.99,5z"
    const val MIC_SPARKLE = "M19.5,1 C19.5,3.6 21.5,5.5 24,5.5 C21.5,5.5 19.5,7.4 19.5,10 C19.5,7.4 17.5,5.5 15,5.5 C17.5,5.5 19.5,3.6 19.5,1 Z"
    /** How far the sparkle mic's mic sits from the centre (a little left and down), leaving its sparkle room. */
    const val MIC_SHIFT_X = -2.5f
    const val MIC_SHIFT_Y = 0.5f
}

/**
 * The search icons' colours: the icon theme chosen in Widget Customization (Material, Accented, System four-colour or
 * Custom), with the Material theme drawing on the wallpaper's dynamic colours ([SearchIconColors]). Shared by the lens
 * and mic icons so they always match.
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

    val accents = remember(context, isLight) { SearchIconColors.themeAccents(context, isLight) }
    val colors = remember(accents, isLight) { SearchIconColors.of(accents, isLight) }

    val mic = MicPalette(
        capsule = Color(colors.micCapsule), stem = Color(colors.micStem),
        leftCradle = Color(colors.micLeftCradle), rightCradle = Color(colors.micRightCradle),
    )
    return when (effectiveTheme) {
        "Custom G Icon" -> Color(customColor).let { SearchIconPalette(it, it, it, it, mic) }
        "Accented G Icon" -> Color(accents.primary).let { SearchIconPalette(it, it, it, it, mic) }
        "System G Icon" -> SearchIconPalette(Color(0xFF4285F4), Color(0xFFFBBC05), Color(0xFF34A853), Color(0xFFEA4335), mic)
        else -> SearchIconPalette(
            rim = Color(colors.lensRim), top = Color(colors.lensTop),
            handle = Color(colors.lensHandle), sparkle = Color(colors.lensSparkle), mic = mic,
        )
    }
}

@Composable
fun SearchOverlayLensIcon(
    modifier: Modifier = Modifier
) {
    val palette = rememberSearchIconPalette()
    SearchLensDrawing(palette.rim, palette.top, palette.handle, palette.sparkle, modifier)
}

/** The search bar's lens with its sparkle: the rim, the top of the rim, the handle and the sparkle in the colours given. */
@Composable
internal fun SearchLensDrawing(rim: Color, top: Color, handle: Color, sparkle: Color, modifier: Modifier = Modifier) {
    val parser = remember { androidx.compose.ui.graphics.vector.PathParser() }
    val rimPath = remember { parser.parsePathString(SearchIconPaths.LENS_RIM).toPath() }
    val topPath = remember { parser.parsePathString(SearchIconPaths.LENS_TOP).toPath() }
    val handlePath = remember { parser.parsePathString(SearchIconPaths.LENS_HANDLE).toPath() }
    val sparklePath = remember { parser.parsePathString(SearchIconPaths.LENS_SPARKLE).toPath() }

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val scaleX = size.width / 24f
        val scaleY = size.height / 24f
        val round = androidx.compose.ui.graphics.StrokeCap.Round
        scale(scaleX, scaleY, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(path = rimPath, color = rim, style = androidx.compose.ui.graphics.drawscope.Stroke(width = SearchIconPaths.LENS_RIM_WIDTH, cap = round))
            drawPath(path = topPath, color = top, style = androidx.compose.ui.graphics.drawscope.Stroke(width = SearchIconPaths.LENS_RIM_WIDTH, cap = round))
            drawPath(path = handlePath, color = handle, style = androidx.compose.ui.graphics.drawscope.Stroke(width = SearchIconPaths.LENS_HANDLE_WIDTH, cap = round))
            drawPath(path = sparklePath, color = sparkle)
        }
    }
}

/**
 * The search bar's voice key: Gboard Rambler's sparkle mic, coloured exactly like the home screen widget's mic
 * (capsule in the lighter third tone, stem and left cradle in the second, right cradle in the darker primary), with
 * the sparkle in the third tone. The mic shapes are the widget's own.
 */
@Composable
fun SearchOverlayMicIcon(modifier: Modifier = Modifier) {
    val palette = rememberSearchIconPalette().mic
    SparkleMicDrawing(palette.capsule, palette.stem, palette.leftCradle, palette.rightCradle, sparkle = palette.capsule, modifier = modifier)
}

/** Gboard Rambler's sparkle mic in the colours given: the mic a little left of centre, the sparkle at its top right. */
@Composable
internal fun SparkleMicDrawing(
    capsule: Color,
    stem: Color,
    leftCradle: Color,
    rightCradle: Color,
    sparkle: Color,
    modifier: Modifier = Modifier
) {
    val parser = remember { androidx.compose.ui.graphics.vector.PathParser() }
    val capsulePath = remember { parser.parsePathString(SearchIconPaths.MIC_CAPSULE).toPath() }
    val stemPath = remember { parser.parsePathString(SearchIconPaths.MIC_STEM).toPath() }
    val leftCradlePath = remember { parser.parsePathString(SearchIconPaths.MIC_LEFT_CRADLE).toPath() }
    val rightCradlePath = remember { parser.parsePathString(SearchIconPaths.MIC_RIGHT_CRADLE).toPath() }
    val sparklePath = remember { parser.parsePathString(SearchIconPaths.MIC_SPARKLE).toPath() }
    androidx.compose.foundation.Canvas(modifier = modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            // The mic sits a little left of centre to leave the sparkle room, as on Rambler's key.
            translate(left = SearchIconPaths.MIC_SHIFT_X, top = SearchIconPaths.MIC_SHIFT_Y) {
                drawPath(path = capsulePath, color = capsule)
                drawPath(path = stemPath, color = stem)
                drawPath(path = leftCradlePath, color = leftCradle)
                drawPath(path = rightCradlePath, color = rightCradle)
            }
            drawPath(path = sparklePath, color = sparkle)
        }
    }
}
