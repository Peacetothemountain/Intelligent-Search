package com.pixel.intelligentsearch.feature.voice

import android.content.ComponentName
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.util.Log

/**
 * Intelligent Voice Interaction Service.
 *
 * System entry point for Android Assist & Voice Interaction framework.
 * Validates active assistant binding, sets contextual capture parameters,
 * and maintains clean lifecycle management across device states.
 */
class IntelligentVoiceInteractionService : VoiceInteractionService() {

    companion object {
        private const val TAG = "IntelligentVoiceService"
    }

    override fun onReady() {
        super.onReady()

        val isCurrentActiveAssistant = isActiveService(
            this,
            ComponentName(this, IntelligentVoiceInteractionService::class.java)
        )

        Log.d(TAG, "Intelligent Voice Interaction Service ready. isActiveAssistant=$isCurrentActiveAssistant")

        // The session never consumes the foreground app's AssistStructure or screenshot, so opt out of
        // both. This removes two cross-process captures from every invocation (lower launch latency) and
        // stops needlessly pulling the content of whatever app is on screen.
        runCatching {
            setDisabledShowContext(
                VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT
            )
        }
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        super.onLaunchVoiceAssistFromKeyguard()
        Log.d(TAG, "Voice assist requested from Keyguard.")
        runCatching {
            showSession(
                Bundle().apply {
                    putBoolean("from_keyguard", true)
                },
                VoiceInteractionSession.SHOW_SOURCE_ASSIST_GESTURE
            )
        }
    }

    override fun onShutdown() {
        Log.d(TAG, "Intelligent Voice Interaction Service shutting down.")
        super.onShutdown()
    }

    override fun onDestroy() {
        Log.d(TAG, "Intelligent Voice Interaction Service destroyed.")
        super.onDestroy()
    }
}
