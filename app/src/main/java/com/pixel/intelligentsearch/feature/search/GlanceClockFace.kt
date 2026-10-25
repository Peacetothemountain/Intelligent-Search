package com.pixel.intelligentsearch.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import com.pixel.intelligentsearch.feature.settings.ActionIconMotion
import com.pixel.intelligentsearch.feature.settings.MaterialMorphShapes
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Test tag on the clock in the At a Glance card's corner. */
internal const val GLANCE_CLOCK_TAG = "glance_clock"

/** The clock face's size beside the heading. */
internal val GLANCE_CLOCK_SIZE: Dp = 64.dp

/** Where a clock's hands and seconds marker point, in degrees clockwise from 12. */
internal data class ClockAngles(val hour: Float, val minute: Float, val second: Float)

/**
 * The clock's angles at [epochMs] in [zone]: the hour hand between the hours as the minutes pass, the minute hand on
 * the whole minute (it moves once a minute), and the seconds marker on the second, or at 12 when [secondsStill].
 */
internal fun clockAngles(epochMs: Long, zone: ZoneId, secondsStill: Boolean = false): ClockAngles {
    val time = Instant.ofEpochMilli(epochMs).atZone(zone)
    val minute = time.minute
    return ClockAngles(
        hour = (time.hour % 12) * 30f + minute * 0.5f,
        minute = minute * 6f,
        second = if (secondsStill) 0f else time.second * 6f
    )
}

/** "Current time, 8:05 PM" (or "20:05"), in the phone's 12 or 24 hour style, for TalkBack. */
internal fun clockDescription(time: String): String = "Current time, $time"

/** A face the At a Glance clock can wear, as Clock Style stores it ([storedValue]) and names it ([label]). */
internal enum class GlanceClockStyle(val storedValue: String, val label: String) {
    /** Twelve soft scallops (MaterialShapes' Cookie12Sided), two round hands and a seconds dot. */
    COOKIE("cookie", "Cookie"),

    /** An eight-pointed soft star (MaterialShapes' Sunny), with the same hands and dot. */
    SUNNY("sunny", "Sunny"),

    /** A soft square (MaterialShapes' Square) with a mark at each quarter and the same hands. */
    SQUARE("square", "Square"),

    /** The hour over the minutes in bold figures on the soft square, stacked as Android's lock screen stacks them. */
    DIGITAL("digital", "Digital");

    companion object {
        /** The style stored as [value]; anything unknown (an older or edited backup) reads as [COOKIE]. */
        fun from(value: String?): GlanceClockStyle = entries.firstOrNull { it.storedValue == value } ?: COOKIE
    }
}

/**
 * The one-shot animation a face plays in the Clock Style picker when it is chosen, as pure curves of its progress (0
 * to 1, linear in time): the face turns one step of its own symmetry (a Cookie scallop, a Sunny point, a quarter of
 * the square) with a small pop, while the hands sweep an hour forward and the seconds dot laps once, so all of it
 * lands back on the 10:10 it started from. Outside (0, 1) everything is at rest.
 */
internal object ClockFaceMotion {
    const val DURATION_MILLIS = 900
    const val MAX_POP = 1.08f

    /** How far the face's shape is turned, in degrees; it ends a whole symmetry step round, which looks unturned. */
    fun faceTurn(style: GlanceClockStyle, progress: Float): Float {
        if (progress <= 0f || progress >= 1f) return 0f
        val step = when (style) {
            GlanceClockStyle.COOKIE -> 30f
            GlanceClockStyle.SUNNY -> 45f
            GlanceClockStyle.SQUARE, GlanceClockStyle.DIGITAL -> 90f
        }
        return step * ActionIconMotion.springStep(ActionIconMotion.segment(progress, 0f, 0.75f))
    }

    /** How much the whole face is scaled up, peaking early and back to 1. */
    fun pop(progress: Float): Float {
        if (progress <= 0f || progress >= 1f) return 1f
        val u = ActionIconMotion.segment(progress, 0f, 0.6f)
        return 1f + (MAX_POP - 1f) * ActionIconMotion.bell(1f - (1f - u) * (1f - u))
    }

