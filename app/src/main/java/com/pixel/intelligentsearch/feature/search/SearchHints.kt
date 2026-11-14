package com.pixel.intelligentsearch.feature.search

import android.content.Context
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled

/**
 * During the first week after installing, the empty search bar shows things to type, one at a time, in place of its
 * plain "Search…": each is one kind of answer the bar gives as you type. After [DAYS] days it is "Search…" for good.
 */
internal object SearchHints {
    const val DAYS = 7
    const val PLAIN = "Search…"

    /** How long "Search…" stays up on opening, how long each example stays, and how long one takes to give way. */
    const val FIRST_SECONDS = 1.6f
    const val HOLD_SECONDS = 2.9f
    const val CHANGE_SECONDS = 0.42f

    private val EXAMPLES = listOf("10 min timer", "18% of 64", "weather tomorrow", "define ethereal", "20 usd in eur", "storage", "time in tokyo")
    private val CALCULATED = setOf("18% of 64", "20 usd in eur")

    /** The examples to offer: the sums only while the Calculator is switched on, since otherwise they answer nothing. */
    fun examples(calculatorOn: Boolean): List<String> = if (calculatorOn) EXAMPLES else EXAMPLES - CALCULATED

    /** Whether [nowMs] is within the first week of an install at [firstInstallMs] (0 when the phone won't say). */
    fun inFirstWeek(firstInstallMs: Long, nowMs: Long): Boolean =
        firstInstallMs > 0L && nowMs - firstInstallMs in 0L until DAYS * 24L * 60L * 60L * 1000L

    /** When this app was first installed, or 0 when the phone won't say. */
    fun firstInstallMs(context: Context): Long = try {
        context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    } catch (_: Exception) {
        0L
    }

    /**
     * Where the hints are [seconds] after search opened: [slot] counts what has been shown (0 is "Search…", then one
     * per example), and [change] is how far that one is through giving way to the next, 0 while it simply stays.
     */
    class Frame(val slot: Int, val change: Float)

    fun frame(seconds: Float): Frame {
        var since = seconds.coerceAtLeast(0f)
        var slot = 0
        var hold = FIRST_SECONDS
        while (since >= hold + CHANGE_SECONDS) {
            since -= hold + CHANGE_SECONDS
            slot++
            hold = HOLD_SECONDS
        }
        return Frame(slot, if (since <= hold) 0f else ((since - hold) / CHANGE_SECONDS).coerceIn(0f, 1f))
    }

    /** What [slot] shows of [examples], starting from the one this opening begins with ([opening]). */
    fun shown(slot: Int, examples: List<String>, opening: Int): String? =
        if (slot == 0) null else examples[(slot - 1 + opening).mod(examples.size)]

    // Each opening starts from the next example, so the first one seen is not always the same.
    private var openings = 0
    fun nextOpening(): Int = openings++
}

private fun easeOut(u: Float): Float {
    val r = 1f - u.coerceIn(0f, 1f)
    return 1f - r * r * r
}

/**
 * The empty bar's hint through its first week: "Search…", then each of [examples] rising in as the last one leaves.
 * With animations turned off it is one example for this opening, standing still.
 */
@Composable
internal fun SearchHint(examples: List<String>, modifier: Modifier = Modifier) {
    val animate by rememberAnimationsEnabled()
    val opening = remember { SearchHints.nextOpening() }
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val strong = MaterialTheme.colorScheme.onSurface
    fun line(example: String?): AnnotatedString = buildAnnotatedString {
        if (example == null) {
            append(SearchHints.PLAIN)
        } else {
            append("Try ")
            withStyle(SpanStyle(color = strong)) { append(example) }
        }
    }
    if (!animate) {
        HintLine(line(SearchHints.shown(1, examples, opening)), quiet, modifier)
        return
    }
    val slot = remember { mutableIntStateOf(0) }
    // Read only where the two lines are placed, so a frame of the change moves them and recomposes nothing.
    val change = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(examples) {
        var start = 0L
        while (true) withInfiniteAnimationFrameNanos { now ->
            if (start == 0L) start = now
            val frame = SearchHints.frame((now - start) / 1e9f)
            if (slot.intValue != frame.slot) slot.intValue = frame.slot
            if (change.floatValue != frame.change) change.floatValue = frame.change
        }
    }
    val current = remember(slot.intValue, examples, quiet, strong) { line(SearchHints.shown(slot.intValue, examples, opening)) }
    val next = remember(slot.intValue, examples, quiet, strong) { line(SearchHints.shown(slot.intValue + 1, examples, opening)) }
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        HintLine(
            current, quiet,
            Modifier.graphicsLayer {
                val out = easeOut(change.floatValue)
                translationY = -RISE.toPx() * out
                alpha = 1f - out
            }
        )
        HintLine(
            next, quiet,
            Modifier
                .graphicsLayer {
                    val into = easeOut((change.floatValue - 0.18f) / 0.82f)
                    translationY = RISE.toPx() * (1f - into)
                    alpha = if (change.floatValue > 0f) into else 0f
                }
                // Read out once it is the one showing, not while it waits unseen.
                .clearAndSetSemantics { }
        )
    }
}

/** How far a hint travels as it leaves or arrives. */
private val RISE = 14.dp

@Composable
private fun HintLine(text: AnnotatedString, color: Color, modifier: Modifier = Modifier) {
    // One line: in a field too narrow for it (a small screen at the largest text) it wrapped out of the bar.
    Text(text, color = color, fontSize = 20.sp, fontFamily = GoogleSansFlex, maxLines = 1, softWrap = false, modifier = modifier)
}
