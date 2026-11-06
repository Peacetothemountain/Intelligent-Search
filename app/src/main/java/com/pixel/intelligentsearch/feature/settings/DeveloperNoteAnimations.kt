package com.pixel.intelligentsearch.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.system.SearchRoleManager
import com.pixel.intelligentsearch.core.system.battery.ChargingStatus
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.core.ui.expressive.MatteShell
import com.pixel.intelligentsearch.core.ui.IdleFlowMotion
import com.pixel.intelligentsearch.core.ui.geminiPlateIdleFlow
import com.pixel.intelligentsearch.core.ui.materialYouGeminiPlate
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import com.pixel.intelligentsearch.feature.search.SearchOverlayLensIcon
import com.pixel.intelligentsearch.feature.search.SplashLook
import com.pixel.intelligentsearch.feature.search.SplashLooks
import com.pixel.intelligentsearch.feature.search.drawSplashIcon
import com.pixel.intelligentsearch.feature.search.ghostText
import com.pixel.intelligentsearch.feature.search.lerpRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** How far setup has come: 0 nothing done, 1 USB debugging on, 2 on the search engine list, 3 the default search engine. */
internal fun setupStepReached(usbDebugging: Boolean, listed: Boolean, isDefault: Boolean): Int = when {
    isDefault -> 3
    listed -> 2
    usbDebugging -> 1
    else -> 0
}

/** The bottom of the Developer Note: the phone's real setup status beside a loop of what it gets you. */
@Composable
internal fun DeveloperNoteAnimations(context: Context, maxLoopHeight: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Infinity) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val pageWidth = maxWidth
        // Side by side only where both stay large: a narrow page or large text puts the loop under the path, full width.
        if (pageWidth < SIDE_BY_SIDE_MIN_WIDTH || LocalDensity.current.fontScale > 1.3f) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SetupPath(context, Modifier.fillMaxWidth())
                SearchLoop(context, pageWidth)
            }
        } else {
            val loopWidth = min(pageWidth * 0.56f, maxLoopHeight / LOOP_RATIO)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SetupPath(context, Modifier.weight(1f), allSetHeight = loopWidth * LOOP_RATIO)
                SearchLoop(context, loopWidth)
            }
        }
    }
}

/** One step of [SetupPath]. */
private class SetupRowText(val title: String, val toDo: String, val done: String)

/**
 * Setup as a path: it grows to the step this phone has reached, read again each time the app comes back. The next
 * step pulses and opens where it gets done. Once all are done, [SetupAllSet] takes its place.
 */
