package com.pixel.intelligentsearch.feature.tabs.gallery

import androidx.core.graphics.withSave
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import androidx.core.graphics.withScale
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * How Gallery's wallpapers are drawn: plain Canvas, Paint and Shader calls (no AGSL, so tests draw them too), into a
 * half-float bitmap, then brought down to 8 bits a channel through a one-step dither. Gradients stay smooth on the
 * phone's screen with no bands, and flat colors stay exactly flat, so nothing reads as grain.
 */

private const val TAU = (2 * PI).toFloat()

/** A smooth line of hills or peaks across the picture: its height at any x (px), and the shape from it to the bottom. */
internal class Ridge(val path: Path, private val height: (Float) -> Float) {
    fun y(x: Float): Float = height(x)
}

/** A repeatable wandering line: a few sine waves with phases from [seed]. About -1..1. */
private class Wave(seed: Int, rough: Float, octaves: Int = 4) {
    private val phase = FloatArray(octaves)
    private val freq = FloatArray(octaves)
    private val gain = FloatArray(octaves)

    init {
        val random = java.util.Random(seed.toLong())
        var f = 1f
        var g = 1f
        var sum = 0f
        for (i in 0 until octaves) {
            phase[i] = random.nextFloat() * TAU
            freq[i] = f * (0.85f + 0.3f * random.nextFloat())
            gain[i] = g
            sum += g
            f *= 2.07f
            g *= rough
        }
        for (i in 0 until octaves) gain[i] /= sum
    }

    fun smooth(x: Float): Float {
        var v = 0f
        for (i in phase.indices) v += gain[i] * sin(TAU * freq[i] * x + phase[i])
        return v
    }

    /** Sharp crests instead of round ones: mountains. */
    fun peaked(x: Float): Float {
        var v = 0f
        for (i in phase.indices) v += gain[i] * (2f * (1f - abs(sin(TAU * 0.5f * freq[i] * x + phase[i]))).powf(1.4f) - 1f)
        return v
    }
}

/**
 * What a wallpaper draws with. Positions are shares of the screen ([w] wide, [h] tall, in px): x 0..1 across the screen
 * and past 1 into the extra width the home screen can slide to ([full] px in all), y 0..1 down it. Sizes are in [u],
 * a hundredth of the screen's shorter side, so shapes keep their proportions on any screen.
 */
internal class Scene(val canvas: Canvas, val w: Float, val h: Float, val full: Float) {
    val u: Float = min(w, h) / 100f
    /** How far x runs, in screen widths. */
    val span: Float = full / w
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun fresh(): Paint = paint.apply {
        reset()
        isAntiAlias = true
        isDither = false
    }

    private fun evenly(n: Int) = FloatArray(n) { it / (n - 1f) }

    /** A gradient down the screen from [y0] to [y1], its colors evenly spaced. */
    fun vertical(y0: Float, y1: Float, vararg colors: Int): Shader =
        LinearGradient(0f, y0 * h, 0f, y1 * h, colors, evenly(colors.size), Shader.TileMode.CLAMP)

    fun linear(x0: Float, y0: Float, x1: Float, y1: Float, vararg colors: Int): Shader =
        LinearGradient(x0 * w, y0 * h, x1 * w, y1 * h, colors, evenly(colors.size), Shader.TileMode.CLAMP)

    fun radial(x: Float, y: Float, r: Float, vararg colors: Int): Shader =
        RadialGradient(x * w, y * h, r * u, colors, evenly(colors.size), Shader.TileMode.CLAMP)

    fun sweep(x: Float, y: Float, vararg colors: Int): Shader = SweepGradient(x * w, y * h, colors, evenly(colors.size))

    /** The whole picture washed top to bottom through [stops] (y share to color). */
    fun wash(vararg stops: Pair<Float, Int>) {
        val p = fresh()
        p.shader = LinearGradient(
            0f, 0f, 0f, h, IntArray(stops.size) { stops[it].second }, FloatArray(stops.size) { stops[it].first }, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, full, h, p)
    }

    /** The whole picture under one shader or color (a tint, a veil). */
    fun veil(shader: Shader? = null, color: Int = Color.BLACK, alpha: Float = 1f, mode: BlendMode? = null) {
        val p = fresh()
        p.color = color
        p.shader = shader
        p.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        if (mode != null) p.blendMode = mode
        canvas.drawRect(0f, 0f, full, h, p)
    }

