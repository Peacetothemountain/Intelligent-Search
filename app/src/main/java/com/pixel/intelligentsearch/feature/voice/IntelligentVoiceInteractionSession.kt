package com.pixel.intelligentsearch.feature.voice

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import com.pixel.intelligentsearch.feature.widget.WidgetActivity

/**
 * The session Android shows for the assistant gesture (corner swipe, power button hold) when Intelligent Search is
 * the digital assistant. It draws nothing of its own, so nothing can flash between the gesture and the search screen:
 * it plays the open haptic, hands off to the search screen (asking it to start listening when Start Assistant with
 * Voice is on) and hides.
 */
class IntelligentVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    /** Set while handing off to search, so the hide that follows doesn't play the dismiss haptic. */
    private var handingOffToSearch = false

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        PixelHapticEngine.get(context).performPredictiveBackHaptic()
        launchFullSearch(startVoice = startsWithVoice())
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
        // Every dismissal ends here, so the haptic plays once per hide.
        if (!handingOffToSearch) PixelHapticEngine.get(context).performPredictiveBackHaptic()
        handingOffToSearch = false
        super.onHide()
    }

    private fun launchFullSearch(startVoice: Boolean) {
        // Safe over the lock screen: the search activities never call setShowWhenLocked, so the system keeps them
        // behind the keyguard until the user unlocks, then shows the search.
        val intent = Intent(context, WidgetActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("query", "")
            putExtra(SearchManager.QUERY, "")
            putExtra(Intent.EXTRA_TEXT, "")
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
