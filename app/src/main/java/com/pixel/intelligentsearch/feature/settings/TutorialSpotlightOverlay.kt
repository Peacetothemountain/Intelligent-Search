package com.pixel.intelligentsearch.feature.settings

import android.content.SharedPreferences
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class TutorialStepInfo(
    val title: String,
    val text: String,
    val cardAlignment: Alignment = Alignment.Center,
    val showArrow: Boolean = true,
    val requireButtonPress: Boolean = true,
    val showCircle: Boolean = false
)

@Composable
fun TutorialSpotlightOverlay(
    prefs: SharedPreferences,
    stepsInfo: Map<Int, TutorialStepInfo>,
    onComplete: () -> Unit,
    onStepAdvance: (Int) -> Unit = {}
) {
    if (!TutorialManager.isTutorialActive(prefs)) return

    var currentStep by rememberIntPreference(prefs, "tutorial_step", 0)
    val stepInfo = stepsInfo[currentStep]
    if (stepInfo == null) {
        LaunchedEffect(currentStep) {
            onComplete()
        }
        return
    }

    var overlayRootOffset by remember { mutableStateOf(Offset.Zero) }
    var cardBounds by remember { mutableStateOf(Rect.Zero) }
    val coroutineScope = rememberCoroutineScope()
    
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(currentStep) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            1f,
            animationSpec = androidx.compose.animation.core.spring(
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy
            )
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "tutorial_transition")
    val slitherPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "slither"
    )
    val colorPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "color"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    val rawTargetRect = TutorialManager.getTargetState(currentStep).rect
    val localTargetRect = remember(rawTargetRect, overlayRootOffset) {
        rawTargetRect?.translate(-overlayRootOffset.x, -overlayRootOffset.y)
    }
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val density = LocalDensity.current
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .graphicsLayer { alpha = 0.99f }
            .background(Color.Transparent)
            .onGloballyPositioned { coordinates ->
                overlayRootOffset = coordinates.positionInRoot()
            }
            .pointerInput(currentStep, localTargetRect, cardBounds) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val downChange = event.changes.firstOrNull { it.changedToDown() }
                        if (downChange != null) {
                            val pos = downChange.position
                            val isInsideCard = cardBounds != Rect.Zero && cardBounds.contains(pos)
                            
                            if (stepInfo.requireButtonPress) {
                                if (!isInsideCard) {
                                    downChange.consume()
                                }
                            } else {
                                val isInsideTarget = localTargetRect != null && run {
                                    val targetCenter = localTargetRect.center
                                    val dx = targetCenter.x - pos.x
                                    val dy = targetCenter.y - pos.y
                                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                                    val targetRadius = kotlin.math.max(localTargetRect.width, localTargetRect.height) / 2f + 8.dp.toPx()
                                    distance <= targetRadius
                                }
                                if (!isInsideCard && !isInsideTarget) {
                                    downChange.consume()
                                }
                            }
                        }
                        
                        val upChange = event.changes.firstOrNull { it.changedToUp() }
                        if (upChange != null && !stepInfo.requireButtonPress) {
                            val pos = upChange.position
                            val isInsideTarget = localTargetRect != null && run {
                                val targetCenter = localTargetRect.center
                                val dx = targetCenter.x - pos.x
                                val dy = targetCenter.y - pos.y
                                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                                val targetRadius = kotlin.math.max(localTargetRect.width, localTargetRect.height) / 2f + 8.dp.toPx()
                                distance <= targetRadius
                            }
                            if (isInsideTarget) {
                                coroutineScope.launch {
                                    delay(150)
                                    if (currentStep < TutorialManager.TOTAL_STEPS - 1) {
                                        val nextStep = currentStep + 1
                                        currentStep = nextStep
                                        onStepAdvance(nextStep)
                                    } else {
                                        TutorialManager.completeTutorial(prefs)
                                        onComplete()
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        // Squiggle Arrow and Target Spotlight
        if (stepInfo.showArrow && localTargetRect != null && cardBounds != Rect.Zero) {
            val squigglePath = remember { Path() }
            val animatedSquigglePath = remember { Path() }
            val pathMeasure = remember { androidx.compose.ui.graphics.PathMeasure() }
            val gradientColors = remember(primaryColor, tertiaryColor) {
                listOf(primaryColor, tertiaryColor, primaryColor, tertiaryColor, primaryColor)
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val targetCenter = localTargetRect.center
                
                // Only draw the pulse circle when explicitly requested for this step
                if (stepInfo.showCircle) {
                    drawCircle(
                        color = primaryColor,
                        alpha = pulseAlpha,
                        radius = (kotlin.math.max(localTargetRect.width, localTargetRect.height) / 2f + 8.dp.toPx()) * pulseScale,
                        center = targetCenter,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
                
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
                val length = kotlin.math.hypot(pdx, pdy)
                val angle = kotlin.math.atan2(pdy, pdx)
                val cosA = kotlin.math.cos(angle)
                val sinA = kotlin.math.sin(angle)

                if (length >= 12f) {
                    val numSquiggles = (length / 32f).toInt().coerceIn(1, 4)
                    val amplitude = kotlin.math.min(12f, length * 0.20f)
                    val frequency = (numSquiggles * Math.PI * 2) / length

                    squigglePath.reset()
                    squigglePath.moveTo(startPoint.x, startPoint.y)

                    val numPoints = 80
                    for (i in 1..numPoints) {
                        val t = i / numPoints.toFloat()
                        val x = t * length
                        val taper = kotlin.math.sin(t * Math.PI).toFloat()
                        val y = kotlin.math.sin(x * frequency - slitherPhase).toFloat() * amplitude * taper

                        val rx = x * cosA - y * sinA
                        val ry = x * sinA + y * cosA

                        squigglePath.lineTo(startPoint.x + rx, startPoint.y + ry)
                    }

                    pathMeasure.setPath(squigglePath, false)

                    animatedSquigglePath.reset()
                    pathMeasure.getSegment(0f, pathMeasure.length * animationProgress.value, animatedSquigglePath, true)

                    val gradientBrush = Brush.linearGradient(
                        colors = gradientColors,
                        start = Offset(
                            startPoint.x + pdx * (colorPhase - 1f),
                            startPoint.y + pdy * (colorPhase - 1f)
                        ),
                        end = Offset(
                            startPoint.x + pdx * (colorPhase + 1f),
                            startPoint.y + pdy * (colorPhase + 1f)
                        )
                    )

                    drawPath(
                        path = animatedSquigglePath,
                        brush = gradientBrush,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    if (animationProgress.value > 0.90f) {
                        val alpha = ((animationProgress.value - 0.90f) * 10f).coerceIn(0f, 1f)
                        val arrowHeadLen = kotlin.math.min(22f, length * 0.35f)
                        val arrowPath = Path().apply {
                            moveTo(endPoint.x, endPoint.y)
                            lineTo(
                                endPoint.x - arrowHeadLen * cos(angle - Math.PI / 5.5).toFloat(),
                                endPoint.y - arrowHeadLen * sin(angle - Math.PI / 5.5).toFloat()
                            )
                            moveTo(endPoint.x, endPoint.y)
                            lineTo(
                                endPoint.x - arrowHeadLen * cos(angle + Math.PI / 5.5).toFloat(),
                                endPoint.y - arrowHeadLen * sin(angle + Math.PI / 5.5).toFloat()
                            )
                        }
                        drawPath(
                            path = arrowPath,
                            brush = gradientBrush,
                            alpha = alpha,
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }
        }

        val isDarkTheme = isSystemInDarkTheme()
        val cardBgColor = if (isDarkTheme) {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f)
        } else {
            MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.90f)
        }
        val cardTextColor = if (isDarkTheme) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.inverseOnSurface
        }

        // Calculate dynamic alignment and responsive padding based on targetRect location
        val isTargetInBottomHalf = localTargetRect != null && (localTargetRect.center.y > constraints.maxHeight / 2f)

        val cardModifier = when {
            localTargetRect != null && stepInfo.showArrow && isTargetInBottomHalf -> {
                // Target is in the bottom half (e.g. search bar). Position Card ABOVE target.
                val targetTopDp = with(density) { localTargetRect.top.toDp() }
                val arrowGap = 44.dp
                val bottomSpacing = (maxHeight - targetTopDp + arrowGap).coerceAtLeast(16.dp)
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomSpacing)
                    .padding(top = statusBarTopPadding + 8.dp)
                    .padding(horizontal = 24.dp)
            }
            localTargetRect != null && stepInfo.showArrow && !isTargetInBottomHalf -> {
                // Target is in the top half (e.g. top search bar). Position Card BELOW target.
                val targetBottomDp = with(density) { localTargetRect.bottom.toDp() }
                val arrowGap = 44.dp
                val topSpacing = (targetBottomDp + arrowGap).coerceAtLeast(statusBarTopPadding + 8.dp)
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topSpacing)
                    .padding(bottom = 16.dp)
                    .padding(horizontal = 24.dp)
            }
            else -> {
                Modifier
                    .align(stepInfo.cardAlignment)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            }
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = cardModifier
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                .onGloballyPositioned { coordinates ->
                    cardBounds = Rect(
                        offset = coordinates.positionInRoot() - overlayRootOffset,
                        size = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stepInfo.title,
                    fontSize = 18.sp,
                    color = cardTextColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stepInfo.text,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = cardTextColor.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                if (stepInfo.requireButtonPress) {
                    Button(
                        onClick = {
                            if (currentStep < TutorialManager.TOTAL_STEPS - 1) {
                                val nextStep = currentStep + 1
                                currentStep = nextStep
                                onStepAdvance(nextStep)
                            } else {
                                TutorialManager.completeTutorial(prefs)
                                onComplete()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "OK", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                } else {
                    Text(
                        text = "Tap the highlighted area to continue",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

