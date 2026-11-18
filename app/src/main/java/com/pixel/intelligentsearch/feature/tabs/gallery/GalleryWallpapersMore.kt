package com.pixel.intelligentsearch.feature.tabs.gallery

import androidx.core.graphics.withSave
import androidx.core.graphics.withSkew
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.PathParser
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/*
 * The wallpapers of the twenty-four looks that came with the swatch fan, drawn on the phone like the others. Each is
 * the approved vector scene (work/wall/wall.mjs) drawn in the 448 x 997 frame it was composed in, scaled to the screen,
 * with the same seeded randomness, gradients, drop shadows and blurs, so the phone draws what the mockup renders.
 */

/** A drawing surface in the scenes' own 448 x 997 frame, with what the scenes are built from. */
private class Svg(val canvas: Canvas) {
    class Stop(val o: Float, val color: Int, val a: Float = 1f)
    sealed class Fill
    class Solid(val color: Int, val a: Float = 1f) : Fill()
    class Lin(val stops: Array<out Stop>, val x1: Float, val y1: Float, val x2: Float, val y2: Float) : Fill()
    class Rad(val stops: Array<out Stop>, val cx: Float, val cy: Float, val r: Float) : Fill()
    class Shadow(val dy: Float, val sigma: Float, val op: Float, val color: Int)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun opaque(rgb: Int) = rgb or 0xFF000000.toInt()

    private fun withAlpha(rgb: Int, a: Float) = (rgb and 0xFFFFFF) or ((a.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 24)

    /** An SVG gradient laid over the shape's own box (objectBoundingBox units), as the scenes use them. */
    private fun shader(f: Fill, box: RectF): Shader? {
        val colors: IntArray
        val pos: FloatArray
        val sh: Shader = when (f) {
            is Solid -> return null
            is Lin -> {
                colors = IntArray(f.stops.size) { withAlpha(f.stops[it].color, f.stops[it].a) }
                pos = FloatArray(f.stops.size) { f.stops[it].o }
                LinearGradient(f.x1, f.y1, f.x2, f.y2, colors, pos, Shader.TileMode.CLAMP)
            }
            is Rad -> {
                colors = IntArray(f.stops.size) { withAlpha(f.stops[it].color, f.stops[it].a) }
                pos = FloatArray(f.stops.size) { f.stops[it].o }
                RadialGradient(f.cx, f.cy, f.r, colors, pos, Shader.TileMode.CLAMP)
            }
        }
        sh.setLocalMatrix(Matrix().apply {
            setScale(max(box.width(), 0.001f), max(box.height(), 0.001f))
            postTranslate(box.left, box.top)
        })
        return sh
    }

    /** Android's blur radius for an SVG standard deviation [sigma]. */
    private fun radius(sigma: Float) = max(0.01f, (sigma - 0.5f) / 0.57735f)

    private fun setUp(f: Fill, box: RectF, opacity: Float, blur: Float) {
        paint.reset()
        paint.isAntiAlias = true
        if (f is Solid) paint.color = withAlpha(f.color, f.a * opacity)
        else {
            paint.color = withAlpha(0x000000, opacity)
            paint.shader = shader(f, box)
        }
        if (blur > 0f) paint.maskFilter = BlurMaskFilter(radius(blur), BlurMaskFilter.Blur.NORMAL)
    }

    private fun drawShadow(path: Path, s: Shadow, style: Paint.Style, width: Float, cap: Paint.Cap) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = style
        paint.strokeWidth = width
        paint.strokeCap = cap
        paint.color = withAlpha(s.color, s.op)
        if (s.sigma > 0f) paint.maskFilter = BlurMaskFilter(radius(s.sigma), BlurMaskFilter.Blur.NORMAL)
        canvas.withTranslation(0f, s.dy) { drawPath(path, paint) }
    }

    fun fill(path: Path, f: Fill, opacity: Float = 1f, shadow: Shadow? = null, blur: Float = 0f) {
        if (shadow != null && shadow.op > 0f) drawShadow(path, shadow, Paint.Style.FILL, 0f, Paint.Cap.BUTT)
        val box = RectF().also { path.computeBounds(it, true) }
        setUp(f, box, opacity, blur)
        canvas.drawPath(path, paint)
    }

    fun stroke(path: Path, f: Fill, width: Float, opacity: Float = 1f, blur: Float = 0f, round: Boolean = false, shadow: Shadow? = null) {
        val cap = if (round) Paint.Cap.ROUND else Paint.Cap.BUTT
        if (shadow != null && shadow.op > 0f) drawShadow(path, shadow, Paint.Style.STROKE, width, cap)
        val box = RectF().also { path.computeBounds(it, true) }
        setUp(f, box, opacity, blur)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.strokeCap = cap
        paint.strokeJoin = if (round) Paint.Join.ROUND else Paint.Join.MITER
        canvas.drawPath(path, paint)
    }

    inline fun turned(deg: Float, px: Float, py: Float, block: () -> Unit) {
        canvas.withRotation(deg, px, py) { block() }
    }

    inline fun skewedX(deg: Float, block: () -> Unit) {
        canvas.withSkew(tan(Math.toRadians(deg.toDouble())).toFloat(), 0f) { block() }
    }

