package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.data.SettingsManager
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

data class TutorialStepInfo(
    val title: String,
    val text: String,
    val cardAlignment: Alignment = Alignment.Center,
    val showArrow: Boolean = true,
    val requireButtonPress: Boolean = true,
    val showCircle: Boolean = false
)


/**
 * Official 3D Bugdroid holding a 4-color Google star balloon with expressive breeze physics:
 * The balloon lightly floats and sways organically in the breeze anchored to Bugdroid's hand,
 * while Bugdroid gently hovers up and down with natural eye blinking.
 * High-resolution multi-layer graphicsLayer implementation with zero clipping or rippling.
 */
@Composable
fun AnimatedBugdroidEmoji(
    modifier: Modifier = Modifier.size(width = 40.dp, height = 56.dp)
) {
    val resources = androidx.compose.ui.platform.LocalResources.current

    val balloonBitmap = remember(resources) {
        android.graphics.BitmapFactory.decodeResource(
            resources,
            R.drawable.ic_bugdroid_balloon,
            android.graphics.BitmapFactory.Options().apply {
                inScaled = false
            }
        ).asImageBitmap()
    }

    val bodyOpenBitmap = remember(resources) {
        android.graphics.BitmapFactory.decodeResource(
            resources,
            R.drawable.ic_bugdroid_body,
            android.graphics.BitmapFactory.Options().apply {
                inScaled = false
            }
        ).asImageBitmap()
    }

    val bodyHalfBitmap = remember(resources) {
        android.graphics.BitmapFactory.decodeResource(
            resources,
            R.drawable.ic_bugdroid_body_half,
            android.graphics.BitmapFactory.Options().apply {
                inScaled = false
            }
        )?.asImageBitmap() ?: bodyOpenBitmap
    }

    val bodyBlinkBitmap = remember(resources) {
        android.graphics.BitmapFactory.decodeResource(
            resources,
            R.drawable.ic_bugdroid_body_blink,
            android.graphics.BitmapFactory.Options().apply {
                inScaled = false
            }
        )?.asImageBitmap() ?: bodyOpenBitmap
    }

    val infiniteTransition = rememberInfiniteTransition(label = "bugdroid_motion_transition")

    // Both phases stay State and are read only inside graphicsLayer {} below, so the float and sway run
    // in the draw phase instead of recomposing the mascot every frame.
    // Bugdroid body hover float: gentle vertical sine wave
    val hoverPhase = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hover_phase"
    )

    // Breeze sway phase: independent natural period for organic air currents
    val breezePhase = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "breeze_phase"
    )

    // 0 = open, 1 = half closed/opening, 2 = fully closed
    var blinkPhase by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val random = java.util.Random()
        while (true) {
            // Idle interval between blinks: around 3.2 to 4.2 seconds
            delay(3200L + random.nextInt(1000))
            // Natural blink down
            blinkPhase = 1
            delay(35L)
            blinkPhase = 2
            delay(80L)
            blinkPhase = 1
            delay(35L)
            blinkPhase = 0

            // 15% chance of an expressive double-blink matching the settings video
            if (random.nextFloat() < 0.15f) {
                delay(120L)
                blinkPhase = 1
                delay(30L)
                blinkPhase = 2
                delay(70L)
                blinkPhase = 1
                delay(30L)
                blinkPhase = 0
            }
        }
    }

    val currentBodyBitmap = when (blinkPhase) {
        1 -> bodyHalfBitmap
        2 -> bodyBlinkBitmap
        else -> bodyOpenBitmap
    }


    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Layer 1: Balloon & String (drawn behind hand, anchored at hand pivot 0.2033f, 0.4455f)
        Image(
            bitmap = balloonBitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val breeze = breezePhase.value
                    val bodyHoverPx = sin(hoverPhase.value) * 1.5.dp.toPx()
                    transformOrigin = TransformOrigin(0.2033f, 0.4455f)
                    rotationZ = (sin(breeze) * 3.5f) + (sin(breeze * 0.5f) * 1.2f)
                    translationX = cos(breeze) * 0.8.dp.toPx()
                    translationY = bodyHoverPx + sin(breeze * 1.3f) * 1.0.dp.toPx()
                }
        )

        // Layer 2: Bugdroid Body (hand grips over string anchor, floating & blinking)
        Image(
            bitmap = currentBodyBitmap,
            contentDescription = "Bugdroid",
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = sin(hoverPhase.value) * 1.5.dp.toPx()
                }
        )
    }
}

