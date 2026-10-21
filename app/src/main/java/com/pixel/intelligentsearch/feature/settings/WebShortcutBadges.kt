package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.get
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine

/** One animation frame shared by every shortcut badge. */
class ShortcutBadgeFrame(val morph: Morph, val rotationAngle: Float, val morphProgress: Float)

/**
 * [frame] is read only inside the Canvas draw lambda, so the running animation redraws the badges without
 * recomposing them or the card around them.
 */
@Composable
fun SynchronizedMorphingShortcutBadge(
    shortcut: String,
    frame: () -> ShortcutBadgeFrame,
    modifier: Modifier = Modifier
) {
    val containerColor = MaterialTheme.colorScheme.secondaryContainer
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer

    val nativePath = remember { android.graphics.Path() }
    val composePath = remember(nativePath) { nativePath.asComposePath() }

    Box(
        modifier = modifier.size(46.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val scaleFactor = size.minDimension * 0.44f
            val current = frame()

            translate(left = center.x, top = center.y) {
                rotate(degrees = current.rotationAngle, pivot = Offset.Zero) {
                    nativePath.rewind()
                    current.morph.toPath(progress = current.morphProgress, path = nativePath)
                    scale(scale = scaleFactor, pivot = Offset.Zero) {
                        drawPath(
                            path = composePath,
                            color = containerColor
                        )
                    }
                }
            }
        }

        // Longer triggers ("!youtube") shrink to fit on one line; ones that already fit keep their usual size.
        Text(
            text = shortcut,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            textAlign = TextAlign.Center,
            softWrap = false,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = if (shortcut.length > 3) 10.sp else 12.sp)
        )
    }
}

@Composable
fun SlideToRemoveAllBar(
    onRemoveAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val context = LocalContext.current
    val hapticEngine = remember(context) { com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context) }

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val thumbSizeDp = 40.dp
    val thumbSizePx = with(LocalDensity.current) { thumbSizeDp.toPx() }
    val maxDragPx = (trackWidthPx - thumbSizePx - with(LocalDensity.current) { 8.dp.toPx() }).coerceAtLeast(0f)

    val dragFraction = if (maxDragPx > 0f) (dragOffsetX / maxDragPx).coerceIn(0f, 1f) else 0f
    val isThresholdReached = dragFraction >= 0.82f
    // The gesture outlives recompositions; read the latest callback so it removes the list shown now.
    val currentOnRemoveAll by androidx.compose.runtime.rememberUpdatedState(onRemoveAll)

    val animDragOffsetX by androidx.compose.animation.core.animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "removeSliderSpring"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .clip(RoundedCornerShape(24.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f + 0.35f * dragFraction),
                RoundedCornerShape(24.dp)
            ),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart
        ) {
            // Fill background behind dragging thumb
            if (trackWidthPx > 0f && animDragOffsetX > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(with(LocalDensity.current) { (animDragOffsetX + thumbSizePx + 8.dp.toPx()).toDp() })
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f + 0.35f * dragFraction))
                )
            }

            // Central Prompt label
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isThresholdReached) "Release to Remove All" else "Slide to Remove All",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isThresholdReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = (1f - dragFraction * 0.7f).coerceIn(0.2f, 1f))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (isThresholdReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = (1f - dragFraction * 0.7f).coerceIn(0.2f, 1f)),
                    modifier = Modifier.size(14.dp)
                )
            }

            // Draggable Thumb Handle
            Box(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset((animDragOffsetX + 4.dp.toPx()).toInt(), 0) }
                    .size(thumbSizeDp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        if (isThresholdReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)
                    )
                    // TalkBack can't slide the thumb, so it offers Remove All as an action. It isn't a click, so a
                    // tap still can't remove everything by accident.
                    .semantics(mergeDescendants = true) {
                        customActions = listOf(CustomAccessibilityAction("Remove all") { currentOnRemoveAll(); true })
                    }
                    .pointerInput(maxDragPx) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragOffsetX >= maxDragPx * 0.82f) {
                                    hapticEngine.performPredictiveBackHaptic(view)
                                    currentOnRemoveAll()
                                }
                                dragOffsetX = 0f
                            },
                            onDragCancel = {
                                dragOffsetX = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val wasPastThreshold = dragOffsetX >= maxDragPx * 0.82f
                                dragOffsetX = (dragOffsetX + dragAmount).coerceIn(0f, maxDragPx)
                                // One tick when crossing the threshold, not on every drag event past it.
                                if (!wasPastThreshold && dragOffsetX >= maxDragPx * 0.82f) {
                                    hapticEngine.performPredictiveBackHaptic(view)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
                    contentDescription = "Slide to remove all",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