    companion object {
        const val W = 448f
        const val H = 997f

        fun c(rgb: Int) = Solid(rgb)
        fun st(o: Float, rgb: Int, a: Float = 1f) = Stop(o, rgb, a)
        fun lin(vararg s: Stop, x1: Float = 0f, y1: Float = 0f, x2: Float = 0f, y2: Float = 1f) = Lin(s, x1, y1, x2, y2)
        fun radial(vararg s: Stop, cx: Float = 0.5f, cy: Float = 0.5f, r: Float = 0.5f) = Rad(s, cx, cy, r)
        fun shadow(dy: Float = 8f, blur: Float = 14f, op: Float = 0.28f, color: Int = 0x000000) = Shadow(dy, blur, op, color)

        fun rect(x: Float = 0f, y: Float = 0f, w: Float = W, h: Float = H, rx: Float = 0f) = Path().apply {
            if (rx > 0f) addRoundRect(x, y, x + w, y + h, rx, rx, Path.Direction.CW) else addRect(x, y, x + w, y + h, Path.Direction.CW)
        }

        fun circle(cx: Float, cy: Float, r: Float) = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        fun d(data: String): Path = PathParser.createPathFromPathData(data)

        /** The scenes' seeded randomness (mulberry32), so every ridge, star and tree lands where the mockup drew it. */
        fun rng(seed: Int): () -> Float {
            var s = seed
            return {
                s += 0x6D2B79F5
                var t = (s xor (s ushr 15)) * (1 or s)
                t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
                ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
            }
        }

        /** A smooth line through [pts] (Catmull-Rom as cubics). */
        fun smooth(pts: List<FloatArray>): Path = Path().apply {
            moveTo(pts[0][0], pts[0][1])
            for (i in 0 until pts.size - 1) {
                val p0 = pts[max(0, i - 1)]
                val p1 = pts[i]
                val p2 = pts[i + 1]
                val p3 = pts[min(pts.size - 1, i + 2)]
                cubicTo(
                    p1[0] + (p2[0] - p0[0]) / 6f, p1[1] + (p2[1] - p0[1]) / 6f,
                    p2[0] - (p3[0] - p1[0]) / 6f, p2[1] - (p3[1] - p1[1]) / 6f,
                    p2[0], p2[1]
                )
            }
        }

        /** A smooth band across the width from the line [pts] down to the bottom. */
        fun band(pts: List<FloatArray>): Path = smooth(pts).apply {
            lineTo(W + 20f, H + 20f)
            lineTo(-20f, H + 20f)
            close()
        }

        fun ridge(y: Float, amp: Float, n: Int, r: () -> Float, peak: Float = 0f, px: Float = 0.5f): List<FloatArray> = List(n + 1) { i ->
            val x = -20f + (W + 40f) * i / n
            val u = i.toFloat() / n
            floatArrayOf(x, y + (r() - 0.5f) * amp - peak * max(0f, 1f - abs(u - px) * 3.2f).pow(1.6f))
        }

        fun wave(y: Float, amp: Float, len: Float, ph: Float, n: Int = 24, amp2: Float = 0f): List<FloatArray> = List(n + 1) { i ->
            val x = -20f + (W + 40f) * i / n
            floatArrayOf(x, y + amp * sin(x / len + ph) + amp2 * sin(x / len * 2.3f + ph * 1.7f))
        }

        fun pine(x: Float, y: Float, s: Float): Path = Path().apply {
            moveTo(x, y - s)
            lineTo(x + s * 0.32f, y - s * 0.45f); lineTo(x + s * 0.16f, y - s * 0.45f); lineTo(x + s * 0.42f, y)
            lineTo(x - s * 0.42f, y); lineTo(x - s * 0.16f, y - s * 0.45f); lineTo(x - s * 0.32f, y - s * 0.45f)
            close()
        }

        fun cloud(x: Float, y: Float, s: Float): Path = Path().apply {
            addCircle(x, y, s * 0.5f, Path.Direction.CW)
            addCircle(x + s * 0.55f, y - s * 0.22f, s * 0.62f, Path.Direction.CW)
            addCircle(x + s * 1.2f, y, s * 0.46f, Path.Direction.CW)
            addRoundRect(x - s * 0.5f, y, x - s * 0.5f + s * 2.16f, y + s * 0.5f, s * 0.25f, s * 0.25f, Path.Direction.CW)
        }
    }

    fun stars(r: () -> Float, n: Int, y1: Float, color: Int = 0xFFFFFF) {
        repeat(n) {
            val s = r()
            val cx = r() * W
            val cy = r() * y1
            fill(circle(cx, cy, 0.5f + s * 1.3f), c(color), opacity = 0.35f + r() * 0.65f)
        }
    }

    fun tree(x: Float, y: Float, s: Float, leaf: Int, dark: Int, trunk: Int = 0x5A3B22) {
        fill(rect(x - s * 0.07f, y - s * 0.5f, s * 0.14f, s * 0.55f, s * 0.05f), c(trunk))
        fill(circle(x, y - s * 0.78f, s * 0.46f), c(dark))
        fill(circle(x - s * 0.1f, y - s * 0.86f, s * 0.36f), c(leaf))
    }
}

/** Draws [scene] for a [Scene]'s screen: the 448 x 997 frame scaled to it, mirrored into any width past it. */
private fun Scene.svg(scene: Svg.() -> Unit) {
    val sx = w / Svg.W
    val sy = h / Svg.H
    val copies = ceil(full / w).toInt().coerceAtLeast(1)
    for (k in 0 until copies) {
        canvas.withSave {
            if (k % 2 == 0) translate(k * w, 0f) else {
                translate((k + 1) * w, 0f)
                scale(-1f, 1f)
            }
            scale(sx, sy)
            clipRect(-1f, -1f, Svg.W + 1f, Svg.H + 1f)
            Svg(this).scene()
        }
    }
}

/* ── Your Colors ── */

