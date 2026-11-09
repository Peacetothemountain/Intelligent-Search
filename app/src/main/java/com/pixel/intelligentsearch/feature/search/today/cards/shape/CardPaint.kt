package com.pixel.intelligentsearch.feature.search.today.cards.shape

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.feature.search.today.CARD_ALPHA
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * What the shaped designs draw with: the page's colors as the approved design maps them, Google Sans Flex as plain
 * paints, and [Stage], the drawing kit every shaped design uses on its 180-unit square (legibility, the content's fit
 * to the shape's middle, edge gauges, curved labels, the living light).
 *
 * NG Designs
 */

/**
 * The shaped designs' colors, from the dynamic scheme as the approved design maps them: [card] the page's card fill,
 * [on], [sub] and [mut] its text, [acc] the accent and [ink] on it, the page's own [amber] and [rose] warm tones,
 * [neg] and [pos] for falls and rises, [cover] the Private cover (opaque), [night] the alarm's night sky, [rain] the
 * weather's rain.
 */
@Immutable
internal class ShapedColors(
    val card: Color,
    val cardSolid: Color,
    val on: Color,
    val sub: Color,
    val mut: Color,
    val acc: Color,
    val ink: Color,
    val amber: Color,
    val rose: Color,
    val neg: Color,
    val pos: Color,
    val cover: Color,
    val night: Color,
    val rain: Color,
    val dark: Boolean
) {
    companion object {
        /** The page's warm tone, as TodayPage picks it. */
        fun warm(dark: Boolean) = if (dark) Color(0xFFF2B872) else Color(0xFFB5651D)

        fun of(scheme: ColorScheme): ShapedColors {
            val dark = scheme.surface.luminance() < 0.5f
            return ShapedColors(
                card = scheme.surfaceContainer.copy(alpha = CARD_ALPHA),
                cardSolid = scheme.surfaceContainer,
                on = scheme.onSurface,
                sub = scheme.onSurfaceVariant,
                mut = scheme.outline,
                acc = scheme.primary,
                ink = scheme.onPrimary,
                amber = warm(dark),
                rose = if (dark) Color(0xFFF4A3B4) else Color(0xFFA8364F),
                neg = scheme.error,
                pos = if (dark) Color(0xFF8FD6A8) else Color(0xFF146C2E),
                cover = scheme.surfaceContainerHighest,
                night = scheme.primaryContainer,
                rain = if (dark) Color(0xFF8AB4F8) else Color(0xFF0B57D0),
                dark = dark
            )
        }
    }
}

/** The shaped designs' colors from the current theme. */
@Composable
internal fun rememberShapedColors(): ShapedColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) { ShapedColors.of(scheme) }
}

internal const val W_REGULAR = 400
internal const val W_MEDIUM = 500
internal const val W_SEMIBOLD = 600
internal const val W_BOLD = 700

/** Google Sans Flex as plain text paints, one per weight, set up as the app's GoogleSansFlex family is. */
internal class CardType(base: Typeface) {
    private val paints = intArrayOf(W_REGULAR, W_MEDIUM, W_SEMIBOLD, W_BOLD).map { w ->
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            typeface = base
            fontVariationSettings = "'wght' $w, 'wdth' 100, 'slnt' 0, 'GRAD' 0, 'ROND' 0"
        }
    }

    /** The paint for [weight] (400, 500, 600 or 700). */
    fun paint(weight: Int): TextPaint = paints[
        when {
            weight >= W_BOLD -> 3
            weight >= W_SEMIBOLD -> 2
            weight >= W_MEDIUM -> 1
            else -> 0
        }
    ]

    companion object {
        @Volatile
        private var base: Typeface? = null

        /** Google Sans Flex, the app's one font, loaded once. */
        fun typeface(context: Context): Typeface = base ?: synchronized(this) {
            base ?: context.applicationContext.resources.getFont(R.font.google_sans_flex).also { base = it }
        }
    }
}

/** A card's text paints. */
@Composable
internal fun rememberCardType(): CardType {
    val context = LocalContext.current
    return remember { CardType(CardType.typeface(context)) }
}

/** Ease out, cubic, of [x] clamped to 0..1: how the approved design draws things in. */
internal fun easeOut(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return 1f - (1f - c).pow(3)
}

