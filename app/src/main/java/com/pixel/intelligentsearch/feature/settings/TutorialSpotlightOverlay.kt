package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.util.clampSafely
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.get
import android.content.SharedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
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
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
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
    val showArrow: Boolean = true,
    val showCircle: Boolean = false,
    /** A small piece of the app playing between the title and the words. */
    val demo: TutorialDemo? = null
)


/**
 * Every step's card rests in the same place: its bottom edge this far above the navigation bar, just over the search
 * bar on the search screen and over the tab pill in Settings (higher only if the search bar reaches above it), so the
 * card never jumps between steps; only its height changes, growing upward.
 */
private val CARD_BOTTOM_ROOM = 112.dp

/** Room kept between the card and the search bar under it. */
private val CARD_BAR_GAP = 44.dp

/** The tutorial card's background, its text and the "STEP x OF y" label above the title. */
internal data class TutorialCardColors(val container: Color, val content: Color, val stepLabel: Color)

/**
 * [dark] is the app's theme, not the system's, so the card matches the screen it covers. The light card is an
 * inverse (dark) surface, so its label takes the inverse primary, which is made to be read on it.
 *
 * The card is opaque: see-through, the search results behind it ran into its text, and on the Settings steps what lay
 * behind it showed as a faint box inside it.
 */
internal fun tutorialCardColors(scheme: androidx.compose.material3.ColorScheme, dark: Boolean): TutorialCardColors =
    if (dark) {
        TutorialCardColors(
            container = scheme.surfaceContainerHighest,
            content = scheme.onSurface,
            stepLabel = scheme.primary
        )
    } else {
        TutorialCardColors(
            container = scheme.inverseSurface,
            content = scheme.inverseOnSurface,
            stepLabel = scheme.inversePrimary
        )
    }