private val tide: Svg.() -> Unit = {
    val sh = Svg.shadow(-6f, 12f, 0.22f)
    fill(Svg.rect(), Svg.lin(Svg.st(0f, 0x0B2A33), Svg.st(0.45f, 0x12505C), Svg.st(1f, 0x062028)))
    fill(Svg.circle(330f, 170f, 150f), Svg.radial(Svg.st(0f, 0x7FE3E6, 0.5f), Svg.st(1f, 0x7FE3E6, 0f)))
    fill(Svg.circle(330f, 170f, 44f), Svg.c(0xD8FBF6))
    val rows = arrayOf(
        floatArrayOf(330f, 34f, 150f, 0.6f) to 0x1B7F8C, floatArrayOf(430f, 30f, 170f, 2.1f) to 0x16697A, floatArrayOf(540f, 26f, 130f, 0.4f) to 0x115668,
        floatArrayOf(650f, 24f, 190f, 3.3f) to 0x0D4556, floatArrayOf(770f, 20f, 150f, 1.2f) to 0x093646, floatArrayOf(880f, 16f, 210f, 5.0f) to 0x062A38
    )
    for ((v, col) in rows) fill(Svg.band(Svg.wave(v[0], v[1], v[2], v[3], 24, v[1] * 0.3f)), Svg.c(col), shadow = sh)
    stroke(Svg.smooth(Svg.wave(352f, 34f, 150f, 0.6f, 24, 10f)), Svg.c(0xBFF3EE), 2.5f, opacity = 0.5f)
}

private val clay: Svg.() -> Unit = {
    val sh = Svg.shadow(10f, 16f, 0.3f, 0x4A1D0C)
    fun arch(cx: Float, by: Float, w: Float, h: Float, f: Int) {
        val r = w / 2f
        fill(Svg.d("M${cx - r} ${by}L${cx - r} ${by - h + r}A$r $r 0 0 1 ${cx + r} ${by - h + r}L${cx + r} ${by}Z"), Svg.c(f), shadow = sh)
    }
    fill(Svg.rect(), Svg.lin(Svg.st(0f, 0xF6DCC6), Svg.st(1f, 0xE9B998)))
    val arches = arrayOf(560f to 980f, 460f to 860f, 360f to 730f, 262f to 600f, 168f to 470f, 84f to 350f)
    val tones = intArrayOf(0xB5562F, 0xC56A3F, 0xD68253, 0xE39C6E, 0xEFB98F, 0xF8D9BB)
    for (k in arches.indices) arch(224f, 1010f, arches[k].first, arches[k].second, tones[k])
    fill(Svg.circle(224f, 742f, 24f), Svg.c(0xFFF3E4))
}

private val meadow: Svg.() -> Unit = {
    val sh = Svg.shadow(8f, 18f, 0.2f, 0x17350F)
    fill(Svg.rect(), Svg.lin(Svg.st(0f, 0xF1F7C8), Svg.st(1f, 0xBFE39A)))
    fill(Svg.circle(120f, 190f, 200f), Svg.lin(Svg.st(0f, 0xFFF6A8), Svg.st(1f, 0xF6DE62)), shadow = sh)
    fill(Svg.circle(390f, 420f, 230f), Svg.lin(Svg.st(0f, 0xA9DD7C), Svg.st(1f, 0x6FBB52)), shadow = sh)
    fill(Svg.circle(60f, 720f, 300f), Svg.lin(Svg.st(0f, 0x5FAE4C), Svg.st(1f, 0x2F7D3A)), shadow = sh)
    fill(Svg.circle(420f, 960f, 280f), Svg.lin(Svg.st(0f, 0x2F7D3A), Svg.st(1f, 0x195A2C)), shadow = sh)
    fill(Svg.circle(300f, 250f, 34f), Svg.c(0xFFFFFF), shadow = sh)
}

/* ── Light ── */

private val linen: Svg.() -> Unit = {
    val r = Svg.rng(7)
    fill(Svg.rect(), Svg.lin(Svg.st(0f, 0xFBF7EF), Svg.st(1f, 0xEFE6D6)))
    fill(Svg.circle(322f, 210f, 120f), Svg.radial(Svg.st(0f, 0xFFFFFF, 0.9f), Svg.st(1f, 0xFFFFFF, 0f)))
    fill(Svg.circle(322f, 210f, 46f), Svg.c(0xFFFDF8), shadow = Svg.shadow(4f, 10f, 0.12f, 0x7A6442))
    for (k in 0 until 15) {
        val y = 330f + k * 46f
        stroke(Svg.smooth(Svg.wave(y, 16f + k * 1.2f, 120f + k * 9f, k * 0.8f + r() * 2f, 24, 7f)), Svg.c(0xB9A889), 1.6f, opacity = 0.24f + k * 0.028f)
    }
}

private val sky: Svg.() -> Unit = {
    val sh = Svg.shadow(6f, 12f, 0.1f, 0x27507A)
    fill(Svg.rect(), Svg.lin(Svg.st(0f, 0x8FC4F4), Svg.st(0.55f, 0xCFE7FB), Svg.st(1f, 0xF4FAFF)))
    fill(Svg.circle(110f, 150f, 170f), Svg.radial(Svg.st(0f, 0xFFFFFF, 0.8f), Svg.st(1f, 0xFFFFFF, 0f)))
    fill(Svg.circle(110f, 150f, 40f), Svg.c(0xFFFFFF))
    fill(Svg.cloud(250f, 250f, 66f), Svg.c(0xFFFFFF), shadow = sh)
    fill(Svg.cloud(40f, 430f, 86f), Svg.c(0xFFFFFF), shadow = sh)
    fill(Svg.cloud(270f, 590f, 54f), Svg.c(0xFFFFFF), opacity = 0.95f, shadow = sh)
    fill(Svg.band(Svg.ridge(790f, 40f, 9, Svg.rng(4), 60f, 0.7f)), Svg.c(0xB9D6EE))
    fill(Svg.band(Svg.ridge(860f, 34f, 8, Svg.rng(9))), Svg.c(0x9CC3E4))
    fill(Svg.band(Svg.ridge(930f, 26f, 7, Svg.rng(2))), Svg.c(0x7FB0DA))
}

