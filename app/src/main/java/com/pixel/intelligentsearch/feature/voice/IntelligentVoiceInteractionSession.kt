package com.pixel.intelligentsearch.feature.voice

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.core.content.ContextCompat
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import androidx.core.view.WindowCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.service.voice.VoiceInteractionSession
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import com.pixel.intelligentsearch.core.haptics.PixelHapticType
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
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
    private var recognitionManager: VoiceRecognitionManager? = null
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

        recognitionManager = VoiceRecognitionManager(context)
        Log.d(TAG, "IntelligentVoiceInteractionSession initialized.")
    }

    override fun onCreateContentView(): View {
        return ComposeView(context).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
            )
            bindViewTreeOwners(this)

            setContent {
                IntelligentSearchTheme {
                    val rm = recognitionManager
                    if (rm != null) {
                        VoiceAssistantBottomSheet(
                            state = rm.state.value,
                            audioAmplitude = { rm.amplitude.floatValue },
                            spokenText = rm.recognizedText.value,
                            errorMessage = rm.errorMessage.value,
                            onMicClick = { rm.toggleListening() },
                            onDismiss = { hide() },
                            onOpenFullSearch = { query ->
                                launchFullSearch(query)
                            },
                            onQuickPromptClick = { prompt ->
                                launchFullSearch(prompt)
                            }
                        )
                    }
                }
            }
        }
    }

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

        // Listen right away only when the user wants voice and has allowed the microphone. Otherwise open the
        // search with the keyboard ready: nobody should meet a permission error before they can type.
        if (startsWithVoice()) recognitionManager?.startListening() else launchFullSearch("")
    }

    private fun startsWithVoice(): Boolean {
        val micAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!micAllowed) return false
        // Settings live in credential-protected storage; before the first unlock keep the voice default.
        return try {
            context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)[SettingsManager.ASSISTANT_STARTS_WITH_VOICE]
        } catch (e: IllegalStateException) {
            true
        }
    }

    override fun onHide() {
        // Cancel rather than stop: a stopped recognizer still delivers results (and their haptics) after
        // the sheet is gone, and stays bound until the session is destroyed.
        recognitionManager?.cancel()
        sessionLifecycleOwner.onPause()
        sessionLifecycleOwner.onStop()
        // Every dismissal (close, scrim, back) ends here, so the haptic plays once per hide.
        if (!handingOffToSearch) PixelHapticEngine.get(context).performHaptic(PixelHapticType.OVERLAY_DISMISS)
        handingOffToSearch = false
        super.onHide()
    }

    override fun onDestroy() {
        recognitionManager?.destroy()
        recognitionManager = null
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

    private fun launchFullSearch(query: String) {
        // Safe over the lock screen: the search activities never call setShowWhenLocked, so the
        // system keeps them behind the keyguard until the user unlocks, then shows the search.
        val intent = Intent(context, WidgetActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("query", query)
            putExtra(SearchManager.QUERY, query)
            putExtra(Intent.EXTRA_TEXT, query)
            putExtra("voice_source", "assistant_overlay")
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // A blocked or failed launch must not crash the assistant process.
            Log.w(TAG, "Could not open full search from the assistant session", e)
        }
        handingOffToSearch = true
        hide()
    }

    companion object {
        private const val TAG = "VoiceInteractionSession"
    }
}
