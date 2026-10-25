package com.pixel.intelligentsearch.core.ui

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import java.util.function.Consumer

/**
 * Set only in unit tests (build.gradle.kts): Robolectric can't blur behind windows, so screens are drawn as on a phone
 * that has blur on. Never set on a phone.
 */
private val BLUR_IN_TESTS: Boolean? = System.getProperty("intelligentsearch.test.crossWindowBlur")?.toBooleanStrictOrNull()

/**
 * Whether the system blurs what is behind a window right now. It turns off with Battery Saver, on phones that can't
 * afford it, and with some accessibility settings, and back on later, so this follows it as it changes. While it is
 * off a window's blur radius draws nothing, and the overlay stands in for it ([overlayScrimAlpha]).
 */
@Composable
fun rememberCrossWindowBlurEnabled(): State<Boolean> {
    val context = LocalContext.current
    val windowManager = context.getSystemService(WindowManager::class.java)
    return produceState(initialValue = BLUR_IN_TESTS ?: windowManager?.isCrossWindowBlurEnabled ?: false, windowManager) {
        if (windowManager == null || BLUR_IN_TESTS != null) return@produceState
        val listener = Consumer<Boolean> { enabled -> value = enabled }
        // Called once at once with the current state, then on every change.
        runCatching { windowManager.addCrossWindowBlurEnabledListener(context.mainExecutor, listener) }
        awaitDispose { runCatching { windowManager.removeCrossWindowBlurEnabledListener(listener) } }
    }
}
