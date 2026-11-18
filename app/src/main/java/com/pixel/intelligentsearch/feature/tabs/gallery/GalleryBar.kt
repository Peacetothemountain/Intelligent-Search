package com.pixel.intelligentsearch.feature.tabs.gallery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.R
import kotlin.math.roundToInt

/** The phone's two dynamic schemes: a look may draw its bar and search page in the other theme. */
@Immutable
internal class GallerySchemes(val dark: ColorScheme, val light: ColorScheme) {
    fun of(dark: Boolean) = if (dark) this.dark else light
    private val widgetDark = this.dark.widgetThemeColors(true)
    private val widgetLight = this.light.widgetThemeColors(false)
    fun widget(dark: Boolean) = if (dark) widgetDark else widgetLight

    /** SearchIconColors.themeAccents: the overlay's accents for a light or dark bar. */
    fun accents(isLight: Boolean): IconAccents = widget(!isLight).let { IconAccents(it.primary, it.secondary, it.tertiary) }
}

internal val LocalGallerySchemes = staticCompositionLocalOf<GallerySchemes?> { null }

@Composable
internal fun rememberGallerySchemes(): GallerySchemes {
    LocalGallerySchemes.current?.let { return it }
    val context = LocalContext.current
    return remember(context) {
        // As the app's theme: the light page is surfaceContainer.
        val light = dynamicLightColorScheme(context).let { it.copy(background = it.surfaceContainer) }
        GallerySchemes(dynamicDarkColorScheme(context), light)
    }
}

/** The bar's true width when the launcher reports no size (the launcher's own search slot): 312 dp. */
internal val BAR_WIDTH = 312.dp

/** The bar's height at true size, for a look's hero design. */
internal fun barHeight(look: Look): Dp = BarShape.of(look.heroStyle, look.hero == Design.MATERIAL).height.dp

/**
 * [look]'s search bar on its hero design, drawn as Widget Customization's live preview draws it (and so as the widget
 * does): the backdrop (rim, pill and circle) faded as one group, the icons over it fully opaque, with the person's own
 * buttons. Drawn at true size (312 dp wide) and scaled as one picture by [scale].
 */
@Composable
internal fun GalleryBar(look: Look, phoneDark: Boolean, modifier: Modifier = Modifier, scale: Float = 1f) {
    val schemes = rememberLookSchemes(look)
    val isMaterial = look.hero == Design.MATERIAL
    val style = look.heroStyle
    val appDark = look.appDark(phoneDark)
    val scheme = schemes.of(appDark)
    val colors = remember(look, appDark, schemes, style) {
        barColors(
            draft = style,
            isMaterialDesign = isMaterial,
            theme = PreviewTheme(scheme.primary, scheme.primaryContainer, scheme.surfaceContainer, appDark),
            iconAccents = schemes::accents,
            widgetColors = schemes::widget
        )
    }
    val shape = remember(style, isMaterial) { BarShape.of(style, isMaterial) }
    val buttons = BarButtons.of(look, look.hero)
    val showsCircle = isMaterial && buttons.actionIcon != "None"
    Box(
        modifier
            .clearAndSetSemantics { }
            .scaledTo(scale, BAR_WIDTH, shape.height.dp)
    ) {
        BarBackdrop(colors, shape, showsCircle, Modifier.matchParentSize())
        BarIcons(colors, style, buttons, shape, isMaterial, showsCircle)
    }
}

/** Lays the child out at [w] × [h] and draws it scaled by [s] from its top start corner, taking the scaled room. */
private fun Modifier.scaledTo(s: Float, w: Dp, h: Dp): Modifier = layout { measurable, _ ->
    val wp = w.roundToPx()
    val hp = h.roundToPx()
    val p = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(wp, hp))
    layout((wp * s).roundToInt(), (hp * s).roundToInt()) {
        p.placeWithLayer(0, 0) {
            scaleX = s
            scaleY = s
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
        }
    }
}

/** WidgetPreviewBackdrop: the outline filling the bar, the pill and circle over it, faded once as one group. */
@Composable
private fun BarBackdrop(colors: BarColors, shape: BarShape, showsCircle: Boolean, modifier: Modifier) {
    Row(
        modifier = modifier
            .graphicsLayer {
                alpha = colors.backdropAlpha
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .then(if (shape.outline > 0) Modifier.background(colors.rim, RoundedCornerShape(shape.outerRadius.dp)) else Modifier)
            .padding(shape.outline.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f).fillMaxHeight().background(colors.pill.fill, RoundedCornerShape(shape.pillRadius.dp)))
        if (showsCircle) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(BarShape.CIRCLE_SIZE.dp)
                    .background(colors.pill.fill, if (shape.circleIsRound) CircleShape else RoundedCornerShape(shape.circleRadius.dp))
            )
        }
    }
}