    /**
     * A pool of light: [color] at its center easing out to nothing at [r], with no edge. [squash] flattens it,
     * [hold] keeps the center solid for that share of the radius.
     */
    fun glow(x: Float, y: Float, r: Float, color: Int, alpha: Float = 1f, squash: Float = 1f, hold: Float = 0f, mode: BlendMode? = null) {
        val cx = x * w
        val cy = y * h
        val radius = r * u
        val n = 24
        val red = Color.red(color) / 255f
        val green = Color.green(color) / 255f
        val blue = Color.blue(color) / 255f
        val colors = LongArray(n + 1)
        val stops = FloatArray(n + 1)
        for (i in 0..n) {
            val t = i / n.toFloat()
            val s = ((t - hold) / (1f - hold)).coerceIn(0f, 1f)
            // Eases out with no slope at either end, so the pool has no rim and no hot point.
            val k = 1f - s * s * (3f - 2f * s)
            // Packed with half-float channels: a plain sRGB color long would round the alpha to 8 bits.
            colors[i] = Color.pack(red, green, blue, alpha * k * k, DEEP)
            stops[i] = t
        }
        val p = fresh()
        p.shader = RadialGradient(cx, cy, radius, colors, stops, Shader.TileMode.CLAMP)
        if (mode != null) p.blendMode = mode
        canvas.withScale(1f, squash, cx, cy) { drawCircle(cx, cy, radius, p) }
    }

    fun circle(x: Float, y: Float, r: Float): Path = Path().apply { addCircle(x * w, y * h, r * u, Path.Direction.CW) }

    /** A rounded rectangle [width] by [height] (in [u]) centered on x, y, turned by [turn] degrees. */
    fun roundRect(x: Float, y: Float, width: Float, height: Float, corner: Float, turn: Float = 0f): Path {
        val cx = x * w
        val cy = y * h
        val path = Path()
        path.addRoundRect(cx - width * u / 2, cy - height * u / 2, cx + width * u / 2, cy + height * u / 2, corner * u, corner * u, Path.Direction.CW)
        if (turn != 0f) path.transform(Matrix().apply { setRotate(turn, cx, cy) })
        return path
    }

    /** An ellipse [rx] by [ry] (in [u]) centered on x, y, turned by [turn] degrees: a petal, a cloud, a leaf. */
    fun oval(x: Float, y: Float, rx: Float, ry: Float, turn: Float = 0f): Path {
        val cx = x * w
        val cy = y * h
        val path = Path()
        path.addOval(cx - rx * u, cy - ry * u, cx + rx * u, cy + ry * u, Path.Direction.CW)
        if (turn != 0f) path.transform(Matrix().apply { setRotate(turn, cx, cy) })
        return path
    }

    /**
     * A line of hills at [y] rising and falling by [amp] (shares of the height), leaning by [tilt] across one screen.
     * [stretch] widens the hills, [rough] adds smaller ones, [peaks] sharpens the crests into mountains.
     */
    fun ridge(y: Float, amp: Float, seed: Int, stretch: Float = 1f, rough: Float = 0.45f, tilt: Float = 0f, peaks: Boolean = false): Ridge {
        val wave = Wave(seed, rough)
        val height = { px: Float ->
            val x = px / w
            val n = if (peaks) wave.peaked(x / stretch) else wave.smooth(x / stretch)
            (y + tilt * (x - 0.5f) - amp * n) * h
        }
        val step = u * if (peaks) 0.4f else 0.75f
        val path = Path()
        var x = -step * 2
        var lastX = x
        var lastY = height(x)
        path.moveTo(x, h + step)
        path.lineTo(x, lastY)
        while (x < full + step * 2) {
            x += step
            val ny = height(x)
            // Through the midpoints, so the crest is one curve and never a run of straight pieces.
            path.quadTo(lastX, lastY, (lastX + x) / 2f, (lastY + ny) / 2f)
            lastX = x
            lastY = ny
        }
        path.lineTo(lastX, lastY)
        path.lineTo(lastX, h + step)
        path.close()
        return Ridge(path, height)
    }