/**
 * Modernized Material 3 Expressive Fluid Spotlight Pulse Ring.
 * Utilizes a hardware-accelerated GraphicsLayer to isolate continuous GPU rasterization,
 * preventing CPU recomposition invalidation loops and delivering 120 FPS fluid multi-harmonic waves.
 */
@Composable
private fun FluidSpotlightPulseRing(
    targetCenter: Offset,
    targetRadius: Float,
    primaryColor: Color,
    tertiaryColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fluid_pulse_transition")

    val rippleWave1 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = CubicBezierEasing(0.2f, 0.0f, 0.2f, 1.0f)),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1"
    )

    val rippleWave2 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1600,
                delayMillis = 800,
                easing = CubicBezierEasing(0.2f, 0.0f, 0.2f, 1.0f)
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2"
    )

    val breathingFactor = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    val auraColors = remember(primaryColor) {
        listOf(
            primaryColor.copy(alpha = 0.25f),
            primaryColor.copy(alpha = 0.08f),
            Color.Transparent
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                // Hardware RenderNode isolation for smooth 120 FPS rendering
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        val strokeBaseWidth = 3.dp.toPx()
        val breathing = breathingFactor.value
        val focalRadius = targetRadius * breathing

        // 1. Soft focal ambient glow aura behind target
        drawCircle(
            brush = Brush.radialGradient(
                colors = auraColors,
                center = targetCenter,
                radius = focalRadius * 1.5f
            ),
            center = targetCenter,
            radius = focalRadius * 1.5f
        )

        // 2. Focal anchor ring with breathing micro-scale
        drawCircle(
            color = primaryColor,
            alpha = 0.88f,
            radius = focalRadius,
            center = targetCenter,
            style = Stroke(width = strokeBaseWidth)
        )

        // 3. Fluid Wave 1 (Primary harmonic wave expanding outward with non-linear dissipation)
        val p1 = rippleWave1.value
        val r1 = targetRadius + (targetRadius * 0.75f * p1)
        val a1 = ((1f - p1) * 0.78f).coerceIn(0f, 1f)
        drawCircle(
            color = primaryColor,
            alpha = a1,
            radius = r1,
            center = targetCenter,
            style = Stroke(width = (strokeBaseWidth * (1f - p1 * 0.45f)).coerceAtLeast(1.dp.toPx()))
        )

        // 4. Fluid Wave 2 (Tertiary harmonic chromatic wave staggered by 800ms)
        val p2 = rippleWave2.value
        val r2 = targetRadius + (targetRadius * 0.75f * p2)
        val a2 = ((1f - p2) * 0.65f).coerceIn(0f, 1f)
        drawCircle(
            color = tertiaryColor,
            alpha = a2,
            radius = r2,
            center = targetCenter,
            style = Stroke(width = (strokeBaseWidth * (1f - p2 * 0.45f)).coerceAtLeast(1.dp.toPx()))
        )
    }
}

