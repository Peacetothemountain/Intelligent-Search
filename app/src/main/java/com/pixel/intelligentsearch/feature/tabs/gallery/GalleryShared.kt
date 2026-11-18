package com.pixel.intelligentsearch.feature.tabs.gallery

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.feature.tabs.ui.MatteGrain
import com.pixel.intelligentsearch.feature.tabs.ui.Springs
import com.pixel.intelligentsearch.feature.tabs.ui.Symbols
import com.pixel.intelligentsearch.feature.tabs.ui.bouncyClickable
import com.pixel.intelligentsearch.feature.tabs.ui.rememberTick
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin


/* What the Gallery page draws its looks with: the M3 shape library, windows onto a look, a look's chips and colors,
 * connected buttons, a baked soft shadow, and the cards' haptics. */

/** The M3 shape library as polar curves (the mockup's cookie, sunny, burst and clover). */
internal class PolarShape(private val n: Int, private val amp: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = minOf(size.width, size.height) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r0 = c / (1f + kotlin.math.abs(amp))
        val path = Path()
        val pts = 120
        for (k in 0 until pts) {
            val t = k.toFloat() / pts * 2f * Math.PI.toFloat()
            val r = r0 * (1f + amp * cos(n * t))
            val x = cx + r * sin(t)
            val y = cy - r * cos(t)
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }

    companion object {
        val Cookie9 = PolarShape(9, 0.085f)
        val Cookie12 = PolarShape(12, 0.06f)
        val Sunny = PolarShape(8, 0.1f)
        val Burst = PolarShape(10, 0.13f)
        val Clover = PolarShape(4, 0.2f)
    }
}

/** A shape from the library filled with [bg], its icon (or anything) centered in it. */
@Composable
internal fun ShapeBadge(shape: Shape, size: Dp, bg: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    Box(modifier.size(size).background(bg, shape), contentAlignment = Alignment.Center) { content() }
}

/**
 * A soft shadow (or glow) of [color] behind the element, blurred by [blur]: drawn once into a picture of its own at
 * 1/[k] of the resolution (a blur this soft has no finer detail), so moving, turning and scaling the card only moves it.
 */
internal fun Modifier.bakedBlur(
    color: Color, blur: Dp, k: Float, offsetY: Dp = 0.dp,
    bounds: CacheDrawScope.() -> Rect, path: CacheDrawScope.() -> Path
): Modifier = drawWithCache {
    val b = bounds()
    val p = path().asAndroidPath()
    val dy = offsetY.toPx()
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        maskFilter = BlurMaskFilter(blur.toPx(), BlurMaskFilter.Blur.NORMAL)
    }
    val baked = obtainGraphicsLayer().apply {
        compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        record(size = IntSize(ceil(b.width / k).toInt().coerceAtLeast(1), ceil(b.height / k).toInt().coerceAtLeast(1))) {
            drawIntoCanvas { c ->
                c.nativeCanvas.save()
                c.nativeCanvas.scale(1f / k, 1f / k)
                c.nativeCanvas.translate(-b.left, -b.top + dy)
                c.nativeCanvas.drawPath(p, paint)
                c.nativeCanvas.restore()
            }
        }
    }
    onDrawBehind { translate(b.left, b.top) { scale(k, k, pivot = Offset.Zero) { drawLayer(baked) } } }
}

/**
 * The cards' haptics, through the app's haptic engine so its Vibration setting applies: a light tick when a card is
 * picked up, a frequent-segment tick as a card passes another, and a firmer thump when a card comes to rest in its slot.
 */
internal object CardHaptics {
    /** Picking a strip up is the lightest moment: the same faint texture tick a strip passing makes. */
    fun start(view: android.view.View) = pass(view)

    fun pass(view: android.view.View) {
        engine(view)?.performFanPass(view)
    }

    /** A strip coming to rest on a new look is the one that registers: the firmer confirm. */
    fun land(view: android.view.View) {
        engine(view)?.performFanLand(view)
    }

    private fun engine(view: android.view.View) =
        runCatching { com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(view.context) }.getOrNull()
}

/**
 * A window onto [look]: its wallpaper (or what is behind the page) with its bar centered where it sits, at the
 * mockup's size for the window ([w] wide). Tapping it ([onClick]) tries the look full screen.
 */
