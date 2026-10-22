package com.pixel.intelligentsearch.feature.widget

import android.content.BroadcastReceiver
import android.content.Context
import com.pixel.intelligentsearch.core.system.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** [WidgetUpdater] for both search bar widget designs and the countdown widget. */
class SearchWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context
) : WidgetUpdater {
    override fun updateAll(pendingResult: BroadcastReceiver.PendingResult?) {
        // The countdown widget follows the app's light or dark theme too.
        CountdownWidgetProvider.updateAll(context)
        SearchWidgetProvider.updateAllWidgets(context, pendingResult)
    }
}