private val peach: Svg.() -> Unit = {
    fun bubble(cx: Float, cy: Float, r: Float, col: Int) {
        fill(Svg.circle(cx, cy, r), Svg.radial(Svg.st(0f, 0xFFFFFF, 0.55f), Svg.st(0.7f, col, 0.35f), Svg.st(1f, 0xFFFFFF, 0.75f), cx = 0.35f, cy = 0.3f, r = 0.75f))
        fill(Svg.circle(cx - r * 0.32f, cy - r * 0.36f, r * 0.16f), Svg.c(0xFFFFFF), opacity = 0.8f)
    }
    fill(Svg.rect(), Svg.lin(Svg.st(0f, 0xFFE9D6), Svg.st(0.5f, 0xFFC9B3), Svg.st(1f, 0xFFAB9C)))
    fill(Svg.circle(360f, 140f, 260f), Svg.radial(Svg.st(0f, 0xFFF6C9, 0.8f), Svg.st(1f, 0xFFF6C9, 0f)))
    bubble(320f, 230f, 96f, 0xFFD0A8); bubble(110f, 380f, 132f, 0xFFB9A6); bubble(350f, 600f, 74f, 0xFFD7C2)
    bubble(150f, 720f, 54f, 0xFFC2B0); bubble(300f, 860f, 110f, 0xFF9F92); bubble(60f, 150f, 40f, 0xFFE0C8)
}

private val mint: Svg.() -> Unit = {
    val sh = Svg.shadow(10f, 14f, 0.2f, 0x0F4A36)
    fill(Svg.rect(), Svg.c(0x3F9C7D))
    val tiles = arrayOf(1500f to 0x3F9C7D, 1180f to 0x52AD8D, 900f to 0x69BD9E, 660f to 0x84CDB0, 450f to 0xA3DCC4, 280f to 0xC4EAD8, 140f to 0xE6F8EE)
    val turns = floatArrayOf(12f, 16f, 20f, 24f, 28f, 32f, 36f)
    for (k in tiles.indices) {
        val s = tiles[k].first
        turned(turns[k], 224f, 330f) { fill(Svg.rect(224f - s / 2f, 330f - s / 2f, s, s, s * 0.3f), Svg.c(tiles[k].second), shadow = sh) }
    }
}

/* ── Dark ── */

private val midnight: Svg.() -> Unit = {
    val r = Svg.rng(21)
    fill(Svg.rect(), Svg.lin(Svg.st(0f, 0x050A1C), Svg.st(0.6f, 0x101E44), Svg.st(1f, 0x1D2F66)))
    stars(r, 110, 640f)
    fill(Svg.circle(318f, 200f, 120f), Svg.radial(Svg.st(0f, 0xC9D6FF, 0.35f), Svg.st(1f, 0xC9D6FF, 0f)))
    fill(Svg.d("M318 152a48 48 0 1 0 34 82a40 40 0 1 1 -34 -82z"), Svg.c(0xEEF2FF))
    stroke(Svg.d("M40 260L150 205"), Svg.c(0xFFFFFF), 1.6f, opacity = 0.7f, round = true)
    fill(Svg.band(Svg.ridge(720f, 60f, 9, r, 120f, 0.3f)), Svg.c(0x0C1638))
    fill(Svg.band(Svg.ridge(820f, 40f, 8, r)), Svg.c(0x081029))
    fill(Svg.band(Svg.ridge(910f, 30f, 7, r)), Svg.c(0x040A1C))
}

private val ember: Svg.() -> Unit = {
    val r = Svg.rng(5)
    val sh = Svg.shadow(-8f, 14f, 0.45f)
    fill(Svg.rect(), Svg.lin(Svg.st(0f, 0x0D0605), Svg.st(0.5f, 0x2A0D07), Svg.st(1f, 0x090403)))
    fill(Svg.circle(224f, 430f, 330f), Svg.radial(Svg.st(0f, 0xFF7A3C, 0.55f), Svg.st(0.5f, 0xE0552A, 0.18f), Svg.st(1f, 0xE0552A, 0f)))
    fill(Svg.circle(224f, 430f, 120f), Svg.radial(Svg.st(0f, 0xFFE2B0), Svg.st(0.6f, 0xFF9D4D), Svg.st(1f, 0xE0552A)))
    val rows = arrayOf(floatArrayOf(470f, 50f) to 0x5A1A0D, floatArrayOf(560f, 44f) to 0x431209, floatArrayOf(660f, 40f) to 0x300C06, floatArrayOf(770f, 34f) to 0x200805, floatArrayOf(880f, 28f) to 0x130504)
    rows.forEachIndexed { k, (v, col) -> fill(Svg.band(Svg.wave(v[0], v[1], 150f + k * 30f, k * 1.9f + 1f, 24, v[1] * 0.3f)), Svg.c(col), shadow = sh) }
    stars(r, 26, 300f, 0xFFC9A0)
}

