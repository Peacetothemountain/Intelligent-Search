package com.pixel.intelligentsearch.feature.voice

import android.content.Context
import android.media.AudioManager
import android.os.Handler

/**
 * Keeps the speech engine's start and stop tones quiet. Google's on-device engine (Android System Intelligence on a
 * Pixel) plays them itself on the notification channel, and Android gives apps no switch for them, so that channel is
 * muted for the moment a tone can play and given back right after. A channel the user had muted is left as it is.
 *
 * Main thread only.
 */
internal class QuietEarcons(context: Context, private val main: Handler) {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private var muted = false
    private val giveBack = Runnable { restore() }

    /** A tone may play now: quiet the channel, and give it back [HOLD_MS] after the last such moment. */
    fun hold() {
        main.removeCallbacks(giveBack)
        val manager = audio
        if (!muted && manager != null && !manager.isStreamMute(CHANNEL)) {
            // A phone that ties notifications to the ringer may refuse this without Do Not Disturb access: the tone
            // then plays, as it always has.
            muted = runCatching { manager.adjustStreamVolume(CHANNEL, AudioManager.ADJUST_MUTE, 0) }.isSuccess
        }
        main.postDelayed(giveBack, HOLD_MS)
    }

    /** Gives the channel back now. */
    fun restore() {
        main.removeCallbacks(giveBack)
        if (!muted) return
        muted = false
        runCatching { audio?.adjustStreamVolume(CHANNEL, AudioManager.ADJUST_UNMUTE, 0) }
    }

    private companion object {
        const val CHANNEL = AudioManager.STREAM_NOTIFICATION
        /** Each tone is about half a second, and comes as listening starts or stops. */
        const val HOLD_MS = 1_500L
    }
}
