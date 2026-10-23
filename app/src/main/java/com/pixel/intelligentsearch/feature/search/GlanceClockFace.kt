package com.pixel.intelligentsearch.feature.search

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
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
internal val GLANCE_CLOCK_SIZE: Dp = 42.dp

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

/** The Material cookie the face is cut from: MaterialShapes' Cookie12Sided, a circle with twelve soft scallops. */
private val cookie: RoundedPolygon by lazy { MaterialMorphShapes.byName.getValue("Cookie12Sided") }

/** The cookie scaled to a [width] × [height] box, as a path; built once per size. */
private fun cookiePath(width: Float, height: Float): Path {
    val path = Path()
    val cubics = cookie.cubics
    if (cubics.isEmpty()) return path
    path.moveTo(cubics.first().anchor0X * width, cubics.first().anchor0Y * height)
    for (c in cubics) {
        path.cubicTo(c.control0X * width, c.control0Y * height, c.control1X * width, c.control1Y * height, c.anchor1X * width, c.anchor1Y * height)
    }
    path.close()
    return path
}

/**
 * A small Material clock for the At a Glance card's corner: a scalloped cookie a tone lighter than the card, two
 * thick round-capped hands in the theme's primary (the hour hand shorter) and a tertiary dot circling near the
 * edge for the seconds. It shows the phone's local time; the hands move each minute and the dot each second, only
 * while the search is on screen. With animations off it moves once a minute and the dot rests at 12.
 */
@Composable
internal fun GlanceClockFace(modifier: Modifier = Modifier, size: Dp = GLANCE_CLOCK_SIZE) {
    val clock = LocalGlanceClock.current
    val animate = rememberAnimationsEnabled()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val period = if (animate.value) 1_000L else 60_000L
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
    Spacer(
        modifier = modifier
            .size(size)
            .testTag(GLANCE_CLOCK_TAG)
            .semantics { contentDescription = description }
            .drawWithCache {
                val path = cookiePath(this.size.width, this.size.height)
                val centre = Offset(this.size.width / 2f, this.size.height / 2f)
                val radius = this.size.minDimension / 2f
                val handWidth = radius * 0.2f
                onDrawBehind {
                    val hands = angles.value
                    drawPath(path, face)
                    drawHand(centre, hands.minute, radius * 0.56f, handWidth, minuteColor)
                    drawHand(centre, hands.hour, radius * 0.38f, handWidth, hourColor)
                    val s = hands.second / 180f * PI.toFloat()
                    val orbit = radius * 0.66f
                    drawCircle(dotColor, radius = radius * 0.11f, center = Offset(centre.x + sin(s) * orbit, centre.y - cos(s) * orbit))
                }
            }
    )
}

/** A round-capped hand from [centre], [length] long, pointing [degrees] clockwise from 12. */
private fun DrawScope.drawHand(centre: Offset, degrees: Float, length: Float, width: Float, color: Color) {
    val a = degrees / 180f * PI.toFloat()
    drawLine(color, centre, Offset(centre.x + sin(a) * length, centre.y - cos(a) * length), strokeWidth = width, cap = StrokeCap.Round)
}
