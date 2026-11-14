package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The battery level as a small tank of water in Material You color, with sea life in it.
 *
 * The water glides to each new reading, rolls harder the more watts are going in, and turns to the theme's warning
 * colors at 20% and 10%. Bubbles rise from the floor only while charging. The fuller the battery, the more company:
 * a starfish stays to the last, a fish joins at about a third, a puffer fish at 60% and an octopus at about 65%.
 * Kelp grows with the level. Tapping a creature plays its trick, and plugging in plays them all.
 *
 * With [animate] off (Remove animations) everything holds a calm pose at the true level.
 */
@Composable
internal fun BatterySeaTank(level: Int, charging: Boolean, watts: Float?, animate: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    // The app's theme, which Settings can set apart from the system's.
    val isDark = scheme.surface.luminance() < 0.5f
    val tones = remember(scheme, isDark) { seaTones(scheme, isDark) }
    val scene = remember { SeaScene() }
    val painter = remember { SeaPainter() }
    // Read only where the tank draws, so a frame redraws the tank and recomposes nothing.
    val frame = remember { mutableLongStateOf(0L) }
    val reading = rememberUpdatedState(SeaReading(level, charging, watts ?: SeaScene.UNKNOWN_WATTS))
    if (animate) {
        LaunchedEffect(scene) {
            while (true) withInfiniteAnimationFrameNanos { now ->
                val r = reading.value
                scene.frame(now, r.level, r.charging, r.watts)
                frame.longValue = now
            }
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.45f))
            .then(
                if (animate) Modifier.pointerInput(scene) { detectTapGestures { scene.tap(it.x / density, it.y / density) } }
                else Modifier
            )
            .clearAndSetSemantics { contentDescription = "Battery $level%" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // The scene is laid out in dp, so it is the same on every screen density.
            val w = size.width / density
            val h = size.height / density
            scene.resize(w, h)
            // Until the first frame arrives, and with animations off, the still picture.
            if (!animate || frame.longValue == 0L) scene.settle(w, h, level)
            scale(density, density, Offset.Zero) { with(painter) { paint(scene, w, h, tones[scene.waterBand]) } }
        }
        Text(
            text = "$level%",
            style = MaterialTheme.typography.titleLarge.copy(
                shadow = Shadow(color = Color.Black.copy(alpha = 0.45f), offset = Offset(0f, 1f), blurRadius = 6f)
            ),
            fontWeight = FontWeight.ExtraBold,
            color = tones[0].ink
        )
    }
}

/** What the tank is showing: the level, whether charge is going in, and how many watts. */
private class SeaReading(val level: Int, val charging: Boolean, val watts: Float)

/** The tank's colors for one color of water: the creatures, and the plants as shades between the water and them. */
internal class SeaTones(val water: Color, val fish: Color, val puffer: Color, val star: Color, val octopus: Color, val ink: Color) {
    val kelpFar = lerp(water, fish, 0.30f)
    val kelpNear = lerp(water, fish, 0.36f)
    val grass = lerp(water, fish, 0.5f)
    val coral = lerp(water, star, 0.62f)
    val anemone = lerp(water, star, 0.8f)
    val sand = lerp(water, DEEP, 0.11f)
    val pebble = lerp(water, DEEP, 0.2f)

    private companion object {
        val DEEP = Color(0xFF000C28)
    }
}

/**
 * The tones for normal, low (20%) and critical (10%) water, in that order. The starfish is the one left at the
 * warning colors, so its tone stands apart from all three.
 */
internal fun seaTones(scheme: ColorScheme, isDark: Boolean): Array<SeaTones> {
    // Tone ~65 of the user's palette: balanced between light and dark, never yellowish-white.
    val water = lerp(scheme.primary, scheme.primaryContainer, if (isDark) 0.35f else 0.50f)
    val ink = if (isDark) Color.White else scheme.onPrimaryContainer
    fun tones(liquid: Color) = SeaTones(
        water = liquid,
        fish = if (isDark) scheme.primaryContainer else scheme.surfaceContainerLowest,
        puffer = if (isDark) scheme.onPrimaryContainer else lerp(scheme.primary, scheme.onPrimaryContainer, 0.35f),
        star = scheme.tertiaryContainer,
        octopus = if (isDark) scheme.onTertiaryContainer else lerp(scheme.tertiary, scheme.onTertiaryContainer, 0.35f),
        ink = ink
    )
    return arrayOf(tones(water), tones(scheme.tertiary), tones(scheme.error))
}

private const val TAU = 6.2831855f
private const val PI = 3.1415927f
private const val DEGREES = 57.29578f

