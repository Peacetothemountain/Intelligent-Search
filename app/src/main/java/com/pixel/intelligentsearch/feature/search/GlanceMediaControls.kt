package com.pixel.intelligentsearch.feature.search

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pixel.intelligentsearch.core.glance.GlanceMedia
import com.pixel.intelligentsearch.core.system.MediaSessionAccess
import com.pixel.intelligentsearch.core.ui.expressive.matteShell
import com.pixel.intelligentsearch.core.ui.expressive.rememberMatteShellLook
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import com.pixel.intelligentsearch.feature.settings.IconButton
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** Test tags on At a Glance's seek bar and its transport controls' pill. */
internal const val GLANCE_SEEK_BAR_TAG = "glance_seek_bar"
internal const val GLANCE_TRANSPORT_TAG = "glance_transport"

/** How long one wave of the played line is, how high it swings, and how long one wave takes to flow past. */
private val WAVE_LENGTH = 20.dp
private val WAVE_AMPLITUDE = 3.dp
private const val WAVE_PERIOD_S = 1.6f

/**
 * A media time as players show it: "m:ss", or "h:mm:ss" from an hour on; negative times read as zero.
 */
internal fun mediaTimeLabel(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    fun two(n: Long) = n.toString().padStart(2, '0')
    return if (hours > 0) "$hours:${two(minutes)}:${two(seconds)}" else "$minutes:${two(seconds)}"
}

/** Where on the track a touch at [x] lands, as a fraction, in a bar whose line runs from [start] to [end]. */
internal fun seekFractionAt(x: Float, start: Float, end: Float, rtl: Boolean): Float {
    val width = end - start
    if (width <= 0f) return 0f
    val fraction = ((x - start) / width).coerceIn(0f, 1f)
    return if (rtl) 1f - fraction else fraction
}

/**
 * [elapsed] (elapsedRealtime) now, moved on every [periodMs] while [enabled], composed and the search on screen;
 * held still otherwise and with a still [GlanceClock] (screenshot tests).
 */
@Composable
private fun rememberElapsedNow(periodMs: Long, enabled: Boolean): Long {
    val clock = LocalGlanceClock.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(clock.elapsed(), clock, periodMs, enabled, lifecycle) {
        value = clock.elapsed()
        if (!clock.ticks || !enabled) return@produceState
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = clock.elapsed()
                delay(periodMs)
            }
        }
    }.value
}

/**
 * What is playing in At a Glance below its title row: the seek bar, when the track has a length, and Previous,
 * Play or Pause and Next together in one pill.
 */
@Composable
internal fun GlanceMediaControls(media: GlanceMedia, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        media.progress?.let { progress ->
            SquigglySeekBar(
                progress = progress,
                playing = media.isPlaying,
                onSeek = media.controls::seekTo,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }
        // 8dp clear of the seek bar's times above it; without a seek bar it sits close under the title row.
        TransportPill(media, Modifier.padding(top = if (media.progress != null) 8.dp else 2.dp, bottom = 6.dp))
    }
}

/**
 * Android's media seek bar: the played part a sine wave that flows while playing and settles flat when paused, the
 * rest a thin straight line, and a round thumb between them. Dragging or tapping it seeks on release when the
 * player takes a seek ([MediaSessionAccess.Progress.canSeek]); otherwise it only shows where the track is. With
 * animations off the wave holds still. The elapsed and remaining times sit small under it.
 */
