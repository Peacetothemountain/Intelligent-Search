package com.pixel.intelligentsearch.feature.widget
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class PinWidgetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.pixel.intelligentsearch.PIN_WIDGET") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val myProvider = ComponentName(context, SearchWidgetProvider::class.java)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val successIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(context, PinWidgetReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                appWidgetManager.requestPinAppWidget(myProvider, null, successIntent)
            }
        }
    }
}
