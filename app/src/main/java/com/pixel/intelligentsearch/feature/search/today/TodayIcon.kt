package com.pixel.intelligentsearch.feature.search.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** The sun's arc in the Today icon's 24-unit box: its center on the horizon, and its radius. */
private const val ARC_CENTER_X = 12f
private const val HORIZON_Y = 17.6f
private const val ARC_RADIUS = 8.4f

/** Whether [hour] (0 to 24, local time) is day on the icon: the sun's half of the day, 6 AM to 6 PM. */
internal fun isDayHour(hour: Float): Boolean = hour in 6f..18f

/**
 * How far along its arc the sun (by day) or the moon (by night) is at [hour]: 0 at the arc's start on the left, 1 at
 * its end on the right. The sun crosses from 6 AM to 6 PM and the moon from 6 PM to 6 AM, a little inside the ends so
 * neither sits off the horizon's tip.
 */
internal fun dayArcFraction(hour: Float): Float {
    val h = ((hour % 24f) + 24f) % 24f
    val f = if (isDayHour(h)) (h - 6f) / 12f else ((h + 6f) % 24f) / 12f
    return f.coerceIn(0.001f, 0.999f)
}

/** The point at [fraction] along the arc, in the icon's 24-unit box. */
internal fun dayArcPoint(fraction: Float): Offset {
    val angle = PI * (1 - fraction)
    return Offset(ARC_CENTER_X + ARC_RADIUS * cos(angle).toFloat(), HORIZON_Y - ARC_RADIUS * sin(angle).toFloat())
}

