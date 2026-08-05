package com.pixel.intelligentsearch.feature.widget.doodle

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider
import com.pixel.intelligentsearch.feature.widget.WidgetActivity

class DoodleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)
        val enableSearchOverlay = prefs.getBoolean("search_overlay_enabled", true)
        
        for (appWidgetId in appWidgetIds) {
            val intent = Intent(context, DoodleWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            
            val views = RemoteViews(context.packageName, R.layout.widget_doodle)
            views.setRemoteAdapter(R.id.doodle_flipper, intent)
            
            // Tap main search text
            val mainIntent = if (enableSearchOverlay) {
                Intent(context, WidgetActivity::class.java).apply {
                    putExtra("SHOW_GEMINI_OVERLAY", false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            } else {
                Intent("android.search.action.GLOBAL_SEARCH").apply {
                    setPackage("com.google.android.googlequicksearchbox")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }.takeIf { context.packageManager.resolveActivity(it, 0) != null }
                ?: Intent(Intent.ACTION_WEB_SEARCH).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            }
            val mainPI = PendingIntent.getActivity(context, appWidgetId + 50000, mainIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.doodle_search_text, mainPI)
            views.setOnClickPendingIntent(R.id.doodle_touch_target, mainPI)
            
            // Tap mic
            val voicePI = PendingIntent.getActivity(context, appWidgetId + 1000, SearchWidgetProvider.getVoiceSearchIntent(context), PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.doodle_mic_icon, voicePI)
            
            // Tap camera
            val cameraPI = PendingIntent.getActivity(context, appWidgetId + 2000, SearchWidgetProvider.getLensSearchIntent(context), PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.doodle_camera_icon, cameraPI)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