/**
 * The drawing kit for one card's shaped design on its 180-unit square. The card sets it up before each draw (the
 * canvas already moved and scaled so one unit is [k] pixels) and the design draws in design units. Text and icons that
 * would draw smaller than [minText] or [minIcon] pixels step aside; a design's main value always draws.
 */
internal class Stage(val type: CardType) {
    lateinit var scope: DrawScope
    lateinit var colors: ShapedColors
    lateinit var outline: CardOutline

    /** Pixels per design unit. */
    var k = 1f

    /** Milliseconds since the card started drawing in (held at the end once it has). */
    var t = 0f

    /** Ambient milliseconds: moving with the page's loop while the card holds an ambient slot, held still otherwise. */
    var now = 0f

    /** The count-up's value now, or null when the design has no number. */
    var count: Double? = null

    /** Whether the count-up has reached its number. */
    var countDone = true

    /** Whether the card's ambient motion runs (it holds a slot). */
    var ambient = false

    var animate = true
    var rtl = false

    /** How much larger the user's font size draws text, within what the shapes can hold. */
    var textScale = 1f
    var minText = 0f
    var minIcon = 0f

    /** Every color is drawn at this much of its alpha (stale content is dimmed). */
    var alpha = 1f

    /** The shape's class and name, for the slot renderer. */
    var shapeName = "circle"

    private var fit = 1f
    private var inContent = false
    private val measure = PathMeasure()
    private val segment = Path()
    private val arc = android.graphics.Path()
    private val oval = RectF()

    /** How far through drawing in something [delay] ms in and [duration] ms long is, eased (1 with animations off). */
    fun p(delay: Float, duration: Float): Float = if (!animate) 1f else easeOut((t - delay) / duration)

    /** [c] at the stage's [alpha]. */
    fun dim(c: Color, a: Float = 1f): Color = c.copy(alpha = c.alpha * alpha * a)

    private val unitPx: Float get() = k * (if (inContent) fit else 1f)

    /** Fills the shape. */
    fun fill(color: Color) {
        scope.drawPath(outline.path(), color)
    }

    /** Draws [block] clipped to the shape. */
    inline fun clipped(block: () -> Unit) {
        scope.clipPath(outline.path()) { block() }
    }

    /** A soft radial light of [color] at [opacity], centered at [fx], [fy] of the square, [fr] of it in radius. */
    fun glow(color: Color, opacity: Float, fx: Float, fy: Float, fr: Float, middle: Pair<Color, Float>? = null) {
        val center = Offset(fx * DESIGN, fy * DESIGN)
        val radius = (fr * DESIGN).coerceAtLeast(1f)
        val stops = if (middle == null) {
            arrayOf(0f to color.copy(alpha = opacity * alpha), 1f to color.copy(alpha = 0f))
        } else {
            arrayOf(0f to color.copy(alpha = opacity * alpha), 0.45f to middle.first.copy(alpha = middle.second * alpha), 1f to color.copy(alpha = 0f))
        }
        scope.drawRect(Brush.radialGradient(*stops, center = center, radius = radius), Offset.Zero, Size(DESIGN, DESIGN))
    }

    /** The living light: a soft glow of [color] drifting slowly inside the shape. */
    fun living(color: Color, opacity: Float) {
        val fx = 0.5f + 0.28f * sin(2f * PI.toFloat() * now / 20_000f)
        val fy = 0.45f + 0.25f * cos(2f * PI.toFloat() * now / 30_000f)
        clipped { glow(color, opacity, fx, fy, 0.75f) }
    }

