package com.pixel.intelligentsearch.feature.voice

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log

/**
 * IntelligentVoiceInteractionSessionService
 *
 * Android 17 (Canary ZP11) Voice Interaction Session Factory Service.
 * Binds to system voice interactions and instantiates Compose-driven IntelligentVoiceInteractionSession.
 */
class IntelligentVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "IntelligentVoiceInteractionSessionService created.")
    }

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        Log.i(TAG, "Dispensing new IntelligentVoiceInteractionSession.")
        return IntelligentVoiceInteractionSession(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d(TAG, "onTrimMemory received: level=$level")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "onLowMemory warning in VoiceInteractionSessionService.")
    }

    companion object {
        private const val TAG = "VoiceSessionService"
    }
}