private val forest: Svg.() -> Unit = {
    val r = Svg.rng(12)
    fun row(y: Float, n: Int, s: Float, col: Int) {
        for (i in 0 until n) {
            val x = -10f + (Svg.W + 20f) * (i + r() * 0.7f) / n
            val yy = y + r() * 16f
            val ss = s * (0.75f + r() * 0.5f)
            fill(Svg.pine(x, yy, ss), Svg.c(col))
        }
    }
    fill(Svg.rect(), Svg.lin(Svg.st(0f, 0x0B1A14), Svg.st(0.5f, 0x15382B), Svg.st(1f, 0x0A1712)))
    fill(Svg.circle(130f, 210f, 190f), Svg.radial(Svg.st(0f, 0xCFEEDD, 0.28f), Svg.st(1f, 0xCFEEDD, 0f)))
    fill(Svg.circle(130f, 210f, 38f), Svg.c(0xE9F7EE))
    fill(Svg.band(Svg.ridge(470f, 50f, 8, r, 130f, 0.68f)), Svg.c(0x1D4A3A))
    fill(Svg.rect(0f, 430f, Svg.W, 150f), Svg.lin(Svg.st(0f, 0xB9E3C5, 0f), Svg.st(1f, 0xB9E3C5, 0.16f)))
    row(600f, 9, 86f, 0x173D2F)
    fill(Svg.rect(0f, 560f, Svg.W, 150f), Svg.lin(Svg.st(0f, 0xB9E3C5, 0f), Svg.st(1f, 0xB9E3C5, 0.13f)))
    fill(Svg.band(Svg.ridge(690f, 20f, 6, r)), Svg.c(0x12301F))
    row(760f, 7, 124f, 0x0F2A1D)
    fill(Svg.band(Svg.ridge(850f, 18f, 6, r)), Svg.c(0x0B2016))
    row(940f, 5, 170f, 0x07160F)
    fill(Svg.band(Svg.ridge(960f, 10f, 5, r)), Svg.c(0x050F0A))
}

private val carbon: Svg.() -> Unit = {
    fill(Svg.rect(), Svg.c(0x141416))
    turned(32f, 224f, 498f) {
        for (k in -8 until 16) fill(Svg.rect(k * 64f, -300f, 36f, 1700f), Svg.lin(Svg.st(0f, 0x2A2A2E), Svg.st(1f, 0x1B1B1E), x2 = 1f, y2 = 0f))
    }
    fill(Svg.circle(224f, 300f, 300f), Svg.radial(Svg.st(0f, 0x9AA0B0, 0.22f), Svg.st(1f, 0x9AA0B0, 0f)))
    stroke(Svg.circle(224f, 300f, 94f), Svg.lin(Svg.st(0f, 0xF2F2F6), Svg.st(1f, 0x5C5C66)), 10f)
    fill(Svg.circle(224f, 300f, 60f), Svg.lin(Svg.st(0f, 0x3A3A40), Svg.st(1f, 0x18181B)))
    fill(Svg.rect(0f, 560f, Svg.W, 437f), Svg.lin(Svg.st(0f, 0x000000, 0f), Svg.st(1f, 0x000000, 0.55f)))
}

/* ── Glass ── */

private fun Svg.ribbons(y: Float, col: Int, w: Float, op: Float, ph: Float, amp: Float, len: Float, amp2: Float, soft: Float) =
    stroke(Svg.smooth(Svg.wave(y, amp, len, ph, 24, amp2)), Svg.c(col), w, opacity = op, blur = w / soft)

private val lagoon: Svg.() -> Unit = {
    fill(Svg.rect(), Svg.lin(Svg.st(0f, 0x0B6F86), Svg.st(0.5f, 0x19B3A6), Svg.st(1f, 0x0D5F7A)))
    fill(Svg.circle(90f, 180f, 170f), Svg.c(0xB6F36A), opacity = 0.75f, blur = 46f)
    fill(Svg.circle(400f, 380f, 190f), Svg.c(0x31D7E8), opacity = 0.8f, blur = 46f)
    fill(Svg.circle(120f, 660f, 210f), Svg.c(0x19E0B0), opacity = 0.7f, blur = 46f)
    fill(Svg.circle(380f, 900f, 200f), Svg.c(0x1273C4), opacity = 0.8f, blur = 46f)
    ribbons(300f, 0xEAFFD8, 30f, 0.28f, 0.4f, 46f, 170f, 16f, 5f)
    ribbons(560f, 0xD8FFFA, 44f, 0.22f, 2.2f, 46f, 170f, 16f, 5f)
    ribbons(790f, 0xC8F4FF, 26f, 0.25f, 4.1f, 46f, 170f, 16f, 5f)
    stroke(Svg.smooth(Svg.wave(300f, 46f, 170f, 0.4f, 24, 16f)), Svg.c(0xFFFFFF), 2f, opacity = 0.45f)
    stroke(Svg.smooth(Svg.wave(790f, 46f, 170f, 4.1f, 24, 16f)), Svg.c(0xFFFFFF), 1.6f, opacity = 0.35f)
}

private val rose: Svg.() -> Unit = {
    fill(Svg.rect(), Svg.lin(Svg.st(0f, 0xFFD3DE), Svg.st(0.5f, 0xFF9FB9), Svg.st(1f, 0xF6C1A0)))
    fill(Svg.circle(360f, 160f, 180f), Svg.c(0xFFF1C4), opacity = 0.9f, blur = 50f)
    fill(Svg.circle(60f, 380f, 190f), Svg.c(0xFF7FA8), opacity = 0.75f, blur = 50f)
    fill(Svg.circle(400f, 640f, 200f), Svg.c(0xFFB38A), opacity = 0.8f, blur = 50f)
    fill(Svg.circle(110f, 900f, 210f), Svg.c(0xE86AA6), opacity = 0.7f, blur = 50f)
    ribbons(260f, 0xFFFFFF, 34f, 0.3f, 1.2f, 52f, 190f, 18f, 5f)
    ribbons(520f, 0xFFE8F0, 46f, 0.26f, 3.0f, 52f, 190f, 18f, 5f)
    ribbons(770f, 0xFFF4E0, 28f, 0.28f, 5.2f, 52f, 190f, 18f, 5f)
    stroke(Svg.smooth(Svg.wave(520f, 52f, 190f, 3.0f, 24, 18f)), Svg.c(0xFFFFFF), 2f, opacity = 0.5f)
}

