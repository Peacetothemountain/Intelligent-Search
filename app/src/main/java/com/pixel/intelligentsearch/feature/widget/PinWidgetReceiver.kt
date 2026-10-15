package com.pixel.intelligentsearch.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Pins a widget from the widget settings screen ([requestPinWidget]) and, through the launcher's success
 * callback, draws the newly pinned widget straight away. Only reachable through its own explicit PendingIntent.
 */
class PinWidgetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PIN_WIDGET_SUCCESS -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val isMaterial = if (intent.hasExtra(EXTRA_IS_MATERIAL)) {
                        intent.getBooleanExtra(EXTRA_IS_MATERIAL, false)
                    } else null
                    val pendingResult = goAsync()
                    try {
                        SearchWidgetProvider.updateWidgetsAsync(
                            context = context,
                            appWidgetIds = intArrayOf(appWidgetId),
                            forcedIsMaterial = isMaterial,
                            pendingResult = pendingResult
                        )
                    } catch (e: Throwable) {
                        Log.e(TAG, "Failed to update newly pinned widget $appWidgetId", e)
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "PinWidgetReceiver"
        const val ACTION_PIN_WIDGET_SUCCESS = "com.pixel.intelligentsearch.PIN_WIDGET_SUCCESS"
        const val EXTRA_IS_MATERIAL = "extra_is_material"

        private const val REQUEST_CODE_SYSTEM = 1001
        private const val REQUEST_CODE_MATERIAL = 1002

        /**
         * Requests the system launcher to pin an Intelligent Search widget to the home screen.
         * Returns true if pinning is supported by the current launcher and the request was dispatched.
         */
        fun requestPinWidget(context: Context, isMaterial: Boolean): Boolean {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return false
            if (!appWidgetManager.isRequestPinAppWidgetSupported) {
                return false
            }

            val targetClass = if (isMaterial) {
                SearchWidgetMaterialProvider::class.java
            } else {
                SearchWidgetProvider::class.java
            }
            val provider = ComponentName(context, targetClass)
            val requestCode = if (isMaterial) REQUEST_CODE_MATERIAL else REQUEST_CODE_SYSTEM

            // Android 14+ / 17: PendingIntent for requestPinAppWidget MUST be FLAG_MUTABLE
            // because the framework injects AppWidgetManager.EXTRA_APPWIDGET_ID upon completion.
            // Explicit component ensures complete compliance with strict Android 14+ mutable PendingIntent rules.
            val successIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, PinWidgetReceiver::class.java).apply {
                    action = ACTION_PIN_WIDGET_SUCCESS
                    identifier = "pin_widget_${if (isMaterial) "material" else "system"}_$requestCode"
                    putExtra(EXTRA_IS_MATERIAL, isMaterial)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            return try {
                appWidgetManager.requestPinAppWidget(provider, null, successIntent)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to request pin app widget", e)
                false
            }
        }
    }
}