@Composable
internal fun SquigglySeekBar(
    progress: MediaSessionAccess.Progress,
    playing: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val animate by rememberAnimationsEnabled()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val moving = playing && progress.speed != 0f
    val nowElapsed = rememberElapsedNow(if (animate) 250L else 1_000L, enabled = moving)
    val live = MediaSessionAccess.livePositionMs(progress, nowElapsed)
    // Where a drag has the thumb, and where it was let go until the player reports the new position.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var heldFraction by remember(progress) { mutableStateOf<Float?>(null) }
    val fraction = (dragFraction ?: heldFraction ?: (live.toFloat() / progress.durationMs)).coerceIn(0f, 1f)
    val shownMs = (fraction * progress.durationMs).toLong()
    // Read where it is called, so the gesture handlers always seek with this composition's track and callback.
    val seek by rememberUpdatedState<(Float) -> Unit> { f ->
        heldFraction = f
        onSeek((f * progress.durationMs).toLong())
    }

    val amplitude by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = if (animate) spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow) else snap(),
        label = "wave amplitude"
    )
    // The wave's phase, read only while drawing, so its flow redraws the bar without recomposing it.
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(moving, animate) {
        if (!moving || !animate) return@LaunchedEffect
        val startPhase = phase.floatValue
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { t ->
                val seconds = (t - startNanos) / 1_000_000_000f
                phase.floatValue = (startPhase + seconds / WAVE_PERIOD_S * 2f * PI.toFloat()) % (2f * PI.toFloat())
            }
        }
    }

    val density = LocalDensity.current
    val stroke = remember(density) { with(density) { Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round) } }
    val path = remember { Path() }
    val played = scheme.primary
    val remaining = scheme.onSurfaceVariant.copy(alpha = 0.38f)
    val timeText = "${mediaTimeLabel(shownMs)} of ${mediaTimeLabel(progress.durationMs)}"

    Column(modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (progress.canSeek) 48.dp else 24.dp)
                .testTag(GLANCE_SEEK_BAR_TAG)
                .semantics {
                    contentDescription = "Playback Position"
                    stateDescription = timeText
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    if (progress.canSeek) setProgress { target -> seek(target.coerceIn(0f, 1f)); true }
                }
                .then(
                    if (!progress.canSeek) Modifier else Modifier
                        .pointerInput(progress.durationMs, rtl) {
                            val inset = 8.dp.toPx()
                            detectTapGestures { offset -> seek(seekFractionAt(offset.x, inset, size.width - inset, rtl)) }
                        }
                        .pointerInput(progress.durationMs, rtl) {
                            val inset = 8.dp.toPx()
                            detectHorizontalDragGestures(
                                onDragStart = { offset -> dragFraction = seekFractionAt(offset.x, inset, size.width - inset, rtl) },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    dragFraction = seekFractionAt(change.position.x, inset, size.width - inset, rtl)
                                },
                                onDragEnd = {
                                    dragFraction?.let(seek)
                                    dragFraction = null
                                },
                                onDragCancel = { dragFraction = null }
                            )
                        }
                )
        ) {
            val inset = 8.dp.toPx()
            val start = inset
            val end = size.width - inset
            val cy = size.height / 2f
            val thumbX = start + (end - start) * fraction
            val waveLength = WAVE_LENGTH.toPx()
            val swing = WAVE_AMPLITUDE.toPx() * amplitude
            val shift = phase.floatValue
            // Drawn left to right, mirrored for right-to-left languages, as Android's media seek bar is.
            scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f) {
                drawLine(remaining, Offset(thumbX, cy), Offset(end, cy), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                path.rewind()
                val step = 2.dp.toPx()
                var x = start
                path.moveTo(x, cy + swing * sin(-shift))
                while (x < thumbX) {
                    x = minOf(x + step, thumbX)
                    path.lineTo(x, cy + swing * sin((x - start) / waveLength * 2f * PI.toFloat() - shift))
                }
                drawPath(path, played, style = stroke)
                drawCircle(played, radius = 6.dp.toPx(), center = Offset(thumbX, cy))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(mediaTimeLabel(shownMs), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text("-" + mediaTimeLabel(progress.durationMs - shownMs), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}

/**
 * Previous, Play or Pause and Next in one pill of the card's next tone, evenly spaced, every button a 48 dp target;
 * Play or Pause is the filled, emphasised one.
 */
@Composable
private fun TransportPill(media: GlanceMedia, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val look = rememberMatteShellLook(containerColor = scheme.surfaceContainerHighest, border = null)
    Row(
        modifier = modifier
            .matteShell(look, CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag(GLANCE_TRANSPORT_TAG),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { media.controls.previous() }) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous Track", tint = scheme.onSurfaceVariant)
        }
        IconButton(
            onClick = { if (media.isPlaying) media.controls.pause() else media.controls.play() },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary)
        ) {
            Icon(
                imageVector = if (media.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (media.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(24.dp)
            )
        }
        IconButton(onClick = { media.controls.next() }) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Next Track", tint = scheme.onSurfaceVariant)
        }
    }
}
