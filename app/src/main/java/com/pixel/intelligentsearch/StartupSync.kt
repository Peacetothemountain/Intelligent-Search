package com.pixel.intelligentsearch

import android.content.Context
import com.pixel.intelligentsearch.core.currency.CurrencyRateRepository
import com.pixel.intelligentsearch.feature.search.SelectedTextSearchActivity
import com.pixel.intelligentsearch.feature.widget.WidgetDoodles

/**
 * Things [App] brings in line with the settings at every start, once the user is unlocked: state Android or WorkManager
 * keeps outside the app's settings, which a restore, a data clear or an update can leave out of step.
 */
internal object StartupSync {

    fun afterUnlock(context: Context, currencyRates: CurrencyRateRepository) {
        // Component states are kept by Android, outside the app's data; turn the selection entry back on where an
        // older version's switch turned it off.
        SelectedTextSearchActivity.ensureEnabled(context)
        // Redraw the widgets when the day's doodle next changes (a doodle day starts or ends at midnight).
        WidgetDoodles.scheduleNextChange(context)
        // Keep the daily currency refresh only while the Calculator and Live Currency Rates are on and Tor isn't the
        // engine.
        currencyRates.syncSchedule()
    }
}