@Composable
internal fun LookWindowBox(
    look: Look, phoneDark: Boolean, w: Dp, h: Dp, shape: Shape, modifier: Modifier = Modifier,
    register: ((LayoutCoordinates) -> Unit)? = null, registerBar: ((LayoutCoordinates) -> Unit)? = null,
    badge: Boolean = false, onClick: (() -> Unit)? = null, label: String = look.name, content: @Composable () -> Unit = {}
) {
    Box(
        modifier
            .size(w, h)
            .then(if (register != null) Modifier.onGloballyPositioned(register) else Modifier)
            .lookWindow(look, shape)
            .clip(shape)
            .then(if (onClick != null) Modifier.bouncyTry(shape, label, onClick).semantics { contentDescription = "Try $label" } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Box(if (registerBar != null) Modifier.onGloballyPositioned(registerBar) else Modifier) {
            GalleryBar(look, phoneDark, scale = w.value * 1.05f / 360f)
        }
        if (badge) CheckShape(Modifier.align(Alignment.TopStart).padding(7.dp))
        content()
    }
}

private fun Modifier.bouncyTry(shape: Shape, name: String, onClick: () -> Unit): Modifier =
    bouncyClickable(shape, onClickLabel = "try $name", onClick = onClick)

/** The check on the look that is on: a cookie in primary with a check. */
@Composable
internal fun CheckShape(modifier: Modifier = Modifier, size: Dp = 26.dp) {
    val s = MaterialTheme.colorScheme
    ShapeBadge(PolarShape.Cookie9, size, s.primary, modifier) { Icon(Symbols.Check, null, tint = s.onPrimary, modifier = Modifier.size(size * 0.65f)) }
}

/** The look's colors at a glance: a cookie in its rim (or pill) color with its pill (or icon) color in the middle. */
@Composable
internal fun LookTile(look: Look, phoneDark: Boolean, size: Dp) {
    val sw = rememberSwatch(look, phoneDark)
    Box(Modifier.size(size).background(sw[0], PolarShape.Cookie9), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size - 14.dp).background(sw[1], CircleShape))
    }
}

/** The bar's three colors: its rim or pill, its pill or icon, and its icon. */
@Composable
internal fun rememberSwatch(look: Look, phoneDark: Boolean): List<Color> {
    val schemes = rememberLookSchemes(look)
    val isMaterial = look.hero == Design.MATERIAL
    val style = look.heroStyle
    val appDark = look.appDark(phoneDark)
    val scheme = schemes.of(appDark)
    return remember(look, appDark, schemes) {
        val b = barColors(
            draft = style, isMaterialDesign = isMaterial,
            theme = PreviewTheme(scheme.primary, scheme.primaryContainer, scheme.surfaceContainer, appDark),
            iconAccents = schemes::accents, widgetColors = schemes::widget
        )
        val icon = if (isMaterial) b.accentPrimary else b.systemIconTint
        if (isMaterial) listOf(b.rim.copy(alpha = 1f), b.pill.fill, icon) else listOf(b.pill.fill, icon, icon)
    }
}

