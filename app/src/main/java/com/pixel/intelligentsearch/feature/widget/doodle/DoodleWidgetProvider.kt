package com.pixel.intelligentsearch.feature.widget.doodle

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.pixel.intelligentsearch.R

class DoodleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val intent = Intent(context, DoodleWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            
            val views = RemoteViews(context.packageName, R.layout.widget_doodle)
            views.setRemoteAdapter(R.id.doodle_flipper, intent)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
