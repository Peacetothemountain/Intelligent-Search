package com.pixel.intelligentsearch.feature.voice

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.compose.material.icons.filled.Search
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import androidx.core.view.WindowCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.service.voice.VoiceInteractionSession
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import com.pixel.intelligentsearch.core.haptics.PixelHapticType
import com.pixel.intelligentsearch.feature.widget.WidgetActivity

/**
 * IntelligentVoiceInteractionSession
 *
 * Modern Android 17 (Canary ZP11) Voice & Assistant Search Session Overlay.
 * Renders edge-to-edge Material 3 Expressive UI in a floating assistant window,
 * featuring a multi-harmonic ripple waveform, live speech-to-text
 * transcription, quick action chips, and seamless handoff to Intelligent Search.
 */
class IntelligentVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    private val sessionLifecycleOwner = SessionLifecycleOwner(
        fallbackOnBackPressed = { hide() }
    )
    /** Set while handing the query to full search, so hiding the sheet doesn't play the dismiss haptic. */
    private var handingOffToSearch = false

    private fun bindViewTreeOwners(targetView: View) {
        targetView.setViewTreeLifecycleOwner(sessionLifecycleOwner)
        targetView.setViewTreeViewModelStoreOwner(sessionLifecycleOwner)
        targetView.setViewTreeSavedStateRegistryOwner(sessionLifecycleOwner)
        targetView.setViewTreeOnBackPressedDispatcherOwner(sessionLifecycleOwner)
    }

    override fun onCreate() {
        super.onCreate()
        sessionLifecycleOwner.onCreate()

        // Configure floating assistant overlay window
        val dialog = window
        val win = dialog?.window
        win?.let { w ->
            w.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
            w.setBackgroundDrawableResource(android.R.color.transparent)
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                w.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
                w.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                w.addFlags(
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                )
                @Suppress("DEPRECATION")
                WindowCompat.setDecorFitsSystemWindows(w, false)
            }

            bindViewTreeOwners(w.decorView)
        }

        Log.d(TAG, "IntelligentVoiceInteractionSession initialized.")
    }

    // No content view: the session draws nothing. It only hands off to the search screen, so nothing can flash on
    // screen between the assistant gesture and search appearing.

    override fun onComputeInsets(insets: Insets) {
        super.onComputeInsets(insets)
        insets.contentInsets.top = 0
        insets.contentInsets.bottom = 0
        // Entire frame intercepts touches so backdrop scrim dismisses cleanly
        insets.touchableInsets = Insets.TOUCHABLE_INSETS_FRAME
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        window?.window?.let { win -> bindViewTreeOwners(win.decorView) }
        sessionLifecycleOwner.onStart()
        sessionLifecycleOwner.onResume()

        // Haptic feedback on assistant invocation
        PixelHapticEngine.get(context).performHaptic(PixelHapticType.OVERLAY_OPEN)

        // The assistant gesture always opens the search screen; with Start Assistant with Voice on, it asks that
        // screen to start listening.
        launchFullSearch("", startVoice = startsWithVoice())
    }

    private fun startsWithVoice(): Boolean {
        // Settings live in credential-protected storage; before the first unlock keep the default (off).
        return try {
            context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)[SettingsManager.ASSISTANT_STARTS_WITH_VOICE]
        } catch (e: IllegalStateException) {
            false
        }
    }

    override fun onHide() {
        sessionLifecycleOwner.onPause()
        sessionLifecycleOwner.onStop()
        // Every dismissal (close, scrim, back) ends here, so the haptic plays once per hide.
        if (!handingOffToSearch) PixelHapticEngine.get(context).performHaptic(PixelHapticType.OVERLAY_DISMISS)
        handingOffToSearch = false
        super.onHide()
    }

    override fun onDestroy() {
        sessionLifecycleOwner.onDestroy()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (sessionLifecycleOwner.onBackPressedDispatcher.hasEnabledCallbacks()) {
            sessionLifecycleOwner.onBackPressedDispatcher.onBackPressed()
        } else {
            hide()
        }
    }

    private fun launchFullSearch(query: String, startVoice: Boolean = false) {
        // Safe over the lock screen: the search activities never call setShowWhenLocked, so the
        // system keeps them behind the keyguard until the user unlocks, then shows the search.
        val intent = Intent(context, WidgetActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("query", query)
            putExtra(SearchManager.QUERY, query)
            putExtra(Intent.EXTRA_TEXT, query)
            putExtra("voice_source", "assistant_overlay")
            putExtra(EXTRA_START_VOICE, startVoice)
        }
        try {
            // The assistant's own launch call: allowed from the assistant gesture without a background-start exemption.
            startAssistantActivity(intent)
        } catch (e: Exception) {
            // A blocked or failed launch must not crash the assistant process.
            Log.w(TAG, "Could not open full search from the assistant session", e)
        }
        handingOffToSearch = true
        hide()
    }

    companion object {
        private const val TAG = "VoiceInteractionSession"
        /** Asks the search screen to start voice input as it opens (Start Assistant with Voice). */
        const val EXTRA_START_VOICE = "start_voice"
    }
}