    /** Fills [path] with a [shader] or a [color]. [blur] (in [u]) softens its edge; [stroke] draws its outline instead. */
    fun draw(
        path: Path, color: Int = Color.BLACK, shader: Shader? = null, alpha: Float = 1f, blur: Float = 0f,
        stroke: Float = 0f, mode: BlendMode? = null, dx: Float = 0f, dy: Float = 0f
    ) {
        val p = fresh()
        p.color = color
        p.shader = shader
        p.alpha = (Color.alpha(color) * alpha).roundToInt().coerceIn(0, 255)
        if (blur > 0f) p.maskFilter = BlurMaskFilter(blur * u, BlurMaskFilter.Blur.NORMAL)
        if (stroke > 0f) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = stroke * u
            p.strokeCap = Paint.Cap.ROUND
            p.strokeJoin = Paint.Join.ROUND
        }
        if (mode != null) p.blendMode = mode
        if (dx != 0f || dy != 0f) {
            canvas.withTranslation(dx * u, dy * u) { drawPath(path, p) }
        } else canvas.drawPath(path, p)
    }

    /** [color] easing from [alpha] to nothing over [n] steps, with no slope at either end. */
    private fun easing(color: Int, alpha: Float, n: Int): LongArray {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        return LongArray(n + 1) {
            val t = it / n.toFloat()
            Color.pack(r, g, b, alpha * (1f - t * t * (3f - 2f * t)), DEEP)
        }
    }

    /**
     * The shade of a disc or a rounded rectangle (as [roundRect] takes them; a disc is one whose corner is half its
     * side), drawn as its soft edge only: a band [spread] wide either side of the outline, full under the shape and
     * gone beyond it, as a blurred copy would be there. The shape drawn after covers the inner half.
     */
    fun roundShade(
        x: Float, y: Float, width: Float, height: Float, corner: Float, color: Int, alpha: Float, spread: Float,
        turn: Float = 0f, dx: Float = 0f, dy: Float = 0f
    ) {
        val cx = x * w + dx * u
        val cy = y * h + dy * u
        val hw = width * u / 2
        val hh = height * u / 2
        val cr = (corner * u).coerceAtMost(min(hw, hh))
        val reach = spread * u
        val steps = 16
        val fade = easing(color, alpha, steps)
        // Around a corner: full out to where the band starts, then easing out across it.
        val inner = (cr - reach).coerceAtLeast(0f)
        val outer = cr + reach
        val cornerColors = LongArray(steps + 2)
        val cornerStops = FloatArray(steps + 2)
        cornerColors[0] = fade[0]
        cornerStops[0] = 0f
        for (i in 0..steps) {
            // Where the corner is tighter than the band, the fade starts part way in.
            val d = inner + (outer - inner) * i / steps
            val t = ((d - (cr - reach)) / (2 * reach)).coerceIn(0f, 1f)
            cornerColors[i + 1] = fade[(t * steps).roundToInt()]
            cornerStops[i + 1] = d / outer
        }
        val p = fresh()
        // Not smoothed: the pieces meet edge to edge, and a smoothed edge would leave a hairline between them.
        p.isAntiAlias = false
        canvas.withSave {
        if (turn != 0f) canvas.rotate(turn, cx, cy)
        if (hw - cr < 0.5f && hh - cr < 0.5f) {
            // A disc: one ring.
            p.shader = RadialGradient(cx, cy, outer, cornerColors, cornerStops, Shader.TileMode.CLAMP)
            val ring = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                addCircle(cx, cy, outer, Path.Direction.CW)
                if (inner > 1f) addCircle(cx, cy, inner, Path.Direction.CW)
            }
            p.isAntiAlias = true
            canvas.drawPath(ring, p)
        } else {
            val stops = FloatArray(steps + 1) { it / steps.toFloat() }
            for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
                val ox = cx + sx * (hw - cr)
                val oy = cy + sy * (hh - cr)
                p.shader = RadialGradient(ox, oy, outer, cornerColors, cornerStops, Shader.TileMode.CLAMP)
                canvas.drawRect(min(ox, ox + sx * outer), min(oy, oy + sy * outer), maxOf(ox, ox + sx * outer), maxOf(oy, oy + sy * outer), p)
            }
            for (side in intArrayOf(-1, 1)) {
                if (hw - cr > 0.5f) {
                    val edge = cy + side * hh
                    p.shader = LinearGradient(0f, edge - side * reach, 0f, edge + side * reach, fade, stops, Shader.TileMode.CLAMP)
                    canvas.drawRect(cx - (hw - cr), min(edge - side * reach, edge + side * reach), cx + (hw - cr), maxOf(edge - side * reach, edge + side * reach), p)
                }
                if (hh - cr > 0.5f) {
                    val edge = cx + side * hw
                    p.shader = LinearGradient(edge - side * reach, 0f, edge + side * reach, 0f, fade, stops, Shader.TileMode.CLAMP)
                    canvas.drawRect(min(edge - side * reach, edge + side * reach), cy - (hh - cr), maxOf(edge - side * reach, edge + side * reach), cy + (hh - cr), p)
                }
            }
        }
        }
    }

    /**
     * A soft band along a line across the picture, as a strip of triangles: [profile] gives its strength at each of
     * its rows, which lie from [from] to [to] (in [u], down the screen) off the line's height at x.
     */
    private fun band(line: (Float) -> Float, from: Float, to: Float, color: Int, profile: FloatArray, mode: BlendMode?) {
        val rows = profile.size
        val step = u * 2f
        val columns = ((full + 8 * step) / step).toInt() + 1
        val points = FloatArray(columns * rows * 2)
        val colors = IntArray(columns * rows)
        for (i in 0 until columns) {
            val px = -4 * step + i * step
            val py = line(px)
            for (j in 0 until rows) {
                val k = i * rows + j
                points[k * 2] = px
                points[k * 2 + 1] = py + (from + (to - from) * j / (rows - 1f)) * u
                colors[k] = alpha(color, profile[j])
            }
        }
        val indices = ShortArray((columns - 1) * (rows - 1) * 6)
        var n = 0
        for (i in 0 until columns - 1) for (j in 0 until rows - 1) {
            val a = (i * rows + j).toShort()
            val b = ((i + 1) * rows + j).toShort()
            val c = (i * rows + j + 1).toShort()
            val d = ((i + 1) * rows + j + 1).toShort()
            indices[n++] = a; indices[n++] = b; indices[n++] = c
            indices[n++] = b; indices[n++] = d; indices[n++] = c
        }
        val p = fresh()
        p.color = Color.WHITE
        p.isAntiAlias = false
        if (mode != null) p.blendMode = mode
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES, points.size, points, 0, null, 0, colors, 0, indices, 0, indices.size, p)
    }

    /** The shade a sheet whose top edge is [ridge] casts up onto what lies behind it, [reach] (in [u]) high. Draw it before the sheet. */
    fun ridgeShade(ridge: Ridge, color: Int, alpha: Float, reach: Float) {
        val rows = 9
        val profile = FloatArray(rows) {
            // The top row is nothing; the rows at and under the edge are full, and the sheet covers the lowest.
            val t = (it / (rows - 2f)).coerceAtMost(1f)
            alpha * t * t * (3f - 2f * t)
        }
        band(ridge::y, -reach, reach / (rows - 2f), color, profile, null)
    }

    /** A ribbon of light along [line] (a height in px for an x in px), [width] (in [u]) across, brightest down its middle. */
    fun ribbon(line: (Float) -> Float, width: Float, color: Int, alpha: Float, mode: BlendMode? = BlendMode.SCREEN) {
        val rows = 13
        val profile = FloatArray(rows) {
            val t = 1f - kotlin.math.abs(it / (rows - 1f) * 2f - 1f)
            alpha * t * t * (3f - 2f * t)
        }
        band(line, -width / 2, width / 2, color, profile, mode)
    }

    /** The line a ribbon follows: [waves] to a screen, starting at [y] and leaning by [slope] a screen. */
    fun wave(y: Float, slope: Float, amp: Float, waves: Float, phase: Float): (Float) -> Float = { px ->
        val t = px / w
        (y + slope * t + amp * sin(TAU * (waves * t + phase))) * h
    }

    /** Draws only inside [path]. */
    inline fun within(path: Path, block: () -> Unit) {
        canvas.withClip(path) { block() }
    }

    /**
     * Furrows on a field: [count] lines that follow [ridge]'s crest, each lower and wider apart than the last, as
     * rows running away over a hill look. Drawn only on the hill.
     */
    fun furrows(ridge: Ridge, count: Int, color: Int, alpha: Float, first: Float = 1.2f, grow: Float = 1.32f, width: Float = 0.22f) {
        within(ridge.path) {
            var gap = first
            var down = first * 0.6f
            var line = width
            repeat(count) {
                draw(ridge.path, color, alpha = alpha, stroke = line, dy = down)
                gap *= grow
                down += gap
                line *= 1.16f
            }
        }
    }

    companion object {
        /** sRGB with half-float channels, for colors that need finer steps than 8 bits. */
        val DEEP: ColorSpace = ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB)

        fun mix(a: Int, b: Int, t: Float): Int {
            val k = t.coerceIn(0f, 1f)
            fun ch(shift: Int) = ((a shr shift and 0xFF) + ((b shr shift and 0xFF) - (a shr shift and 0xFF)) * k).roundToInt()
            return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        fun alpha(color: Int, a: Float): Int = (color and 0xFFFFFF) or ((a.coerceIn(0f, 1f) * 255).roundToInt() shl 24)
    }
}

