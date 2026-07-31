package com.pixel.intelligentsearch.feature.voice
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class IntelligentVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return IntelligentVoiceInteractionSession(this)
    }
}