/** A look's chips: where its colors come from, the theme search uses, its G, anything to know, its months. */
@Composable
internal fun LookChips(look: Look, phoneDark: Boolean, today: LocalDate, bg: Color, fg: Color, accent: Color, modifier: Modifier = Modifier) {
    val sw = rememberSwatch(look, phoneDark)
    FlowRow(modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (look.dyn) FactChip("Your Colors", bg, fg) { Icon(Symbols.PaletteFilled, null, tint = accent, modifier = Modifier.size(15.dp)) }
        else FactChip("Fixed Colors", bg, fg) {
            Row {
                listOf(sw[0], if (look.hero == Design.MATERIAL) sw[1] else sw[2], sw[2]).forEachIndexed { k, col ->
                    Box(Modifier.offset(x = (-4 * k).dp).size(12.dp).border(1.5.dp, bg, CircleShape).padding(1.5.dp).background(col, CircleShape))
                }
            }
        }
        when (look.search.theme) {
            ThemeChoice.Dark -> FactChip("Dark Search", bg, fg) { Icon(Symbols.DarkModeFilled, null, tint = accent, modifier = Modifier.size(15.dp)) }
            ThemeChoice.Light -> FactChip("Light Search", bg, fg) { Icon(Symbols.LightModeFilled, null, tint = accent, modifier = Modifier.size(15.dp)) }
            ThemeChoice.Keep -> Unit
        }
        when (look.gChoice) {
            G_SYSTEM -> FactChip("Google G", bg, fg) { androidx.compose.foundation.Image(painterResource(R.drawable.gallery_ic_g_logo_colored), null, modifier = Modifier.size(14.dp)) }
            G_ACCENTED -> FactChip("Accented G", bg, fg) { Icon(painterResource(R.drawable.gallery_ic_g_logo), null, tint = accent, modifier = Modifier.size(14.dp)) }
            G_MATERIAL -> FactChip("Material G", bg, fg) { Icon(painterResource(R.drawable.gallery_ic_g_logo), null, tint = fg, modifier = Modifier.size(14.dp)) }
            else -> FactChip("Keeps Your G", bg, fg) { Icon(painterResource(R.drawable.gallery_ic_g_logo), null, tint = fg, modifier = Modifier.size(14.dp)) }
        }
        look.note?.let { FactChip(it, bg, fg) { Icon(Symbols.Contrast, null, tint = accent, modifier = Modifier.size(15.dp)) } }
        look.season?.let { s -> if (s != Season.of(today)) FactChip(s.months, bg, fg) { Icon(Symbols.CalendarMonth, null, tint = accent, modifier = Modifier.size(15.dp)) } }
    }
}

@Composable
private fun FactChip(text: String, bg: Color, fg: Color, icon: @Composable () -> Unit) {
    Row(
        Modifier.height(26.dp).background(bg, RoundedCornerShape(13.dp)).padding(start = 6.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        icon()
        Text(text, color = fg, maxLines = 1, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp))
    }
}

/** One button of a connected pair. */
internal class GroupButton(
    val text: String, val icon: androidx.compose.ui.graphics.vector.ImageVector?, val bg: Color, val fg: Color,
    val description: String? = null, val onClick: () -> Unit
)

/**
 * Two buttons joined as one group (M3 Expressive's connected button group): fully round outside, [inner] corners where
 * they meet; a pressed one morphs to 14 dp corners all round on the fast spring. The right one is the wider.
 */
@Composable
internal fun ConnectedButtons(modifier: Modifier, height: Dp, outer: Dp, left: GroupButton, right: GroupButton, inner: Dp = 8.dp, fontSize: Float = 15f) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        MorphButton(left, height, listOf(outer, inner, inner, outer), Modifier.weight(1f), fontSize)
        MorphButton(right, height, listOf(inner, outer, outer, inner), Modifier.weight(1.45f), fontSize)
    }
}

/** A button whose corners morph from [rest] (top start, top end, bottom end, bottom start) to 14 dp while pressed. */
@Composable
internal fun MorphButton(b: GroupButton, height: Dp, rest: List<Dp>, modifier: Modifier = Modifier, fontSize: Float = 15f, pressed: Dp = 14.dp) {
    val src = remember { MutableInteractionSource() }
    val isPressed by src.collectIsPressedAsState()
    val p = remember { Animatable(0f) }
    val tick = rememberTick()
    LaunchedEffect(isPressed) {
        if (isPressed) tick()
        p.animateTo(if (isPressed) 1f else 0f, Springs.fast())
    }
    Row(
        modifier
            .height(height)
            .graphicsLayer {
                val u = p.value
                fun r(k: Int) = (rest[k] + (pressed - rest[k]) * u).coerceAtLeast(0.dp)
                shape = RoundedCornerShape(r(0), r(1), r(2), r(3))
                clip = true
            }
            .background(b.bg)
            .clickable(interactionSource = src, indication = ripple()) { tick(); b.onClick() }
            .then(if (b.description != null) Modifier.semantics(mergeDescendants = true) { contentDescription = b.description } else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (b.icon != null) Icon(b.icon, null, tint = b.fg, modifier = Modifier.size(20.dp))
        Text(
            b.text, color = b.fg, maxLines = 1, modifier = Modifier.padding(start = if (b.icon != null) 8.dp else 0.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp)
        )
    }
}
