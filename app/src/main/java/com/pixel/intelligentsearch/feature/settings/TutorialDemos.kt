package com.pixel.intelligentsearch.feature.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.answers.TemperatureUnit
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.core.ui.materialYouGeminiPlate
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import com.pixel.intelligentsearch.core.ui.symbols.Symbols
import com.pixel.intelligentsearch.feature.search.SearchOverlayLensIcon
import com.pixel.intelligentsearch.feature.search.rememberGlanceNow
import com.pixel.intelligentsearch.feature.search.today.TodayIcon
import com.pixel.intelligentsearch.feature.search.today.dayGreeting
import com.pixel.intelligentsearch.feature.search.today.hourOfDay
import com.pixel.intelligentsearch.feature.search.today.rememberLoop
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * What a tutorial step plays above its words: a small piece of the app at work, in the app's own colors.
 * [description] is what TalkBack reads in its place.
 */
enum class TutorialDemo(val description: String) {
    /** A tap on the Discover button in the search bar opens the Discover page. */
    Discover("Preview: a tap on the Discover button in the search bar opens the Discover page."),

    /** Words type themselves into the search bar, each answered as it is typed. */
    Answers("Preview: a timer, a sum, the weather and a definition, each answered as it is typed in the search bar.")
}

private val STAGE_HEIGHT = 176.dp
private val STAGE_SHAPE = RoundedCornerShape(20.dp)
private val BAR_HEIGHT = 40.dp
private val BAR_SIDE = 12.dp
private val BAR_BOTTOM = 10.dp
private val BAR_INSET = 14.dp
private val BAR_ICON = 20.dp

/** The Discover button's center in the small search bar: in from the stage's left edge, and up from its bottom. */
private val DISCOVER_BUTTON_X = BAR_SIDE + BAR_INSET + BAR_ICON / 2
private val DISCOVER_BUTTON_UP = BAR_BOTTOM + BAR_HEIGHT / 2

/** The Discover demo, in seconds: one turn, and the moment shown with animations off (the page open). */
private const val DISCOVER_LOOP = 7.2f
private const val DISCOVER_STILL = 4.4f

/** What the Answers demo types, each for [ANSWER_EACH] seconds: typed from [TYPE_START], a letter every [TYPE_CHAR]. */
internal val ANSWER_EXAMPLES = listOf("timer 10 min", "20% off 59.99", "weather in paris", "define serene")
internal const val ANSWER_EACH = 3.7f
private const val TYPE_START = 0.3f
private const val TYPE_CHAR = 0.065f
private const val ANSWER_LEAVES = 3.12f
private const val ANSWER_CLEAR = 3.4f

/** With animations off: the weather example, typed and answered. */
private const val ANSWERS_STILL = 2 * ANSWER_EACH + 2.6f

/** How far [t] is from [from] to [to], 0 to 1. */
private fun seg(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun decelerate(x: Float): Float = 1f - (1f - x) * (1f - x) * (1f - x)

private fun accelerate(x: Float): Float = x * x * x

/** Past the end and back: the small overshoot an answer lands with. */
private fun overshoot(x: Float): Float {
    val y = x - 1f
    return 1f + 2.4f * y * y * y + 1.4f * y * y
}

/** The words in the Answers demo's search bar [seconds] into its loop. */
internal fun typedAt(seconds: Float): String {
    val text = ANSWER_EXAMPLES[(seconds / ANSWER_EACH).toInt() % ANSWER_EXAMPLES.size]
    val local = seconds % ANSWER_EACH
    if (local >= ANSWER_CLEAR) return ""
    return text.take(((local - TYPE_START) / TYPE_CHAR).toInt().coerceIn(0, text.length))
}

/**
 * Seconds into a demo's loop of [loopSeconds], read while drawing so the demo redraws and never recomposes.
 * With animations off it stays on [still], the moment that shows the feature best.
 */
@Composable
private fun rememberDemoSeconds(loopSeconds: Float, still: Float, animate: Boolean): () -> Float {
    if (!animate) return remember(still) { { still } }
    val loop = rememberLoop((loopSeconds * 1000).toInt(), easing = LinearEasing)
    return remember(loop, loopSeconds) { { loop() * loopSeconds } }
}

/**
 * The stage a tutorial step's [demo] plays on. It wears the app's own theme, not the tutorial card's, so it shows
 * the app as it will look. It is a picture: its words keep their size at any font size, nothing in it takes a tap,
 * and TalkBack reads [TutorialDemo.description] for the whole of it.
 */
@Composable
internal fun TutorialDemoStage(demo: TutorialDemo, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val animate by rememberAnimationsEnabled()
    val density = LocalDensity.current
    Box(
        modifier
            .fillMaxWidth()
            .height(STAGE_HEIGHT)
            .clip(STAGE_SHAPE)
            .background(scheme.surface)
            .clearAndSetSemantics {
                contentDescription = demo.description
                traversalIndex = 2.5f
            }
    ) {
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
            when (demo) {
                TutorialDemo.Discover -> DiscoverDemo(rememberDemoSeconds(DISCOVER_LOOP, DISCOVER_STILL, animate), animate)
                TutorialDemo.Answers -> AnswersDemo(rememberDemoSeconds(ANSWER_EACH * ANSWER_EXAMPLES.size, ANSWERS_STILL, animate))
            }
        }
    }
}