@Composable
fun TutorialSpotlightOverlay(
    prefs: SharedPreferences,
    stepsInfo: Map<Int, TutorialStepInfo>,
    onComplete: () -> Unit,
    onStepAdvance: (Int) -> Unit = {},
    /**
     * Over Settings' tabs: the page shows, dimmed, with the part the step is about in a spotlight; only the card takes
     * a touch, and back stays on the step.
     */
    spotlight: Boolean = false
) {
    if (!TutorialManager.isTutorialActive(prefs)) return

    var currentStep by rememberSetting(SettingsManager.TUTORIAL_STEP)
    val stepInfo = stepsInfo[currentStep]
    if (stepInfo == null) {
        LaunchedEffect(currentStep) {
            // Finished, or stuck on a search-screen step with the search overlay switched off (those steps can only
            // run there): either way the tutorial ends rather than hanging around unfinished.
            if (currentStep >= TutorialManager.TOTAL_STEPS || !prefs[SettingsManager.SEARCH_OVERLAY_ENABLED]) {
                TutorialManager.completeTutorial(prefs)
            }
            onComplete()
        }
        return
    }

    if (spotlight) {
        // Tells Gallery and Wellbeing which step shows, so the page marks (and scrolls to) the part it spotlights.
        androidx.compose.runtime.DisposableEffect(currentStep) {
            TutorialManager.liveStep = currentStep
            onDispose {
                TutorialManager.liveStep = null
                TutorialManager.cardTop = 0f
            }
        }
    }

    val localView = LocalView.current
    val hapticEngine = remember(localView.context) {
        com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(localView.context)
    }
    var isAdvancing by remember { mutableStateOf(false) }

    // Only decode the keyed video on the step that shows it, and only where the AGSL key is supported (API 33+).
    val showsBugdroidVideo = android.os.Build.VERSION.SDK_INT >= 33 &&
        (stepInfo.text.contains("[bugdroid]") || stepInfo.text.contains(":)"))
    val welcomePlayer = rememberBugdroidPlayer(BugdroidClip.WELCOME, enabled = showsBugdroidVideo)

    val cardEnter = remember { Animatable(0f) }
    val endScope = androidx.compose.runtime.rememberCoroutineScope()
    val animate by rememberAnimationsEnabled()
    // Got It and Skip: the card (and the dim over Settings) fade away before the tutorial ends, rather than vanishing.
    val finish: () -> Unit = {
        endScope.launch {
            if (animate) cardEnter.animateTo(0f, tween(180))
            TutorialManager.completeTutorial(prefs)
            onComplete()
        }
    }

    val stepForward: () -> Unit = {
        if (currentStep < TutorialManager.TOTAL_STEPS - 1) {
            val nextStep = currentStep + 1
            currentStep = nextStep
            onStepAdvance(nextStep)
        } else {
            finish()
        }
    }
    val advanceToNextStep: () -> Unit = {
        if (!isAdvancing) {
            isAdvancing = true
            hapticEngine.performPredictiveBackHaptic(localView)
            stepForward()
        }
    }
    // Taps are accepted again only once the new step has been on screen briefly, so a double tap can't skip one.
    LaunchedEffect(currentStep) {
        delay(350)
        isAdvancing = false
    }

    var overlayRootOffset by remember { mutableStateOf(Offset.Zero) }
    var cardBounds by remember { mutableStateOf(Rect.Zero) }

    // The card comes in once, on a spring with no bounce; after that a step changes only what the card says (crossfaded)
    // and its height, so the card never jumps or pops between steps.
    LaunchedEffect(Unit) {
        cardEnter.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))
    }
    // The arrow draws itself in on each step that has one.
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(currentStep) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            1f,
            animationSpec = androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = androidx.compose.animation.core.Spring.StiffnessLow)
        )
    }

    // Draw-phase infinite transitions: state reads are scoped strictly to onDraw/graphicsLayer. With animations
    // off the arrow rests still at the loops' start.
    val loopsOn by rememberAnimationsEnabled()
    val slitherPhase: State<Float>
    val colorPhase: State<Float>
    if (loopsOn) {
        val infiniteTransition = rememberInfiniteTransition(label = "tutorial_transition")
        slitherPhase = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (Math.PI * 2).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "slither"
        )
        colorPhase = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "color"
        )
    } else {
        slitherPhase = remember { mutableFloatStateOf(0f) }
        colorPhase = slitherPhase
    }

    val rawTargetRect = TutorialManager.getTargetState(currentStep).rect
    val localTargetRect = remember(rawTargetRect, overlayRootOffset) {
        rawTargetRect?.translate(-overlayRootOffset.x, -overlayRootOffset.y)
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val density = LocalDensity.current

    val localTargetRectState = rememberUpdatedState(localTargetRect)
    val cardBoundsState = rememberUpdatedState(cardBounds)

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
                // accessibility tree is built, i.e. as soon as TalkBack is on. It stays the same on every step, so
                // a step change isn't announced as a new pane; the card's live region reads each step.
                paneTitle = "Tutorial"
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
                        val currentCardBounds = cardBoundsState.value
                        val currentTargetRect = localTargetRectState.value

                        val downChange = event.changes.firstOrNull { it.changedToDown() }
                        if (downChange != null) {
                            val pos = downChange.position
                            val isInsideCard = currentCardBounds != Rect.Zero && currentCardBounds.contains(pos)
                            // Over Settings the spotlit part is only shown: a tap there would open a look or leave the
                            // app (usage access) mid-step.
                            val isInsideTarget = !spotlight && currentTargetRect != null && run {
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
                    }
                }
            }
    ) {
        // In a short window (a phone on its side) a card placed beside its target is left a slit too short for the
        // step's words, so there it is placed as a centered step's is, with no arrow, the target still ringed.
        val arrowShown = stepInfo.showArrow && maxHeight >= SHORT_WINDOW_HEIGHT

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
        if (arrowShown && localTargetRect != null && cardBounds != Rect.Zero) {
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
                        val startX = targetCenter.x.clampSafely(cardBounds.left + 24f, cardBounds.right - 24f)
                        val startY = cardBounds.bottom + 2f
                        val endX = targetCenter.x
                        val endY = (localTargetRect.top - 12f).coerceAtLeast(startY + 8f)
                        Offset(startX, startY) to Offset(endX, endY)
                    } else if (cardBounds.top >= localTargetRect.bottom) {
                        // Card is below target: arrow flows upwards from card top to target bottom
                        val startX = targetCenter.x.clampSafely(cardBounds.left + 24f, cardBounds.right - 24f)
                        val startY = cardBounds.top - 2f
                        val endX = targetCenter.x
                        val endY = (localTargetRect.bottom + 12f).coerceAtMost(startY - 8f)
                        Offset(startX, startY) to Offset(endX, endY)
                    } else {
                        // Horizontally adjacent
                        val startY = targetCenter.y.clampSafely(cardBounds.top + 16f, cardBounds.bottom - 16f)
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

        val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val cardColors = tutorialCardColors(MaterialTheme.colorScheme, isDarkTheme)
        val cardBgColor = cardColors.container
        val cardTextColor = cardColors.content

        val shortWindow = maxHeight < SHORT_WINDOW_HEIGHT
        // The search bar's top edge (px), when it is on this screen and at its bottom: the card keeps clear of it.
        val barTop = if (spotlight || !prefs[SettingsManager.BOTTOM_SEARCH]) null else TutorialManager.targetStates[1]?.rect?.top?.minus(overlayRootOffset.y)
        val topPadding = (statusBarTopPadding + 16.dp).coerceAtLeast(16.dp)
        val bottomPadding = run {
            val resting = navBarBottomPadding + if (shortWindow) 24.dp + 64.dp else CARD_BOTTOM_ROOM
            val overBar = barTop?.let { with(density) { maxHeight - it.toDp() } + CARD_BAR_GAP } ?: 0.dp
            // Never so high that the card is left less than a usable height.
            maxOf(resting, overBar).coerceAtMost((maxHeight - topPadding - 160.dp).coerceAtLeast(16.dp))
        }

        if (spotlight) {
            TutorialSpotlightDim(localTargetRect, cardBounds.top.takeIf { cardBounds != Rect.Zero }, cardEnter.value, primaryColor)
            // Back stays on the step: it would otherwise slide the tab away from what the step is showing.
            androidx.activity.compose.BackHandler { }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding, bottom = bottomPadding)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    // Wider in a short window (a phone on its side), so the step's words take fewer lines of its height.
                    .widthIn(max = if (shortWindow) 720.dp else 520.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                    .graphicsLayer {
                        val enter = cardEnter.value
                        val scale = 0.96f + 0.04f * enter
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        alpha = enter.coerceIn(0f, 1f)
                        translationY = (1f - enter) * 16.dp.toPx()
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
                            if (spotlight) TutorialManager.cardTop = windowBounds.top
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                        .semantics {
                            isTraversalGroup = true
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // The step text scrolls within whatever height is left (small screens, large text), so Skip
                    // and Next below it always stay in view.
                    BoxWithConstraints(Modifier.weight(1f, fill = false)) {
                    // The height the step's words have before they must scroll (the Welcome video sizes to it).
                    val scrollRoom = maxHeight
                    val scrollWidth = maxWidth
                    // A new step crossfades in place of the last one while the card eases to the new height (it grows
                    // and shrinks upward, its bottom edge staying put).
                    androidx.compose.animation.AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            if (loopsOn) {
                                (androidx.compose.animation.fadeIn(tween(220, delayMillis = 80)) togetherWith
                                    androidx.compose.animation.fadeOut(tween(80))) using
                                    androidx.compose.animation.SizeTransform(clip = true) { _, _ ->
                                        androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
                                    }
                            } else {
                                androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                            }
                        },
                        contentAlignment = Alignment.BottomCenter,
                        label = "tutorialStep"
                    ) { shownStep ->
                    val shownInfo = stepsInfo[shownStep] ?: stepInfo
                    // Each step's words start at their top: a step scrolled to reach a switch doesn't open the next
                    // one scrolled past its title.
                    val stepScroll = remember(shownStep) { ScrollState(0) }
                    Column(
                        modifier = Modifier
                            .verticalScroll(stepScroll),
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
                                text = "STEP ${shownStep + 1} OF ${TutorialManager.TOTAL_STEPS}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = cardColors.stepLabel,
                                letterSpacing = 1.2.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // TalkBack Traversal 2: Title with Heading Semantics
                        Text(
                            text = shownInfo.title,
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

                        shownInfo.demo?.let { demo ->
                            Spacer(modifier = Modifier.height(6.dp))
                            TutorialDemoStage(demo)
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // TalkBack Traversal 3: Description Body Text
                        val bugdroidTag = "[bugdroid]"
                        val rawText = shownInfo.text
                        if (rawText.contains(bugdroidTag) || rawText.contains(":)")) {
                            val cleanText = rawText.replace(bugdroidTag, "").replace(":)", "").trim()
                            // 275 dp on a phone, but never more than 30% of the window, so short screens keep room for text,
                            // and never so tall that the words under it are pushed out of the card (a 4:3 unfolded screen):
                            // the card's room less the step's heading and the words.
                            val windowHeightPx = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height
                            val welcomeMeasurer = androidx.compose.ui.text.rememberTextMeasurer(cacheSize = 1)
                            val welcomeStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp)
                            val welcomeVideoHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
                                val textWidth = (scrollWidth.roundToPx() - 16.dp.roundToPx()).coerceAtLeast(1)
                                val words = welcomeMeasurer.measure(cleanText, welcomeStyle, constraints = androidx.compose.ui.unit.Constraints(maxWidth = textWidth)).size.height.toDp()
                                val fits = scrollRoom - WELCOME_HEADING_ROOM - words
                                // With no room for it beside the words (a phone on its side), the video stays out.
                                if (fits < WELCOME_VIDEO_MIN) 0.dp else minOf(275.dp, (windowHeightPx * 0.3f).toDp(), fits)
                            }
                            if (welcomePlayer != null && welcomeVideoHeight > 0.dp) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics {
                                            traversalIndex = 3f
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    BugdroidPlayer(
                                        player = welcomePlayer,
                                        clip = BugdroidClip.WELCOME,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(welcomeVideoHeight)
                                            .offset(y = (-10).dp)
                                            .semantics {
                                                contentDescription = "Animated Bugdroid holding a Google star balloon"
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
                            val normalizedText = if (shownInfo.text.contains(iconTag)) {
                                shownInfo.text
                            } else if (shownInfo.text.contains("( the three bar )")) {
                                shownInfo.text.replace("( the three bar )", "( [three_bars] )")
                            } else {
                                shownInfo.text
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
                                            contentDescription = null,
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
                                    text = shownInfo.text,
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

                        // The Discover step turns its button on right here, with the switch Settings has for it.
                        if (shownInfo.demo == TutorialDemo.Discover) {
                            Spacer(modifier = Modifier.height(14.dp))
                            TutorialDiscoverSwitch()
                        }
                    }
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
                                    finish()
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
                        val isFinalStep = currentStep >= TutorialManager.TOTAL_STEPS - 1
                        val buttonLabel = if (isFinalStep) TUTORIAL_FINISH_LABEL else "Next"
                        val buttonDesc = if (isFinalStep) "$TUTORIAL_FINISH_LABEL, finish tutorial" else "Next tutorial step"
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
                    }
                }
            }
        }
    }
}

/** The tutorial's last button, as its last step names it too. */
internal const val TUTORIAL_FINISH_LABEL = "Got It"

/** Above the Welcome video: the step's number and title, and the room between them and the words under it. */
private val WELCOME_HEADING_ROOM = 72.dp

/** The Welcome video never shrinks below this to make room: the card scrolls instead. */
private val WELCOME_VIDEO_MIN = 96.dp

/** Below this window height a centered card keeps clear of the search bar on the bar's side only. */
private val SHORT_WINDOW_HEIGHT = 480.dp

/** The dim over a Settings tab, outside the spotlight: a rounded window this far around the step's part of the page. */
private val SPOTLIGHT_MARGIN = 8.dp
private val SPOTLIGHT_RADIUS = 28.dp

/**
 * Dims the page except a rounded window over [target] (the overlay's coordinates), ringed in [ring], and ending above
 * the card ([cardTop]). The window glides to each step's target as the page brings it into place, staying where it was
 * while the next tab slides in; until there is a first one, the whole page is dimmed. [shown] fades it in.
 */
@Composable
private fun TutorialSpotlightDim(target: Rect?, cardTop: Float?, shown: Float, ring: Color) {
    val density = LocalDensity.current
    val margin = with(density) { SPOTLIGHT_MARGIN.toPx() }
    val cardGap = with(density) { 12.dp.toPx() }
    // The last target seen, kept (not as state: it only changes as [target] does) for the slide to the next one.
    val last = remember { arrayOfNulls<Rect>(1) }
    target?.inflate(margin)?.let { t ->
        val bottom = if (cardTop != null) minOf(t.bottom, cardTop - cardGap) else t.bottom
        last[0] = if (bottom > t.top) t.copy(bottom = bottom) else t
    }
    val hole = last[0]
    val window by androidx.compose.animation.core.animateRectAsState(
        hole ?: Rect.Zero,
        androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "spotlight"
    )
    val open by androidx.compose.animation.core.animateFloatAsState(if (hole != null) 1f else 0f, label = "spotlightOpen")
    val path = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        val radius = SPOTLIGHT_RADIUS.toPx()
        path.reset()
        path.fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        path.addRect(Rect(Offset.Zero, size))
        if (open > 0f && window != Rect.Zero) {
            // Opens from its center as it first appears.
            val w = Rect(window.center, Size.Zero).let { c ->
                Rect(
                    androidx.compose.ui.util.lerp(c.left, window.left, open), androidx.compose.ui.util.lerp(c.top, window.top, open),
                    androidx.compose.ui.util.lerp(c.right, window.right, open), androidx.compose.ui.util.lerp(c.bottom, window.bottom, open)
                )
            }
            path.addRoundRect(androidx.compose.ui.geometry.RoundRect(w, androidx.compose.ui.geometry.CornerRadius(radius)))
            drawPath(path, Color.Black.copy(alpha = 0.55f * shown))
            drawRoundRect(
                ring.copy(alpha = 0.9f * shown * open), w.topLeft, w.size, androidx.compose.ui.geometry.CornerRadius(radius),
                style = Stroke(width = 2.dp.toPx())
            )
        } else {
            drawPath(path, Color.Black.copy(alpha = 0.55f * shown))
        }
    }
}
