package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.get
import android.content.Context
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.theme.SearchIconColors
import com.pixel.intelligentsearch.core.occasion.Occasion
import com.pixel.intelligentsearch.feature.search.SearchIconPaths
import com.pixel.intelligentsearch.feature.search.SearchLensDrawing

internal val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

/**
 * The widget opens custom shortcut URLs as https unless they start with http(s)://, so anything
 * else with a scheme (javascript:, intent:, file:) or without a host is rejected up front.
 */
internal fun isValidShortcutUrl(raw: String): Boolean {
    if (raw.isEmpty() || raw.any { it.isWhitespace() }) return false
    val withScheme = when {
        raw.startsWith("https://", ignoreCase = true) || raw.startsWith("http://", ignoreCase = true) -> raw
        raw.contains(':') -> return false
        else -> "https://$raw"
    }
    val host = android.net.Uri.parse(withScheme).host ?: return false
    return host.contains('.') && !host.startsWith('.') && !host.endsWith('.')
}

private val searchActionPath1 by lazy { androidx.compose.ui.graphics.vector.PathParser().parsePathString(SearchIconPaths.LENS_RIM).toPath() }
private val searchActionPath2 by lazy { androidx.compose.ui.graphics.vector.PathParser().parsePathString(SearchIconPaths.LENS_TOP).toPath() }
private val searchActionPath3 by lazy { androidx.compose.ui.graphics.vector.PathParser().parsePathString(SearchIconPaths.LENS_HANDLE).toPath() }
private val searchActionPath4 by lazy { androidx.compose.ui.graphics.vector.PathParser().parsePathString(SearchIconPaths.LENS_SPARKLE).toPath() }

private val micCapsulePath by lazy { androidx.compose.ui.graphics.vector.PathParser().parsePathString(SearchIconPaths.MIC_CAPSULE).toPath() }
private val micStemPath by lazy { androidx.compose.ui.graphics.vector.PathParser().parsePathString(SearchIconPaths.MIC_STEM).toPath() }
private val micLeftCradlePath by lazy { androidx.compose.ui.graphics.vector.PathParser().parsePathString(SearchIconPaths.MIC_LEFT_CRADLE).toPath() }
private val micRightCradlePath by lazy { androidx.compose.ui.graphics.vector.PathParser().parsePathString(SearchIconPaths.MIC_RIGHT_CRADLE).toPath() }