    /** How far through its hour sweep the hands are: the minute hand adds 360 times this, the hour hand 30. */
    fun handSweep(progress: Float): Float {
        if (progress <= 0f || progress >= 1f) return 0f
        return ActionIconMotion.springStep(ActionIconMotion.segment(progress, 0.05f, 0.9f))
    }

    /** How far round its extra lap the seconds dot is, in degrees. */
    fun secondsLap(progress: Float): Float {
        if (progress <= 0f || progress >= 1f) return 0f
        return 360f * ActionIconMotion.springStep(ActionIconMotion.segment(progress, 0.1f, 1f))
    }
}

/** The Material shape a face is cut from. */
private fun faceShape(style: GlanceClockStyle): RoundedPolygon = MaterialMorphShapes.byName.getValue(
    when (style) {
        GlanceClockStyle.COOKIE -> "Cookie12Sided"
        GlanceClockStyle.SUNNY -> "Sunny"
        GlanceClockStyle.SQUARE, GlanceClockStyle.DIGITAL -> "Square"
    }
)

/** [shape] (normalized to 0..1) scaled to a [width] × [height] box, as a path; built once per size. */
private fun shapePath(shape: RoundedPolygon, width: Float, height: Float): Path {
    val path = Path()
    val cubics = shape.cubics
    if (cubics.isEmpty()) return path
    path.moveTo(cubics.first().anchor0X * width, cubics.first().anchor0Y * height)
    for (c in cubics) {
        path.cubicTo(c.control0X * width, c.control0Y * height, c.control1X * width, c.control1Y * height, c.anchor1X * width, c.anchor1Y * height)
    }
    path.close()
    return path
}

/**
 * The hour and minutes the digital face shows at [epochMs] in [zone]: in the phone's 12 hour style the hour has no
 * leading zero (12 at noon and midnight), in 24 hour style it is two figures; the minutes are always two.
 */
internal fun digitalClockFigures(epochMs: Long, zone: ZoneId, is24Hour: Boolean): Pair<String, String> {
    val time = Instant.ofEpochMilli(epochMs).atZone(zone)
    val hour = if (is24Hour) "%02d".format(time.hour) else (time.hour % 12).let { if (it == 0) 12 else it }.toString()
    return hour to "%02d".format(time.minute)
}

/**
 * A small Material clock for the At a Glance card's corner, in one of the [GlanceClockStyle] faces: a shape a tone
 * lighter than the card with two thick round-capped hands in the theme's primary (the hour hand shorter) and, on the
 * Cookie and Sunny faces, a tertiary dot circling near the edge for the seconds; or the Digital face's stacked
 * figures. It shows the phone's local time; the hands move each minute and the dot each second, only while the search
 * is on screen. With animations off it moves once a minute and the dot rests at 12. [motion] is the progress of the
 * Clock Style picker's [ClockFaceMotion], read only while drawing; elsewhere it stays at rest.
 */