/** The hour of day now, as a fraction (14.5 at 2:30 PM). */
internal fun hourOfDay(epochMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Float {
    val t = java.time.Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime()
    return t.hour + t.minute / 60f
}

/**
 * Today's icon: the sun on its arc across the sky, placed by the time of day ([hour]), the arc solid behind it and
 * dotted ahead of it, so it shows how much of the day has gone; at night a crescent moon with a small sparkle. The sun
 * keeps a little clear sky around it so the arc never runs into it. When [rise] is on it glides up its arc from
 * the start of the day (or night) to now as it first appears, in about half a second.
 */
@Composable
internal fun TodayIcon(
    hour: Float,
    color: Color,
    modifier: Modifier = Modifier,
    rise: Boolean = false,
    /** The sun's (or moon's) own color; the arc's [color] by default. */
    sunColor: Color = color,
    /** Whether the sun's glow swells and settles, every three seconds. */
    pulse: Boolean = false
) {
    val target = dayArcFraction(hour)
    val shown = remember { Animatable(if (rise) 0f else target) }
    LaunchedEffect(target, rise) {
        if (rise && shown.value == 0f) {
            shown.animateTo(target, tween(RISE_MS, easing = EmphasizedDecelerate))
        } else {
            shown.snapTo(target)
        }
    }
    val day = isDayHour(hour)
    // Only the sun has a glow: by night nothing moves, so the moon isn't drawn again on every frame.
    val glow = if (pulse && day) rememberLoop(1_500, reverse = true) else null
    // Drawn in its own layer, so the clear sky around the sun cuts only the icon, not what is behind it.
    Canvas(modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val unit = size.minDimension / 24f
        val inset = Offset((size.width - unit * 24f) / 2f, (size.height - unit * 24f) / 2f)
        drawDayArc(shown.value, day, color, sunColor, if (glow != null) 0.82f + 0.30f * glow() else 1f, unit, inset)
    }
}

private const val RISE_MS = 560

private fun DrawScope.drawDayArc(fraction: Float, day: Boolean, color: Color, sunColor: Color, glow: Float, unit: Float, inset: Offset) {
    fun p(x: Float, y: Float) = Offset(inset.x + x * unit, inset.y + y * unit)
    // The horizon.
    drawLine(color, p(2.4f, HORIZON_Y), p(21.6f, HORIZON_Y), strokeWidth = HORIZON_STROKE * unit, cap = StrokeCap.Round)
    // The arc already travelled, solid.
    val topLeft = p(ARC_CENTER_X - ARC_RADIUS, HORIZON_Y - ARC_RADIUS)
    val arcSize = Size(ARC_RADIUS * 2 * unit, ARC_RADIUS * 2 * unit)
    drawArc(color, startAngle = 180f, sweepAngle = 180f * fraction, useCenter = false, topLeft = topLeft, size = arcSize,
        style = Stroke(width = ARC_STROKE * unit, cap = StrokeCap.Round))
    // The arc still ahead, as dots every 2.6 units.
    val ahead = (PI * ARC_RADIUS * (1 - fraction)).toFloat()
    val dots = (ahead / DOT_SPACING).toInt()
    for (i in 1..dots) {
        val f = fraction + (1 - fraction) * (i * DOT_SPACING / ahead)
        val d = dayArcPoint(f.coerceAtMost(1f))
        drawCircle(color.copy(alpha = color.alpha * 0.75f), radius = ARC_STROKE / 2f * unit, center = p(d.x, d.y))
    }
    val c = dayArcPoint(fraction)
    val center = p(c.x, c.y)
    // A little clear sky around the sun or moon, so the arc stops short of it: cut out of what is drawn so far, so
    // it works on any background (the search bar's plate is a gradient).
    drawCircle(Color.Black, radius = 3.4f * unit, center = center, blendMode = BlendMode.Clear)
    if (day) {
        drawCircle(sunColor.copy(alpha = sunColor.alpha * 0.2f), radius = 3.4f * unit * glow, center = center)
        drawCircle(sunColor, radius = 2.2f * unit, center = center)
    } else {
        drawGlyph(moonPath(c.x, c.y), sunColor, unit, inset)
        val sx = if (c.x < 12f) c.x + 5.2f else c.x - 5.4f
        drawGlyph(sparklePath(sx, c.y - 1.6f), color, unit, inset)
    }
}

private const val DOT_SPACING = 2.6f

/** Line weights in the 24-unit box: the search bar's Settings bars are a 1.3 stroke, and these match them. */
private const val HORIZON_STROKE = 1.4f
private const val ARC_STROKE = 1.35f

/** A crescent moon centered near ([x], [y]): a disc with a bite out of its upper right. */
private fun moonPath(x: Float, y: Float): String =
    "M${x + 0.9f} ${y - 2.75f}A2.9 2.9 0 1 0 ${x + 2.75f} ${y + 1.05f}A2.25 2.25 0 0 1 ${x + 0.9f} ${y - 2.75f}Z"

/** A small four-pointed sparkle centered on ([x], [y]). */
private fun sparklePath(x: Float, y: Float): String =
    "M$x ${y - 1.3f}Q${x + 0.2f} ${y - 0.2f} ${x + 1.3f} ${y}Q${x + 0.2f} ${y + 0.2f} $x ${y + 1.3f}" +
        "Q${x - 0.2f} ${y + 0.2f} ${x - 1.3f} ${y}Q${x - 0.2f} ${y - 0.2f} $x ${y - 1.3f}Z"

private fun DrawScope.drawGlyph(path: String, color: Color, unit: Float, inset: Offset) {
    val shape = PathParser().parsePathString(path).toPath()
    translate(left = inset.x, top = inset.y) {
        scale(unit, unit, pivot = Offset.Zero) { drawPath(shape, color) }
    }
}

/**
 * The moon as it shows tonight, [age] through its cycle ([MoonPhase.age]): the lit part in [color] over a faint whole
 * disc, lit on the right while it waxes and on the left as it wanes, as it looks from the northern hemisphere.
 */
@Composable
internal fun MoonPhaseIcon(age: Double, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension / 2f * 0.84f
        drawCircle(color.copy(alpha = 0.3f), radius, center)
        drawPath(moonLitPath(center, radius, age), color)
    }
}

/** The lit part of a moon of [radius] at [center], [age] through its cycle. */
private fun moonLitPath(center: Offset, radius: Float, age: Double): Path {
    val waxing = age < 0.5
    // 1 at new moon, 0 at the quarters, -1 at full: how far the terminator bows from the middle, and which way.
    val bow = cos(2 * PI * age).toFloat()
    val halfWidth = radius * abs(bow)
    return Path().apply {
        moveTo(center.x, center.y - radius)
        // The lit edge of the disc, top to bottom on the lit side.
        arcTo(Rect(center, radius), -90f, if (waxing) 180f else -180f, false)
        // Back up the terminator: bowing toward the lit side for a crescent, away from it once past a quarter.
        val bowsRight = waxing == (bow > 0)
        arcTo(
            Rect(center.x - halfWidth, center.y - radius, center.x + halfWidth, center.y + radius),
            90f, if (bowsRight) -180f else 180f, false
        )
        close()
    }
}