private val assistantActionPath1 by lazy { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M11.5,9.5 L9.0,4.0 L6.5,9.5 L1.0,12.0 l5.5,2.5 L9.0,20.0 l2.5,-5.5 L17.0,12.0 l-5.5,-2.5 Z").toPath() }
private val assistantActionPath2 by lazy { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M19.0,9.0 l1.25,-2.75 L23.0,5.0 l-2.75,-1.25 L19.0,1.0 l-1.25,2.75 L15.0,5.0 l2.75,1.25 L19.0,9.0 Z").toPath() }
private val assistantActionPath3 by lazy { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M19.0,15.0 l-1.25,2.75 L15.0,19.0 l2.75,1.25 L19.0,23.0 l1.25,-2.75 L23.0,19.0 l-2.75,-1.25 L19.0,15.0 Z").toPath() }

private val nowPlayingActionPath1 by lazy { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 19,11.5 L 19,12.5").toPath() }
private val nowPlayingActionPath2 by lazy { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 14.5,9.5 L 14.5,15.5").toPath() }
private val nowPlayingActionPath3 by lazy { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 10,7 L 10,16").toPath() }
private val nowPlayingActionPath4 by lazy { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 11.6,16.5 C 11.6,19.26 9.36,21.5 6.6,21.5 C 3.84,21.5 1.6,19.26 1.6,16.5 C 1.6,13.74 3.84,11.5 6.6,11.5 C 8.6,11.5 10.3,12.7 11.1,14.4 Z").toPath() }

/** The search overlay's icon colours ([SearchIconColors]) from [primary], [secondary] and [tertiary] on a light or dark pill. */
@Composable
private fun rememberSearchIconColors(primary: Color, secondary: Color, tertiary: Color, isPillLight: Boolean): SearchIconColors =
    remember(primary, secondary, tertiary, isPillLight) {
        SearchIconColors.of(SearchIconColors.Accents(primary.toArgb(), secondary.toArgb(), tertiary.toArgb()), isPillLight)
    }

/** The search overlay's lens in its colours. */
@Composable
private fun OverlayLens(colors: SearchIconColors, modifier: Modifier) = SearchLensDrawing(
    rim = Color(colors.lensRim), top = Color(colors.lensTop), handle = Color(colors.lensHandle),
    sparkle = Color(colors.lensSparkle), modifier = modifier
)

/**
 * The widget's mic, as the widget draws it: the search overlay's mic in its colours, centred and without the
 * overlay's sparkle, which only the overlay's mic has.
 */
@Composable
private fun WidgetMic(colors: SearchIconColors, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            drawPath(path = micCapsulePath, color = Color(colors.micCapsule))
            drawPath(path = micStemPath, color = Color(colors.micStem))
            drawPath(path = micLeftCradlePath, color = Color(colors.micLeftCradle))
            drawPath(path = micRightCradlePath, color = Color(colors.micRightCradle))
        }
    }
}

@Composable
fun ComposeActionIcon(
    iconType: String,
    modifier: Modifier = Modifier,
    primaryColor: androidx.compose.ui.graphics.Color,
    secondaryColor: androidx.compose.ui.graphics.Color,
    tertiaryColor: androidx.compose.ui.graphics.Color,
    quaternaryColor: androidx.compose.ui.graphics.Color = primaryColor,
    isMaterialTheme: Boolean = false,
    isPillLight: Boolean = false
) {
    // The Material G style draws the search overlay's icons in the overlay's colours, from these accents.
    val colors = rememberSearchIconColors(primaryColor, secondaryColor, tertiaryColor, isPillLight)
    val pDarker = remember(colors) { androidx.compose.ui.graphics.Color(colors.tiers.primaryDarker) }
    val sLighter = remember(colors) { androidx.compose.ui.graphics.Color(colors.tiers.secondLighter) }
    val tLighter = remember(colors) { androidx.compose.ui.graphics.Color(colors.tiers.thirdLighter) }

    if (isMaterialTheme && iconType == "Search") {
        OverlayLens(colors, modifier)
        return
    }
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val scaleX = size.width / 24f
        val scaleY = size.height / 24f
        scale(scaleX, scaleY, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            when (iconType) {
                "Search" -> {
                    drawPath(
                        path = searchActionPath1,
                        color = primaryColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = SearchIconPaths.LENS_RIM_WIDTH, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    drawPath(
                        path = searchActionPath2,
                        color = tertiaryColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = SearchIconPaths.LENS_RIM_WIDTH, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    drawPath(
                        path = searchActionPath3,
                        color = secondaryColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = SearchIconPaths.LENS_HANDLE_WIDTH, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    drawPath(
                        path = searchActionPath4,
                        color = primaryColor
                    )
                }
                "Assistant", "Gemini", "Voice" -> {
                    val starColor = if (isMaterialTheme) tLighter else primaryColor
                    val sparkle1Color = if (isMaterialTheme) pDarker else primaryColor
                    val sparkle2Color = if (isMaterialTheme) sLighter else secondaryColor
                    drawPath(
                        path = assistantActionPath1,
                        color = starColor
                    )
                    drawPath(
                        path = assistantActionPath2,
                        color = sparkle1Color
                    )
                    drawPath(
                        path = assistantActionPath3,
                        color = sparkle2Color
                    )
                }
                "Now Playing" -> {
                    val wave1Color = if (isMaterialTheme) tLighter else tertiaryColor
                    val wave2Color = if (isMaterialTheme) sLighter else secondaryColor
                    val wave3Color = if (isMaterialTheme) pDarker else primaryColor
                    val noteHeadColor = if (isMaterialTheme) tLighter else quaternaryColor
                    drawPath(
                        path = nowPlayingActionPath1,
                        color = wave1Color,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    drawPath(
                        path = nowPlayingActionPath2,
                        color = wave2Color,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    drawPath(
                        path = nowPlayingActionPath3,
                        color = wave3Color,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    drawPath(
                        path = nowPlayingActionPath4,
                        color = noteHeadColor
                    )
                }
                else -> {
                    // None or unmapped: draw nothing
                }
            }
        }
    }
}

@Composable
fun ComposeThemedShortcutIcon(
    resId: Int,
    contentDescription: String,
    primaryColor: androidx.compose.ui.graphics.Color,
    secondaryColor: androidx.compose.ui.graphics.Color,
    tertiaryColor: androidx.compose.ui.graphics.Color,
    isMaterialTheme: Boolean,
    modifier: Modifier = Modifier,
    quaternaryColor: androidx.compose.ui.graphics.Color = primaryColor,
    isPillLight: Boolean = false
) {
    // The Material G style draws the search overlay's icons in the overlay's colours, from these accents.
    val colors = rememberSearchIconColors(primaryColor, secondaryColor, tertiaryColor, isPillLight)
    val pDarker = remember(colors) { androidx.compose.ui.graphics.Color(colors.tiers.primaryDarker) }
    val sLighter = remember(colors) { androidx.compose.ui.graphics.Color(colors.tiers.secondLighter) }
    val tLighter = remember(colors) { androidx.compose.ui.graphics.Color(colors.tiers.thirdLighter) }

    if (isMaterialTheme && resId == com.pixel.intelligentsearch.R.drawable.ic_mic) {
        WidgetMic(colors, modifier)
    } else if (isMaterialTheme && resId == com.pixel.intelligentsearch.R.drawable.ic_camera) {
        val path1 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M75.0365 83.3333C79.6388 83.3333 83.3698 79.6023 83.3698 75C83.3698 70.3976 79.6388 66.6666 75.0365 66.6666C70.4341 66.6666 66.7031 70.3976 66.7031 75C66.7031 79.6023 70.4341 83.3333 75.0365 83.3333Z").toPath() }
        val path2 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M50.0364 66.6666C56.9399 66.6666 62.5364 61.0702 62.5364 54.1666C62.5364 47.2631 56.9399 41.6666 50.0364 41.6666C43.1328 41.6666 37.5364 47.2631 37.5364 54.1666C37.5364 61.0702 43.1328 66.6666 50.0364 66.6666Z").toPath() }
        val path3 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M12.5 70.4166C12.5 79.8489 20.151 87.5 29.5833 87.5H50V79.1666L29.1146 79.1145C24.5313 79.1145 20.8333 74.8489 20.8333 69.7916V60.4166H12.5V70.4166Z").toPath() }
        val path4 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M87.5001 37.9167C87.5001 28.4844 79.849 20.8334 70.4167 20.8334H60.4167L70.8334 29.1667C75.4167 29.1667 79.1667 33.4844 79.1667 38.5417V54.1667H87.5001V37.9167Z").toPath() }
        val path5 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M58.3333 12.5H41.6667L35.4167 20.8333H29.5833C20.151 20.8333 12.5 28.4844 12.5 37.9167V47.9167H20.8333V38.5417C20.8333 33.4844 24.5833 29.1667 29.1667 29.1667H70.8333L58.3333 12.5Z").toPath() }

        androidx.compose.foundation.Canvas(modifier = modifier) {
            val scaleX = size.width / 100f
            val scaleY = size.height / 100f
            scale(scaleX, scaleY, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                drawPath(path = path1, color = sLighter)
                drawPath(path = path2, color = pDarker)
                drawPath(path = path3, color = tLighter)
                drawPath(path = path4, color = tLighter)
                drawPath(path = path5, color = tLighter)
            }
        }
    } else if (isMaterialTheme && resId == com.pixel.intelligentsearch.R.drawable.ic_lens_action) {
        androidx.compose.foundation.Canvas(modifier = modifier) {
            val scaleX = size.width / 24f
            val scaleY = size.height / 24f
            scale(scaleX, scaleY, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                drawPath(path = assistantActionPath1, color = tLighter)
                drawPath(path = assistantActionPath2, color = pDarker)
                drawPath(path = assistantActionPath3, color = sLighter)
            }
        }
    } else if (isMaterialTheme && resId == com.pixel.intelligentsearch.R.drawable.ic_search_lens_expressive) {
        OverlayLens(colors, modifier)
    } else if (isMaterialTheme && resId == com.pixel.intelligentsearch.R.drawable.ic_music) {
        androidx.compose.foundation.Canvas(modifier = modifier) {
            val scaleX = size.width / 24f
            val scaleY = size.height / 24f
            scale(scaleX, scaleY, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                drawPath(path = nowPlayingActionPath1, color = tLighter, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.0f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                drawPath(path = nowPlayingActionPath2, color = sLighter, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.0f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                drawPath(path = nowPlayingActionPath3, color = pDarker, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.0f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                drawPath(path = nowPlayingActionPath4, color = tLighter)
            }
        }
    } else {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = resId),
            contentDescription = contentDescription,
            tint = primaryColor,
            modifier = modifier
        )
    }
}

fun updateWidgets(context: Context) {
    try {
        com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider.updateAllWidgets(context)
    } catch (e: Throwable) {
        android.util.Log.e("SettingsScreens", "Failed to update widgets", e)
    }
}

/**
 * Plays a doodle around a 24dp G, from the same layout the widget uses so it moves exactly as it does there. Its art
 * is a 48dp square centred on the G, so it's drawn at that size around the G's slot. A larger [size] draws it larger
 * (around a G scaled by the same amount), still sharp, as Developer Settings' doodle gallery does.
 */
@Composable
fun WidgetDoodleOverlay(doodle: Occasion, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    key(doodle, px) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = {
                android.view.LayoutInflater.from(it).inflate(doodle.layout, null).apply {
                    // The widget style caps the player at 48dp.
                    (this as? android.widget.ProgressBar)?.let { bar ->
                        bar.minWidth = px
                        bar.maxWidth = px
                        bar.minHeight = px
                        bar.maxHeight = px
                    }
                }
            },
            modifier = modifier.requiredSize(size)
        )
    }
}

@Composable
fun ComposeGIcon(
    modifier: Modifier = Modifier,
    primaryColor: androidx.compose.ui.graphics.Color,
    secondaryColor: androidx.compose.ui.graphics.Color,
    tertiaryColor: androidx.compose.ui.graphics.Color,
    isAccented: Boolean = false,
    accentColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    fallbackTint: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    useOriginalColors: Boolean = false,
    isPillLight: Boolean = false
) {
    if (useOriginalColors) {
        Image(
            painter = androidx.compose.ui.res.painterResource(id = com.pixel.intelligentsearch.R.drawable.ic_g_logo_colored),
            contentDescription = "G Logo",
            modifier = modifier
        )
        return
    }
    if (fallbackTint != androidx.compose.ui.graphics.Color.Unspecified) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = com.pixel.intelligentsearch.R.drawable.ic_g_logo),
            contentDescription = "G Logo",
            modifier = modifier,
            tint = fallbackTint
        )
        return
    }
    if (isAccented) {
        val effectiveColor = if (accentColor != androidx.compose.ui.graphics.Color.Unspecified) accentColor else primaryColor
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = com.pixel.intelligentsearch.R.drawable.ic_g_logo),
            contentDescription = "G Logo",
            modifier = modifier,
            tint = effectiveColor
        )
        return
    }
    // The search overlay's lens colours, which are the Material G's.
    val palette = rememberSearchIconColors(primaryColor, secondaryColor, tertiaryColor, isPillLight).g
    val rightColor = remember(palette) { androidx.compose.ui.graphics.Color(palette.rightArcAndBar) }
    val bottomColor = remember(palette) { androidx.compose.ui.graphics.Color(palette.bottomArc) }
    val leftColor = remember(palette) { androidx.compose.ui.graphics.Color(palette.leftArc) }
    val topColor = remember(palette) { androidx.compose.ui.graphics.Color(palette.topArc) }

    val mainPath = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M12,18.63C15.23,18.63 17.45,16.79 17.92,14.26L20,12L22.409,14.26C21.604,19.462 17.649,23 12,23C5.92,23 1,18.08 1,12C1,5.92 5.92,1 12,1C14.97,1 17.45,2.09 19.36,3.87L16.21,7.02C15.06,5.94 13.62,5.38 12,5.38C8.41,5.38 5.49,8.43 5.49,12.01C5.49,15.59 8.41,18.63 12,18.63Z").toPath() }
    val coverPath = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M12,14.26V10H22.36C22.49,10.72 22.56,11.47 22.56,12.25C22.56,12.943 22.509,13.614 22.409,14.26H17.92H12Z").toPath() }
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val scaleX = size.width / 24f
        val scaleY = size.height / 24f
        scale(scaleX, scaleY, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(
                path = mainPath,
                brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                    0.0f to rightColor,
                    0.25f to bottomColor,
                    0.50f to leftColor,
                    0.75f to topColor,
                    1.0f to rightColor,
                    center = androidx.compose.ui.geometry.Offset(12f, 12f)
                )
            )
            drawPath(
                path = coverPath,
                color = rightColor
            )
        }
    }
}