/**
 * The Discover step's switch: the very row Appearance shows for the Discover button, on the app's own surface, so
 * the button can be turned on without leaving the tutorial and is recognized later in Settings.
 */
@Composable
internal fun TutorialDiscoverSwitch(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(STAGE_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .semantics {
                isTraversalGroup = true
                traversalIndex = 3.5f
            }
    ) {
        DiscoverOverlaySwitchRow(showDivider = false)
    }
}

/** One line of a demo's words. */
@Composable
private fun Words(
    text: String,
    size: Float,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    spacing: Float = 0f,
    italic: Boolean = false
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        lineHeight = (size * 1.25f).sp,
        fontWeight = weight,
        fontFamily = GoogleSansFlex,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        letterSpacing = spacing.sp,
        maxLines = 1,
        softWrap = false
    )
}

/** The search bar, small: the bar's own plate, with [content] laid out as the bar lays out its icons and field. */
@Composable
private fun MiniSearchBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .padding(start = BAR_SIDE, end = BAR_SIDE, bottom = BAR_BOTTOM)
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(CircleShape)
            .materialYouGeminiPlate(cornerRadius = BAR_HEIGHT / 2)
            .padding(horizontal = BAR_INSET),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * The search bar with the Discover button in it. The button is nudged, then tapped, and the Discover page opens out
 * of it in a circle as the real one does; its cards arrive one after another, one of the quick controls is switched
 * on, and the page closes back into the button.
 */
@Composable
private fun DiscoverDemo(seconds: () -> Float, animate: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val hour = hourOfDay(rememberGlanceNow(60_000L))
    Box(Modifier.fillMaxSize()) {
        MiniSearchBar(Modifier.align(Alignment.BottomCenter)) {
            TodayIcon(hour = hour, color = scheme.onSurface, rise = animate, modifier = Modifier.size(BAR_ICON))
            Spacer(Modifier.width(8.dp))
            SearchOverlayLensIcon(Modifier.size(BAR_ICON))
            Spacer(Modifier.width(10.dp))
            Words("Search…", 13.5f, scheme.onSurfaceVariant)
        }
        // The nudge (a ring leaving the button) and the tap on it.
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val t = seconds()
                val at = Offset(DISCOVER_BUTTON_X.toPx(), size.height - DISCOVER_BUTTON_UP.toPx())
                val reach = 13.dp.toPx()
                val ring = seg(t, 0.8f, 1.45f)
                if (ring > 0f && ring < 1f) {
                    drawCircle(
                        scheme.primary,
                        radius = reach * (1f + 0.9f * decelerate(ring)),
                        center = at,
                        alpha = 0.55f * (1f - ring),
                        style = Stroke(1.5.dp.toPx())
                    )
                }
                val tap = seg(t, 1.3f, 1.42f) * (1f - seg(t, 1.5f, 1.72f))
                if (tap > 0f) drawCircle(scheme.onSurface, radius = reach * (0.6f + 0.4f * tap), center = at, alpha = 0.2f * tap)
            }
        )
        DiscoverMiniPage(seconds, hour, animate)
    }
}