private fun smooth(u: Float): Float {
    val c = u.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

private fun easeOut(u: Float): Float {
    val r = 1f - u.coerceIn(0f, 1f)
    return 1f - r * r * r
}

/** How puffed the puffer fish is through its cycle: calm, a small breath in, out with a bounce, a held tremble, air let go. */
internal fun puffAt(t: Float): Float = when {
    t < 4.6f -> 0f
    t < 4.78f -> -0.14f * smooth((t - 4.6f) / 0.18f)
    t < 5.25f -> {
        val u = (t - 4.78f) / 0.47f - 1f
        -0.14f + 1.14f * (1f + 3.2f * u * u * u + 2.2f * u * u)
    }
    t < SeaScene.PUFF_OUT -> 1f + 0.025f * sin(t * 34f) * sin(PI * (t - 5.25f) / (SeaScene.PUFF_OUT - 5.25f))
    t < SeaScene.PUFF_OUT + 0.55f -> 1f - smooth((t - SeaScene.PUFF_OUT) / 0.55f)
    else -> 0f
}

/** How far up its climb the octopus is through its cycle: three pushes upward, then a slow fall. */
internal fun octopusRise(t: Float): Float =
    if (t < 3f) {
        val push = floor(t)
        val tau = t - push
        push * 0.33f + 0.38f * easeOut(tau / 0.55f) - 0.05f * smooth((tau - 0.55f) / 0.45f)
    } else {
        0.99f * (1f - smooth((t - 3f) / (SeaScene.OCTOPUS_CYCLE - 3f)))
    }

/** How wide the octopus's arms are spread: snapped shut on each push, opening as it slows, widest on the way down. */
internal fun octopusSpread(t: Float): Float =
    if (t < 3f) {
        val tau = t - floor(t)
        if (tau < 0.14f) 1f - 0.9f * smooth(tau / 0.14f) else 0.1f + 0.9f * smooth((tau - 0.14f) / 0.86f)
    } else {
        1f + 0.25f * sin(PI * (t - 3f) / (SeaScene.OCTOPUS_CYCLE - 3f))
    }

/** How stretched the octopus's head is by a push. */
internal fun octopusStretch(t: Float): Float = if (t < 3f) sin(PI * min(1f, (t - floor(t)) / 0.4f)) else 0f

/**
 * Everything in the tank and how it moves, in dp, apart from how it is drawn. One [frame] per display frame while
 * animations are on; [settle] for the still picture.
 */
internal class SeaScene(private val random: Random = Random.Default) {

    /** A creature: how far it has arrived (springing a touch past 1 as it pops in) and where it is. */
    class Dweller {
        var presence = 0f
        internal var speed = 0f
        var x = 0f
        var y = 0f
        internal var lane = -1f
        val present get() = presence > 0.5f
        val alpha get() = presence.coerceIn(0f, 1f)
        val pop get() = 0.6f + 0.4f * presence
    }

    class Bubble(var x: Float, var y: Float, val r: Float, val rise: Float, var wobble: Float)
    class Ripple(val x: Float, val r: Float, var age: Float)

    /** The level the water is drawn at, gliding to each reading. */
    var shown = 0f
        private set
    private var velocity = 0f
    var phase = 0f
        private set
    var phase2 = 0f
        private set
    /** 0 to 1: how lively the water is, from the watts going in. */
    var energy = 0f
        private set
    var amp = 0f
        private set
    /** The water line's resting height, measured from the top. */
    var surface = 0f
        private set
    var time = 0f
        private set
    var still = true
        private set
    private var w = 0f
    private var h = 0f
    private var lastNanos = 0L
    private var wasCharging = false
    private var hadReading = false
    private var spawn = 0f

    val bubbles = ArrayList<Bubble>()
    val ripples = ArrayList<Ripple>()
    val fish = Dweller()
    val puffer = Dweller()
    val star = Dweller()
    val octopus = Dweller()

    var fishTurn = -0.47f
        private set
    var fishWag = 0f
        private set
    var fishLoop = -1f
        private set
    private var fishNext = 3.5f
    var fishFace = 1f
        private set
    var fishAngle = 0f
        private set
    var pufferClock = 2.6f
        private set
    var pufferRadius = 4.8f
        private set
    var pufferSpike = 0.25f
        private set
    var pufferPuff = 0f
        private set
    var starScale = 1f
        private set
    var starTurn = 0.1f
        private set
    var starSpin = 0f
        private set
    var starHop = -1f
        private set
    private var starHopWait = -1f
    var octopusClock = 0f
        private set
    /** 1 open, down to 0.15 while the starfish is beside it. */
    var anemoneOpen = 1f
        private set

    /** 0 normal water, 1 low (20% and under), 2 critical (10% and under). */
    val waterBand: Int get() = if (shown <= 10f) 2 else if (shown <= 20f) 1 else 0

    /** The water line at [px] across a tank [width] wide. */
    fun surfaceAt(px: Float, width: Float = w): Float =
        surface + sin(px / width * TAU + phase) * amp + sin(px / width * TAU * 2.3f - phase * 0.7f) * amp * 0.3f

    /** The tank's size, known once it has been laid out. */
    fun resize(w: Float, h: Float) {
        this.w = w
        this.h = h
    }

    /** Moves the scene on to the display frame at [nanos]. Nothing moves until the tank has a size. */
    fun frame(nanos: Long, level: Int, charging: Boolean, watts: Float) {
        val dt = if (lastNanos == 0L) 0f else ((nanos - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = nanos
        if (w > 0f && h > 0f) advance(dt, w, h, level, charging, watts)
    }

    /** Moves the scene on by [dt] seconds. */
    fun advance(dt: Float, w: Float, h: Float, level: Int, charging: Boolean, watts: Float) =
        update(dt, w, h, level, charging, watts, still = false)

    /** The still picture: the true level, everyone who fits, nobody moving. */
    fun settle(w: Float, h: Float, level: Int) = update(0f, w, h, level, charging = false, watts = 0f, still = true)

    /** Everyone's trick, one after another: played when the charger goes in. */
    fun cheer() {
        fishNext = 0f
        pufferClock = 4.35f
        octopusClock = 0f
        starHopWait = 0.45f
    }

    /** A tap at ([x], [y]): the creature there plays its trick; open water gives a few bubbles. */
    fun tap(x: Float, y: Float) {
        if (still) return
        var hit: Dweller? = null
        var best = TAP_REACH
        for (dweller in arrayOf(fish, puffer, star, octopus)) {
            val d = hypot(dweller.x - x, dweller.y - y)
            if (dweller.present && d < best) {
                best = d
                hit = dweller
            }
        }
        when {
            hit === fish -> fishNext = 0f
            hit === puffer -> if (pufferClock < 4.6f || pufferClock >= PUFF_OUT + 0.55f) pufferClock = 4.6f
            hit === octopus -> octopusClock = 0f
            hit === star -> if (starHop < 0f) starHop = 0f
            y > surface + 6f -> repeat(4) {
                addBubble(x + (random.nextFloat() - 0.5f) * 8f, y + random.nextFloat() * 5f, 1f + random.nextFloat() * 1.6f, 20f + random.nextFloat() * 16f)
            }
        }
    }

    private fun addBubble(x: Float, y: Float, r: Float, rise: Float) {
        if (bubbles.size < MAX_BUBBLES) bubbles += Bubble(x, y, r, rise, random.nextFloat() * TAU)
    }

    /** A creature arrives and leaves on a spring, so it pops in a touch past full size and settles. */
    private fun glide(dweller: Dweller, on: Boolean, dt: Float) {
        val target = if (on) 1f else 0f
        if (still) {
            dweller.presence = target
            dweller.speed = 0f
        } else {
            dweller.speed += (110f * (target - dweller.presence) - 14f * dweller.speed) * dt
            dweller.presence += dweller.speed * dt
        }
    }

    private fun follow(dweller: Dweller, lane: Float, dt: Float) {
        if (dweller.lane < 0f || dweller.presence < 0.02f || still) dweller.lane = lane
        else dweller.lane += (lane - dweller.lane) * min(1f, dt * 2.6f)
    }

    private fun update(dt: Float, w: Float, h: Float, level: Int, charging: Boolean, watts: Float, still: Boolean) {
        this.still = still
        this.w = w
        this.h = h
        if (still) {
            shown = level.toFloat()
            velocity = 0f
            energy = 0f
            time = 0f
            bubbles.clear()
            ripples.clear()
        } else {
            velocity += (60f * (level - shown) - 13f * velocity) * dt        // a soft spring
            shown += velocity * dt
            val want = if (charging) (watts / 30f).coerceIn(0.15f, 1f) else 0.08f
            energy += (want - energy) * min(1f, dt * 3f)
            // Wrapped where every use of them comes round to a whole number of turns, so they never lose precision.
            phase = (phase + dt * (0.9f + 3.4f * energy)) % (TAU * 100f)
            phase2 = (phase2 + dt * (0.6f + 2.1f * energy)) % (TAU * 100f)
            time += dt
            if (charging && !wasCharging && hadReading) cheer()
            wasCharging = charging
            if (level > 0) hadReading = true
        }
        amp = if (still) 0f else 1.2f + 4.2f * energy
        surface = h - h * max(0.05f, shown / 100f)
        val depth = h - surface
        val live = !still

        if (live && charging) {
            spawn += dt * (0.6f + min(watts, 45f) * 0.22f)
            while (spawn > 1f) {
                spawn -= 1f
                addBubble(8f + random.nextFloat() * (w - 16f), h + 4f, 1.2f + random.nextFloat() * 2.2f, 16f + random.nextFloat() * 22f + min(watts, 45f) * 0.8f)
            }
        }
        if (live) {
            var i = bubbles.size - 1
            while (i >= 0) {
                val bubble = bubbles[i]
                bubble.y -= bubble.rise * dt
                bubble.wobble += dt * 3f
                if (bubble.y < surface + 4f) {
                    bubbles.removeAt(i)
                    if (ripples.size < MAX_RIPPLES) ripples += Ripple(bubble.x, bubble.r, 0f)   // where it reaches the surface
                }
                i--
            }
            i = ripples.size - 1
            while (i >= 0) {
                ripples[i].age += dt
                if (ripples[i].age >= RIPPLE_SECONDS) ripples.removeAt(i)
                i--
            }
        }

        // Starfish: on the floor for as long as there is water to cover it. It rolls as far as it travels.
        val starFit = min(1f, (depth - 3f) / 12f)
        glide(star, starFit >= 0.45f, dt)
        if (starFit >= 0.45f) starScale += (starFit - starScale) * if (still) 1f else min(1f, dt * 3f)
        val seg = TAU / 5f
        val starRange = max(1f, floor((w / 2f - 11f) / (seg * STAR_RADIUS))) * seg * STAR_RADIUS
        if (live) {
            starTurn += dt * 0.15f * (0.8f + 0.6f * energy) * min(1f, 53f / starRange) * if (starHop >= 0f) 0.2f else 1f
            if (starHopWait >= 0f) {
                starHopWait -= dt
                if (starHopWait < 0f && starHop < 0f) starHop = 0f
            }
            if (starHop >= 0f) {
                starHop += dt / 0.85f
                if (starHop >= 1f) starHop = -1f
            }
        }
        star.x = w / 2f + starRange * sin(starTurn)
        val roll = (star.x - w / 2f) / STAR_RADIUS
        var lowestArm = ((roll - PI) % seg + seg) % seg                      // how far its lowest arm is from straight down
        if (lowestArm > seg / 2f) lowestArm -= seg
        val hop = if (starHop >= 0f) starHop else 0f
        val starSize = starScale * star.pop
        star.y = h - 1.5f - (STAR_RADIUS + 1f) * starSize * cos(lowestArm) - 9f * starSize * sin(PI * hop)
        starSpin = roll + TAU * smooth(hop)

        // The anemone tucks its arms in while the starfish is close, and opens again more slowly.
        val close = star.present && abs(star.x - w * ANEMONE_AT) < 13f
        anemoneOpen += ((if (close) 0.15f else 1f) - anemoneOpen) * if (still) 1f else min(1f, dt * if (close) 9f else 2.2f)

        // Fish: the lane under the number. It turns round as it slows at each end, and loops the loop now and then.
        val fishOn = surface + 14f <= h - 20f
        glide(fish, fishOn, dt)
        if (fishOn) follow(fish, min(h - 20f, max(h - 25f, surface + 14f)), dt)
        val fishLength = FISH_LENGTH * fish.pop
        val fishRange = max(0f, w / 2f - fishLength * 0.62f - 5f)
        if (live) {
            val looping = fishLoop >= 0f
            fishTurn += dt * 0.42f * (0.6f + 1.1f * energy) * min(1f, 48f / max(1f, fishRange)) * if (looping) 0.3f else 1f
            fishWag += dt * (5f + 10f * abs(cos(fishTurn)) * (0.45f + energy) + if (looping) 9f else 0f)
            if (looping) {
                fishLoop += dt / 1.2f
                if (fishLoop >= 1f) fishLoop = -1f
            } else {
                fishNext -= dt
                if (fishNext <= 0f && fish.presence > 0.9f && abs(cos(fishTurn)) > 0.8f && fish.lane - 15f >= surface + 7f) {
                    fishLoop = 0f
                    fishNext = 6f + random.nextFloat() * 4f
                }
            }
        }
        val heading = (cos(fishTurn) * 8f).coerceIn(-1f, 1f)
        val direction = if (heading < 0f) -1f else 1f
        fishFace = direction * (1f - (1f - abs(heading)).pow(2))
        var fishX = w / 2f + fishRange * sin(fishTurn)
        var fishY = fish.lane + if (still) 0f else sin(fishTurn * 2.3f) * 1.2f
        fishY = min(max(fishY, surfaceAt(fishX) + fishLength * 0.3f + 2f), h - fishLength * 0.26f)
        fishAngle = 0f
        if (fishLoop >= 0f) {
            fishAngle = TAU * smooth(fishLoop)
            fishX += 5.2f * sin(fishAngle) * direction
            fishY -= 5.2f * (1f - cos(fishAngle))
        }
        fish.x = fishX
        fish.y = fishY

        // Puffer fish: the left side, near the surface.
        val pufferOn = surface + 17f <= h / 2f + 6f
        glide(puffer, pufferOn, dt)
        if (pufferOn) follow(puffer, min(h / 2f + 6f, surface + 17f), dt)
        if (live) {
            val before = pufferClock
            pufferClock = (pufferClock + dt) % PUFF_CYCLE
            // Its air leaves as a few bubbles of its own.
            if (puffer.presence > 0.9f && before < PUFF_OUT && pufferClock >= PUFF_OUT) repeat(3) {
                addBubble(w * PUFFER_AT + 10f + random.nextFloat() * 3f, puffer.lane + random.nextFloat() * 4f, 0.8f + random.nextFloat() * 0.8f, 17f + random.nextFloat() * 12f)
            }
        }
        pufferPuff = if (still) 0f else puffAt(pufferClock)
        pufferRadius = 4.8f + 4.4f * pufferPuff
        pufferSpike = max(0f, 0.25f + 2.5f * pufferPuff)
        // Pushed back as the air leaves.
        val recoil = if (live && pufferClock >= PUFF_OUT && pufferClock < PUFF_OUT + 0.55f) sin(PI * (pufferClock - PUFF_OUT) / 0.55f) else 0f
        puffer.x = w * PUFFER_AT + sin(time * 0.45f) * 2f - 3f * recoil
        puffer.y = max(puffer.lane + sin(time * 1.1f) * 1.6f, surfaceAt(puffer.x) + pufferRadius + pufferSpike + 1.5f)

        // Octopus: the right side, when there is room to climb.
        glide(octopus, surface + 15f <= h / 2f + 1f, dt)
        if (live) octopusClock = (octopusClock + dt * (0.85f + 0.5f * energy)) % OCTOPUS_CYCLE
        val top = surface + 15f
        val bottom = h / 2f + 9f
        octopus.x = w * OCTOPUS_AT + sin(time * 0.5f) * 2f
        octopus.y = max(bottom - octopusRise(octopusPose) * (bottom - top), surfaceAt(octopus.x) + 9f)
    }

    /** Where the octopus is in its cycle; the still picture catches it part of the way down, arms spread. */
    val octopusPose: Float get() = if (still) 4.6f else octopusClock

    companion object {
        const val PUFF_CYCLE = 8.2f
        const val PUFF_OUT = 6.9f
        const val OCTOPUS_CYCLE = 7.2f
        const val STAR_RADIUS = 6f
        const val FISH_LENGTH = 19f
        const val PUFFER_AT = 0.13f
        const val OCTOPUS_AT = 0.87f
        const val ANEMONE_AT = 0.74f
        const val RIPPLE_SECONDS = 0.34f
        /** The watts taken for a charging phone that doesn't report them. */
        const val UNKNOWN_WATTS = 6f
        private const val TAP_REACH = 15f
        private const val MAX_BUBBLES = 48
        private const val MAX_RIPPLES = 12
    }
}

/** Draws a [SeaScene]. Its path and strokes are made once, so a frame allocates nothing. */
internal class SeaPainter {
    private val path = Path()
    private val thin = Stroke(width = 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val fin = Stroke(width = 0.8f, cap = StrokeCap.Round)
    private val fishEdge = Stroke(width = SeaScene.FISH_LENGTH * 0.07f, join = StrokeJoin.Round)
    private val gill = Stroke(width = SeaScene.FISH_LENGTH * 0.035f, cap = StrokeCap.Round)
    private val stem = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val starEdge = Stroke(width = SeaScene.STAR_RADIUS * 0.34f, join = StrokeJoin.Round)
    private val arm = Stroke(width = 2.3f, cap = StrokeCap.Round)
    private val branch = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val kelpX = FloatArray(10)
    private val kelpY = FloatArray(10)

    fun DrawScope.paint(scene: SeaScene, w: Float, h: Float, tones: SeaTones) {
        wave(scene, w, h, scene.amp * 1.25f, scene.phase2 + 1.7f, 0.8f, tones.water, 0.45f)
        wave(scene, w, h, scene.amp, scene.phase, 1f, tones.water, 1f)
        garden(scene, w, h, tones)
        star(scene, tones)
        fish(scene, tones)
        puffer(scene, tones)
        octopus(scene, tones)
        for (i in scene.bubbles.indices) {
            val bubble = scene.bubbles[i]
            drawCircle(
                color = Color.White,
                radius = bubble.r,
                center = Offset(bubble.x + sin(bubble.wobble) * 1.6f, bubble.y),
                alpha = 0.35f * min(1f, (bubble.y - scene.surface) / 14f).coerceAtLeast(0f)
            )
        }
        for (i in scene.ripples.indices) {
            val ripple = scene.ripples[i]
            val u = ripple.age / SeaScene.RIPPLE_SECONDS
            val r = ripple.r + 3.5f * easeOut(u)
            drawOval(Color.White, Offset(ripple.x - r, scene.surfaceAt(ripple.x, w) + 0.5f - r * 0.45f), Size(r * 2f, r * 0.9f), alpha = 0.45f * (1f - u), style = thin)
        }
    }

    private fun DrawScope.wave(scene: SeaScene, w: Float, h: Float, a: Float, p: Float, k: Float, color: Color, alpha: Float) {
        path.rewind()
        path.moveTo(0f, h)
        var x = 0f
        while (x <= w + 3f) {
            path.lineTo(x, scene.surface + sin(x / w * TAU * k + p) * a + sin(x / w * TAU * k * 2.3f - p * 0.7f) * a * 0.3f)
            x += 3f
        }
        path.lineTo(w + 3f, h)
        path.close()
        drawPath(path, color, alpha)
    }

    /** The floor: sand, pebbles, kelp that grows with the charge, sea grass, a coral and an anemone, swaying with the water. */
    private fun DrawScope.garden(scene: SeaScene, w: Float, h: Float, tones: SeaTones) {
        val depth = h - scene.surface
        if (depth < 5f) return
        val sway = scene.phase2 * 1.2f
        val live = if (scene.still) 0f else 1f

        path.rewind()
        path.moveTo(0f, h + 1f)
        var x = 0f
        while (x <= w + 4f) {
            path.lineTo(x, h - 2.4f - sin(x / w * TAU * 1.5f + 1f) * 0.9f)
            x += 4f
        }
        path.lineTo(w + 4f, h + 1f)
        path.close()
        drawPath(path, tones.sand)

        kelp(w * 0.055f, 42f, 0.4f, tones.kelpFar, depth, h, sway, live)       // furthest back
        kelp(w * 0.955f, 27f, 2.3f, tones.kelpNear, depth, h, sway, live)
        drawOval(tones.pebble, Offset(w * 0.43f - 3.4f, h - 3.8f), Size(6.8f, 4.4f))
        drawOval(tones.pebble, Offset(w * 0.475f - 2f, h - 2.5f), Size(4f, 2.8f))
        drawOval(tones.pebble, Offset(w * 0.9f - 2.6f, h - 3.1f), Size(5.2f, 3.6f))
        grass(scene, w * 0.125f, 11f, 0.7f, 4, tones.grass, depth, h, sway, live)
        grass(scene, w * 0.29f, 9f, 2.1f, 5, tones.grass, depth, h, sway, live)
        grass(scene, w * 0.86f, 7f, 4.2f, 3, tones.grass, depth, h, sway, live)
        coral(w * 0.6f, tones.coral, depth, h, sway, live)
        anemone(scene, w * SeaScene.ANEMONE_AT, tones.anemone, depth, h, sway, live)
    }

    /** A stem as tall as the water lets it be, each leaf swaying a beat behind the one below. */
    private fun DrawScope.kelp(bx: Float, full: Float, seed: Float, color: Color, depth: Float, h: Float, sway: Float, live: Float) {
        val height = min(full, depth - 5f)
        if (height < 6f) return
        val n = (full / 5.5f).roundToInt()
        val grown = 0.6f + 0.4f * height / full
        path.rewind()
        for (j in 0..n) {
            val u = j.toFloat() / n
            kelpX[j] = bx + live * sin(sway - u * 2.6f + seed) * height * 0.11f * u.pow(1.2f) + sin(u * 5f + seed) * 1.3f * u
            kelpY[j] = h - 1f - u * height
            if (j == 0) path.moveTo(kelpX[j], kelpY[j]) else path.lineTo(kelpX[j], kelpY[j])
        }
        drawPath(path, color, style = stem)
        for (j in 1..n) {
            val along = kotlin.math.atan2(kelpY[j] - kelpY[j - 1], kelpX[j] - kelpX[j - 1])
            val tip = j == n
            val turn = if (tip) along else along + (if (j % 2 == 1) 1f else -1f) * (0.95f + live * sin(sway * 1.3f + j + seed) * 0.2f)
            val length = (if (tip) 3.4f else 3.9f - j.toFloat() / n) * grown
            withTransform({
                translate(kelpX[j], kelpY[j])
                rotate(turn * DEGREES, Offset.Zero)
            }) {
                drawOval(color, Offset(length * -0.05f, length * -0.42f), Size(length * 2f, length * 0.84f))
            }
        }
    }

    /** A tuft of blades that leans away from the starfish as it rolls through. */
    private fun DrawScope.grass(scene: SeaScene, bx: Float, full: Float, seed: Float, blades: Int, color: Color, depth: Float, h: Float, sway: Float, live: Float) {
        val fit = ((depth - 4f) / full).coerceIn(0f, 1f)
        if (fit < 0.3f) return
        val height = full * fit
        val dx = bx - scene.star.x
        val lean = if (scene.star.present) (dx / 9f).coerceIn(-1f, 1f) * exp(-dx * dx / 100f) * 0.6f else 0f
        path.rewind()
        for (j in 0 until blades) {
            val f = j.toFloat() / (blades - 1) - 0.5f
            val a = f * 1.1f + lean + live * sin(sway * 1.15f + seed + j * 0.9f) * 0.2f
            val length = height * (1f - abs(f) * 0.55f + 0.12f * sin(seed * 7f + j * 2f))
            val root = bx + f * 2.4f
            path.moveTo(root, h - 0.5f)
            path.quadraticTo(root + sin(a * 0.3f) * length * 0.5f, h - 1.5f - length * 0.55f, root + sin(a) * length, h - 1.5f - cos(a) * length)
        }
        drawPath(path, color, style = stem)
    }

    private fun DrawScope.coral(bx: Float, color: Color, depth: Float, h: Float, sway: Float, live: Float) {
        val fit = ((depth - 4f) / 14f).coerceIn(0f, 1f)
        if (fit < 0.3f) return
        withTransform({
            translate(bx, h - 1f)
            scale(fit, fit, Offset.Zero)
            rotate(live * sin(sway * 0.8f) * 0.035f * DEGREES, Offset.Zero)
        }) {
            path.rewind()
            path.moveTo(0f, 0f); path.lineTo(0f, -5f); path.lineTo(-3.6f, -9.5f)
            path.moveTo(0f, -5f); path.lineTo(3.2f, -8f); path.lineTo(2.6f, -12.2f)
            path.moveTo(3.2f, -8f); path.lineTo(6.6f, -9.4f)
            path.moveTo(-2f, -7.5f); path.lineTo(-5.8f, -6.6f)
            drawPath(path, color, style = branch)
        }
    }

    private fun DrawScope.anemone(scene: SeaScene, bx: Float, color: Color, depth: Float, h: Float, sway: Float, live: Float) {
        val fit = ((depth - 4f) / 11f).coerceIn(0f, 1f)
        if (fit < 0.3f) return
        val open = scene.anemoneOpen
        withTransform({
            translate(bx, h - 1f)
            scale(fit, fit, Offset.Zero)
        }) {
            path.rewind()
            for (j in 0 until 6) {
                val f = j / 5f - 0.5f
                val a = f * 2f * (0.35f + 0.65f * open) + live * sin(sway * 1.7f + j * 1.1f) * 0.24f * open
                val length = (4.4f + 1.5f * cos(f * 3f)) * (0.25f + 0.75f * open)
                path.moveTo(f * 4.4f, -3.2f)
                path.quadraticTo(f * 4.4f + sin(a) * length * 0.3f, -3.2f - length * 0.6f, f * 4.4f + sin(a) * length, -3.2f - cos(a) * length)
            }
            drawPath(path, color, style = stem)
            path.rewind()
            path.moveTo(-3.6f, 0.5f)
            path.lineTo(-3.6f, -1.5f)
            path.quadraticTo(-3.6f, -4.4f, 0f, -4.4f)
            path.quadraticTo(3.6f, -4.4f, 3.6f, -1.5f)
            path.lineTo(3.6f, 0.5f)
            path.close()
            drawPath(path, color)
        }
    }

    /** Cartwheels along the floor, waves its top arm when it stops at an end, and hops with a spin. */
    private fun DrawScope.star(scene: SeaScene, tones: SeaTones) {
        val star = scene.star
        if (star.presence < 0.02f) return
        val radius = SeaScene.STAR_RADIUS
        val seg = TAU / 5f
        val spin = scene.starSpin
        val waving = if (scene.still) 0f else smooth((0.3f - abs(cos(scene.starTurn))) / 0.3f)
        var up = 0
        var best = 9f
        for (j in 0 until 5) {
            val a = ((spin + j * seg) % TAU + TAU) % TAU
            val off = min(a, TAU - a)
            if (off < best) {
                best = off
                up = j
            }
        }
        val size = scene.starScale * star.pop
        withTransform({
            translate(star.x, star.y)
            scale(size, size, Offset.Zero)
        }) {
            path.rewind()
            for (j in 0 until 5) {
                val a = spin + j * seg - PI / 2f
                val tip = a + if (j == up) sin(scene.time * 12f) * 0.3f * waving else 0f
                val inner = a + seg / 2f
                if (j == 0) path.moveTo(cos(tip) * radius, sin(tip) * radius) else path.lineTo(cos(tip) * radius, sin(tip) * radius)
                path.lineTo(cos(inner) * radius * 0.5f, sin(inner) * radius * 0.5f)
            }
            path.close()
            drawPath(path, tones.star, star.alpha)
            drawPath(path, tones.star, star.alpha, starEdge)
            withTransform({ rotate(spin * DEGREES, Offset.Zero) }) {
                drawCircle(tones.water, 0.7f, Offset(-radius * 0.2f, -radius * 0.04f), star.alpha)
                drawCircle(tones.water, 0.7f, Offset(radius * 0.2f, -radius * 0.04f), star.alpha)
            }
        }
    }

    private fun DrawScope.fish(scene: SeaScene, tones: SeaTones) {
        val fish = scene.fish
        if (fish.presence < 0.02f) return
        val length = SeaScene.FISH_LENGTH
        val wag = if (scene.still) 0f else sin(scene.fishWag)
        val face = if (abs(scene.fishFace) < 0.02f) 0.02f else scene.fishFace
        val alpha = fish.alpha
        withTransform({
            translate(fish.x, fish.y)
            scale(face * fish.pop, fish.pop, Offset.Zero)
            rotate((wag * 0.045f - scene.fishAngle) * DEGREES, Offset.Zero)
        }) {
            withTransform({
                translate(-length * 0.2f, 0f)
                rotate(wag * 0.42f * DEGREES, Offset.Zero)
            }) {
                path.rewind()
                path.moveTo(length * 0.02f, 0f)
                path.lineTo(-length * 0.27f, -length * 0.2f)
                path.quadraticTo(-length * 0.19f, 0f, -length * 0.27f, length * 0.2f)
                path.close()
                drawPath(path, tones.fish, alpha)
                drawPath(path, tones.fish, alpha, fishEdge)
            }
            path.rewind()
            path.moveTo(length * 0.2f, -length * 0.17f)
            path.quadraticTo(length * 0.02f, -length * 0.36f, -length * 0.12f, -length * 0.3f)
            path.lineTo(-length * 0.05f, -length * 0.14f)
            path.close()
            drawPath(path, tones.fish, alpha)
            drawPath(path, tones.fish, alpha, fishEdge)
            drawOval(tones.fish, Offset(length * -0.26f, length * -0.22f), Size(length * 0.72f, length * 0.44f), alpha)
            val eye = length * 0.05f
            val lid = if (!scene.still && (scene.time + 1.3f) % 4.3f < 0.12f) 0.15f else 1f
            drawOval(tones.water, Offset(length * 0.3f - eye, length * -0.05f - eye * lid), Size(eye * 2f, eye * 2f * lid), alpha)
            drawArc(tones.water, -51.6f, 103.2f, false, Offset(length * -0.05f, length * -0.09f), Size(length * 0.22f, length * 0.22f), alpha, gill)
        }
    }

    private fun DrawScope.puffer(scene: SeaScene, tones: SeaTones) {
        val puffer = scene.puffer
        if (puffer.presence < 0.02f) return
        val r = scene.pufferRadius
        val spike = scene.pufferSpike
        val puff = scene.pufferPuff
        val live = !scene.still
        val tail = if (live) sin(scene.time * 6f) * 1.1f else 0f
        val flap = if (live) sin(scene.time * 7f) * 0.25f else 0f
        val alpha = puffer.alpha
        withTransform({
            translate(puffer.x, puffer.y)
            scale(puffer.pop, puffer.pop, Offset.Zero)
            rotate(sin(scene.time * 0.9f) * 0.07f * DEGREES, Offset.Zero)
        }) {
            path.rewind()
            path.moveTo(-r * 0.8f, 0f)
            path.lineTo(-r - 3.4f, -2.3f + tail)
            path.lineTo(-r - 3.4f, 2.3f + tail)
            path.close()
            for (i in 0 until 11) {
                val a = i / 11f * TAU + 0.29f
                if (cos(a) > 0.72f) continue                                  // none across its face
                val half = 0.85f / r
                path.moveTo(cos(a - half) * r * 0.95f, sin(a - half) * r * 0.95f)
                path.lineTo(cos(a) * (r + spike), sin(a) * (r + spike))
                path.lineTo(cos(a + half) * r * 0.95f, sin(a + half) * r * 0.95f)
                path.close()
            }
            drawPath(path, tones.puffer, alpha)
            drawPath(path, tones.puffer, alpha, thin)
            drawCircle(tones.puffer, r, Offset.Zero, alpha)
            val finRadius = r * 0.33f
            drawArc(tones.water, (0.3f + flap) * DEGREES, 1.6f * DEGREES, false, Offset(-r * 0.1f - finRadius, r * 0.2f - finRadius), Size(finRadius * 2f, finRadius * 2f), alpha, fin)
            val eye = 0.95f + 0.6f * max(0f, puff)
            val lid = if (live && puff < 0.2f && (scene.time + 2.6f) % 5.1f < 0.12f) 0.15f else 1f
            drawOval(tones.water, Offset(r * 0.45f - eye, -r * 0.28f - eye * lid), Size(eye * 2f, eye * 2f * lid), alpha)
            if (puff > 0.25f) drawCircle(tones.water, 0.75f, Offset(r * 0.9f, r * 0.15f), alpha)
        }
    }

    private fun DrawScope.octopus(scene: SeaScene, tones: SeaTones) {
        val octopus = scene.octopus
        if (octopus.presence < 0.02f) return
        val pose = scene.octopusPose
        val spread = octopusSpread(pose)
        val stretch = octopusStretch(pose)
        val live = !scene.still
        val alpha = octopus.alpha
        withTransform({
            translate(octopus.x, octopus.y)
            scale(octopus.pop, octopus.pop, Offset.Zero)
        }) {
            path.rewind()
            for (i in 0 until 4) {
                val root = (i - 1.5f) * 2.6f
                val side = (i - 1.5f) / 1.5f
                val wiggle = if (live) sin(scene.time * 4.2f + i * 1.4f) * 1.3f * spread else 0f
                path.moveTo(root, 2.2f)
                path.quadraticTo(
                    root + side * spread * 1.2f - wiggle, 6.8f,
                    root + side * spread * 5f + wiggle, 2.2f + 8.2f * (1f - 0.3f * abs(side) * spread) + (1f - min(1f, spread)) * 1.5f
                )
            }
            drawPath(path, tones.octopus, alpha, arm)
            val rx = 5f * (1f - 0.1f * stretch)
            val ry = 5.4f * (1f + 0.18f * stretch)
            drawOval(tones.octopus, Offset(-rx, -0.6f - stretch * 0.8f - ry), Size(rx * 2f, ry * 2f), alpha)
            val lid = if (live && scene.time % 3.7f < 0.13f) 0.15f else 1f
            drawOval(tones.water, Offset(-1.9f - 0.95f, 0.4f - 0.95f * lid), Size(1.9f, 1.9f * lid), alpha)
            drawOval(tones.water, Offset(1.9f - 0.95f, 0.4f - 0.95f * lid), Size(1.9f, 1.9f * lid), alpha)
        }
    }
}
