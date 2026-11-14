package com.pixel.intelligentsearch.feature.settings

import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.PorterDuff
import android.graphics.Shader
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** How high a category's point sits against the graph's [ceilingMb]: its true share, so a small one stays small. */
internal fun ramPointFraction(ramMb: Float, ceilingMb: Int): Float =
    if (ceilingMb <= 0) 0f else (ramMb / ceilingMb).coerceIn(0f, 1f)

private val CEILING_STEPS_MB = intArrayOf(
    100, 150, 200, 300, 400, 500, 750, 1000, 1500, 2000, 3000, 4000, 5000, 6000, 8000,
    10_000, 12_000, 16_000, 20_000, 24_000, 32_000, 48_000, 64_000
)

/**
 * The top of the graph's scale for its [largestMb] part: the next round size with some room above it. The scale
 * holds while a part grows inside it, so a bigger reading raises its own peak instead of lowering the others.
 */
internal fun ramCeilingMb(largestMb: Float): Int {
    if (largestMb <= 0f) return 500
    val needed = largestMb * 1.15f
    for (step in CEILING_STEPS_MB) if (step >= needed) return step
    return ceil(needed).toInt()
}

/** The scale's top as the graph labels it. */
internal fun ramCeilingLabel(mb: Int): String = when {
    mb < 1000 -> "$mb MB"
    mb % 1000 == 0 -> "${mb / 1000} GB"
    else -> String.format(java.util.Locale.US, "%.1f GB", mb / 1000f)
}

/** A part's size as the card writes it. */
internal fun ramPartSize(ramMb: Float): String =
    if (ramMb >= 1000f) String.format(java.util.Locale.US, "%.1f GB", ramMb / 1000f) else "${ramMb.toInt()} MB"

/** One memory reading for the graph: each used part in MB, what is still available, and the percentage in use. */
internal class RamReading(val partsMb: FloatArray, val availableMb: Float, val usedPercent: Int) {
    val count: Int get() = min(partsMb.size, RamScene.MAX_PARTS)
}

private const val TAU = 6.2831855f
private const val PI = 3.1415927f

/**
 * The memory graph's moving picture, in dp: one peak for each used part of memory at its true height, joined by a
 * line that never sits still.
 *
 * A new reading springs its peak to the new height, and a faint outline behind follows a moment later, so it shows
 * where the line just was. Lights run along the line, more of them and faster the fuller memory is, and the surface
 * grows restless with them. A tapped peak hops and is [picked] for a moment; a tap anywhere else sends a ripple out
 * along the line. The used-memory bar's pieces ([bar]) are kept here too, so they move with the graph.
 */
internal class RamScene {
    var count = 0
        private set

    /** Each part's size as drawn, in MB: it springs to the reading. */
    val shown = FloatArray(MAX_PARTS)
    private val shownSpeed = FloatArray(MAX_PARTS)

    /** Where each part was a moment ago, in MB. */
    val ghost = FloatArray(MAX_PARTS)
    val grow = FloatArray(MAX_PARTS)
    private val growSpeed = FloatArray(MAX_PARTS)
    val hop = FloatArray(MAX_PARTS)
    private val hopSpeed = FloatArray(MAX_PARTS)
    private val seen = FloatArray(MAX_PARTS) { -1f }
    private val sway = FloatArray(MAX_PARTS)
    private val breath = FloatArray(MAX_PARTS)
    var haloPhase = 0f
        private set

    /** The bar's pieces as shares of all memory: the used parts in order, then what is available. They add up to 1. */
    val bar = FloatArray(MAX_PARTS + 1).also { it[0] = 1f }

    /** How picked each piece of the bar is, 0 to 1. */
    val glow = FloatArray(MAX_PARTS + 1)

    /** The top of the scale in MB: [ceilingStep] is where it is going, [ceiling] where it is drawn. */
    var ceilingStep = 500
        private set
    var ceiling = 500f
        private set
    var energy = REST_ENERGY
        private set
    var flow = 0f
        private set

