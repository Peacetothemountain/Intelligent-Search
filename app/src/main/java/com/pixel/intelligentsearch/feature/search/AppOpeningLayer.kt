package com.pixel.intelligentsearch.feature.search

import android.os.UserHandle
import android.view.RoundedCorner
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.floor
import kotlin.math.min

/** The test tag on the app-opening layer while an app grows out of its icon. */
internal const val APP_OPENING_TAG = "app_opening"

/**
 * One app opening from the overlay: [from], the bounds it grows out of in the overlay's view (the tapped icon), the
 * icon drawn there now ([startIcon], tinted [startTint] when monochrome), and [start], which opens the app once the
 * motion has reached the middle of the screen.
 */
@Stable
internal class AppOpening(
    val packageName: String,
    val user: UserHandle?,
    val activityName: String?,
    val from: Rect,
    val startIcon: ImageBitmap?,
    val startIconMonochrome: Boolean,
    val startTint: Color,
    val start: () -> Unit
)

/** The app opening now, if any: one at a time, so a second tap while one grows is ignored. */
@Stable
internal class AppOpenings {
    var current: AppOpening? by mutableStateOf(null)

    /** How far the opening app has grown (0 to 1, back to 0 as it fades), for the overlay to blur behind it. */
    var progress by mutableFloatStateOf(0f)

    /** Shows [opening]; false (and nothing changes) while another app is still opening. */
    fun begin(opening: AppOpening): Boolean {
        if (current != null) return false
        current = opening
        return true
    }

    fun end(opening: AppOpening) {
        if (current === opening) {
            current = null
            progress = 0f
        }
    }
}

/** How far the motion has come (0 at the icon, 1 filling the screen) when the app itself is started. */
internal const val APP_OPENING_START_AT = 0.995f

/** How long the drawn splash waits for the app to take over before it gives the overlay back. */
internal const val APP_OPENING_HANDOFF_TIMEOUT_MS = 1500L

/**
 * The opening of Android 17's launcher, drawn in the overlay's own window (Android lets only the launcher move another
 * app's window): the tapped icon becomes a rounded window in the app's splash color that grows out of the icon's place
 * to fill the screen, the icon riding in its middle and turning into the app's splash icon, while the overlay dims
 * behind it. Near the end the app is started, its splash screen showing the same icon on the same color, so it takes
 * over seamlessly. The layer goes once the overlay is out of sight, or fades away if the app never came up.
 */
@Composable
internal fun AppOpeningLayer(openings: AppOpenings) {
    val opening = openings.current ?: return
    key(opening) {
        AppOpeningSurface(opening, onProgress = { openings.progress = it }, onDone = { openings.end(opening) })
    }
}