/** The icons, laid out on the same lines as the backdrop (the editor preview's Row). */
@Composable
private fun BarIcons(colors: BarColors, style: WidgetStyle, buttons: BarButtons, shape: BarShape, isMaterial: Boolean, showsCircle: Boolean) {
    val pillLight = colors.pill.isLight
    val gPrimary = colors.accentPrimary
    val mPrimary = Color(colors.materialIconAccents.primary)
    val mSecondary = Color(colors.materialIconAccents.secondary)
    val mTertiary = Color(colors.materialIconAccents.tertiary)
    // The Custom G style (the person's own, kept by a look that keeps the G): each icon in the color picked for it,
    // adjusted as the widget adjusts it (WidgetColorRules.customIconColors). Without colors to show, as Accented.
    val picked = buttons.colors?.takeIf { style.gIconTheme == G_CUSTOM }
    val gTheme = if (style.gIconTheme == G_CUSTOM && picked == null) G_ACCENTED else style.gIconTheme
    fun drawn(color: Int) = Color(WidgetRules.iconColor(color, isMaterial, colors.lockDark, pillLight))
    val tint: (String) -> List<Color> = { key ->
        if (picked != null) {
            val c = when {
                buttons.syncColors -> picked.gIcon
                key == "mic" -> picked.mic
                key == "shortcut1" -> picked.shortcut1
                key == "shortcut2" -> picked.shortcut2
                key == "shortcut3" -> picked.shortcut3
                key == "action" -> picked.action
                else -> picked.gIcon
            }
            List(4) { drawn(c) }
        } else when (gTheme) {
            G_ACCENTED -> List(4) { gPrimary }
            G_MATERIAL -> listOf(mPrimary, mSecondary, mTertiary, mPrimary)
            else -> List(4) { colors.systemIconTint }
        }
    }
    Row(Modifier.padding(shape.outline.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).height(shape.pillHeight.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (buttons.showG) {
                // Keep System G is a choice within the Custom G style: Google's own colors (the widget's useOriginalG).
                val original = gTheme == G_SYSTEM || (picked != null && buttons.keepSystemG)
                val customG = if (picked != null && !buttons.keepSystemG) drawn(picked.gIcon) else null
                GIcon(
                    Modifier.size(24.dp), mPrimary, mSecondary, mTertiary,
                    isAccented = gTheme == G_ACCENTED || customG != null, accentColor = customG ?: gPrimary,
                    useOriginalColors = original, isPillLight = pillLight
                )
            }
            Spacer(Modifier.weight(1f))
            val items = buttons.order.mapNotNull { key ->
                when (key) {
                    "mic" -> if (buttons.showVoice) key to (if (colors.usesMaterialYouIcons) R.drawable.gallery_ic_mic else R.drawable.gallery_ic_mic_original) else null
                    else -> buttons.shortcuts[key]?.takeIf { it != "None" }?.let { key to shortcutIcon(it, colors.usesMaterialYouIcons) }
                }
            }
            items.forEachIndexed { i, (key, res) ->
                if (i > 0) Spacer(Modifier.width(16.dp))
                val (p, s, t) = tint(key)
                ThemedShortcutIcon(res, p, s, t, isMaterialTheme = gTheme == G_MATERIAL, Modifier.size(24.dp), pillLight)
            }
        }
        if (showsCircle) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                val (p, s, t) = tint("action")
                ActionIcon(buttons.actionIcon, Modifier.size(24.dp), p, s, t, isMaterialTheme = gTheme == G_MATERIAL, isPillLight = pillLight)
            }
        }
    }
}

/** getShortcutIconRes, for the buttons the preview shows. */
private fun shortcutIcon(name: String, materialYou: Boolean): Int = when (name) {
    "Translate (text)" -> R.drawable.gallery_ic_translate
    "Google Lens" -> R.drawable.gallery_ic_camera
    else -> com.pixel.intelligentsearch.feature.widget.getShortcutIconRes(name, materialYou)
}

