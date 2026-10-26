package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.ui.ClickDebouncer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pixel.intelligentsearch.di.appEntryPoint
import com.pixel.intelligentsearch.core.util.findActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long a settings page takes to slide in; leaving before it settles also popped the page underneath. */
private const val ENTER_TRANSITION_MS = 450L

/** Away from the app for longer than this, a protected page asks again when the user comes back. */
private const val RELOCK_AFTER_MS = 30_000L

/**
 * Guards a settings page behind the user's fingerprint, face or screen lock. When the page opens, the
 * system prompt is shown; on success this returns true, on cancel or failure the page is left exactly
 * once via [onBack]. Devices without a screen
 * lock can't prompt, so the page opens directly.
 *
 * While the page is on screen its window blocks screenshots, screen recording and the Recents thumbnail. If
 * the app is left for more than [RELOCK_AFTER_MS], the content is hidden and the prompt shown again on return.
 * Activity results (a file picked for export) arrive before that check, so a picker round-trip completes first.
 *
 * @return whether the page may show its content.
 */
@Composable
fun rememberProtectedPageUnlock(
    title: String,
    subtitle: String,
    onBack: () -> Unit
): Boolean {
    val context = LocalContext.current
    val activity = context.findActivity()
    var unlocked by remember { mutableStateOf(false) }
    var promptRound by remember { mutableIntStateOf(0) }
    var stoppedAtMs by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    val enteredAtMs = remember { SystemClock.uptimeMillis() }
    val currentOnBack by rememberUpdatedState(onBack)

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Other apps' floating windows can't sit over (or spoof) the page. HIDE_OVERLAY_WINDOWS is a normal,
        // auto-granted permission; if it were ever missing the page must still open.
        runCatching { activity?.window?.setHideOverlayWindows(true) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) activity?.setRecentsScreenshotEnabled(false)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            runCatching { activity?.window?.setHideOverlayWindows(false) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) activity?.setRecentsScreenshotEnabled(true)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        stoppedAtMs = SystemClock.elapsedRealtime()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (unlocked && stoppedAtMs != 0L && SystemClock.elapsedRealtime() - stoppedAtMs > RELOCK_AFTER_MS) {
            unlocked = false
            promptRound++
        }
        stoppedAtMs = 0L
    }

    LaunchedEffect(promptRound) {
        val leaveOnce = {
            val leave = {
                // A back tap during the prompt was debounced; clear it so this exit isn't swallowed.
                ClickDebouncer.recordClick(0L)
                ClickDebouncer.recordClick(0L, key = "settings_top_bar_back")
                currentOnBack()
            }
            val elapsed = SystemClock.uptimeMillis() - enteredAtMs
            if (elapsed < ENTER_TRANSITION_MS) {
                scope.launch {
                    delay(ENTER_TRANSITION_MS - elapsed)
                    if (!unlocked) leave()
                }
            } else {
                leave()
            }
        }

        val gate = context.appEntryPoint.biometricSearchGate()
        when {
            !gate.canPrompt() -> unlocked = true
            activity == null -> leaveOnce()
            else -> gate.authenticate(
                activity = activity,
                title = title,
                subtitle = subtitle,
                // The prompt itself can stop the activity; a slow unlock mustn't count as time away.
                onSuccess = {
                    stoppedAtMs = 0L
                    unlocked = true
                },
                onFailure = { leaveOnce() }
            )
        }
    }
    return unlocked
}