@Composable
private fun AppOpeningSurface(opening: AppOpening, onProgress: (Float) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val look by produceState<SplashLook?>(null, opening) {
        value = withContext(Dispatchers.Default) {
            runCatching { SplashLooks.resolve(context, opening.packageName, opening.user, opening.activityName) }.getOrNull()
        }
    }
    val background by animateColorAsState(look?.let { Color(it.background) } ?: surfaceColor, tween(120), label = "splash")
    val progress = remember(opening) { Animatable(0f) }
    val fade = remember(opening) { Animatable(1f) }
    val screenCorner = remember(view) {
        view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat()
    }

    LaunchedEffect(opening) {
        launch { snapshotFlow { progress.value.coerceIn(0f, 1f) * fade.value }.collect(onProgress) }
        // Critically damped: it comes to rest without overshooting, so it is still when the real splash takes over.
        launch { progress.animateTo(1f, spring(dampingRatio = 1f, stiffness = 500f)) }
        snapshotFlow { progress.value }.first { it >= APP_OPENING_START_AT }
        opening.start()
        // The app covers the overlay (which then stops) or it never came up: then the overlay comes back.
        val left = withTimeoutOrNull(APP_OPENING_HANDOFF_TIMEOUT_MS) {
            snapshotFlow { lifecycleOwner.lifecycle.currentState }.first { !it.isAtLeast(Lifecycle.State.RESUMED) }
        }
        if (left == null) {
            fade.animateTo(0f, tween(200))
            onDone()
        }
    }
    // Out of sight, the drawn splash has done its job; coming back (after a pause) shows the overlay as it was.
    DisposableEffect(lifecycleOwner, opening) {
        var paused = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> paused = true
                Lifecycle.Event.ON_STOP -> onDone()
                Lifecycle.Event.ON_RESUME -> if (paused) onDone()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val density = view.resources.displayMetrics.density
    Canvas(
        Modifier
            .fillMaxSize()
            .testTag(APP_OPENING_TAG)
            // Nothing under the opening app takes a touch while it grows.
            .pointerInput(Unit) {
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            }
    ) {
        val p = progress.value
        val alpha = fade.value
        val full = Rect(Offset.Zero, size)
        val bounds = lerpRect(opening.from, full, p)
        val startCorner = min(opening.from.width, opening.from.height) / 2f
        val corner = lerp(startCorner, screenCorner ?: (28f * density), p.coerceIn(0f, 1f))

        // The overlay dims behind the growing window, as the home screen does behind a launching app.
        drawRect(Color.Black, alpha = 0.45f * p.coerceIn(0f, 1f) * alpha)
        drawRoundRect(
            color = background,
            topLeft = bounds.topLeft,
            size = bounds.size,
            cornerRadius = CornerRadius(corner),
            alpha = (p / 0.12f).coerceIn(0f, 1f) * alpha
        )

        val center = bounds.center
        val startSize = min(opening.from.width, opening.from.height)
        // The icon that was tapped hands over to the splash icon in the first part of the motion.
        val startAlpha = (1f - p / 0.35f).coerceIn(0f, 1f) * alpha
        val splashAlpha = ((p - 0.05f) / 0.35f).coerceIn(0f, 1f) * alpha
        val currentLook = look
        if (currentLook != null && splashAlpha > 0f) {
            val scale = lerp(startSize / currentLook.visiblePx, 1f, p.coerceIn(0f, 1f))
            drawSplashIcon(currentLook, center, scale, splashAlpha)
        }
        val startIcon = opening.startIcon
        if (startIcon != null && startAlpha > 0f) {
            val visible = currentLook?.visiblePx ?: (160f * density)
            val iconSize = lerp(startSize, visible, p.coerceIn(0f, 1f))
            drawImage(
                image = startIcon,
                dstOffset = IntOffset((center.x - iconSize / 2f).toInt(), (center.y - iconSize / 2f).toInt()),
                dstSize = IntSize(iconSize.toInt(), iconSize.toInt()),
                alpha = startAlpha,
                colorFilter = if (opening.startIconMonochrome) ColorFilter.tint(opening.startTint) else null
            )
        }
    }
}

private fun lerpRect(a: Rect, b: Rect, t: Float) = Rect(
    lerp(a.left, b.left, t), lerp(a.top, b.top, t), lerp(a.right, b.right, t), lerp(a.bottom, b.bottom, t)
)

/** Draws [look]'s splash icon centered on [center] at [scale] of its splash-screen size. */
/**
 * Draws [look]'s splash icon centered on [center] at [scale] of its splash size. At full size it lands on the pixel
 * Android's splash icon view does: its square centered in the window, rounded down.
 */
private fun DrawScope.drawSplashIcon(look: SplashLook, center: Offset, scale: Float, alpha: Float) {
    val size = look.iconSizePx * scale
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val layer = native.saveLayerAlpha(null, (alpha * 255).toInt().coerceIn(0, 255))
        native.translate(floor(center.x - size / 2f), floor(center.y - size / 2f))
        native.scale(scale, scale)
        look.paint(native)
        native.restoreToCount(layer)
    }
}
