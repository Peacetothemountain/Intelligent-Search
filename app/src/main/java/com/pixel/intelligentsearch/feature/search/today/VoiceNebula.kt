package com.pixel.intelligentsearch.feature.search.today

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import java.util.Random
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The voice nebula that floats above the search bar while its Voice Nebula shortcut listens
 * ([com.pixel.intelligentsearch.feature.widget.VoiceNebulaActivity]): two to four soft light volumes in deep tones of
 * the theme color orbit and blend, adding up to a core in the color's light tone and fading out wide, with pinpoint stardust and a faint stipple (the Material Morph's diamond dot grid) inside. Speaking, it
 * grows and brightens, each lobe pulled by low, middle or high sound and pushed at each syllable's start, so loud words
 * stretch it into lopsided triangle and heart forms, and the stardust sparkles and drifts outward. Silent, the sparkle
 * fades, it contracts a little and breathes slowly. Every shader is made once per color and moved into place with its
 * local matrix, so a frame allocates nothing.
 */
internal class NebulaPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private val lobeShaders = arrayOfNulls<Shader>(LOBES)
    private var haloShader: Shader? = null
    private var coreShader: Shader? = null
    private var colorKey = Float.NaN
    private var coreColor = 0
    private var volColor = 0

    private val ox = FloatArray(LOBES)
    private val oy = FloatArray(LOBES)
    private val vx = FloatArray(LOBES)
    private val vy = FloatArray(LOBES)
    private val px = FloatArray(LOBES)
    private val py = FloatArray(LOBES)
    private val pr = FloatArray(LOBES)
    private val ps = FloatArray(LOBES)
    private val bands = FloatArray(3)
    private var onset = 0f
    private var energy = 0f
    private var sparkle = 0f
    private var spin = 0f
    private var lastT = Float.NaN

    private val random = Random(7)
    private val starA = FloatArray(STARS) { random.nextFloat() * TAU }
    private val starR = FloatArray(STARS) { sqrt(random.nextFloat()) * 0.85f }
    private val starTw = FloatArray(STARS) { 1.5f + random.nextFloat() * 3.5f }
    private val starPh = FloatArray(STARS) { random.nextFloat() * TAU }
    private val starDrift = FloatArray(STARS)

    /**
     * Draws the nebula centerd on [cx], [cy] in a square [w] pixels wide, at [t] seconds. [sound] is loudness, low, middle,
     * high and a syllable's start; [shown] fades and grows it in and out, 0 to 1. With [moving] false it holds a still
     * frame.
     */
    fun draw(c: Canvas, cx: Float, cy: Float, w: Float, t: Float, sound: FloatArray, talking: Boolean, dark: Boolean, hue: Float, moving: Boolean, shown: Float) {
        colors(hue, dark)
        val dt = if (lastT.isNaN() || !moving) 0f else (t - lastT).coerceIn(0f, 0.05f)
        lastT = t
        val level = sound[0]
        for (i in 0 until 3) bands[i] += (sound[i + 1] - bands[i]) * 0.35f
        onset = max(sound[4], onset * 0.86f)
        if (!moving) {
            energy = if (talking) 0.5f else 0f
            sparkle = if (talking) 0.6f else 0f
        } else {
            val target = if (talking) level else 0f
            energy += (target - energy) * (1f - exp(-dt * (if (target > energy) 14f else 4f)))
            sparkle += ((if (talking) 0.35f + level * 0.9f else 0f) - sparkle) * (1f - exp(-dt * (if (talking) 6f else 2f)))
            spin += dt * (0.18f + energy * 0.5f)
        }
        val e = energy
        val u = w / 190f
        val breathe = 1f + 0.05f * sin(t * 0.9f) + 0.03f * sin(t * 1.7f + 1f)
        val r = w * 0.21f * breathe * (1f - 0.06f * (1f - min(1f, e * 3f)) + e * 0.16f) * (0.55f + 0.45f * shown)
        val blend = if (dark) BlendMode.PLUS else BlendMode.SRC_OVER
        paint.blendMode = blend
        dotPaint.blendMode = blend

        // a faint, wide atmosphere breathing around it
        val ha = (if (dark) 0.08f else 0.07f) * (1f + 0.25f * sin(t * 0.8f)) * (1f + e * 0.8f) * shown
        disc(c, haloShader, cx, cy, r * 2.3f, ha)

        for (i in 0 until LOBES) {
            val show = if (i < 3) 1f else max(0f, sin(t * 0.23f + 1.3f)) * (0.4f + e)
            val a = PHASE[i] + t * (0.21f + 0.07f * i) * 0.35f + spin * 0.4f
            val d = r * (DIST[i] + 0.1f * sin(t * 0.5f + i * 2.1f) + 0.07f * sin(t * 0.17f + i * 1.9f) * (1f - min(1f, e * 2f)))
            val band = bands[i % 3]
            val push = r * (0.42f * band + 0.26f * onset) * (if (i % 2 == 1) 1f else 0.7f)
            val ta = a + (i - 1) * 0.4f
            val kx = cos(ta) * push
            val ky = sin(ta) * push
            vx[i] += (SPRING * (kx - ox[i]) - DAMP * vx[i]) * dt
            vy[i] += (SPRING * (ky - oy[i]) - DAMP * vy[i]) * dt
            ox[i] += vx[i] * dt
            oy[i] += vy[i] * dt
            px[i] = cx + cos(a) * d + ox[i]
            py[i] = cy + sin(a) * d + oy[i]
            pr[i] = r * SIZE[i] * (1f + 0.12f * sin(t * 0.7f + i) + band * 0.25f)
            ps[i] = show
            if (show > 0.01f) disc(c, lobeShaders[i], px[i], py[i], pr[i] * 1.55f, (if (dark) 0.62f else 0.5f) * show * (1f + e * 0.3f) * shown)
        }
        // the core where the volumes meet, in the color's light tone
        val mx = (px[0] + px[1] + px[2]) / 3f
        val my = (py[0] + py[1] + py[2]) / 3f
        disc(c, coreShader, mx, my, r * (0.7f + e * 0.25f), min(1f, (if (dark) 0.34f else 0.55f) * (0.42f + e * 0.9f + onset * 0.22f)) * shown)

        // a faint stipple inside the light
        val cell = r / 5.5f
        val h = cell * HALF_SQRT2
        val span = ceil(r * 1.5f / h).toInt()
        dotPaint.color = if (dark) coreColor else android.graphics.Color.WHITE
        dotPaint.alpha = ((if (dark) 0.1f else 0.22f) * shown * 255f).toInt().coerceIn(0, 255)
        for (ga in -span..span) {
            var gc = -span + ((ga + span) and 1)
            while (gc <= span) {
                val x = mx + ga * h
                val y = my + gc * h
                val v = density(x, y)
                if (v >= 0.18f) c.drawCircle(x, y, 0.5f * cell * 0.38f * v.pow(0.75f), dotPaint)
                gc += 2
            }
        }
        // the stardust: sparkling while it speaks, drifting outward and starting again near the core
        for (s in 0 until STARS) {
            if (talking || sparkle > 0.02f) starDrift[s] += dt * (0.05f + level * 0.35f) * (if (talking) 1f else 0.3f)
            var sr = starR[s] + starDrift[s]
            if (sr > 1.15f) {
                starR[s] = 0.1f + sqrt(random.nextFloat()) * 0.35f
                starDrift[s] = 0f
                starA[s] = random.nextFloat() * TAU
                sr = starR[s]
            }
            val ang = starA[s] + spin * 0.3f
            val x = mx + cos(ang) * sr * r
            val y = my + sin(ang) * sr * r
            val tw = (0.5f + 0.5f * sin(t * starTw[s] + starPh[s])).pow(2.2f)
            val edge = if (sr > 0.95f) max(0f, (1.15f - sr) / 0.2f) else 1f
            val a3 = (0.06f + 0.7f * sparkle) * tw * min(1f, density(x, y) * 1.6f) * edge * shown
            if (a3 < 0.02f) continue
            val sz = (0.35f + tw * (0.35f + sparkle * 0.35f)) * u
            dotPaint.color = if (dark) coreColor else darker(volColor)
            dotPaint.alpha = (min(1f, a3 * (if (dark) 0.85f else 0.8f)) * 255f).toInt()
            c.drawCircle(x, y, sz, dotPaint)
            if (tw > 0.985f && sparkle > 0.5f) {
                dotPaint.alpha = (min(1f, a3) * 0.45f * 255f).toInt()
                c.drawRect(x - sz * 2.2f, y - 0.25f * u, x + sz * 2.2f, y + 0.25f * u, dotPaint)
                c.drawRect(x - 0.25f * u, y - sz * 2.2f, x + 0.25f * u, y + sz * 2.2f, dotPaint)
            }
        }
    }

    private fun density(x: Float, y: Float): Float {
        var v = 0f
        for (i in 0 until LOBES) {
            val q = ((x - px[i]) * (x - px[i]) + (y - py[i]) * (y - py[i])) / (pr[i] * pr[i] * 0.45f)
            v += ps[i] * exp(-q)
        }
        return min(1f, v * 0.7f)
    }

    private fun disc(c: Canvas, shader: Shader?, x: Float, y: Float, radius: Float, alpha: Float) {
        if (shader == null || alpha <= 0.004f) return
        matrix.setScale(radius, radius)
        matrix.postTranslate(x, y)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        paint.alpha = (min(1f, alpha) * 255f).toInt()
        c.drawCircle(x, y, radius, paint)
    }

    /** Deep tones of [hue], as the Material Morph plates derive theirs: richer and much darker, the core its light tone. */
    private fun colors(hue: Float, dark: Boolean) {
        val key = hue + if (dark) 1000f else 0f
        if (key == colorKey) return
        colorKey = key
        val vol = if (dark) hsv(hue, 0.72f, 0.38f) else hsv(hue, 0.62f, 0.62f)
        val core = if (dark) hsv(hue, 0.4f, 0.78f) else hsv(hue, 0.42f, 0.86f)
        volColor = vol
        coreColor = core
        haloShader = gradient(vol, GAUSS)
        coreShader = gradient(core, GAUSS)
        for (i in 0 until LOBES) {
            val h = (hue + SHADE[i] + 360f) % 360f
            lobeShaders[i] = gradient(if (dark) hsv(h, 0.7f + 0.05f * (i % 2), 0.4f - 0.04f * (i % 2)) else hsv(h, 0.7f, 0.66f), LOBE_FALLOFF)
        }
    }

    private fun gradient(color: Int, stops: Array<FloatArray>): Shader {
        val colors = IntArray(stops.size) { withAlpha(color, stops[it][1]) }
        val positions = FloatArray(stops.size) { stops[it][0] }
        return RadialGradient(0f, 0f, 1f, colors, positions, Shader.TileMode.CLAMP)
    }

    private companion object {
        const val LOBES = 4
        const val STARS = 110
        const val TAU = (2 * PI).toFloat()
        const val HALF_SQRT2 = 0.70710677f
        const val SPRING = 90f
        val DAMP = 2f * sqrt(SPRING) * 0.75f
        val SIZE = floatArrayOf(0.78f, 0.64f, 0.52f, 0.46f)
        val DIST = floatArrayOf(0.3f, 0.44f, 0.52f, 0.5f)
        val PHASE = floatArrayOf(0.4f, 2.3f, 4.4f, 3.3f)
        val SHADE = floatArrayOf(-14f, 0f, 16f, 8f)
        val GAUSS = arrayOf(
            floatArrayOf(0f, 1f), floatArrayOf(0.12f, 0.93f), floatArrayOf(0.25f, 0.76f), floatArrayOf(0.38f, 0.55f), floatArrayOf(0.5f, 0.36f),
            floatArrayOf(0.62f, 0.2f), floatArrayOf(0.75f, 0.1f), floatArrayOf(0.87f, 0.035f), floatArrayOf(1f, 0f)
        )
        /** A lobe keeps a fuller body before its soft edge, so the outline of the mass reads. */
        val LOBE_FALLOFF = arrayOf(
            floatArrayOf(0f, 1f), floatArrayOf(0.2f, 0.96f), floatArrayOf(0.4f, 0.82f), floatArrayOf(0.55f, 0.62f),
            floatArrayOf(0.68f, 0.38f), floatArrayOf(0.8f, 0.17f), floatArrayOf(0.9f, 0.06f), floatArrayOf(1f, 0f)
        )

        fun hsv(h: Float, s: Float, v: Float) = android.graphics.Color.HSVToColor(floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
        fun withAlpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a * 255f).toInt().coerceIn(0, 255) shl 24)
        fun darker(color: Int): Int {
            val r = (android.graphics.Color.red(color) * 0.5f).toInt()
            val g = (android.graphics.Color.green(color) * 0.5f).toInt()
            val b = (android.graphics.Color.blue(color) * 0.5f).toInt()
            return android.graphics.Color.rgb(r, g, b)
        }
    }
}

/** The hue of a theme color, for the nebula's tones. */
internal fun hueOf(color: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(android.graphics.Color.argb(255, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()), hsv)
    return hsv[0]
}