/** SearchIconPaths: the lens with its sparkle and Rambler's mic, in a 24-unit square. */
internal object IconPaths {
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
    const val MIC_SHIFT_X = -2.5f
    const val MIC_SHIFT_Y = 0.5f
    const val G_MAIN = "M12,18.63C15.23,18.63 17.45,16.79 17.92,14.26L20,12L22.409,14.26C21.604,19.462 17.649,23 12,23C5.92,23 1,18.08 1,12C1,5.92 5.92,1 12,1C14.97,1 17.45,2.09 19.36,3.87L16.21,7.02C15.06,5.94 13.62,5.38 12,5.38C8.41,5.38 5.49,8.43 5.49,12.01C5.49,15.59 8.41,18.63 12,18.63Z"
    const val G_COVER = "M12,14.26V10H22.36C22.49,10.72 22.56,11.47 22.56,12.25C22.56,12.943 22.509,13.614 22.409,14.26H17.92H12Z"
    const val CAM_1 = "M75.0365 83.3333C79.6388 83.3333 83.3698 79.6023 83.3698 75C83.3698 70.3976 79.6388 66.6666 75.0365 66.6666C70.4341 66.6666 66.7031 70.3976 66.7031 75C66.7031 79.6023 70.4341 83.3333 75.0365 83.3333Z"
    const val CAM_2 = "M50.0364 66.6666C56.9399 66.6666 62.5364 61.0702 62.5364 54.1666C62.5364 47.2631 56.9399 41.6666 50.0364 41.6666C43.1328 41.6666 37.5364 47.2631 37.5364 54.1666C37.5364 61.0702 43.1328 66.6666 50.0364 66.6666Z"
    const val CAM_3 = "M12.5 70.4166C12.5 79.8489 20.151 87.5 29.5833 87.5H50V79.1666L29.1146 79.1145C24.5313 79.1145 20.8333 74.8489 20.8333 69.7916V60.4166H12.5V70.4166Z"
    const val CAM_4 = "M87.5001 37.9167C87.5001 28.4844 79.849 20.8334 70.4167 20.8334H60.4167L70.8334 29.1667C75.4167 29.1667 79.1667 33.4844 79.1667 38.5417V54.1667H87.5001V37.9167Z"
    const val CAM_5 = "M58.3333 12.5H41.6667L35.4167 20.8333H29.5833C20.151 20.8333 12.5 28.4844 12.5 37.9167V47.9167H20.8333V38.5417C20.8333 33.4844 24.5833 29.1667 29.1667 29.1667H70.8333L58.3333 12.5Z"

    private val parser = PathParser()
    private val cache = HashMap<String, androidx.compose.ui.graphics.Path>()
    fun path(d: String): androidx.compose.ui.graphics.Path = synchronized(cache) { cache.getOrPut(d) { parser.parsePathString(d).toPath() } }
}

@Composable
private fun rememberIconColors(p: Color, s: Color, t: Color, isPillLight: Boolean): IconColors =
    remember(p, s, t, isPillLight) {
        IconColors.of(IconAccents(p.toArgbInt(), s.toArgbInt(), t.toArgbInt()), isPillLight)
    }

private fun Color.toArgbInt(): Int = this.toArgb()

/** The search lens with its sparkle (SearchLensDrawing). */
@Composable
internal fun LensDrawing(rim: Color, top: Color, handle: Color, sparkle: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            drawPath(IconPaths.path(IconPaths.LENS_RIM), rim, style = Stroke(IconPaths.LENS_RIM_WIDTH, cap = StrokeCap.Round))
            drawPath(IconPaths.path(IconPaths.LENS_TOP), top, style = Stroke(IconPaths.LENS_RIM_WIDTH, cap = StrokeCap.Round))
            drawPath(IconPaths.path(IconPaths.LENS_HANDLE), handle, style = Stroke(IconPaths.LENS_HANDLE_WIDTH, cap = StrokeCap.Round))
            drawPath(IconPaths.path(IconPaths.LENS_SPARKLE), sparkle)
        }
    }
}

/** Gboard Rambler's sparkle mic (SparkleMicDrawing): the overlay's voice key. */
@Composable
internal fun SparkleMicDrawing(capsule: Color, stem: Color, leftCradle: Color, rightCradle: Color, sparkle: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            translate(IconPaths.MIC_SHIFT_X, IconPaths.MIC_SHIFT_Y) {
                drawPath(IconPaths.path(IconPaths.MIC_CAPSULE), capsule)
                drawPath(IconPaths.path(IconPaths.MIC_STEM), stem)
                drawPath(IconPaths.path(IconPaths.MIC_LEFT_CRADLE), leftCradle)
                drawPath(IconPaths.path(IconPaths.MIC_RIGHT_CRADLE), rightCradle)
            }
            drawPath(IconPaths.path(IconPaths.MIC_SPARKLE), sparkle)
        }
    }
}