@Composable
internal fun SetupPath(context: Context, modifier: Modifier = Modifier, allSetHeight: androidx.compose.ui.unit.Dp = ALL_SET_HEIGHT) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val usbDebugging = remember(revision) { isUsbDebuggingOn(context) }
    val step = remember(revision) {
        setupStepReached(
            usbDebugging = usbDebugging,
            listed = SearchRoleManager.isListedAsSearchEngine(context),
            isDefault = SearchRoleManager.isSearchRoleHeld(context)
        )
    }
    if (step >= SETUP_STEPS) {
        SetupAllSet(modifier.height(allSetHeight))
        return
    }
    val animations by rememberAnimationsEnabled()
    val shown by animateFloatAsState(
        targetValue = step.toFloat(),
        animationSpec = if (animations) spring(dampingRatio = 1f, stiffness = 200f) else tween(0),
        label = "setup_path"
    )
    val infinite = rememberInfiniteTransition(label = "setup_path_motion")
    val pulse by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween(PULSE_MS, easing = LinearEasing), RepeatMode.Restart), label = "setup_pulse"
    )
    val rows = listOf(
        SetupRowText("USB debugging", "Turn this on", if (usbDebugging) "On" else "Done"),
        SetupRowText("Search engine list", "Run the commands", "On the list"),
        SetupRowText("Default search engine", "Choose it in Settings", "Set")
    )
    Column(modifier) {
        rows.forEachIndexed { index, row ->
            val active = index == step
            val done = index < step
            val scheme = MaterialTheme.colorScheme
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .then(
                        if (active) {
                            Modifier.clickable(role = Role.Button) { openSetupStep(context, index) }
                        } else {
                            Modifier.semantics(mergeDescendants = true) {}
                        }
                    )
                    .drawBehind {
                        val x = 20.dp.toPx()
                        val cy = size.height / 2f
                        val line = 4.dp.toPx()
                        val top = if (index == 0) cy else 0f
                        val bottom = if (index == rows.lastIndex) cy else size.height
                        drawLine(scheme.outlineVariant, Offset(x, top), Offset(x, bottom), line, StrokeCap.Round)
                        val reached = shown.coerceAtMost(rows.lastIndex.toFloat())
                        val end = (cy + (reached - index) * size.height).coerceAtMost(bottom)
                        if (end > top) drawLine(scheme.primary, Offset(x, top), Offset(x, end), line, StrokeCap.Round)
                        if (animations && active) {
                            drawCircle(
                                scheme.primary,
                                radius = (14f + pulse * 9f).dp.toPx(),
                                center = Offset(x, cy),
                                alpha = (1f - pulse) * 0.8f,
                                style = Stroke(2.dp.toPx())
                            )
                        }
                        val on = (shown - index).coerceIn(0f, 1f)
                        val radius = 14.dp.toPx() * (1f + 0.16f * sin(PI.toFloat() * on))
                        drawCircle(if (on > 0.5f) scheme.primary else scheme.surfaceContainerHighest, radius, Offset(x, cy))
                        drawCircle(
                            if (on > 0.5f || active) scheme.primary else scheme.outlineVariant,
                            radius,
                            Offset(x, cy),
                            style = Stroke(2.dp.toPx())
                        )
                        if (on > 0f) {
                            val u = 1.dp.toPx() * 14f / 12f
                            val check = Path().apply {
                                moveTo(x - 5.5f * u, cy + 0.5f * u)
                                relativeLineTo(4f * u, 4f * u)
                                relativeLineTo(7.5f * u, -8.5f * u)
                            }
                            val partial = Path()
                            PathMeasure().run {
                                setPath(check, false)
                                getSegment(0f, length * on, partial)
                            }
                            drawPath(
                                partial,
                                scheme.onPrimary,
                                style = Stroke(2.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                    .padding(start = 46.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (done || active) scheme.onSurface else scheme.onSurfaceVariant
                    )
                    Text(
                        text = if (done) row.done else row.toDo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Whether USB debugging is on: the setting, or the USB connection reporting it when a phone hides the setting. */
internal fun isUsbDebuggingOn(context: Context): Boolean {
    if (Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1) return true
    return runCatching {
        ContextCompat.registerReceiver(context, null, IntentFilter(ChargingStatus.ACTION_USB_STATE), ContextCompat.RECEIVER_NOT_EXPORTED)
            ?.getBooleanExtra("adb", false) == true
    }.getOrDefault(false)
}

/** Takes the user to where step [index] of [SetupPath] gets done. */
private fun openSetupStep(context: Context, index: Int) {
    when (index) {
        0 -> {
            val opened = runCatching {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            // Developer options can't open until they're turned on, which starts on the About Phone page.
            if (opened.isFailure) {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        1 -> {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("ADB Commands", SearchRoleManager.ADB_ACTIVATION_COMMANDS))
            Toast.makeText(context, "ADB commands copied to clipboard.", Toast.LENGTH_SHORT).show()
        }
        else -> {
            SearchRoleManager.syncAllSearchEngineSettings(context)
            SearchRoleManager.openDefaultSearchEngineSettings(context)
        }
    }
}

/**
 * Setup finished: a container with a scalloped check that pops in and keeps slowly turning over a breathing halo, and
 * "You're all set" under it. With animations removed it holds still.
 */
@Composable
internal fun SetupAllSet(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val animations by rememberAnimationsEnabled()
    var time by remember { mutableFloatStateOf(if (animations) 0f else ALL_SET_REST) }
    LaunchedEffect(animations) {
        if (!animations) {
            time = ALL_SET_REST
            return@LaunchedEffect
        }
        val start = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { time = (it - start) / 1_000_000_000f }
        }
    }
    MatteShell(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        containerColor = scheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Canvas(Modifier.size(ALL_SET_BADGE)) {
                val t = time
                val u = size.minDimension / 110f
                val center = Offset(size.width / 2f, size.height / 2f)
                val grown = popIn((t - 0.05f) / 0.65f)
                val spin = if (animations) t else 0f
                val breath = if (animations) (sin(spin * 1.2f) + 1f) / 2f else 0.5f
                drawPath(
                    scallopPath(center, (46f + breath * 5f) * u * grown, -spin * 0.1f),
                    scheme.primaryContainer,
                    alpha = 0.55f + 0.2f * breath
                )
                drawPath(scallopPath(center, 38f * u * grown, spin * 0.14f), scheme.primary)
                val check = Path().apply {
                    moveTo(center.x - 15f * u, center.y + 1f * u)
                    relativeLineTo(11f * u, 11f * u)
                    relativeLineTo(20f * u, -23f * u)
                }
                val drawn = Path()
                PathMeasure().run {
                    setPath(check, false)
                    getSegment(0f, length * easeOut((t - 0.2f) / 0.35f), drawn)
                }
                drawPath(drawn, scheme.onPrimary, style = Stroke(5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "You're all set",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    val shown = easeOut((time - 0.25f) / 0.4f)
                    alpha = shown
                    translationY = (1f - shown) * 8.dp.toPx()
                }
            )
        }
    }
}

/** A nine-lobed scallop of [radius] around [center], turned by [rotation] radians: Material's cookie shape. */
private fun scallopPath(center: Offset, radius: Float, rotation: Float): Path = Path().apply {
    for (i in 0 until SCALLOP_POINTS) {
        val angle = i * 2f * PI.toFloat() / SCALLOP_POINTS
        val r = radius * (1f + 0.07f * cos(9f * (angle - rotation)))
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private const val PULSE_MS = 1800
private const val SETUP_STEPS = 3
private const val SCALLOP_POINTS = 72
private const val ALL_SET_REST = 2f
private val ALL_SET_BADGE = 110.dp
private val ALL_SET_HEIGHT = 200.dp

/** When each part of [SearchLoop] happens, in seconds from the start of a loop. */
internal object SearchLoopTimes {
    const val NAME = "Intelligent Search"
    const val FIRST_LETTER = 0.7f
    const val RESULT_POPS = 1.3f
    const val REST_OF_NAME = 1.85f
    const val ENTER = 2.95f
    const val OPENS = 3.3f
    const val LEAVES = 4.35f
    const val LENGTH = 4.75f

    /** Shown still when animations are removed: the name typed and its result up, before Enter. */
    const val STILL = 2.8f

    private const val SECONDS_PER_LETTER = 0.045f
    private val OPENING_RATE = sqrt(500f)

    /** How many letters of [NAME] are typed at [time]: "Int", a pause as its result pops up, then the rest. */
    fun typedAt(time: Float): Int = when {
        time < FIRST_LETTER -> 0
        time < 0.95f -> 1
        time < 1.2f -> 2
        time < REST_OF_NAME -> 3
        else -> (3 + ((time - REST_OF_NAME) / SECONDS_PER_LETTER).toInt()).coerceAtMost(NAME.length)
    }

    /** How far the app has opened at [time]: AppOpeningLayer's critically damped spring of stiffness 500. */
    fun openedAt(time: Float): Float {
        val seconds = time - OPENS
        if (seconds <= 0f) return 0f
        return 1f - (1f + OPENING_RATE * seconds) * exp(-OPENING_RATE * seconds)
    }
}

private fun easeOut(x: Float) = 1f - (1f - x.coerceIn(0f, 1f)).pow(3)
private fun popIn(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return 1f - exp(-6f * t) * cos(8f * t)
}

/**
 * The search overlay in small: the bar drawn with the overlay's own plate, light and lens, laid out at a phone's real
 * 412 dp and scaled, typing the app's name until Enter opens it with the overlay's own Android 17 opening, and starting
 * over. With animations removed it holds still, typed and ready.
 */
@Composable
private fun SearchLoop(context: Context, width: androidx.compose.ui.unit.Dp) {
    val animations by rememberAnimationsEnabled()
    var time by remember { mutableFloatStateOf(if (animations) 0f else SearchLoopTimes.STILL) }
    LaunchedEffect(animations) {
        if (!animations) {
            time = SearchLoopTimes.STILL
            return@LaunchedEffect
        }
        val start = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { time = ((it - start) / 1_000_000_000f) % SearchLoopTimes.LENGTH }
        }
    }
    val look by produceState<SplashLook?>(null) {
        value = withContext(Dispatchers.Default) {
            runCatching { SplashLooks.resolve(context, context.packageName, null, null) }.getOrNull()
        }
    }
    val icon by produceState<ImageBitmap?>(null) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                context.packageManager.getApplicationIcon(context.applicationInfo).toBitmap(240, 240).asImageBitmap()
            }.getOrNull()
        }
    }
    val height = width * LOOP_RATIO
    val scale = width / SCREEN_WIDTH
    val screenHeight = height / scale
    val shape = RoundedCornerShape(22.dp)
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    Box(
        Modifier
            .size(width, height)
            .clip(shape)
            .background(scheme.surface)
            .clearAndSetSemantics {
                contentDescription = "Animated example: typing Intelligent Search in the search bar, pressing Enter and the app opening"
            }
    ) {
        // The text keeps its proportions to the bar whatever the phone's font size: this is a picture of the screen.
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(SCREEN_WIDTH, screenHeight)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                LoopResult({ time }, icon, Modifier.align(Alignment.BottomStart))
                LoopBar({ time }, Modifier.align(Alignment.BottomStart))
                LoopOpening({ time }, look, icon, screenHeight)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (time > SearchLoopTimes.LEAVES) {
                                easeOut((time - SearchLoopTimes.LEAVES) / 0.25f)
                            } else if (animations) {
                                1f - easeOut(time / 0.2f)
                            } else {
                                0f
                            }
                        }
                        .background(scheme.surface)
                )
            }
        }
        Box(Modifier.fillMaxSize().border(2.dp, scheme.outlineVariant, shape))
    }
}

/** The Best Match card, the only result: it pops up at "Int" and lights as Enter goes down. */
@Composable
private fun LoopResult(time: () -> Float, icon: ImageBitmap?, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .padding(start = 14.dp, end = 14.dp, bottom = RESULT_BOTTOM)
            .fillMaxWidth()
            .graphicsLayer {
                val shown = popIn((time() - SearchLoopTimes.RESULT_POPS) / 0.4f)
                alpha = shown.coerceIn(0f, 1f)
                translationY = (1f - shown) * 20.dp.toPx()
            }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("BEST MATCH", color = scheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = GoogleSansFlex)
            Text("Press Enter to Open", color = scheme.onSurfaceVariant, fontSize = 13.sp, fontFamily = GoogleSansFlex)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(RESULT_CARD_HEIGHT)
                .clip(RoundedCornerShape(28.dp))
                .background(scheme.surfaceContainerHigh)
                .drawWithContent {
                    drawContent()
                    val pressed = time() - SearchLoopTimes.ENTER
                    if (pressed > 0f && pressed < 0.45f) {
                        drawRect(scheme.onSurface, alpha = (1f - kotlin.math.abs((pressed - 0.17f) / 0.2f)).coerceIn(0f, 1f) * 0.12f)
                    }
                }
                .padding(horizontal = RESULT_ICON_INSET),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.size(RESULT_ICON))
            } else {
                Spacer(Modifier.size(RESULT_ICON))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(SearchLoopTimes.NAME, color = scheme.onSurface, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, fontFamily = GoogleSansFlex, maxLines = 1)
                Text("Application", color = scheme.onSurfaceVariant, fontSize = 15.sp, fontFamily = GoogleSansFlex, maxLines = 1)
            }
        }
    }
}