@Composable
fun TutorialSpotlightOverlay(
    prefs: SharedPreferences,
    stepsInfo: Map<Int, TutorialStepInfo>,
    onComplete: () -> Unit,
    onStepAdvance: (Int) -> Unit = {}
) {
    if (!TutorialManager.isTutorialActive(prefs)) return

    var currentStep by rememberSetting(SettingsManager.TUTORIAL_STEP)
    val stepInfo = stepsInfo[currentStep]
    if (stepInfo == null) {
        LaunchedEffect(currentStep) {
            if (currentStep >= TutorialManager.TOTAL_STEPS) {
                TutorialManager.completeTutorial(prefs)
            }
            onComplete()
        }
        return
    }

    val coroutineScope = rememberCoroutineScope()
    val localView = LocalView.current
    val hapticEngine = remember(localView.context) {
        com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(localView.context)
    }
    var isAdvancing by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    // Only decode the keyed video on the step that shows it, and only where the AGSL key is supported (API 33+).
    val showsBugdroidVideo = android.os.Build.VERSION.SDK_INT >= 33 &&
        (stepInfo.text.contains("[bugdroid]") || stepInfo.text.contains(":)"))
    val welcomeExoPlayer = rememberBugdroidPlayer(BugdroidClip.WELCOME, enabled = showsBugdroidVideo)

    val stepForward: () -> Unit = {
        if (currentStep < TutorialManager.TOTAL_STEPS - 1) {
            val nextStep = currentStep + 1
            currentStep = nextStep
            onStepAdvance(nextStep)
        } else {
            TutorialManager.completeTutorial(prefs)
            onComplete()
        }
    }
    val advanceToNextStep: () -> Unit = {
        if (!isAdvancing) {
            isAdvancing = true
            hapticEngine.performHaptic(localView, com.pixel.intelligentsearch.core.haptics.PixelHapticType.CLICK)
            stepForward()
        }
    }
    // Taps are accepted again only once the new step has been on screen briefly, so a double tap can't skip one.
    LaunchedEffect(currentStep) {
        delay(350)
        isAdvancing = false
    }


    // Screen reader TalkBack live announcement on step change
    @Suppress("DEPRECATION")
    LaunchedEffect(currentStep, stepInfo.title) {
        val cleanSpeech = stepInfo.text
            .replace("[three_bars]", "three bar settings icon")
            .replace("[bugdroid]", "")
            .replace(":)", "")
        val announcement = "Tutorial step ${currentStep + 1} of ${TutorialManager.TOTAL_STEPS}: ${stepInfo.title}. $cleanSpeech"
        localView.announceForAccessibility(announcement)
    }

    var overlayRootOffset by remember { mutableStateOf(Offset.Zero) }
    var cardBounds by remember { mutableStateOf(Rect.Zero) }

    // Spring animation for entering each step card
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(currentStep) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            1f,
            animationSpec = ExpressiveMotionTokens.bouncySpring()
        )
    }

    // Draw-phase infinite transitions: state reads are scoped strictly to onDraw/graphicsLayer
    val infiniteTransition = rememberInfiniteTransition(label = "tutorial_transition")
    val slitherPhase = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "slither"
    )
    val colorPhase = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "color"
    )

    val rawTargetRect = TutorialManager.getTargetState(currentStep).rect
    val localTargetRect = remember(rawTargetRect, overlayRootOffset) {
        rawTargetRect?.translate(-overlayRootOffset.x, -overlayRootOffset.y)
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val density = LocalDensity.current

    val localTargetRectState = rememberUpdatedState(localTargetRect)
    val cardBoundsState = rememberUpdatedState(cardBounds)
    val stepInfoState = rememberUpdatedState(stepInfo)

    // Edge-to-edge safe drawing insets for Android 15/16/17 (compileSdk 37)
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val statusBarTopPadding = safeInsets.calculateTopPadding()
    val navBarBottomPadding = safeInsets.calculateBottomPadding()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .semantics {
                isTraversalGroup = true
                // The only pane title here: a second one nested inside (on the card) makes Compose throw when the
                // accessibility tree is built, i.e. as soon as TalkBack is on.
                paneTitle = "Tutorial step ${currentStep + 1} of ${TutorialManager.TOTAL_STEPS}: ${stepInfo.title}"
            }
            .onGloballyPositioned { coordinates ->
                if (coordinates.isAttached) {
                    overlayRootOffset = coordinates.boundsInWindow().topLeft
                }
            }
            .pointerInput(currentStep) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val activeStepInfo = stepInfoState.value
                        val currentCardBounds = cardBoundsState.value
                        val currentTargetRect = localTargetRectState.value

                        val downChange = event.changes.firstOrNull { it.changedToDown() }
                        if (downChange != null) {
                            val pos = downChange.position
                            val isInsideCard = currentCardBounds != Rect.Zero && currentCardBounds.contains(pos)
                            val isInsideTarget = currentTargetRect != null && run {
                                val targetCenter = currentTargetRect.center
                                val dx = targetCenter.x - pos.x
                                val dy = targetCenter.y - pos.y
                                val distance = hypot(dx, dy)
                                val targetRadius = maxOf(currentTargetRect.width, currentTargetRect.height) / 2f + 8.dp.toPx()
                                distance <= targetRadius
                            }

                            if (!isInsideCard && !isInsideTarget) {
                                downChange.consume()
                            }
                        }

                        val upChange = event.changes.firstOrNull { it.changedToUp() }
                        if (upChange != null && !activeStepInfo.requireButtonPress && !isAdvancing) {
                            val pos = upChange.position
                            val isInsideTarget = currentTargetRect != null && run {
                                val targetCenter = currentTargetRect.center
                                val dx = targetCenter.x - pos.x
                                val dy = targetCenter.y - pos.y
                                val distance = hypot(dx, dy)
                                val targetRadius = maxOf(currentTargetRect.width, currentTargetRect.height) / 2f + 8.dp.toPx()
                                distance <= targetRadius
                            }
                            if (isInsideTarget) {
                                isAdvancing = true
                                coroutineScope.launch {
                                    delay(150)
                                    stepForward()
                                }
                            }
                        }
                    }
                }
            }
    ) {
        // Modern Fluid Pulse Ring with GraphicsLayer isolation
        if (stepInfo.showCircle && localTargetRect != null) {
            val targetCenter = localTargetRect.center
            val density = LocalDensity.current
            val targetRadius = maxOf(localTargetRect.width, localTargetRect.height) / 2f + with(density) { 8.dp.toPx() }
            FluidSpotlightPulseRing(
                targetCenter = targetCenter,
                targetRadius = targetRadius,
                primaryColor = primaryColor,
                tertiaryColor = tertiaryColor
            )
        }

        // Hardware-optimized Squiggle Arrow connecting Card to Target
        if (stepInfo.showArrow && localTargetRect != null && cardBounds != Rect.Zero) {
            val squigglePath = remember { Path() }
            val animatedSquigglePath = remember { Path() }
            val arrowHeadPath = remember { Path() }
            val pathMeasure = remember { PathMeasure() }
            val gradientColors = remember(primaryColor, tertiaryColor) {
                listOf(primaryColor, tertiaryColor, primaryColor, tertiaryColor, primaryColor)
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Isolates 120 FPS squiggle slither and color gradient updates into a hardware RenderNode layer
                        // Prevents layout/composition invalidation of surrounding nodes
                    }
            ) {
                val targetCenter = localTargetRect.center

                // Determine arrow connection points based on relative bounding boxes
                val (startPoint, endPoint) = run {
                    if (cardBounds.bottom <= localTargetRect.top) {
                        // Card is above target: arrow flows downwards from card bottom to target top
                        val startX = targetCenter.x.coerceIn(cardBounds.left + 24f, cardBounds.right - 24f)
                        val startY = cardBounds.bottom + 2f
                        val endX = targetCenter.x
                        val endY = (localTargetRect.top - 12f).coerceAtLeast(startY + 8f)
                        Offset(startX, startY) to Offset(endX, endY)
                    } else if (cardBounds.top >= localTargetRect.bottom) {
                        // Card is below target: arrow flows upwards from card top to target bottom
                        val startX = targetCenter.x.coerceIn(cardBounds.left + 24f, cardBounds.right - 24f)
                        val startY = cardBounds.top - 2f
                        val endX = targetCenter.x
                        val endY = (localTargetRect.bottom + 12f).coerceAtMost(startY - 8f)
                        Offset(startX, startY) to Offset(endX, endY)
                    } else {
                        // Horizontally adjacent
                        val startY = targetCenter.y.coerceIn(cardBounds.top + 16f, cardBounds.bottom - 16f)
                        if (cardBounds.right <= localTargetRect.left) {
                            Offset(cardBounds.right + 2f, startY) to Offset((localTargetRect.left - 12f).coerceAtLeast(cardBounds.right + 8f), targetCenter.y)
                        } else {
                            Offset(cardBounds.left - 2f, startY) to Offset((localTargetRect.right + 12f).coerceAtMost(cardBounds.left - 8f), targetCenter.y)
                        }
                    }
                }

                val pdx = endPoint.x - startPoint.x
                val pdy = endPoint.y - startPoint.y
                val length = hypot(pdx, pdy)
                val angle = atan2(pdy, pdx)
                val cosA = cos(angle)
                val sinA = sin(angle)

                if (length >= 12f) {
                    val numSquiggles = (length / 38f).toInt().coerceIn(2, 6)
                    val amplitude = min(14f, length * 0.16f)
                    val frequency = (numSquiggles * Math.PI * 2) / length

                    squigglePath.reset()
                    squigglePath.moveTo(startPoint.x, startPoint.y)

                    val currentSlither = slitherPhase.value
                    val numPoints = 100
                    for (i in 1..numPoints) {
                        val t = i / numPoints.toFloat()
                        val x = t * length
                        val taper = sin(t * Math.PI).toFloat()
                        val y = sin(x * frequency - currentSlither).toFloat() * amplitude * taper

                        val rx = x * cosA - y * sinA
                        val ry = x * sinA + y * cosA

                        squigglePath.lineTo(startPoint.x + rx, startPoint.y + ry)
                    }

                    pathMeasure.setPath(squigglePath, false)
                    animatedSquigglePath.reset()
                    pathMeasure.getSegment(0f, pathMeasure.length * animationProgress.value, animatedSquigglePath, true)

                    val currentColorPhase = colorPhase.value
                    val gradientBrush = Brush.linearGradient(
                        colors = gradientColors,
                        start = Offset(
                            startPoint.x + pdx * (currentColorPhase - 1f),
                            startPoint.y + pdy * (currentColorPhase - 1f)
                        ),
                        end = Offset(
                            startPoint.x + pdx * (currentColorPhase + 1f),
                            startPoint.y + pdy * (currentColorPhase + 1f)
                        )
                    )

                    drawPath(
                        path = animatedSquigglePath,
                        brush = gradientBrush,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    if (animationProgress.value > 0.85f) {
                        val alpha = ((animationProgress.value - 0.85f) * 6.6f).coerceIn(0f, 1f)
                        val arrowHeadLen = 30f
                        arrowHeadPath.reset()
                        arrowHeadPath.moveTo(endPoint.x, endPoint.y)
                        arrowHeadPath.lineTo(
                            endPoint.x - arrowHeadLen * cos(angle - Math.PI / 6).toFloat(),
                            endPoint.y - arrowHeadLen * sin(angle - Math.PI / 6).toFloat()
                        )
                        arrowHeadPath.moveTo(endPoint.x, endPoint.y)
                        arrowHeadPath.lineTo(
                            endPoint.x - arrowHeadLen * cos(angle + Math.PI / 6).toFloat(),
                            endPoint.y - arrowHeadLen * sin(angle + Math.PI / 6).toFloat()
                        )

                        drawPath(
                            path = arrowHeadPath,
                            brush = gradientBrush,
                            alpha = alpha,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }
        }

        val isDarkTheme = isSystemInDarkTheme()
        val cardBgColor = if (isDarkTheme) {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f)
        } else {
            MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f)
        }
        val cardTextColor = if (isDarkTheme) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.inverseOnSurface
        }

        val (topPadding, bottomPadding) = remember(
            localTargetRect,
            stepInfo.showArrow,
            constraints.maxHeight,
            statusBarTopPadding,
            navBarBottomPadding
        ) {
            if (localTargetRect != null && stepInfo.showArrow) {
                val targetTopDp = with(density) { localTargetRect.top.toDp() }
                val targetBottomDp = with(density) { localTargetRect.bottom.toDp() }
                val isBottomTarget = localTargetRect.center.y > (constraints.maxHeight / 2f)

                if (isBottomTarget) {
                    val minClearance = 36.dp
                    val topPad = (statusBarTopPadding + 16.dp).coerceAtLeast(16.dp)
                    val rawBotPad = (maxHeight - targetTopDp + minClearance).coerceAtLeast(navBarBottomPadding + 16.dp)
                    val maxAllowedBotPad = (maxHeight - topPad - 130.dp).coerceAtLeast(16.dp)
                    val botPad = rawBotPad.coerceAtMost(maxAllowedBotPad)
                    topPad to botPad
                } else {
                    val minClearance = 36.dp
                    val botPad = (navBarBottomPadding + 24.dp).coerceAtLeast(24.dp)
                    val rawTopPad = (targetBottomDp + minClearance).coerceAtLeast(statusBarTopPadding + 16.dp)
                    val maxAllowedTopPad = (maxHeight - botPad - 130.dp).coerceAtLeast(16.dp)
                    val topPad = rawTopPad.coerceAtMost(maxAllowedTopPad)
                    topPad to botPad
                }
            } else {
                val topPad = (statusBarTopPadding + 16.dp).coerceAtLeast(16.dp)
                val botPad = (navBarBottomPadding + 24.dp).coerceAtLeast(24.dp)
                topPad to botPad
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding, bottom = bottomPadding)
                .padding(horizontal = 24.dp),
            contentAlignment = stepInfo.cardAlignment
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                    .graphicsLayer {
                        val enter = animationProgress.value
                        val scale = 0.90f + 0.10f * enter
                        scaleX = scale
                        scaleY = scale
                        alpha = enter.coerceIn(0f, 1f)
                        translationY = (1f - enter) * 24.dp.toPx()
                    }
                    .semantics {
                        isTraversalGroup = true
                        liveRegion = LiveRegionMode.Polite
                    }
                    .onGloballyPositioned { coordinates ->
                        if (coordinates.isAttached) {
                            val windowBounds = coordinates.boundsInWindow()
                            cardBounds = Rect(
                                offset = Offset(windowBounds.left - overlayRootOffset.x, windowBounds.top - overlayRootOffset.y),
                                size = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
                            )
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                        .semantics {
                            isTraversalGroup = true
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // TalkBack Traversal 1: Step Indicator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                traversalIndex = 1f
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STEP ${currentStep + 1} OF ${TutorialManager.TOTAL_STEPS}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.2.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // TalkBack Traversal 2: Title with Heading Semantics
                    Text(
                        text = stepInfo.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = cardTextColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                heading()
                                traversalIndex = 2f
                            }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // TalkBack Traversal 3: Description Body Text
                    val bugdroidTag = "[bugdroid]"
                    val rawText = stepInfo.text
                    if (rawText.contains(bugdroidTag) || rawText.contains(":)")) {
                        val cleanText = rawText.replace(bugdroidTag, "").replace(":)", "").trim()
                        if (welcomeExoPlayer != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        traversalIndex = 3f
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                BugdroidPlayer(
                                    player = welcomeExoPlayer,
                                    clip = BugdroidClip.WELCOME,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(275.dp)
                                        .offset(y = (-10).dp)
                                        .semantics {
                                            contentDescription = "Animated 3D Bugdroid holding Google star balloon"
                                        },
                                    scaleFactor = 0.95f
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = cleanText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = cardTextColor.copy(alpha = 0.92f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        traversalIndex = 3f
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = cleanText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    color = cardTextColor.copy(alpha = 0.92f),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                AnimatedBugdroidEmoji(
                                    modifier = Modifier.size(width = 44.dp, height = 58.dp)
                                )
                            }
                        }
                    } else {
                        val iconTag = "[three_bars]"
                        val normalizedText = if (stepInfo.text.contains(iconTag)) {
                            stepInfo.text
                        } else if (stepInfo.text.contains("( the three bar )")) {
                            stepInfo.text.replace("( the three bar )", "( [three_bars] )")
                        } else {
                            stepInfo.text
                        }

                        if (normalizedText.contains(iconTag)) {
                            val parts = normalizedText.split(iconTag)
                            val inlineContent = mapOf(
                                "three_bars" to InlineTextContent(
                                    Placeholder(
                                        width = 18.sp,
                                        height = 18.sp,
                                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                    )
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_three_bars),
                                        contentDescription = "Settings icon",
                                        tint = cardTextColor.copy(alpha = 0.95f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                            val annotatedString = buildAnnotatedString {
                                parts.forEachIndexed { index, part ->
                                    append(part)
                                    if (index < parts.size - 1) {
                                        appendInlineContent("three_bars", "[icon]")
                                    }
                                }
                            }
                            Text(
                                text = annotatedString,
                                inlineContent = inlineContent,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                color = cardTextColor.copy(alpha = 0.92f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        traversalIndex = 3f
                                    }
                            )
                        } else {
                            Text(
                                text = stepInfo.text,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                color = cardTextColor.copy(alpha = 0.92f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        traversalIndex = 3f
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Controls: Row with guaranteed min 48dp touch targets and clear TalkBack traversal
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                isTraversalGroup = true
                                traversalIndex = 4f
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                if (!isAdvancing) {
                                    isAdvancing = true
                                    hapticEngine.performHaptic(localView, com.pixel.intelligentsearch.core.haptics.PixelHapticType.OVERLAY_DISMISS)
                                    TutorialManager.completeTutorial(prefs)
                                    onComplete()
                                }
                            },
                            shape = RoundedCornerShape(percent = 50),
                            modifier = Modifier
                                .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
                                .minimumInteractiveComponentSize()
                                .semantics {
                                    role = Role.Button
                                    traversalIndex = 4.1f
                                    contentDescription = "Skip entire tutorial"
                                }
                        ) {
                            Text(
                                text = "Skip",
                                color = cardTextColor.copy(alpha = 0.75f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }

                        // Primary / Advance Action Button (Guaranteed min 48x48dp touch target)
                        if (stepInfo.requireButtonPress) {
                            val isFinalStep = currentStep >= TutorialManager.TOTAL_STEPS - 1
                            val buttonLabel = if (isFinalStep) "Got it" else "Next"
                            val buttonDesc = if (isFinalStep) "Finish tutorial" else "Next tutorial step"
                            Button(
                                onClick = advanceToNextStep,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDarkTheme) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.inversePrimary,
                                    contentColor = if (isDarkTheme) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.inverseSurface
                                ),
                                shape = RoundedCornerShape(percent = 50),
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 88.dp, minHeight = 48.dp)
                                    .minimumInteractiveComponentSize()
                                    .semantics {
                                        role = Role.Button
                                        traversalIndex = 4.2f
                                        contentDescription = buttonDesc
                                    }
                            ) {
                                Text(
                                    text = buttonLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        } else {
                            Button(
                                onClick = advanceToNextStep,
                                colors = ButtonDefaults.filledTonalButtonColors(),
                                shape = RoundedCornerShape(percent = 50),
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 96.dp, minHeight = 48.dp)
                                    .minimumInteractiveComponentSize()
                                    .semantics {
                                        role = Role.Button
                                        traversalIndex = 4.2f
                                        contentDescription = "Tap highlighted area or tap here to continue"
                                    }
                            ) {
                                Text(
                                    text = "Continue",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
