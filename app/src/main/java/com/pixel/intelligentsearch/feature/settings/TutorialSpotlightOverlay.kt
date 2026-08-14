package com.pixel.intelligentsearch.feature.settings
import android.content.SharedPreferences
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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

    val arrowColor = MaterialTheme.colorScheme.primary
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

    val infiniteTransition = rememberInfiniteTransition()
    val slitherPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    val colorPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val targetRect = TutorialManager.getTargetState(currentStep).rect
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 0.99f }
            .background(Color.Transparent)
            .pointerInput(currentStep, targetRect, cardBounds) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val downChange = event.changes.firstOrNull { it.changedToDown() }
                        if (downChange != null) {
                            val pos = downChange.position
                            val isInsideCard = cardBounds.contains(pos)
                            
                            if (stepInfo.requireButtonPress) {
                                if (!isInsideCard) {
                                    downChange.consume()
                                }
                            } else {
                                val isInsideTarget = targetRect != null && run {
                                    val targetCenter = targetRect.center
                                    val dx = targetCenter.x - pos.x
                                    val dy = targetCenter.y - pos.y
                                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                                    val targetRadius = kotlin.math.max(targetRect.width, targetRect.height) / 2f + 8.dp.toPx()
                                    distance <= targetRadius
                                }
                                if (!isInsideCard && !isInsideTarget) {
                                    // Block interactions outside the target and card
                                    downChange.consume()
                                }
                            }
                        }
                        
                        val upChange = event.changes.firstOrNull { it.changedToUp() }
                        if (upChange != null && !stepInfo.requireButtonPress) {
                            val pos = upChange.position
                            val isInsideTarget = targetRect != null && run {
                                val targetCenter = targetRect.center
                                val dx = targetCenter.x - pos.x
                                val dy = targetCenter.y - pos.y
                                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                                val targetRadius = kotlin.math.max(targetRect.width, targetRect.height) / 2f + 8.dp.toPx()
                                distance <= targetRadius
                            }
                            if (isInsideTarget) {
                                // Passed through to target. Advance tutorial with slight delay to allow the gesture to complete.
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(150)
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
        if (stepInfo.showArrow && targetRect != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cardCenter = if (cardBounds != Rect.Zero) cardBounds.center else Offset(size.width / 2, size.height / 2)
                val targetCenter = targetRect.center
                
                val dx = targetCenter.x - cardCenter.x
                val dy = targetCenter.y - cardCenter.y
                
                // Only draw the pulse circle when explicitly requested for this step
                if (stepInfo.showCircle) {
                    drawCircle(
                        color = primaryColor.copy(alpha = pulseAlpha),
                        radius = (kotlin.math.max(targetRect.width, targetRect.height) / 2f + 8.dp.toPx()) * pulseScale,
                        center = targetCenter,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
                
                val startPoint = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                    Offset(if (dx > 0) cardBounds.right else cardBounds.left, cardCenter.y)
                } else {
                    Offset(cardCenter.x, if (dy > 0) cardBounds.bottom else cardBounds.top)
                }
                
                val endPoint = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                    Offset(if (dx > 0) targetRect.left - 24f else targetRect.right + 24f, targetCenter.y)
                } else {
                    Offset(targetCenter.x, if (dy > 0) targetRect.top - 24f else targetRect.bottom + 24f)
                }

                val path = Path()
                path.moveTo(startPoint.x, startPoint.y)
                
                val pdx = endPoint.x - startPoint.x
                val pdy = endPoint.y - startPoint.y
                val length = kotlin.math.hypot(pdx, pdy)
                val angle = kotlin.math.atan2(pdy, pdx)
                val cosA = kotlin.math.cos(angle)
                val sinA = kotlin.math.sin(angle)
                
                val numSquiggles = (length / 40f).toInt().coerceAtLeast(1)
                val amplitude = 15f
                val frequency = (numSquiggles * Math.PI * 2) / length
                
                path.moveTo(startPoint.x, startPoint.y)
                val numPoints = 100
                for (i in 1..numPoints) {
                    val t = i / numPoints.toFloat()
                    val x = t * length
                    val taper = kotlin.math.sin(t * Math.PI).toFloat()
                    val y = kotlin.math.sin(x * frequency - slitherPhase).toFloat() * amplitude * taper
                    
                    val rx = x * cosA - y * sinA
                    val ry = x * sinA + y * cosA
                    
                    path.lineTo(startPoint.x + rx, startPoint.y + ry)
                }
                
                val pathMeasure = androidx.compose.ui.graphics.PathMeasure()
                pathMeasure.setPath(path, false)
                
                val animatedPath = Path()
                pathMeasure.getSegment(0f, pathMeasure.length * animationProgress.value, animatedPath, true)
                
                val gradientBrush = Brush.linearGradient(
                    colors = listOf(primaryColor, tertiaryColor, primaryColor, tertiaryColor, primaryColor),
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
                    path = animatedPath,
                    brush = gradientBrush,
                    style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                if (animationProgress.value > 0.95f) {
                    val alpha = ((animationProgress.value - 0.95f) * 20f).coerceIn(0f, 1f)
                    val arrowHeadLen = 30f
                    val arrowPath = Path().apply {
                        moveTo(endPoint.x, endPoint.y)
                        lineTo(
                            endPoint.x - arrowHeadLen * cos(angle - Math.PI / 6).toFloat(),
                            endPoint.y - arrowHeadLen * sin(angle - Math.PI / 6).toFloat()
                        )
                        moveTo(endPoint.x, endPoint.y)
                        lineTo(
                            endPoint.x - arrowHeadLen * cos(angle + Math.PI / 6).toFloat(),
                            endPoint.y - arrowHeadLen * sin(angle + Math.PI / 6).toFloat()
                        )
                    }
                    drawPath(
                        path = arrowPath,
                        brush = gradientBrush,
                        alpha = alpha,
                        style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }

        val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
        val cardBgColor = if (isDarkTheme) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f)
        }
        val cardTextColor = if (isDarkTheme) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.inverseOnSurface
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .align(stepInfo.cardAlignment)
                .padding(horizontal = 32.dp, vertical = 96.dp)
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                .onGloballyPositioned { coordinates ->
                    cardBounds = coordinates.boundsInRoot()
                }
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stepInfo.title,
                    fontSize = 18.sp,
                    color = cardTextColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stepInfo.text,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = cardTextColor.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))
                
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "OK", fontWeight = FontWeight.Bold)
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

