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

@Composable
fun SearchOverlayLensIcon(
    modifier: Modifier = Modifier
) {
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

    val path1 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 10.5,4 A 6.5,6.5 0 0 0 4,10.5 A 6.5,6.5 0 0 0 10.5,17 A 6.5,6.5 0 0 0 15,15").toPath() }
    val path2 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 14,5 A 6.5,6.5 0 0 0 10.5,4 A 6.5,6.5 0 0 0 5,7.5").toPath() }
    val path3 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 15.5,15.5 L 20,20").toPath() }
    val path4 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 18,0.5 C 18,4 21,6.5 24,6.5 C 21,6.5 18,9 18,12.5 C 18,9 15,6.5 12,6.5 C 15,6.5 18,4 18,0.5 Z").toPath() }

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val scaleX = size.width / 24f
        val scaleY = size.height / 24f
        scale(scaleX, scaleY, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            when (effectiveTheme) {
                "Custom G Icon" -> {
                    val color = Color(customColor)
                    drawPath(path = path1, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    drawPath(path = path2, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    drawPath(path = path3, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.0f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    drawPath(path = path4, color = color)
                }
                "Accented G Icon" -> {
                    val color = Color(pColor)
                    drawPath(path = path1, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    drawPath(path = path2, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    drawPath(path = path3, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.0f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    drawPath(path = path4, color = color)
                }
                "System G Icon" -> {
                    // Google 4-color Search Lens: Blue rim, Yellow sparkle, Green handle, Red dot
                    drawPath(path = path1, color = Color(0xFF4285F4), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    drawPath(path = path2, color = Color(0xFFFBBC05), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    drawPath(path = path3, color = Color(0xFF34A853), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.0f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    drawPath(path = path4, color = Color(0xFFEA4335))
                }
                else -> { // "Material G Icon" or default dynamic
                    val cRight = Color(fourPalette.rightArcAndBar)
                    val cBottom = Color(fourPalette.bottomArc)
                    val cLeft = Color(fourPalette.leftArc)
                    val cTop = Color(fourPalette.topArc)

                    // Lens rim: primary right color
                    drawPath(path = path1, color = cRight, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    // Top arc: top accent
                    drawPath(path = path2, color = cTop, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    // Handle: secondary bottom color
                    drawPath(path = path3, color = cBottom, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.0f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    // Sparkle: tertiary left color
                    drawPath(path = path4, color = cLeft)
                }
            }
        }
    }
}