/** The overlay's search bar, without its clear, voice and settings buttons. */
@Composable
private fun LoopBar(time: () -> Float, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val typedCount by remember { derivedStateOf { SearchLoopTimes.typedAt(time()) } }
    val typed = SearchLoopTimes.NAME.take(typedCount)
    val provider by rememberSetting(SettingsManager.SEARCH_ENGINE)
    val corner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (typedCount > 0) 20.dp else 100.dp,
        animationSpec = com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens.morphSpring(),
        label = "loop_bar_shape"
    )
    val barShape = RoundedCornerShape(corner)
    Box(modifier.padding(start = 10.dp, end = 10.dp, bottom = BAR_BOTTOM).fillMaxWidth().height(BAR_HEIGHT)) {
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    shape = barShape
                    clip = true
                }
                .materialYouGeminiPlate(cornerRadius = corner)
                .geminiPlateIdleFlow(idle = typedCount == 0, motion = IdleFlowMotion.SearchBar)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchOverlayLensIcon(Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Box(contentAlignment = Alignment.CenterStart) {
                    if (typed.isEmpty()) {
                        Text("Search…", color = scheme.onSurfaceVariant, fontSize = 20.sp, fontFamily = GoogleSansFlex)
                    } else {
                        com.pixel.intelligentsearch.feature.search.ghostText(typed, SearchLoopTimes.NAME)?.let {
                            Text(it, color = scheme.onSurfaceVariant, fontSize = 20.sp, fontFamily = GoogleSansFlex, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(typed, color = scheme.onSurface, fontSize = 20.sp, fontFamily = GoogleSansFlex, maxLines = 1)
                    }
                }
                if (typed.isNotEmpty()) {
                    Text(
                        provider,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.70f),
                        fontSize = 12.sp,
                        fontFamily = GoogleSansFlex,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
        }
        // Enter going down, at the end of the bar.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp)
                .size(60.dp, 44.dp)
                .graphicsLayer {
                    val t = time()
                    val pressed = t - SearchLoopTimes.ENTER
                    val shown = popIn(pressed / 0.2f).coerceIn(0f, 1f)
                    alpha = if (pressed > 0f) shown * (1f - easeOut((t - SearchLoopTimes.OPENS) / 0.1f)) else 0f
                    val keyScale = if (pressed > 0.18f) 0.94f else lerp(0.8f, 1f, shown)
                    scaleX = keyScale
                    scaleY = keyScale
                }
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(26.dp))
        }
    }
}

