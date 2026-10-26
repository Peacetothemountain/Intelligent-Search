package com.pixel.intelligentsearch.feature.widget

import android.content.BroadcastReceiver
import android.content.Context
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** [WidgetUpdater] for both search bar widget designs and the countdown widget ([SearchWidgetProvider.updateAllWidgets]). */
class SearchWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context
) : WidgetUpdater {
    override fun updateAll(pendingResult: BroadcastReceiver.PendingResult?) {
        SearchWidgetProvider.updateAllWidgets(context, pendingResult)
    }
}
