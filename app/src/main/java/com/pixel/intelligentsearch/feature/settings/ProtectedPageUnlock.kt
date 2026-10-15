package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.ui.ClickDebouncer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.pixel.intelligentsearch.di.appEntryPoint
import com.pixel.intelligentsearch.core.util.findActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long a settings page takes to slide in; leaving before it settles also popped the page underneath. */
private const val ENTER_TRANSITION_MS = 450L

/**
 * Guards a settings page behind the user's fingerprint, face or screen lock. When the page opens and
 * [isProtectionNeeded] says there is something to protect, the system prompt is shown; on success this
 * returns true, on cancel or failure the page is left exactly once via [onBack]. Devices without a screen
 * lock can't prompt, so the page opens directly.
 *
 * @return whether the page may show its content.
 */
@Composable
fun rememberProtectedPageUnlock(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    isProtectionNeeded: suspend () -> Boolean = { true }
): Boolean {
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val enteredAtMs = remember { SystemClock.uptimeMillis() }
    val currentOnBack by rememberUpdatedState(onBack)

    LaunchedEffect(Unit) {
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
        val activity = context.findActivity()
        when {
            !isProtectionNeeded() || !gate.canPrompt() -> unlocked = true
            activity == null -> leaveOnce()
            else -> gate.authenticate(
                activity = activity,
                title = title,
                subtitle = subtitle,
                onSuccess = { unlocked = true },
                onFailure = { leaveOnce() }
            )
        }
    }
    return unlocked
}