private val smoke: Svg.() -> Unit = {
    fill(Svg.rect(), Svg.lin(Svg.st(0f, 0x15171D), Svg.st(0.5f, 0x272B38), Svg.st(1f, 0x101116)))
    fill(Svg.circle(330f, 200f, 170f), Svg.c(0x6F7FA8), opacity = 0.6f, blur = 52f)
    fill(Svg.circle(70f, 470f, 190f), Svg.c(0x8A8FA0), opacity = 0.4f, blur = 52f)
    fill(Svg.circle(380f, 720f, 200f), Svg.c(0x4D5A80), opacity = 0.6f, blur = 52f)
    ribbons(240f, 0xE8ECFF, 40f, 0.16f, 0.9f, 58f, 200f, 20f, 4f)
    ribbons(480f, 0xCFD6F0, 60f, 0.13f, 2.6f, 58f, 200f, 20f, 4f)
    ribbons(720f, 0xE8ECFF, 34f, 0.15f, 4.4f, 58f, 200f, 20f, 4f)
    stroke(Svg.smooth(Svg.wave(480f, 58f, 200f, 2.6f, 24, 20f)), Svg.c(0xFFFFFF), 1.8f, opacity = 0.28f)
}

/* ── Bold ── */

private val signal: Svg.() -> Unit = {
    fill(Svg.rect(), Svg.c(0xFFD92E))
    skewedX(-28f) { for (k in -4 until 12) fill(Svg.rect(k * 96f, 640f, 48f, 600f), Svg.c(0x141414)) }
    fill(Svg.rect(0f, 0f, Svg.W, 660f), Svg.c(0xFFD92E))
    fill(Svg.rect(0f, 648f, Svg.W, 16f), Svg.c(0x141414))
    fill(Svg.circle(224f, 300f, 176f), Svg.c(0x141414))
    fill(Svg.circle(224f, 300f, 126f), Svg.c(0xFFD92E))
    fill(Svg.circle(224f, 300f, 76f), Svg.c(0x141414))
    fill(Svg.circle(224f, 300f, 30f), Svg.c(0xFFD92E))
}

private val cobalt: Svg.() -> Unit = {
    val sh = Svg.shadow(12f, 18f, 0.28f, 0x07124A)
    fill(Svg.rect(), Svg.c(0x1F46D6))
    fill(Svg.circle(20f, 120f, 210f), Svg.c(0xFF7A2F), shadow = sh)
    turned(24f, 325f, 270f) { fill(Svg.rect(250f, 60f, 150f, 420f, 75f), Svg.c(0xE6ECFF), shadow = sh) }
    fill(Svg.circle(370f, 610f, 150f), Svg.c(0x0D1F73), shadow = sh)
    fill(Svg.d("M-20 1020A300 300 0 0 1 280 720L280 1020Z"), Svg.c(0xFFD23A), shadow = sh)
    fill(Svg.circle(150f, 520f, 34f), Svg.c(0xFFFFFF), shadow = sh)
}

private val pop: Svg.() -> Unit = {
    val sh = Svg.shadow(10f, 14f, 0.22f, 0x5A0A2C)
    fill(Svg.rect(), Svg.c(0xFF4F8B))
    for (y in 0 until 8) for (x in 0 until 6) fill(Svg.circle(250f + x * 30f, 60f + y * 30f, 5.5f), Svg.c(0xFFFFFF), opacity = 0.9f)
    fill(Svg.circle(110f, 330f, 150f), Svg.c(0x19C8B9), shadow = sh)
    fill(Svg.d("M448 480A230 230 0 0 0 218 710L448 710Z"), Svg.c(0xFFD84A), shadow = sh)
    turned(-18f, 145f, 668f) { fill(Svg.rect(40f, 620f, 210f, 96f, 48f), Svg.c(0x3A2BD6), shadow = sh) }
    stroke(Svg.d("M60 940q28 -46 56 0t56 0t56 0t56 0t56 0t56 0"), Svg.c(0xFFFFFF), 12f, round = true, shadow = sh)
    fill(Svg.circle(330f, 880f, 44f), Svg.c(0x141414), shadow = sh)
}

private val stencil: Svg.() -> Unit = {
    fill(Svg.rect(), Svg.c(0xF4F0E6))
    turned(-24f, 224f, 498f) {
        fill(Svg.rect(-300f, 110f, 1100f, 150f), Svg.c(0x111111))
        fill(Svg.rect(-300f, 300f, 1100f, 46f), Svg.c(0x111111))
        fill(Svg.rect(-300f, 386f, 1100f, 230f), Svg.c(0xD93A2B))
        fill(Svg.rect(-300f, 656f, 1100f, 46f), Svg.c(0x111111))
        fill(Svg.rect(-300f, 742f, 1100f, 150f), Svg.c(0x111111))
    }
    fill(Svg.circle(312f, 250f, 92f), Svg.c(0xF4F0E6))
    fill(Svg.circle(312f, 250f, 58f), Svg.c(0x111111))
}

/* ── Quiet ── */

private val fog: Svg.() -> Unit = {
    val r = Svg.rng(31)
    fill(Svg.rect(), Svg.lin(Svg.st(0f, 0xAEB4BD), Svg.st(0.5f, 0xC9CCD2), Svg.st(1f, 0x8F96A2)))
    fill(Svg.circle(300f, 240f, 150f), Svg.radial(Svg.st(0f, 0xFFFFFF, 0.55f), Svg.st(1f, 0xFFFFFF, 0f)))
    fill(Svg.circle(300f, 240f, 34f), Svg.c(0xF4F5F7), opacity = 0.9f)
    fill(Svg.band(Svg.ridge(520f, 50f, 8, r, 110f, 0.35f)), Svg.c(0xA2A8B3))
    fill(Svg.rect(0f, 470f, Svg.W, 170f), Svg.lin(Svg.st(0f, 0xD6D9DE, 0f), Svg.st(1f, 0xD6D9DE, 0.75f)))
    fill(Svg.band(Svg.ridge(650f, 40f, 7, r, 60f, 0.7f)), Svg.c(0x8D94A1))
    fill(Svg.rect(0f, 610f, Svg.W, 170f), Svg.lin(Svg.st(0f, 0xC6CAD1, 0f), Svg.st(1f, 0xC6CAD1, 0.7f)))
    fill(Svg.band(Svg.ridge(790f, 30f, 6, r)), Svg.c(0x77808E))
    fill(Svg.rect(0f, 760f, Svg.W, 160f), Svg.lin(Svg.st(0f, 0xB0B6C0, 0f), Svg.st(1f, 0xB0B6C0, 0.6f)))
    fill(Svg.band(Svg.ridge(910f, 20f, 6, r)), Svg.c(0x626B7A))
}