/** The opening from AppOpeningLayer, drawn the same way: out of the card's icon into the app's real splash screen. */
@Composable
private fun LoopOpening(time: () -> Float, look: SplashLook?, icon: ImageBitmap?, screenHeight: androidx.compose.ui.unit.Dp) {
    val surface = MaterialTheme.colorScheme.surface
    val background = look?.let { Color(it.background) }?.takeIf { it.alpha > 0.99f } ?: surface
    Canvas(Modifier.fillMaxSize()) {
        val p = SearchLoopTimes.openedAt(time())
        if (p <= 0f) return@Canvas
        val iconSize = RESULT_ICON.toPx()
        val top = screenHeight.toPx() - RESULT_BOTTOM.toPx() - RESULT_CARD_HEIGHT.toPx() + (RESULT_CARD_HEIGHT.toPx() - iconSize) / 2f
        val left = (14.dp + RESULT_ICON_INSET).toPx()
        val from = Rect(left, top, left + iconSize, top + iconSize)
        val bounds = lerpRect(from, Rect(Offset.Zero, size), p)
        val corner = lerp(iconSize / 2f, 28.dp.toPx(), p.coerceIn(0f, 1f))
        drawRect(Color.Black, alpha = 0.45f * p.coerceIn(0f, 1f))
        drawRoundRect(background, bounds.topLeft, bounds.size, CornerRadius(corner), alpha = (p / 0.12f).coerceIn(0f, 1f))
        val center = bounds.center
        val startAlpha = (1f - p / 0.35f).coerceIn(0f, 1f)
        val splashAlpha = ((p - 0.05f) / 0.35f).coerceIn(0f, 1f)
        if (look != null && splashAlpha > 0f) {
            drawSplashIcon(look, center, lerp(iconSize / look.visiblePx, 1f, p.coerceIn(0f, 1f)), splashAlpha)
        }
        if (icon != null && (look == null || startAlpha > 0f)) {
            val visible = look?.visiblePx ?: 160.dp.toPx()
            val shown = lerp(iconSize, visible, p.coerceIn(0f, 1f)).toInt()
            drawImage(
                image = icon,
                dstOffset = androidx.compose.ui.unit.IntOffset((center.x - shown / 2f).toInt(), (center.y - shown / 2f).toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(shown, shown),
                alpha = if (look == null) 1f else startAlpha
            )
        }
    }
}

private val SCREEN_WIDTH = 412.dp
/** The loop's height to its width: tall enough for its result and bar, with no empty screen above them to shrink them. */
private const val LOOP_RATIO = 0.8f
private val SIDE_BY_SIDE_MIN_WIDTH = 420.dp
private val BAR_HEIGHT = 72.dp
private val BAR_BOTTOM = 26.dp
private val RESULT_BOTTOM = 112.dp
private val RESULT_CARD_HEIGHT = 92.dp
private val RESULT_ICON = 60.dp
private val RESULT_ICON_INSET = 16.dp
