package com.pixel.intelligentsearch.feature.voice

import android.app.Activity
import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import androidx.core.content.edit

/**
 * Keeps the speech engine's start and stop tones quiet. Google's on-device engine (Android System Intelligence on a
 * Pixel) plays them itself on the notification channel, and Android gives apps no switch for them, so that channel is
 * muted for the moment a tone can play and given back right after. A channel the user had muted is left as it is.
 *
 * The channel is often given back after the app's screen has closed (the tone comes as listening stops, as Google's
 * search opens). Android 17 can ignore a volume change from an app with nothing on screen, so the mute is also written
 * down, and [LeftMutedCheck] gives the channel back the next time any of the app's screens shows. Without that, a
 * dropped unmute would leave notifications silent until the user noticed.
 *
 * Main thread only.
 */
internal class QuietEarcons(context: Context, private val main: Handler) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
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
            if (muted) note(app, true)
        }
        if (muted) heldUntil = SystemClock.uptimeMillis() + HOLD_MS
        main.postDelayed(giveBack, HOLD_MS)
    }

    /** Gives the channel back now. */
    fun restore() {
        main.removeCallbacks(giveBack)
        if (!muted) return
        muted = false
        heldUntil = 0L
        unmute(app)
    }

    /** Gives the channel back when a screen of the app shows and an earlier unmute didn't take. */
    object LeftMutedCheck : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            // While a recognizer has just muted it for a tone, the mute is meant to be on. A time, not a count, so a
            // recognizer whose give-back never ran can't keep the channel muted for good.
            if (SystemClock.uptimeMillis() < heldUntil || !wasLeftMuted(activity)) return
            unmute(activity)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    companion object {
        private const val CHANNEL = AudioManager.STREAM_NOTIFICATION
        /** Each tone is about half a second, and comes as listening starts or stops. */
        private const val HOLD_MS = 1_500L
        private const val STORE = "quiet_earcons"
        private const val KEY_MUTED = "notification_channel_muted"

        /** Until when a recognizer in this process means the channel to be muted (uptime); 0 when none does. */
        private var heldUntil = 0L

        /** Unmutes the channel and forgets the mute once Android shows it took. */
        private fun unmute(context: Context) {
            val manager = context.getSystemService(AudioManager::class.java) ?: return
            runCatching { manager.adjustStreamVolume(CHANNEL, AudioManager.ADJUST_UNMUTE, 0) }
            if (!manager.isStreamMute(CHANNEL)) note(context, false)
        }

        private fun wasLeftMuted(context: Context): Boolean = store(context).getBoolean(KEY_MUTED, false)

        private fun note(context: Context, muted: Boolean) {
            store(context).edit { putBoolean(KEY_MUTED, muted) }
        }

        // Device-protected: the overlay can open on the lock screen before the first unlock.
        private fun store(context: Context) =
            context.applicationContext.createDeviceProtectedStorageContext().getSharedPreferences(STORE, Context.MODE_PRIVATE)
    }
}