    /** Seconds the scene has run. */
    var life = 0f
        private set
    private var opened = 0f

    /** True for the still picture (animations off): nothing sways, and there are no lights, rings or ripples. */
    var still = false
        private set

    /** The tapped part, or -1. */
    var picked = -1
        private set
    private var labelLeft = 0f

    val ringPart = IntArray(MAX_RINGS)
    val ringAge = FloatArray(MAX_RINGS)
    var ringCount = 0
        private set
    private val rippleX = FloatArray(MAX_RIPPLES)
    private val rippleAge = FloatArray(MAX_RIPPLES)
    var rippleCount = 0
        private set

    /** How far along the line each light is, 0 to 1. */
    val lightAt = FloatArray(LIGHTS) { it / LIGHTS.toFloat() }
    private val lightPace = FloatArray(LIGHTS) { 0.8f + (it * 0.37f) % 0.5f }

    /** How many lights are running: two with memory mostly free, up to seven with it nearly full. */
    val lightsOn: Int get() = if (still || count == 0) 0 else 2 + (5f * energy).roundToInt()

    var w = 0f
        private set
    var h = 0f
        private set
    val left: Float get() = 22f
    val right: Float get() = w - 8f
    val top: Float get() = 8f
    val floor: Float get() = h - 14f

    /** Where each peak rests, where it is this frame, and how tall it and its outline are. */
    val restX = FloatArray(MAX_PARTS)
    val peakX = FloatArray(MAX_PARTS)
    val peakHeight = FloatArray(MAX_PARTS)
    val ghostHeight = FloatArray(MAX_PARTS)
    private var spread = 1f
    private var lastNanos = 0L

    /** How see-through the picked part's label is: it fades in, holds, and fades out. */
    val labelAlpha: Float
        get() = if (picked < 0) 0f else (min(1f, labelLeft / 0.25f) * min(1f, (LABEL_SECONDS - labelLeft) / 0.12f)).coerceIn(0f, 1f)

    /** The graph's size, known once it has been laid out. */
    fun resize(w: Float, h: Float) {
        if (w == this.w && h == this.h) return
        this.w = w
        this.h = h
        layout()
    }