    /** The outline at [at] of its size, stroked [width] wide in [color]. */
    fun edge(at: Float, color: Color, width: Float, effect: PathEffect? = null) {
        scope.drawPath(outline.path(at), dim(color), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
    }

    /** The outline at [at] of its size from [from] to [to] of the way round (clockwise from the top), stroked. */
    fun edgeSpan(at: Float, color: Color, width: Float, from: Float, to: Float) {
        if (from.isNaN() || to.isNaN()) return
        val a = from.coerceIn(0f, 1f)
        val b = to.coerceIn(0f, 1f)
        if (b - a <= 0.0005f) return
        measure.setPath(outline.path(at), false)
        val length = measure.length
        segment.reset()
        if (!measure.getSegment(a * length, b * length, segment, true)) return
        scope.drawPath(segment, dim(color), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    /** An edge gauge: a faint track round the outline at [at] and [fraction] of it in [color] from the top. */
    fun gauge(at: Float, color: Color, width: Float, fraction: Float, trackAlpha: Float = 0.08f, track: Color = colors.on) {
        edge(at, track.copy(alpha = trackAlpha), width)
        edgeSpan(at, color, width, 0f, fraction)
    }

    /** Calls [block] with each of [n] points spaced evenly round the outline at [at], from the top, clockwise. */
    fun forEachEdgePoint(at: Float, n: Int, block: (Int, Offset) -> Unit) {
        if (n <= 0) return
        measure.setPath(outline.path(at), false)
        val length = measure.length
        for (k in 0 until n) block(k, measure.getPosition((k + 0.5f) / n * length))
    }

    /**
     * Draws [block] as the design's content: scaled round the middle to fit the shape's middle ([CardOutline.contentFit]),
     * so text sizes in it are smaller too.
     */
    fun content(block: () -> Unit) {
        fit = outline.contentFit
        inContent = true
        scope.withTransform({ scale(fit, fit, Offset(MID, MID)) }) { block() }
        inContent = false
        fit = 1f
    }

    /** Whether text [size] units tall would draw large enough to read. */
    fun legible(size: Float): Boolean = size * textScale * unitPx >= minText

    /** Whether an icon [size] units across would draw large enough to read. */
    fun iconLegible(size: Float): Boolean = size * unitPx >= minIcon

    /** [s]'s width at [size] units in [weight]. */
    fun width(s: String, size: Float, weight: Int = W_REGULAR, spacing: Float = 0f): Float {
        val paint = setUp(size, weight, spacing, Color.Unspecified)
        return paint.measureText(s)
    }

    /**
     * Draws [s] with its baseline at [y], centered on [x] (or starting or ending there for [align]), [size] units tall.
     * It is cut short with an ellipsis to [maxWidth]; [main] text shrinks to fit first and always draws, other text
     * steps aside when it would be too small to read. Returns the width drawn (0 when it didn't draw).
     */
    fun text(
        s: String,
        x: Float,
        y: Float,
        size: Float,
        color: Color,
        weight: Int = W_REGULAR,
        align: Paint.Align = Paint.Align.CENTER,
        maxWidth: Float = 150f,
        main: Boolean = false,
        spacing: Float = 0f
    ): Float {
        if (s.isEmpty() || maxWidth <= 0f) return 0f
        if (!main && !legible(size)) return 0f
        val paint = setUp(size, weight, spacing, dim(color))
        paint.textAlign = align
        var w = paint.measureText(s)
        if (main && w > maxWidth) {
            paint.textSize = paint.textSize * max(0.6f, maxWidth / w)
            w = paint.measureText(s)
        }
        val shown = if (w > maxWidth) TextUtils.ellipsize(s, paint, maxWidth, TextUtils.TruncateAt.END).toString() else s
        if (shown.isEmpty()) return 0f
        scope.drawContext.canvas.nativeCanvas.drawText(shown, x, y, paint)
        return if (shown === s) w else paint.measureText(shown)
    }

    /** [s] broken into at most [maxLines] lines no wider than [maxWidth] (the last cut short with an ellipsis). */
    fun lines(s: String, size: Float, maxWidth: Float, maxLines: Int, weight: Int = W_REGULAR): List<String> {
        if (s.isBlank() || maxLines <= 0) return emptyList()
        val paint = setUp(size, weight, 0f, Color.Unspecified)
        val words = s.trim().split(Regex("\\s+"))
        val out = ArrayList<String>()
        var line = ""
        var i = 0
        while (i < words.size) {
            val next = if (line.isEmpty()) words[i] else "$line ${words[i]}"
            if (paint.measureText(next) <= maxWidth || line.isEmpty()) {
                line = next
                i++
            } else {
                out += line
                line = ""
                if (out.size == maxLines - 1) break
            }
        }
        if (out.size < maxLines) {
            val rest = if (i < words.size) (if (line.isEmpty()) "" else "$line ") + words.subList(i, words.size).joinToString(" ") else line
            if (rest.isNotEmpty()) out += TextUtils.ellipsize(rest, paint, maxWidth, TextUtils.TruncateAt.END).toString()
        }
        // A single word wider than the line is cut short too.
        return out.map { if (paint.measureText(it) > maxWidth) TextUtils.ellipsize(it, paint, maxWidth, TextUtils.TruncateAt.END).toString() else it }
            .filter { it.isNotEmpty() }
    }

    /**
     * Draws [s] curved along an arc inside the outline: along the bottom (or the [top]), [inset] of the way from the
     * middle to the shape's inner radius. Call it inside [content].
     */
    fun curved(s: String, size: Float, color: Color, top: Boolean, inset: Float, weight: Int = W_REGULAR, spacing: Float = 0f, main: Boolean = false) {
        if (s.isEmpty()) return
        if (!main && !legible(size)) return
        val radius = 96f
        val ey = if (top) MID - outline.inner * inset else MID + outline.inner * inset
        arc.reset()
        if (top) {
            oval.set(MID - radius, ey, MID + radius, ey + 2 * radius)
            arc.addArc(oval, 228f, 84f)
        } else {
            oval.set(MID - radius, ey - 2 * radius, MID + radius, ey)
            arc.addArc(oval, 132f, -84f)
        }
        val length = (radius * 84f / 180f * PI).toFloat()
        val paint = setUp(size, weight, spacing, dim(color))
        paint.textAlign = Paint.Align.LEFT
        var w = paint.measureText(s)
        val room = length * 0.94f
        if (main && w > room) {
            paint.textSize = paint.textSize * max(0.6f, room / w)
            w = paint.measureText(s)
        }
        val shown = if (w > room) TextUtils.ellipsize(s, paint, room, TextUtils.TruncateAt.END).toString() else s
        if (shown.isEmpty()) return
        val sw = if (shown === s) w else paint.measureText(shown)
        scope.drawContext.canvas.nativeCanvas.drawTextOnPath(shown, arc, (length - sw) / 2f, 0f, paint)
    }

    /** Draws [vector] [size] units across, centered on [cx], [cy], unless it would be too small to read (or [keep]). */
    fun icon(vector: ImageVector, cx: Float, cy: Float, size: Float, color: Color, keep: Boolean = false) {
        if (!keep && !iconLegible(size)) return
        val viewport = vector.viewportWidth.takeIf { it > 0f } ?: 24f
        val path = IconPaths.of(vector)
        scope.translate(cx - size / 2f, cy - size / 2f) {
            scale(size / viewport, Offset.Zero) { drawPath(path, dim(color)) }
        }
    }

    /** Draws [image] filling a square [side] across centered on [cx], [cy] (cropped to its middle), scaled by [zoom]. */
    fun image(image: ImageBitmap, cx: Float, cy: Float, side: Float, zoom: Float = 1f) {
        val w = image.width
        val h = image.height
        if (w <= 0 || h <= 0 || side <= 0f) return
        val s = side / min(w, h) * zoom
        scope.translate(cx - w * s / 2f, cy - h * s / 2f) {
            scale(s, Offset.Zero) { drawImage(image, alpha = alpha) }
        }
    }

    /** Draws [image] in a circle of [radius] at [cx], [cy], unless it would be too small to read. */
    fun imageCircle(image: ImageBitmap, cx: Float, cy: Float, radius: Float) {
        if (!iconLegible(radius * 2f)) return
        val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(cx - radius, cy - radius, cx + radius, cy + radius)) }
        scope.clipPath(clip) { image(image, cx, cy, radius * 2f) }
    }

    /** The Private cover's stripes over the whole square: the cover color with faint diagonal lines every 8 units. */
    fun stripes(base: Color, line: Color) {
        scope.drawRect(base, Offset.Zero, Size(DESIGN, DESIGN))
        scope.rotate(45f, Offset(MID, MID)) {
            var x = -MID
            while (x < DESIGN + MID) {
                drawRect(line, Offset(x, -MID), Size(2f, DESIGN * 2f))
                x += 8f
            }
        }
    }

    private fun setUp(size: Float, weight: Int, spacing: Float, color: Color): TextPaint {
        val paint = type.paint(weight)
        val s = (size * textScale).coerceAtLeast(0.1f)
        paint.textSize = s
        paint.letterSpacing = if (s > 0f) spacing / s else 0f
        if (color != Color.Unspecified) paint.color = color.toArgb()
        return paint
    }
}

/** Mixes [a] toward [b] by [t] (0..1), alpha included. */
internal fun mix(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))

/** The angle on a dial (degrees clockwise from the top) to a point [r] from [cx], [cy]. */
internal fun dialPoint(degrees: Float, r: Float, cx: Float = MID, cy: Float = MID): Offset {
    val a = degrees * PI.toFloat() / 180f
    return Offset(cx + r * sin(a), cy - r * cos(a))
}
