package com.pixel.intelligentsearch.feature.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.session.PlaybackState
import android.util.Log
import com.pixel.intelligentsearch.core.system.MediaSessionAccess

/**
 * The widget media row's play/pause and next buttons: sends them to the session the row shows
 * ([MediaSessionAccess.current], by the same rule as At a Glance, leaving out hidden apps). Not exported; only the widget's own PendingIntents reach it. The media session
 * listener redraws the widget once the session reports its new state.
 */
class WidgetMediaReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val controller = MediaSessionAccess.current(context) ?: return
        try {
            val controls = controller.transportControls
            when (intent.action) {
                ACTION_PLAY_PAUSE ->
                    if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
                ACTION_NEXT -> controls.skipToNext()
            }
        } catch (e: Exception) {
            Log.w(TAG, "The media session didn't take ${intent.action}", e)
        }
    }

    companion object {
        private const val TAG = "WidgetMediaReceiver"
        const val ACTION_PLAY_PAUSE = "com.pixel.intelligentsearch.WIDGET_MEDIA_PLAY_PAUSE"
        const val ACTION_NEXT = "com.pixel.intelligentsearch.WIDGET_MEDIA_NEXT"
    }
}