    /** Moves the scene on to the display frame at [nanos]. Nothing moves until the graph has a size. */
    fun frame(nanos: Long, reading: RamReading) {
        val dt = if (lastNanos == 0L) 0f else ((nanos - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = nanos
        if (w > 0f && h > 0f) advance(dt, reading)
    }

    fun advance(dt: Float, reading: RamReading) {
        val n = reading.count
        if (still || n != count) {
            still = false
            count = n
            open()
        }
        life += dt
        opened += dt
        energy += (energyFor(reading.usedPercent) - energy) * min(1f, dt * 2f)
        flow = (flow + dt * (1.1f + 2.4f * energy)) % TAU
        haloPhase = (haloPhase + dt * 2.1f) % TAU

        var largest = 0f
        var total = reading.availableMb.coerceAtLeast(0f)
        for (i in 0 until n) {
            largest = max(largest, reading.partsMb[i])
            total += reading.partsMb[i]
        }
        ceilingStep = ramCeilingMb(largest)
        // The scale is simply there on opening; after that it glides when it has to step.
        ceiling = if (ceiling < 0f) ceilingStep.toFloat() else ceiling + (ceilingStep - ceiling) * min(1f, dt * 3.5f)

        val barEase = easeOut((opened - 0.1f) / 0.5f)
        var usedShare = 0f
        for (i in 0 until n) {
            val want = reading.partsMb[i]
            if (seen[i] < 0f) {
                // On opening the peak rises to the reading; it does not also chase it from nothing.
                seen[i] = want
                shown[i] = want
                shownSpeed[i] = 0f
            } else if (abs(want - seen[i]) > ceilingStep * RING_SHARE) {
                seen[i] = want
                ring(i)
            }
            val rising = if (opened > 0.12f + i * 0.14f) 1f else 0f
            growSpeed[i] += (130f * (rising - grow[i]) - 13f * growSpeed[i]) * dt
            grow[i] += growSpeed[i] * dt
            shownSpeed[i] += (75f * (want - shown[i]) - 9.5f * shownSpeed[i]) * dt
            shown[i] += shownSpeed[i] * dt
            ghost[i] += (shown[i] - ghost[i]) * min(1f, dt / 1.6f)
            hopSpeed[i] += (-210f * hop[i] - 11f * hopSpeed[i]) * dt
            hop[i] += hopSpeed[i] * dt
            sway[i] = (sway[i] + dt * (0.41f + i * 0.13f)) % TAU
            breath[i] = (breath[i] + dt * (0.9f + i * 0.23f)) % TAU

            val share = if (total > 0f) want / total * barEase else 0f
            bar[i] += (share - bar[i]) * min(1f, dt * 7f)
            usedShare += bar[i]
        }
        bar[n] = (1f - usedShare).coerceAtLeast(0f)
        for (i in 0..n) glow[i] += ((if (picked == i) 1f else 0f) - glow[i]) * min(1f, dt * 9f)

        if (picked >= 0) {
            labelLeft -= dt
            if (labelLeft <= 0f) picked = -1
        }
        ringCount = age(ringAge, ringCount, dt, RING_SECONDS) { from, to -> ringPart[to] = ringPart[from] }
        rippleCount = age(rippleAge, rippleCount, dt, RIPPLE_SECONDS) { from, to -> rippleX[to] = rippleX[from] }
        val pace = 0.07f + 0.2f * energy
        for (i in 0 until LIGHTS) lightAt[i] = (lightAt[i] + dt * pace * lightPace[i]) % 1f
        layout()
    }

    /** The still picture: every part at its true size, with nothing moving. */
    fun settle(reading: RamReading) {
        val n = reading.count
        still = true
        count = n
        var largest = 0f
        var total = reading.availableMb.coerceAtLeast(0f)
        for (i in 0 until n) {
            largest = max(largest, reading.partsMb[i])
            total += reading.partsMb[i]
        }
        ceilingStep = ramCeilingMb(largest)
        ceiling = ceilingStep.toFloat()
        energy = energyFor(reading.usedPercent)
        var usedShare = 0f
        for (i in 0 until n) {
            val want = reading.partsMb[i]
            shown[i] = want
            ghost[i] = want
            seen[i] = want
            shownSpeed[i] = 0f
            grow[i] = 1f
            growSpeed[i] = 0f
            hop[i] = 0f
            hopSpeed[i] = 0f
            bar[i] = if (total > 0f) want / total else 0f
            usedShare += bar[i]
        }
        bar[n] = (1f - usedShare).coerceAtLeast(0f)
        glow.fill(0f)
        picked = -1
        ringCount = 0
        rippleCount = 0
        layout()
    }

    /** The line's height at [x], or its outline's with [outline]: lower numbers are higher up. */
    fun curve(x: Float, outline: Boolean): Float {
        var s = 0f
        val heights = if (outline) ghostHeight else peakHeight
        for (i in 0 until count) {
            val d = (x - peakX[i]) / spread
            s += heights[i] * exp(-0.5f * d * d)
        }
        if (!outline && !still) {
            s += sin(x * 0.085f - flow) * (0.35f + 1.25f * energy) * min(1f, s / 12f)
            for (j in 0 until rippleCount) {
                val age = rippleAge[j]
                val out = 62f * age
                val d1 = (x - rippleX[j] - out) / 8f
                val d2 = (x - rippleX[j] + out) / 8f
                val start = if (age < 0.12f) age / 0.12f else 1f
                s += 4.2f * (1f - age / RIPPLE_SECONDS) * (exp(-0.5f * d1 * d1) + exp(-0.5f * d2 * d2)) * start
            }
        }
        return max(top - 4f, floor - 2f - s)
    }

    /**
     * A tap at ([x], [y]): on a peak it hops and is picked for a moment, anywhere else on the graph a ripple runs out
     * along the line. Returns the part that was picked, or -1.
     */
    fun tap(x: Float, y: Float): Int {
        if (still || count == 0) return -1
        var best = -1
        var nearest = TAP_REACH
        for (i in 0 until count) {
            val d = hypot(x - peakX[i], y - curve(peakX[i], false))
            if (d < nearest) {
                nearest = d
                best = i
            }
        }
        if (best >= 0) {
            hopSpeed[best] = -95f
            picked = best
            labelLeft = LABEL_SECONDS
            ring(best)
        } else if (x > left && x < right && rippleCount < MAX_RIPPLES) {
            rippleX[rippleCount] = x
            rippleAge[rippleCount] = 0f
            rippleCount++
        }
        return best
    }

    /** The start of the opening: the peaks rise one after another and the bar fills. */
    private fun open() {
        opened = 0f
        ceiling = -1f
        for (i in 0 until MAX_PARTS) {
            grow[i] = 0f
            growSpeed[i] = 0f
            ghost[i] = 0f
            hop[i] = 0f
            hopSpeed[i] = 0f
            seen[i] = -1f
        }
        bar.fill(0f)
        bar[count] = 1f
        glow.fill(0f)
        picked = -1
        ringCount = 0
        rippleCount = 0
    }

    private fun ring(part: Int) {
        if (ringCount == MAX_RINGS) return
        ringPart[ringCount] = part
        ringAge[ringCount] = 0f
        ringCount++
    }

    /** Ages a list held in arrays by [dt] and drops what has outlived [limit], keeping the rest in order. */
    private inline fun age(ages: FloatArray, size: Int, dt: Float, limit: Float, move: (from: Int, to: Int) -> Unit): Int {
        var kept = 0
        for (i in 0 until size) {
            val older = ages[i] + dt
            if (older < limit) {
                move(i, kept)
                ages[kept] = older
                kept++
            }
        }
        return kept
    }

    private fun layout() {
        if (w <= 0f || h <= 0f) return
        val width = right - left
        val height = floor - top
        val gap = if (count > 1) 0.68f / (count - 1) else 0f
        val first = if (count > 1) 0.16f else 0.5f
        spread = width * (if (count > 1) gap / 3.1f else 0.11f)
        val scale = ceiling.roundToInt()
        for (i in 0 until count) {
            restX[i] = left + width * (first + i * gap)
            peakX[i] = restX[i] + if (still) 0f else sin(sway[i] + i * 2.1f) * 2.2f
            val swell = if (still) 0f else 0.04f * (0.6f + energy)
            val rise = height * grow[i]
            peakHeight[i] = max(0f, rise * ramPointFraction(shown[i], scale) * (1f + swell * sin(breath[i] + i * 1.4f)) - hop[i])
            // The outline breathes a little behind the line.
            val behind = breath[i] - 0.7f * (0.9f + i * 0.23f)
            ghostHeight[i] = max(0f, rise * ramPointFraction(ghost[i], scale) * (1f + swell * sin(behind + i * 1.4f)))
        }
    }

    companion object {
        const val MAX_PARTS = 4
        const val MAX_RINGS = 8
        const val MAX_RIPPLES = 6
        const val LIGHTS = 7
        const val REST_ENERGY = 0.08f
        const val RING_SECONDS = 0.75f
        const val RIPPLE_SECONDS = 1.3f
        const val LABEL_SECONDS = 1.9f
        const val TAP_REACH = 18f

        /** A reading has to move by this share of the scale before its peak rings. */
        const val RING_SHARE = 0.035f

        /** How restless the graph is: calm with memory mostly free, at its liveliest by 90% in use. */
        fun energyFor(usedPercent: Int): Float = ((usedPercent - 45) / 45f).coerceIn(REST_ENERGY, 1f)

        private fun easeOut(u: Float): Float {
            val r = 1f - u.coerceIn(0f, 1f)
            return 1f - r * r * r
        }
    }
}

/** The graph's colors: one for each part, the grid's, and the surface the peaks' dots are ringed with. */
internal class RamTones(val parts: List<Color>, val grid: Color, val surface: Color) {
    fun part(i: Int): Color = parts[i % parts.size]
}

/** Draws a [RamScene] in dp. It keeps its paths and brushes, so a frame makes nothing new. */
internal class RamPainter {
    private val line = Path()
    private val fill = Path()
    private val outline = Path()
    private val lineStroke = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val glowStroke = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val outlineStroke = Stroke(width = 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val ringStroke = Stroke(width = 1.6f)
    private val dashes = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))
    private val tail = FloatArray(TAIL) { 0.9f * (1f - it / TAIL.toFloat()).pow(1.6f) }

    private var lineBrush: Brush = SolidColor(Color.Transparent)
    private var fillBrush: Brush = SolidColor(Color.Transparent)
    private var brushTones: RamTones? = null
    private var brushCount = -1
    private var brushWidth = 0f
    private var brushHeight = 0f

    fun DrawScope.paint(scene: RamScene, tones: RamTones) {
        if (scene.w <= 0f || scene.h <= 0f) return
        val left = scene.left
        val right = scene.right
        val floor = scene.floor
        val count = scene.count
        if (count > 0) {
            brushes(scene, tones)
            trace(scene, line, outline = false, closed = false)
            // The same curve, closed down to the floor.
            fill.rewind()
            fill.addPath(line)
            fill.lineTo(scene.right, floor)
            fill.lineTo(left, floor)
            fill.close()
            // Each peak's own color under the line, fading out toward the floor.
            drawPath(fill, fillBrush, alpha = 0.42f)
        }

        for (g in 1..3) {
            val y = scene.top + (floor - scene.top) * g / 4f
            drawLine(tones.grid.copy(alpha = 0.25f), Offset(left, y), Offset(right, y), strokeWidth = 0.8f, pathEffect = dashes)
        }
        if (count == 0) return

        if (!scene.still) {
            // Where the line was a moment ago.
            trace(scene, outline, outline = true, closed = false)
            drawPath(outline, lineBrush, alpha = 0.34f, style = outlineStroke)
        }
        drawPath(line, lineBrush, alpha = 0.13f, style = glowStroke)
        drawPath(line, lineBrush, style = lineStroke)

        val width = right - left
        for (i in 0 until scene.lightsOn) {
            val at = scene.lightAt[i]
            val edge = sin(PI * at)
            for (j in 0 until TAIL) {
                val u1 = at - j * 0.011f
                val u0 = u1 - 0.0115f
                if (u0 < 0f) break
                val x0 = left + u0 * width
                val x1 = left + u1 * width
                drawLine(
                    Color.White.copy(alpha = edge * tail[j]),
                    Offset(x0, scene.curve(x0, false)), Offset(x1, scene.curve(x1, false)), strokeWidth = 2.2f
                )
            }
            val x = left + at * width
            drawCircle(Color.White, radius = 1.35f, center = Offset(x, scene.curve(x, false)), alpha = edge)
        }

        for (i in 0 until scene.ringCount) {
            val part = scene.ringPart[i]
            if (part >= count) continue
            val u = scene.ringAge[i] / RamScene.RING_SECONDS
            val x = scene.peakX[part]
            drawCircle(
                tones.part(part), radius = 4f + 13f * (1f - (1f - u) * (1f - u)),
                center = Offset(x, scene.curve(x, false)), alpha = 0.55f * (1f - u), style = ringStroke
            )
        }

        for (i in 0 until count) {
            val color = tones.part(i)
            val grow = scene.grow[i].coerceIn(0f, 1f)
            val x = scene.peakX[i]
            val y = scene.curve(x, false)
            if (y + 5f < floor) {
                drawLine(color.copy(alpha = 0.34f * grow), Offset(x, y + 5f), Offset(x, floor), strokeWidth = 1f, pathEffect = dashes)
            }
            val halo = if (scene.still) 6.5f else 6f + 1.6f * sin(scene.haloPhase + i * 2f)
            drawCircle(color, radius = halo, center = Offset(x, y), alpha = 0.24f * grow)
            drawCircle(tones.surface, radius = 4f * grow, center = Offset(x, y))
            drawCircle(color, radius = 2.5f * grow, center = Offset(x, y))
        }
    }

    private fun trace(scene: RamScene, path: Path, outline: Boolean, closed: Boolean) {
        val left = scene.left
        val right = scene.right
        path.rewind()
        if (closed) {
            path.moveTo(left, scene.floor)
            path.lineTo(left, scene.curve(left, outline))
        } else {
            path.moveTo(left, scene.curve(left, outline))
        }
        var x = left + 1f
        while (x < right) {
            path.lineTo(x, scene.curve(x, outline))
            x += 1f
        }
        path.lineTo(right, scene.curve(right, outline))
        if (closed) {
            path.lineTo(right, scene.floor)
            path.close()
        }
    }

    /** The line's colors run from one peak's to the next; the fill is the same, thinning to nothing at the floor. */
    private fun brushes(scene: RamScene, tones: RamTones) {
        val count = scene.count
        if (brushTones === tones && brushCount == count && brushWidth == scene.w && brushHeight == scene.h) return
        brushTones = tones
        brushCount = count
        brushWidth = scene.w
        brushHeight = scene.h
        val from = scene.restX[0]
        val to = if (count > 1) scene.restX[count - 1] else from + 1f
        val colors = IntArray(max(2, count)) { tones.part(min(it, count - 1)).toArgb() }
        val stops = FloatArray(colors.size) { it / (colors.size - 1f) }
        lineBrush = if (count > 1) {
            Brush.horizontalGradient(*Array(count) { stops[it] to tones.part(it) }, startX = from, endX = to)
        } else {
            SolidColor(tones.part(0))
        }
        val across = LinearGradient(from, 0f, to, 0f, colors, stops, Shader.TileMode.CLAMP)
        val down = LinearGradient(0f, scene.top, 0f, scene.floor, 0xFF000000.toInt(), 0x0F000000, Shader.TileMode.CLAMP)
        fillBrush = ShaderBrush(ComposeShader(across, down, PorterDuff.Mode.DST_IN))
    }

    private companion object {
        const val TAIL = 9
    }
}

/** The memory graph's scene and what the page reads from it: the frame it is on, and the part that was tapped. */
@Stable
internal class RamPeaksState(reading: RamReading) {
    val scene = RamScene()
    val reading = mutableStateOf(reading)