/** Draws wallpapers into bitmaps. Safe on any thread; each call owns its bitmaps. */
internal object WallpaperRenderer {
    private const val TILE = 128

    /** The most the dither moves a value either way, in 8-bit steps. */
    private const val REACH = 110

    /**
     * Fixed noise for the dither, as a deep, opaque tile: each channel holds between nothing and [REACH] / 255 of
     * one 8-bit step, a value far too small for an 8-bit tile to hold.
     */
    private val noise: Bitmap by lazy {
        val random = java.util.Random(0x15D17E5L)
        val pixels = IntArray(TILE * TILE) {
            0xFF000000.toInt() or (random.nextInt(REACH + 1) shl 16) or (random.nextInt(REACH + 1) shl 8) or random.nextInt(REACH + 1)
        }
        val coarse = Bitmap.createBitmap(pixels, TILE, TILE, Bitmap.Config.ARGB_8888)
        val fine = Bitmap.createBitmap(null, TILE, TILE, Bitmap.Config.RGBA_F16, false, ColorSpace.get(ColorSpace.Named.SRGB))
        // At one 255th of its strength over black: a 255th of a step for every unit of the 8-bit noise.
        Canvas(fine).drawBitmap(coarse, 0f, 0f, Paint().apply { alpha = 1 })
        coarse.recycle()
        fine
    }