private val sand: Svg.() -> Unit = {
    val sh = Svg.shadow(-7f, 14f, 0.16f, 0x5A4022)
    fill(Svg.rect(), Svg.lin(Svg.st(0f, 0xF3E6CF), Svg.st(1f, 0xE7D3B1)))
    fill(Svg.circle(110f, 190f, 130f), Svg.radial(Svg.st(0f, 0xFFF8E6, 0.9f), Svg.st(1f, 0xFFF8E6, 0f)))
    fill(Svg.circle(110f, 190f, 40f), Svg.c(0xFFFAF0))
    val rows = arrayOf(
        floatArrayOf(420f, 60f, 230f, 0.2f) to 0xE3CAA0, floatArrayOf(540f, 54f, 260f, 2.4f) to 0xD9BB8B, floatArrayOf(660f, 48f, 210f, 4.0f) to 0xCDAA76,
        floatArrayOf(780f, 40f, 280f, 1.1f) to 0xBF9862, floatArrayOf(890f, 30f, 240f, 3.2f) to 0xAE8650
    )
    for ((v, col) in rows) fill(Svg.band(Svg.wave(v[0], v[1], v[2], v[3], 24, v[1] * 0.2f)), Svg.c(col), shadow = sh)
}

private val dusk: Svg.() -> Unit = {
    val r = Svg.rng(44)
    fill(Svg.rect(), Svg.lin(Svg.st(0f, 0x1D1A33), Svg.st(0.42f, 0x4A3A6B), Svg.st(0.62f, 0xA66F8F), Svg.st(0.74f, 0xE3A58F), Svg.st(1f, 0x2A2240)))
    stars(r, 40, 330f, 0xEFE6FF)
    fill(Svg.circle(300f, 210f, 20f), Svg.c(0xF4ECFF))
    fill(Svg.rect(60f, 560f, 260f, 5f, 2.5f), Svg.c(0xF6C9B0), opacity = 0.55f)
    fill(Svg.rect(210f, 610f, 200f, 4f, 2f), Svg.c(0xF6C9B0), opacity = 0.4f)
    fill(Svg.band(Svg.ridge(760f, 44f, 8, r, 70f, 0.25f)), Svg.c(0x3B2F58))
    fill(Svg.band(Svg.ridge(840f, 30f, 7, r)), Svg.c(0x2C2346))
    fill(Svg.band(Svg.ridge(920f, 22f, 6, r)), Svg.c(0x1E1832))
}

/* ── Seasonal ── */

private val maple: Svg.() -> Unit = {
    val r = Svg.rng(17)
    fun grove(y: Float, n: Int, s: Float) {
        repeat(n) {
            val k = r()
            val x = 20f + r() * 410f
            val yy = y + r() * 30f
            val ss = s * (0.8f + r() * 0.5f)
            val leaf = if (k < 0.34f) 0xE6472E else if (k < 0.67f) 0xF08A2C else 0xC9312A
            val dark = if (k < 0.34f) 0xB5301F else if (k < 0.67f) 0xC9661A else 0x93201D
            tree(x, yy, ss, leaf, dark)
        }
    }
    fill(Svg.rect(), Svg.lin(Svg.st(0f, 0xFFE9D2), Svg.st(0.55f, 0xFFC98F), Svg.st(1f, 0xF6A25E)))
    fill(Svg.circle(330f, 250f, 150f), Svg.radial(Svg.st(0f, 0xFFF3C9, 0.9f), Svg.st(1f, 0xFFF3C9, 0f)))
    fill(Svg.circle(330f, 250f, 46f), Svg.c(0xFFF7E0))
    fill(Svg.band(Svg.ridge(600f, 40f, 9, r, 90f, 0.3f)), Svg.c(0xE9A86D))
    fill(Svg.band(Svg.ridge(660f, 26f, 8, r)), Svg.c(0xD98445))
    grove(700f, 7, 40f)
    fill(Svg.band(Svg.ridge(760f, 24f, 7, r)), Svg.c(0xC0632C))
    grove(800f, 6, 62f)
    fill(Svg.band(Svg.ridge(870f, 20f, 6, r)), Svg.c(0x8F3F1C))
    grove(915f, 4, 90f)
    fill(Svg.band(Svg.ridge(950f, 14f, 6, r)), Svg.c(0x5E2612))
}