@Composable
internal fun GlanceClockFace(
    modifier: Modifier = Modifier,
    size: Dp = GLANCE_CLOCK_SIZE,
    style: GlanceClockStyle = GlanceClockStyle.COOKIE,
    motion: () -> Float = { 1f }
) {
    val clock = LocalGlanceClock.current
    val animate = rememberAnimationsEnabled()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val showsSeconds = style == GlanceClockStyle.COOKIE || style == GlanceClockStyle.SUNNY
    // A face without a seconds dot only needs to wake on the minute.
    val period = if (animate.value && showsSeconds) 1_000L else 60_000L
    // Read only while drawing, so a tick redraws the face without recomposing it.
    val now = produceState(clock.now(), clock, period, lifecycle) {
        value = clock.now()
        if (!clock.ticks) return@produceState
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = clock.now()
                delay(period - clock.now() % period)
            }
        }
    }
    val zone = remember { ZoneId.systemDefault() }
    // Worked out once per tick, not per frame the card redraws (as it resizes).
    val angles = remember(zone) { derivedStateOf { clockAngles(now.value, zone, secondsStill = !animate.value) } }
    val context = LocalContext.current
    val minuteOfDay = now.value / 60_000L
    val description = remember(minuteOfDay) {
        clockDescription(android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(minuteOfDay * 60_000L)))
    }

    val scheme = MaterialTheme.colorScheme
    val face = scheme.surfaceContainerHighest
    val hourColor = scheme.primary
    val minuteColor = lerp(face, scheme.primary, 0.78f).compositeOver(face)
    val dotColor = scheme.tertiary
    val shape = remember(style) { faceShape(style) }
    val faceModifier = modifier
        .size(size)
        .testTag(GLANCE_CLOCK_TAG)
        .semantics { contentDescription = description }

    if (style == GlanceClockStyle.DIGITAL) {
        val is24Hour = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
        val (hour, minutes) = remember(minuteOfDay, is24Hour) { digitalClockFigures(minuteOfDay * 60_000L, zone, is24Hour) }
        val density = LocalDensity.current
        // Sized to the face, not the user's text size: the face is a fixed picture of the time, and TalkBack reads it.
        val figures = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = with(density) { (size * 0.3f).toSp() } / density.fontScale,
            lineHeight = with(density) { (size * 0.31f).toSp() } / density.fontScale,
            textAlign = TextAlign.Center
        )
        Box(
            modifier = faceModifier.drawWithCache {
                val path = shapePath(shape, this.size.width, this.size.height)
                val centre = Offset(this.size.width / 2f, this.size.height / 2f)
                onDrawBehind {
                    val p = motion()
                    val pop = ClockFaceMotion.pop(p)
                    withTransform({
                        rotate(ClockFaceMotion.faceTurn(style, p), centre)
                        scale(pop, pop, centre)
                    }) { drawPath(path, face) }
                }
            },
            contentAlignment = Alignment.Center
        ) {
            // The figures pop with the face but stay upright.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    val pop = ClockFaceMotion.pop(motion())
                    scaleX = pop
                    scaleY = pop
                }
            ) {
                Text(hour, style = figures, color = hourColor, maxLines = 1)
                Text(minutes, style = figures, color = minuteColor, maxLines = 1)
            }
        }
        return
    }

    Spacer(
        modifier = faceModifier.drawWithCache {
            val path = shapePath(shape, this.size.width, this.size.height)
            val centre = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.minDimension / 2f
            val handWidth = radius * 0.2f
            val markRadius = radius * 0.075f
            val markInset = radius * 0.74f
            onDrawBehind {
                val hands = angles.value
                val p = motion()
                val pop = ClockFaceMotion.pop(p)
                val sweep = ClockFaceMotion.handSweep(p)
                scale(pop, pop, centre) {
                    // The shape and its quarter marks turn together; the hands keep telling the time.
                    rotate(ClockFaceMotion.faceTurn(style, p), centre) {
                        drawPath(path, face)
                        if (style == GlanceClockStyle.SQUARE) {
                            // A dot at 12, 3, 6 and 9, set in from the square's flat sides.
                            for (k in 0 until 4) {
                                val a = k * PI.toFloat() / 2f
                                drawCircle(minuteColor, radius = markRadius, center = Offset(centre.x + sin(a) * markInset, centre.y - cos(a) * markInset))
                            }
                        }
                    }
                    drawHand(centre, hands.minute + 360f * sweep, radius * 0.56f, handWidth, minuteColor)
                    drawHand(centre, hands.hour + 30f * sweep, radius * 0.38f, handWidth, hourColor)
                    if (showsSeconds) {
                        val s = (hands.second + ClockFaceMotion.secondsLap(p)) / 180f * PI.toFloat()
                        val orbit = radius * 0.66f
                        drawCircle(dotColor, radius = radius * 0.11f, center = Offset(centre.x + sin(s) * orbit, centre.y - cos(s) * orbit))
                    }
                }
            }
        }
    )
}

/** A round-capped hand from [centre], [length] long, pointing [degrees] clockwise from 12. */
private fun DrawScope.drawHand(centre: Offset, degrees: Float, length: Float, width: Float, color: Color) {
    val a = degrees / 180f * PI.toFloat()
    drawLine(color, centre, Offset(centre.x + sin(a) * length, centre.y - cos(a) * length), strokeWidth = width, cap = StrokeCap.Round)
}