    /**
     * [design] for a screen [screenWidth] by [screenHeight] px, [fullWidth] px wide in all. Returns the part in
     * [region] (px of the whole picture; the whole picture when null), made [scale] times its size.
     */
    fun render(design: Scene.() -> Unit, screenWidth: Int, screenHeight: Int, fullWidth: Int = screenWidth, region: Rect? = null, scale: Float = 1f): Bitmap {
        val part = region ?: Rect(0, 0, fullWidth, screenHeight)
        val width = (part.width() * scale).roundToInt().coerceAtLeast(1)
        val height = (part.height() * scale).roundToInt().coerceAtLeast(1)
        // Half floats, so a blend of many soft layers keeps every in-between value until the last step.
        val deep = Bitmap.createBitmap(null, width, height, Bitmap.Config.RGBA_F16, false, ColorSpace.get(ColorSpace.Named.SRGB))
        val canvas = Canvas(deep)
        canvas.withScale(scale, scale) {
            translate(-part.left.toFloat(), -part.top.toFloat())
            Scene(this, screenWidth.toFloat(), screenHeight.toFloat(), fullWidth.toFloat()).design()
        }

        // The dither. A little noise is added and other noise taken away, each less than half an 8-bit step, and
        // the picture is then rounded to 8 bits. A value between two steps comes out as a mix of both, so a slow
        // gradient has no bands; black stays black, white stays white, and a flat color stays as good as flat.
        fun dice(mode: BlendMode, shiftX: Int, shiftY: Int) = Paint().apply {
            shader = BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
                // Lined up with the whole picture, so a part drawn alone matches the same part cut from the whole.
                val left = if (scale == 1f) part.left else 0
                val top = if (scale == 1f) part.top else 0
                setLocalMatrix(Matrix().apply { setTranslate((shiftX - left).toFloat(), (shiftY - top).toFloat()) })
            }
            blendMode = mode
            isFilterBitmap = false
        }
        canvas.drawPaint(dice(BlendMode.PLUS, 0, 0))
        // Taken away as a difference: the picture is opaque and the noise tiny, so it is a plain subtraction (and
        // on black, where there is nothing to take from, it leaves less than half a step, which rounds to black).
        canvas.drawPaint(dice(BlendMode.DIFFERENCE, 53, 71))
        val out = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val settle = Paint().apply {
            blendMode = BlendMode.SRC
            isDither = false
            isFilterBitmap = false
        }
        Canvas(out).drawBitmap(deep, 0f, 0f, settle)
        deep.recycle()
        out.setHasAlpha(false)
        return out
    }
}

private fun Float.powf(e: Float): Float = this.toDouble().pow(e.toDouble()).toFloat()