private val aurora: Svg.() -> Unit = {
    val r = Svg.rng(9)
    fill(Svg.rect(), Svg.lin(Svg.st(0f, 0x040B1C), Svg.st(0.6f, 0x0C2540), Svg.st(1f, 0x12324A)))
    stars(r, 90, 560f)
    ribbons(210f, 0x39F0B0, 110f, 0.75f, 0.6f, 70f, 150f, 26f, 3.2f)
    ribbons(310f, 0x5AD0FF, 80f, 0.55f, 1.9f, 70f, 150f, 26f, 3.2f)
    ribbons(410f, 0x9A7BFF, 70f, 0.45f, 3.3f, 70f, 150f, 26f, 3.2f)
    ribbons(186f, 0xE6FFF5, 16f, 0.8f, 0.6f, 70f, 150f, 26f, 3.2f)
    ribbons(296f, 0xDFF6FF, 10f, 0.5f, 1.9f, 70f, 150f, 26f, 3.2f)
    fill(Svg.band(Svg.ridge(700f, 50f, 9, r, 120f, 0.62f)), Svg.c(0xC9DCEE))
    fill(Svg.band(Svg.ridge(770f, 30f, 8, r)), Svg.c(0xE6F0FA))
    repeat(6) {
        val x = 20f + r() * 410f
        val y = 830f + r() * 20f
        val s = 46f + r() * 30f
        fill(Svg.pine(x, y, s), Svg.c(0x1D3C50))
    }
    fill(Svg.band(Svg.ridge(860f, 24f, 7, r)), Svg.c(0xF6FBFF))
    fill(Svg.band(Svg.ridge(940f, 16f, 6, r)), Svg.c(0xD5E4F2))
}

private val shore: Svg.() -> Unit = {
    val sh = Svg.shadow(-5f, 8f, 0.12f, 0x0B4A5C)
    fill(Svg.rect(), Svg.lin(Svg.st(0f, 0x6EC3F0), Svg.st(0.38f, 0xC8ECFB), Svg.st(0.4f, 0x2AA9C9), Svg.st(1f, 0x2AA9C9)))
    fill(Svg.circle(110f, 170f, 150f), Svg.radial(Svg.st(0f, 0xFFFBE0, 0.9f), Svg.st(1f, 0xFFFBE0, 0f)))
    fill(Svg.circle(110f, 170f, 44f), Svg.c(0xFFFDF0))
    fill(Svg.cloud(280f, 150f, 44f), Svg.c(0xFFFFFF), opacity = 0.95f)
    fill(Svg.cloud(330f, 290f, 30f), Svg.c(0xFFFFFF), opacity = 0.9f)
    fill(Svg.band(Svg.wave(400f, 5f, 60f, 0f)), Svg.c(0x27A2C4))
    fill(Svg.band(Svg.wave(470f, 8f, 80f, 1.3f)), Svg.c(0x35B4CF))
    fill(Svg.band(Svg.wave(550f, 10f, 100f, 2.6f)), Svg.c(0x52C6D8))
    fill(Svg.band(Svg.wave(640f, 14f, 120f, 0.7f, 24, 5f)), Svg.c(0x86DBE2), shadow = sh)
    fill(Svg.band(Svg.wave(712f, 16f, 130f, 2.0f, 24, 6f)), Svg.c(0xFFFFFF), shadow = sh)
    fill(Svg.band(Svg.wave(728f, 16f, 130f, 2.0f, 24, 6f)), Svg.c(0xF6E2B6))
    fill(Svg.band(Svg.wave(820f, 12f, 170f, 4.2f)), Svg.c(0xEED29C))
    fill(Svg.band(Svg.wave(910f, 10f, 190f, 1.0f)), Svg.c(0xE3C184))
    fill(Svg.circle(330f, 850f, 16f), Svg.c(0xFF7A59))
    stroke(Svg.d("M330 850L330 800"), Svg.c(0xFFFFFF), 3f)
    fill(Svg.d("M296 806A36 22 0 0 1 364 806Z"), Svg.c(0xFF7A59))
}

private fun w(id: String, focus: Float, base: Int, seed: Int, scene: Svg.() -> Unit) =
    Wallpaper(id, focus, base or 0xFF000000.toInt(), seed or 0xFF000000.toInt()) { svg(scene) }

/** The wallpapers of the twenty-four looks that came with the swatch fan, by look id. */
internal val MORE_WALLPAPERS: List<Wallpaper> = listOf(
    w("tide", 0.56f, 0x12505C, 0x12505C, tide),
    w("clay", 0.50f, 0xD68253, 0xC56A3F, clay),
    w("meadow", 0.58f, 0x8FCB6A, 0x5FAE4C, meadow),
    w("linen", 0.50f, 0xF3ECDF, 0xB9A889, linen),
    w("sky", 0.60f, 0xB5D8F6, 0x8FC4F4, sky),
    w("peach", 0.52f, 0xFFC4AE, 0xFF9F92, peach),
    w("mint", 0.60f, 0x84CDB0, 0x3F9C7D, mint),
    w("midnight", 0.56f, 0x101E44, 0x1D2F66, midnight),
    w("ember", 0.62f, 0x2A0D07, 0xE0552A, ember),
    w("forest", 0.50f, 0x15382B, 0x1D4A3A, forest),
    w("carbon", 0.58f, 0x1E1E22, 0x5C5C66, carbon),
    w("lagoon", 0.50f, 0x19A3A0, 0x19B3A6, lagoon),
    w("rose", 0.58f, 0xFFA9BF, 0xFF7FA8, rose),
    w("smoke", 0.56f, 0x272B38, 0x4D5A80, smoke),
    w("signal", 0.56f, 0xFFD92E, 0xFFD92E, signal),
    w("cobalt", 0.50f, 0x1F46D6, 0x1F46D6, cobalt),
    w("pop", 0.56f, 0xFF4F8B, 0xFF4F8B, pop),
    w("stencil", 0.52f, 0xD9C9B0, 0xD93A2B, stencil),
    w("fog", 0.45f, 0xB8BCC4, 0x8F96A2, fog),
    w("sand", 0.58f, 0xE3CAA0, 0xBF9862, sand),
    w("dusk", 0.50f, 0x6A4A78, 0x4A3A6B, dusk),
    w("maple", 0.50f, 0xF0A868, 0xE6472E, maple),
    w("aurora", 0.56f, 0x0C2540, 0x39F0B0, aurora),
    w("shore", 0.56f, 0x7FD0E6, 0x2AA9C9, shore),
)
