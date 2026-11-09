package com.pixel.intelligentsearch.feature.search.today.cards.widget

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException

/*
 * NG Designs
 */

/**
 * Discover's widget reader, in its own short-lived process (`:reader`). The app binds it, sends the widget ids to read
 * ([MSG_READ]) or the providers to check for the Add card list ([MSG_CHOICES]), and gets back only tiny models: the
 * widgets' views, and the memory they take, never enter the app's process. When the app unbinds, the service stops and
 * its process ends with it, so nothing a widget built outlives the read. Not exported: only this app can bind it.
 * Public only because Android creates it from the manifest.
 */
class WidgetReaderService : Service() {
    private var core: WidgetReaderCore? = null
    private var reply: Messenger? = null

    private val incoming = Messenger(Handler(Looper.getMainLooper()) { msg -> handle(msg); true })

    private fun handle(msg: Message) {
        msg.replyTo?.let { reply = it }
        val data: Bundle = msg.data
        val reader = core ?: WidgetReaderCore(this) { id, read -> send(card(id, read)) }.also { core = it }
        when (msg.what) {
            MSG_READ -> data.getIntArray(KEY_IDS)?.let { reader.read(it) }
            MSG_CHOICES -> {
                val request = data.getInt(KEY_REQUEST)
                val providers = data.getStringArrayList(KEY_PROVIDERS).orEmpty()
                reader.pictureOnly(providers) { picture ->
                    send(Message.obtain(null, MSG_CHOICES_DONE).apply {
                        this.data = Bundle().apply {
                            putInt(KEY_REQUEST, request)
                            putStringArrayList(KEY_PROVIDERS, picture)
                        }
                    })
                }
            }
        }
    }

    private fun card(id: Int, read: SnapCandidate): Message = Message.obtain(null, MSG_CARD).apply {
        data = Bundle().apply {
            putInt(KEY_ID, id)
            putString(KEY_VERDICT, read.verdict.name)
            read.model?.let { putString(KEY_MODEL, SnapCodec.encode(it, withFrom = false).toString()) }
            putString(KEY_FP, read.fingerprint)
        }
    }

    private fun send(message: Message) {
        try {
            reply?.send(message)
        } catch (_: RemoteException) {
            // The app went away; the process ends once it is unbound.
        }
    }

    override fun onBind(intent: Intent?): IBinder = incoming.binder

    override fun onDestroy() {
        core?.stop()
        core = null
        super.onDestroy()
        // Short-lived by design: nothing from the widgets outlives the read.
        Process.killProcess(Process.myPid())
    }
}