    /** Read only where the graph and the bar draw, so a frame redraws them and recomposes nothing. */
    val frame = mutableLongStateOf(0L)

    /** The tapped part, or -1: the page reads it to pick out that part's row and its piece of the bar. */
    val picked = mutableIntStateOf(-1)
}

/**
 * The state behind [RamPeaksGraph] and [UsedMemoryBar] for these [parts] of used memory. With [animate] off (Remove
 * animations) both show the still picture.
 */
@Composable
internal fun rememberRamPeaks(parts: List<ProcessRamEntry>, availableMb: Float, usedPercent: Int, animate: Boolean): RamPeaksState {
    val reading = remember(parts, availableMb, usedPercent) {
        RamReading(FloatArray(parts.size) { parts[it].ramMb }, availableMb, usedPercent)
    }
    val state = remember { RamPeaksState(reading) }
    state.reading.value = reading
    LaunchedEffect(state, animate) {
        if (!animate) {
            state.picked.intValue = -1
            return@LaunchedEffect
        }
        while (true) withInfiniteAnimationFrameNanos { now ->
            state.scene.frame(now, state.reading.value)
            if (state.picked.intValue != state.scene.picked) state.picked.intValue = state.scene.picked
            state.frame.longValue = now
        }
    }
    return state
}

/**
 * Used memory by part: a peak for each of [parts] at its true height, in its own one of [colors]. The peaks spring
 * to each new reading and the line keeps moving, livelier the fuller memory is. Tapping a peak names its size.
 */
@Composable
internal fun RamPeaksGraph(
    state: RamPeaksState,
    parts: List<ProcessRamEntry>,
    colors: List<Color>,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val tones = remember(colors, scheme) { RamTones(colors, scheme.outlineVariant, scheme.surface) }
    val painter = remember { RamPainter() }
    val scene = state.scene
    val density = LocalDensity.current.density
    val labelColor = scheme.onSurfaceVariant.copy(alpha = 0.65f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.45f))
            // The scene is laid out in dp, so it is the same on every screen density.
            .onSizeChanged { scene.resize(it.width / density, it.height / density) }
            .then(
                if (animate) Modifier.pointerInput(scene) { detectTapGestures { scene.tap(it.x / density, it.y / density) } }
                else Modifier
            )
            // Read as one picture; the list beside it says each category's share.
            .clearAndSetSemantics { contentDescription = "Memory by category" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // With animations off, the still picture of this reading; with them on, whatever frame the scene is at.
            if (animate) state.frame.longValue else scene.settle(state.reading.value)
            scale(this.density, this.density, Offset.Zero) { with(painter) { paint(scene, tones) } }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = ramCeilingLabel(ramCeilingMb(parts.maxOfOrNull { it.ramMb } ?: 0f)),
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                color = labelColor,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // It steps aside for a tapped peak's label.
                    .graphicsLayer {
                        state.frame.longValue
                        alpha = 1f - scene.labelAlpha
                    }
            )
            Text(
                text = "0",
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                color = labelColor,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 10.dp)
            )
            Text(
                text = "RAM by Category",
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Medium,
                color = labelColor,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 1.dp)
            )
        }

        val picked = state.picked.intValue
        if (picked in parts.indices && picked < RamScene.MAX_PARTS) {
            Text(
                text = "${parts[picked].name}  ${ramPartSize(parts[picked].ramMb)}",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (colors[picked % colors.size].luminance() > 0.4f) Color.Black else Color.White,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // It rides just above its peak, or below it when there is no room, and stays inside the graph.
                    .graphicsLayer {
                        state.frame.longValue
                        val peak = scene.peakX[picked]
                        val x = peak * this.density
                        val y = scene.curve(peak, false) * this.density
                        val edge = 3.dp.toPx()
                        val gap = 9.dp.toPx()
                        translationX = (x - size.width / 2f).coerceIn(edge, max(edge, scene.w * this.density - size.width - edge))
                        translationY = if (y - gap - size.height < edge) y + gap else y - gap - size.height
                        alpha = scene.labelAlpha
                    }
                    .background(colors[picked % colors.size], CircleShape)
                    .padding(horizontal = 6.dp, vertical = 1.5.dp)
            )
        }
    }
}