/** The Discover page, small: its greeting and sun, and four of its cards. */
@Composable
private fun DiscoverMiniPage(seconds: () -> Float, hour: Float, animate: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val circle = remember { Path() }
    val stored by rememberSetting(SettingsManager.TEMPERATURE_UNIT)
    val locale = LocalConfiguration.current.locales[0]
    val fahrenheit = TemperatureUnit.chosen(stored, locale) == TemperatureUnit.FAHRENHEIT
    Column(
        Modifier
            .fillMaxSize()
            // Opens out of the Discover button and closes back into it.
            .drawWithContent {
                val t = seconds()
                val open = if (t < 5.7f) decelerate(seg(t, 1.5f, 2.15f)) else 1f - accelerate(seg(t, 5.7f, 6.15f))
                if (open <= 0f) return@drawWithContent
                val at = Offset(DISCOVER_BUTTON_X.toPx(), size.height - DISCOVER_BUTTON_UP.toPx())
                val reach = hypot(max(at.x, size.width - at.x), max(at.y, size.height - at.y)) + 2f
                circle.rewind()
                circle.addOval(Rect(at, open * reach))
                clipPath(circle) { this@drawWithContent.drawContent() }
            }
            .background(scheme.surface)
            // The page's light, a little of the accent from its top corner.
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(scheme.primaryContainer.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset.Zero,
                        radius = size.width * 0.8f
                    )
                )
            }
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
            Words(dayGreeting(hour.toInt()), 17f, scheme.onSurface, Modifier.weight(1f))
            TodayIcon(
                hour = hour,
                color = scheme.onSurfaceVariant,
                sunColor = scheme.primary,
                pulse = animate,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniCard(0, seconds, scheme.primaryContainer, Modifier.weight(1f)) {
                CardLabel("UP NEXT", scheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Words("Dentist · 3:30 PM", 12f, scheme.onPrimaryContainer, weight = FontWeight.Medium)
            }
            MiniCard(1, seconds, scheme.surfaceContainer, Modifier.weight(1f)) {
                CardLabel("WEATHER", scheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Words(if (fahrenheit) "68°" else "20°", 15f, scheme.onSurface, weight = FontWeight.Medium)
                    Spacer(Modifier.width(5.dp))
                    Words("Clear", 10.5f, scheme.onSurfaceVariant, Modifier.padding(bottom = 1.5.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniCard(2, seconds, scheme.surfaceContainer, Modifier.weight(1f)) {
                CardLabel("QUICK CONTROLS", scheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                QuickControls(seconds)
            }
            MiniCard(3, seconds, scheme.surfaceContainer, Modifier.weight(1f)) {
                CardLabel("WORD OF THE DAY", scheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Words("perennial", 14f, scheme.primary)
            }
        }
    }
}

@Composable
private fun CardLabel(text: String, color: Color) {
    Words(text, 7.5f, color.copy(alpha = color.alpha * 0.85f), weight = FontWeight.SemiBold, spacing = 0.7f)
}

/** One of the small page's cards, arriving a moment after the one before it ([index]). */
@Composable
private fun MiniCard(
    index: Int,
    seconds: () -> Float,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .height(50.dp)
            .graphicsLayer {
                val arrived = decelerate(seg(seconds(), 1.75f + 0.07f * index, 2.2f + 0.07f * index))
                alpha = arrived
                translationY = (1f - arrived) * 10.dp.toPx()
                scaleX = 0.94f + 0.06f * arrived
                scaleY = scaleX
            }
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        content = content
    )
}

/** Three quick controls as the page draws them, ovals: one on, one off, and one tapped and switched on as you watch. */
@Composable
private fun QuickControls(seconds: () -> Float) {
    val scheme = MaterialTheme.colorScheme
    val oval = Modifier.size(width = 30.dp, height = 18.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Spacer(oval.background(scheme.primary, CircleShape))
        Spacer(
            oval.drawBehind {
                val t = seconds()
                val on = seg(t, 3.4f, 3.62f)
                scale(1f + 0.14f * sin(PI.toFloat() * on)) {
                    drawRoundRect(
                        if (on > 0.5f) scheme.primary else scheme.surfaceContainerHighest,
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                }
                val tap = seg(t, 3.2f, 3.32f) * (1f - seg(t, 3.45f, 3.7f))
                if (tap > 0f) drawCircle(scheme.onSurface, radius = 11.dp.toPx() * (0.6f + 0.4f * tap), alpha = 0.22f * tap)
            }
        )
        Spacer(oval.background(scheme.surfaceContainerHighest, CircleShape))
    }
}

/**
 * The search bar typing [ANSWER_EXAMPLES] one after another. Each is answered above the bar the way the search
 * answers it: a timer to start, a sum worked out, the weather, a definition.
 */
@Composable
private fun AnswersDemo(seconds: () -> Float) {
    val scheme = MaterialTheme.colorScheme
    // The words change a letter at a time, so only they recompose, and only when a letter arrives.
    val typed by remember(seconds) { derivedStateOf { typedAt(seconds()) } }
    Box(Modifier.fillMaxSize()) {
        ANSWER_EXAMPLES.forEachIndexed { index, text ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = BAR_SIDE, end = BAR_SIDE, bottom = BAR_BOTTOM + BAR_HEIGHT + 8.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val t = seconds()
                        if ((t / ANSWER_EACH).toInt() % ANSWER_EXAMPLES.size != index) {
                            alpha = 0f
                            return@graphicsLayer
                        }
                        val local = t % ANSWER_EACH
                        val typedBy = TYPE_START + text.length * TYPE_CHAR
                        val landed = overshoot(seg(local, typedBy + 0.12f, typedBy + 0.54f))
                        val leaving = seg(local, ANSWER_LEAVES, ANSWER_LEAVES + 0.24f)
                        alpha = seg(local, typedBy + 0.12f, typedBy + 0.3f) * (1f - leaving)
                        translationY = ((1f - landed) * 14f + leaving * 6f).dp.toPx()
                        scaleX = 0.96f + 0.04f * landed
                        scaleY = scaleX
                    }
            ) {
                when (index) {
                    0 -> TimerAnswer()
                    1 -> SumAnswer()
                    2 -> WeatherAnswer()
                    else -> DefinitionAnswer()
                }
            }
        }
        MiniSearchBar(Modifier.align(Alignment.BottomCenter)) {
            SearchOverlayLensIcon(Modifier.size(BAR_ICON))
            Spacer(Modifier.width(10.dp))
            val caret = Modifier
                .size(width = 1.5.dp, height = 16.dp)
                .drawBehind {
                    val t = seconds()
                    val local = t % ANSWER_EACH
                    val typedBy = TYPE_START + ANSWER_EXAMPLES[(t / ANSWER_EACH).toInt() % ANSWER_EXAMPLES.size].length * TYPE_CHAR
                    // Steady while letters arrive, blinking while it waits.
                    if ((local > TYPE_START && local < typedBy + 0.1f) || t % 1f < 0.55f) drawRect(scheme.primary)
                }
            if (typed.isEmpty()) {
                Spacer(caret)
                Spacer(Modifier.width(1.dp))
                Words("Search…", 13.5f, scheme.onSurfaceVariant)
            } else {
                Words(typed, 13.5f, scheme.onSurface)
                Spacer(Modifier.width(1.dp))
                Spacer(caret)
            }
        }
    }
}

/** "timer 10 min": the action row that starts it. */
@Composable
private fun TimerAnswer() {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(CircleShape)
            .background(scheme.primaryContainer.copy(alpha = 0.6f).compositeOver(scheme.surface))
            .padding(start = 9.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp).background(scheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Symbols.Outlined.Timer, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Words("Set Timer", 13f, scheme.onPrimaryContainer, weight = FontWeight.Bold)
            Words("Timer 10 min", 10.5f, scheme.onPrimaryContainer.copy(alpha = 0.85f))
        }
        Icon(
            Symbols.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = scheme.onPrimaryContainer,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** The card an answer shows on: raised from the page in dark theme, white with a hairline in light, as in the search. */
@Composable
private fun AnswerCard(content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest)
            .then(if (dark) Modifier else Modifier.border(1.dp, scheme.outlineVariant.copy(alpha = 0.4f), shape))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        content = content
    )
}

/** The rounded tile an answer's icon sits on. */
@Composable
private fun AnswerTile(icon: ImageVector) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(32.dp).background(scheme.tertiaryContainer, RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AnswerLabel(text: String) {
    Words(text, 7.5f, MaterialTheme.colorScheme.tertiary, weight = FontWeight.SemiBold, spacing = 0.8f)
}

/** "20% off 59.99": the sum and what it comes to. */
@Composable
private fun SumAnswer() {
    val scheme = MaterialTheme.colorScheme
    AnswerCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Words("20% off 59.99", 10.5f, scheme.onSurfaceVariant)
                Words("= 47.99", 22f, scheme.onSurface, weight = FontWeight.Bold)
            }
            Box(Modifier.size(28.dp).background(scheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Symbols.Filled.Calculate, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(15.dp))
            }
        }
    }
}

/** "weather in paris": the weather there now, in the temperature scale the app would answer in. */
@Composable
private fun WeatherAnswer() {
    val scheme = MaterialTheme.colorScheme
    val stored by rememberSetting(SettingsManager.TEMPERATURE_UNIT)
    val unit = TemperatureUnit.chosen(stored, LocalConfiguration.current.locales[0])
    val fahrenheit = unit == TemperatureUnit.FAHRENHEIT
    AnswerCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnswerTile(Symbols.Filled.WbSunny)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                AnswerLabel("WEATHER · NOW")
                Words("Paris", 14f, scheme.onSurface, weight = FontWeight.Bold)
                Words(if (fahrenheit) "Clear · H 66° L 52°" else "Clear · H 19° L 11°", 10.5f, scheme.onSurfaceVariant)
            }
            Words((if (fahrenheit) "61" else "16") + unit.symbol, 22f, scheme.onSurface, weight = FontWeight.Bold)
        }
    }
}

/** "define serene": the word, its part of speech and what it means. */
@Composable
private fun DefinitionAnswer() {
    val scheme = MaterialTheme.colorScheme
    AnswerCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnswerTile(Symbols.AutoMirrored.Filled.MenuBook)
            Spacer(Modifier.width(10.dp))
            Column {
                AnswerLabel("DICTIONARY")
                Words("serene", 14f, scheme.onSurface, weight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Words("adjective", 10.5f, scheme.tertiary, italic = true)
        Words("Calm, peaceful and untroubled.", 11.5f, scheme.onSurface)
    }
}