/** The widget's mic in the overlay's colors, centered, without the overlay's sparkle (WidgetMic). */
@Composable
private fun WidgetMic(colors: IconColors, modifier: Modifier) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            drawPath(IconPaths.path(IconPaths.MIC_CAPSULE), Color(colors.micCapsule))
            drawPath(IconPaths.path(IconPaths.MIC_STEM), Color(colors.micStem))
            drawPath(IconPaths.path(IconPaths.MIC_LEFT_CRADLE), Color(colors.micLeftCradle))
            drawPath(IconPaths.path(IconPaths.MIC_RIGHT_CRADLE), Color(colors.micRightCradle))
        }
    }
}

/** ComposeThemedShortcutIcon: the Material G style draws the overlay's icons in its colors; the others tint the icon. */
@Composable
private fun ThemedShortcutIcon(
    resId: Int, primary: Color, secondary: Color, tertiary: Color, isMaterialTheme: Boolean, modifier: Modifier, isPillLight: Boolean
) {
    val colors = rememberIconColors(primary, secondary, tertiary, isPillLight)
    when {
        isMaterialTheme && resId == R.drawable.gallery_ic_mic -> WidgetMic(colors, modifier)
        isMaterialTheme && resId == R.drawable.gallery_ic_camera -> Canvas(modifier) {
            scale(size.width / 100f, size.height / 100f, pivot = Offset.Zero) {
                drawPath(IconPaths.path(IconPaths.CAM_1), Color(colors.tiers.secondLighter))
                drawPath(IconPaths.path(IconPaths.CAM_2), Color(colors.tiers.primaryDarker))
                drawPath(IconPaths.path(IconPaths.CAM_3), Color(colors.tiers.thirdLighter))
                drawPath(IconPaths.path(IconPaths.CAM_4), Color(colors.tiers.thirdLighter))
                drawPath(IconPaths.path(IconPaths.CAM_5), Color(colors.tiers.thirdLighter))
            }
        }
        else -> Icon(painterResource(resId), contentDescription = null, tint = primary, modifier = modifier)
    }
}

/** ComposeActionIcon: the circle's Search icon (the overlay's lens on the Material style). */
@Composable
private fun ActionIcon(
    iconType: String, modifier: Modifier, primary: Color, secondary: Color, tertiary: Color,
    isMaterialTheme: Boolean, isPillLight: Boolean
) {
    val colors = rememberIconColors(primary, secondary, tertiary, isPillLight)
    if (iconType != "Search") {
        // The person's own circle, in "Your design": drawn as Widget Customization draws it.
        com.pixel.intelligentsearch.feature.settings.ComposeActionIcon(
            iconType, modifier, primary, secondary, tertiary, isMaterialTheme = isMaterialTheme, isPillLight = isPillLight
        )
        return
    }
    if (isMaterialTheme) {
        LensDrawing(Color(colors.lensRim), Color(colors.lensTop), Color(colors.lensHandle), Color(colors.lensSparkle), modifier)
        return
    }
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            drawPath(IconPaths.path(IconPaths.LENS_RIM), primary, style = Stroke(IconPaths.LENS_RIM_WIDTH, cap = StrokeCap.Round))
            drawPath(IconPaths.path(IconPaths.LENS_TOP), tertiary, style = Stroke(IconPaths.LENS_RIM_WIDTH, cap = StrokeCap.Round))
            drawPath(IconPaths.path(IconPaths.LENS_HANDLE), secondary, style = Stroke(IconPaths.LENS_HANDLE_WIDTH, cap = StrokeCap.Round))
            drawPath(IconPaths.path(IconPaths.LENS_SPARKLE), primary)
        }
    }
}

/** ComposeGIcon: Google's colors, one accent, or the Material G's sweep of the overlay's lens colors. */
@Composable
internal fun GIcon(
    modifier: Modifier, primary: Color, secondary: Color, tertiary: Color,
    isAccented: Boolean, accentColor: Color, useOriginalColors: Boolean, isPillLight: Boolean
) {
    if (useOriginalColors) {
        Image(painterResource(R.drawable.gallery_ic_g_logo_colored), contentDescription = null, modifier = modifier)
        return
    }
    if (isAccented) {
        Icon(painterResource(R.drawable.gallery_ic_g_logo), contentDescription = null, tint = accentColor, modifier = modifier)
        return
    }
    val g = rememberIconColors(primary, secondary, tertiary, isPillLight).g
    val right = Color(g.rightArcAndBar)
    val bottom = Color(g.bottomArc)
    val left = Color(g.leftArc)
    val top = Color(g.topArc)
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            drawPath(
                IconPaths.path(IconPaths.G_MAIN),
                Brush.sweepGradient(0f to right, 0.25f to bottom, 0.5f to left, 0.75f to top, 1f to right, center = Offset(12f, 12f))
            )
            drawPath(IconPaths.path(IconPaths.G_COVER), right)
        }
    }
}