/**
 * All of memory as one bar: each used part in its own one of [colors] at its true share, then what is available in
 * [restColor]. The pieces glide to each reading, and the piece for a tapped peak stands out from the rest.
 */
@Composable
internal fun UsedMemoryBar(state: RamPeaksState, colors: List<Color>, restColor: Color, animate: Boolean, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
    ) {
        val scene = state.scene
        if (animate) state.frame.longValue else scene.settle(state.reading.value)
        val pieces = scene.count + 1
        val gap = 2.dp.toPx()
        val least = 3.dp.toPx()
        val free = max(0f, size.width - gap * (pieces - 1) - least * pieces)
        var dim = 0f
        for (i in 0 until pieces) dim = max(dim, scene.glow[i])
        val corner = CornerRadius(5.dp.toPx())
        var x = 0f
        for (i in 0 until pieces) {
            val wide = least + free * scene.bar[i]
            val tall = 10.dp.toPx() * (1f + 0.4f * scene.glow[i])
            drawRoundRect(
                color = if (i < scene.count) colors[i % colors.size] else restColor,
                topLeft = Offset(x, (size.height - tall) / 2f),
                size = Size(wide, tall),
                cornerRadius = corner,
                alpha = 1f - 0.62f * dim * (1f - scene.glow[i])
            )
            x += wide + gap
        }
    }
}
